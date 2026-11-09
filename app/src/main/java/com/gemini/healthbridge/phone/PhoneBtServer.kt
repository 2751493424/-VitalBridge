package com.gemini.healthbridge.phone

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.gemini.healthbridge.core.AckMessage
import com.gemini.healthbridge.core.BiaProfileMessage
import com.gemini.healthbridge.core.BtLink
import com.gemini.healthbridge.core.Cycle
import com.gemini.healthbridge.core.CycleSummaryMessage
import com.gemini.healthbridge.core.Frame
import com.gemini.healthbridge.core.FrameCodec
import com.gemini.healthbridge.core.FrameKinds
import com.gemini.healthbridge.core.HelloMessage
import com.gemini.healthbridge.core.HourStatsMessage
import com.gemini.healthbridge.core.Odi
import com.gemini.healthbridge.core.ApneaSummaryMessage
import com.gemini.healthbridge.core.EnergySummaryMessage
import com.gemini.healthbridge.core.ExtrasSummaryMessage
import com.gemini.healthbridge.core.OdiSummaryMessage
import com.gemini.healthbridge.core.RecordType
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.SleepSummaryMessage
import com.gemini.healthbridge.core.Spo2RecordsMessage
import com.gemini.healthbridge.core.StressSummaryMessage
import com.gemini.healthbridge.core.SyncMessage
import com.gemini.healthbridge.core.Text
import com.gemini.healthbridge.core.db.toEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 蓝牙直连服务端（自愈式）：
 * 单循环监听手表的 RFCOMM 连接；权限缺失/蓝牙未开时轮询等待，条件满足自动开始；
 * 连接断开后自动回到监听状态。重复调用 start() 是安全的。
 */
object PhoneBtServer {
    private const val TAG = "PhoneBtServer"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val loopStarted = AtomicBoolean(false)

    /**
     * 单帧的**字符数**上限（2026-11-03 加）。
     *
     * ## 为什么必须有
     * `BufferedReader.readLine()` **没有上限**：对端只要发一条没有换行符的巨长数据，
     * 手机就会一直往内存里堆，直到 OOM 闪退。RFCOMM 监听是 `insecure`（不要求鉴权），
     * 蓝牙范围内任何设备都能连上，所以这是**外部输入**，不是"我们自己的表不会这样"。
     *
     * 512K 字符对本项目**任何一帧都绰绰有余**：最大的是心电波形帧（30 秒 @ 500Hz ≈
     * 1.5 万点、约 8 字符/点 ≈ 120K）与 PPG 三通道（同量级）。
     * 超限的行**整行丢弃**（读到行尾为止，保持流的位置正确），并按解码失败处理。
     */
    private const val MAX_LINE_CHARS = 512 * 1024

    /**
     * 待处理帧队列容量。
     *
     * ⚠ 队列**必须有界**：无界队列等于把"对端发得比我们处理得快"变成内存泄漏。
     *   满了以后 `send` 挂起 ⇒ 不再读 socket ⇒ 对端的 TCP/RFCOMM 窗口自然收紧（反压），
     *   这正是我们要的行为（手表本来就等 ACK 才发下一批）。
     */
    private const val FRAME_QUEUE_CAPACITY = 16

    /** 记住最后一次成功连上的手表地址（用于"未配对但确实是那只表"的兼容放行）。 */
    private const val PREFS = "bt_server"
    private const val K_LAST_ADDR = "last_watch_addr"

    @Volatile private var current: Connection? = null

    /**
     * 当前**在线的那只表**的名字与 MAC（2026-10-21）。
     *
     * ⚠ OTA 只能连它：以前"遍历所有已配对设备"会连到手柄/耳机上
     *   （用户实测连到了 `GameSir-Nova Pro`，报 BluetoothSocketException）。
     */
    @Volatile
    var connectedName: String? = null
        private set

    @Volatile
    var connectedAddress: String? = null
        private set

    private class Connection(
        val device: BluetoothDevice,
        val socket: BluetoothSocket,
        val reader: BufferedReader,
        val out: OutputStream,
    ) {
        @Volatile var open = true
    }

    /**
     * 蓝牙通道的当前状态文案。
     *
     * ⚠⚠ 值类型是 [ResText]（**资源 id + 参数**），不是 `String`。
     *    以前是 `MutableStateFlow<String>`，文案在「状态变化」那一刻就求好了，
     *    于是**切语言之后它一直停在旧语言**；而紧挨着它的那行
     *    （`stringResource(R.string.s_46fde64a)`）是现取的、立刻就变英文 ——
     *    同一屏一行中文一行英文，用户 2026-10-02 截图报过。
     *    详见 [ResText] 的说明。
     */
    private val _status = MutableStateFlow(ResText.of(R.string.s_8ab7e639))
    val status: StateFlow<ResText> = _status

    /**
     * 是否有手表连着（模块 #15，2026-09-29）。
     *
     * ## 为什么必须有这个，不能拿 [status] 去 `contains("已连接")`
     *
     * [status] 是**给人看的文案**，它随语言变。改造前 `SyncRepository.diagnose()` 里
     * 写的是 `status.value.contains("已连接")` —— 在英文/韩文界面下那个判断**永远为假**，
     * 于是诊断结论会一直建议用户"改用蓝牙通道"，而蓝牙其实好着。
     * 这类错**不报错、不崩溃**，只是悄悄给出相反的结论。
     *
     * ⚠ 纪律：**状态一律用结构化字段判**，显示文案只用于显示。
     * 以后新增状态（"重试中"等）也照这个做法加，别再去匹配文案。
     */
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun start(context: Context) {
        if (!loopStarted.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch { runLoop(app) }
    }

    private suspend fun runLoop(context: Context) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            _status.value = ResText.of(R.string.s_b75c0ab4)
            return
        }
        while (true) {
            // 等待前置条件
            if (!adapter.isEnabled) {
                _status.value = ResText.of(R.string.s_8abd70b3)
                Thread.sleep(5000)
                continue
            }
            if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                _status.value = ResText.of(R.string.s_a52c9ec8)
                Thread.sleep(3000)
                continue
            }
            // 监听一次连接（阻塞直到手表连上）
            //
            // ⚠ 这条状态说的是「手机端已经准备好、**等手表来连**」——
            //   所以文案必须是**一句可执行的指令**，不能写成「等待手表连接…」（2026-10-10 改）。
            //   原因：手机与手表在**系统层**本来就已经配对/连着了（Wear OS 一直连着），
            //   用户看到「等待手表连接」只会以为蓝牙坏了、反复去设置里重连 ——
            //   而真正要做的事是**把手表上的脉迹打开**（RFCOMM 由手表端发起）。
            var server: BluetoothServerSocket? = null
            try {
                @Suppress("DEPRECATION")
                server = adapter.listenUsingInsecureRfcommWithServiceRecord(
                    "gemini-health-bridge", UUID.fromString(BtLink.UUID_STRING)
                )
                _status.value = ResText.of(R.string.s_6c83df94)
                val socket = server.accept()
                handleConnection(context, socket)
            } catch (e: SecurityException) {
                _status.value = ResText.of(R.string.s_a52c9ec8)
                Thread.sleep(3000)
            } catch (e: Exception) {
                Log.w(TAG, "accept error", e)
                _status.value = ResText.of(R.string.s_c13de2e7)
                Thread.sleep(3000)
            } finally {
                runCatching { server?.close() }
            }
        }
    }

    private suspend fun handleConnection(context: Context, socket: BluetoothSocket) {
        var conn: Connection? = null
        try {
            // ⚠ 监听是 insecure 的（见 runLoop 的说明），所以**必须自己认设备**：
            //   不是已配对（或上次连过的那只表）就直接关掉，别让它有机会推帧。
            if (!trustDevice(context, socket.remoteDevice)) {
                runCatching { socket.close() }
                return
            }
            val name = runCatching { socket.remoteDevice.name }.getOrDefault("watch")
            conn = Connection(
                device = socket.remoteDevice,
                socket = socket,
                reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8)),
                out = socket.outputStream,
            )
            current?.open = false
            current = conn
            connectedName = name
            connectedAddress = runCatching { socket.remoteDevice.address }.getOrNull()
            _status.value = ResText.of(R.string.s_c60f3406, name)
            _connected.value = true
            // ⚠ 这里**不再** `setEvent`「手表已通过蓝牙连接：X」（2026-10-13 删，与
            //   `SyncRepository.onHello` 同一条理由）：连接是**状态**不是结果，
            //   它由功能页状态卡的 `手表在线：X` 说；而事件行要留给真正的同步结果，
            //   否则每连一次就把上一次"已同步 N 条"覆盖掉。
            val appContext = context.applicationContext
            // ⚠⚠ 单消费者（2026-11-03 改）：原来是每读一行就 `scope.launch { onFrame }`，
            //   两个问题都真实存在：
            //   ① **不保证顺序** —— DATA 与 ACK 可能乱序处理，落库与回执对不上；
            //   ② **无并发上限** —— 对端连发 N 帧就起 N 个协程，一起写库还会撞 SQLITE_BUSY。
            //   现在：读循环只负责入队，一个协程按序消费，队列满则反压。
            val frames = Channel<Frame>(capacity = FRAME_QUEUE_CAPACITY)
            val consumer = scope.launch {
                for (f in frames) runCatching { onFrame(appContext, f) }
            }
            try {
                while (conn.open) {
                    val line = readLineBounded(conn.reader) ?: break
                    if (line.isEmpty()) continue          // 超长帧/空行：已丢弃，继续读下一行
                    val frame = FrameCodec.decode(line) ?: continue
                    frames.send(frame)
                }
            } finally {
                frames.close()
                // 有上限地等收尾：onFrame 里可能正在读三星健康（秒级），不能无限等
                withTimeoutOrNull(5_000) { consumer.join() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "connection error", e)
        } finally {
            conn?.open = false
            if (current === conn) {
                current = null
                _connected.value = false
            connectedName = null
            connectedAddress = null
                _status.value = ResText.of(R.string.s_6c83df94)
            }
        }
    }

    /**
     * 读一行，最多 [MAX_LINE_CHARS] 个字符。
     *
     * @return `null` = 流结束；空串 = 这一行超长**已被丢弃**（或本来就是空行）；
     *   其余为正常一行。
     *
     * ⚠ 用 `BufferedReader.read()` 逐字符而不是 `readLine()`：后者没有长度上限。
     *   逐字符看起来慢，但 `BufferedReader` 内部有 8K 缓冲，这里是纯内存操作。
     * ⚠ 超限后**继续读到换行**（而不是立刻返回）：否则残余的半行会被当成下一帧的开头，
     *   后面每一帧都跟着错位。
     */
    private fun readLineBounded(reader: BufferedReader): String? {
        val sb = StringBuilder(256)
        var overflow = false
        while (true) {
            val c = reader.read()
            if (c < 0) {
                if (sb.isEmpty() && !overflow) return null
                break
            }
            if (c == '\n'.code) break
            if (sb.length >= MAX_LINE_CHARS) {
                overflow = true
                continue
            }
            sb.append(c.toChar())
        }
        if (overflow) {
            Log.w(TAG, "帧超过 $MAX_LINE_CHARS 字符，已丢弃")
            return ""
        }
        return sb.toString().trimEnd('\r')
    }

    /**
     * 这个对端可信吗？
     *
     * 判据（满足任一即可）：
     * ① **已配对**（`BOND_BONDED`）—— Wear OS 手表与手机在系统层必然配对过，
     *    所以正常用户永远走这一条，不会因为这道校验而连不上；
     * ② 地址等于**上次成功连上**的那只表 —— 兜底某些 ROM 上 `bondState` 不更新的情况，
     *    不引入"以前能连、升级后连不上"的回归。
     *
     * 都不满足 ⇒ 拒绝。这一步挡的是"蓝牙范围内任何设备都能冒充手表推健康数据"，
     * 那些伪造数据会直接污染血压预估、HRV 基线与压力分的分母。
     */
    private fun trustDevice(context: Context, dev: BluetoothDevice): Boolean {
        val addr = runCatching { dev.address }.getOrNull()
        if (addr.isNullOrBlank()) return false
        val bonded = runCatching { dev.bondState == BluetoothDevice.BOND_BONDED }
            .getOrDefault(false)
        if (bonded) {
            runCatching {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(K_LAST_ADDR, addr).apply()
            }
            return true
        }
        val last = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(K_LAST_ADDR, null)
        }.getOrNull()
        if (!last.isNullOrBlank() && last.equals(addr, ignoreCase = true)) return true
        Log.w(TAG, "拒绝未配对设备：$addr")
        return false
    }

    private suspend fun onFrame(context: Context, frame: Frame) {
        when (frame.kind) {
            // 手表 -> 手机：更新回执（2026-10-28）。载荷 `状态|文本`。
            // ⚠ 文本是手表端已经拼好的人话，原样显示即可（不要在这里做判断）。
            // ⚠ 用 `setEvent` 而不是弹窗：更新可能持续几分钟，弹窗会挡住用户正在做的事。
            FrameKinds.WATCH_UPDATE_ACK -> runCatching {
                val raw = frame.data
                val state = raw.substringBefore('|')
                val text = raw.substringAfter('|', "")
                if (state == "ok" && text.isNotEmpty()) {
                    SyncRepository.setEvent(ResText.raw(text))
                } else if (state == "busy") {
                    SyncRepository.setEvent(ResText.raw("手表上已有一个更新任务在进行"))
                }
            }

            FrameKinds.HELLO -> runCatching {
                run {
                    val hello = json.decodeFromString(HelloMessage.serializer(), frame.data)
                    // 记下手表版本：用于「手表是否有更新」的判断
                    AppUpdatePrefs.setWatchVersion(context, hello.watchVersion)
                    SyncRepository.onHello(hello)
                }
                // 推送三星健康当日小时统计（手表全天图表用）
                pushHourStats(context)
                // 推送三星健康近期血氧记录（手表血氧页用）
                pushSpo2Records(context)
                // 推送最近一晚睡眠摘要（手表睡眠页用）
                pushSleepSummary(context)
                // 推送用户档案（手表测体成分 BIA 的前提：手表读不到三星健康的 USER_PROFILE）
                pushBiaProfile(context)
                // 推送经期一行摘要（模块 #13；即使未开启也推，让手表显示"未开启"）
                pushCycleSummary(context)
                // 推送压力/疲劳评分（模块 #12；算不出来也推，让手表显示原因而不是旧分数）
                pushStressSummary(context)
                // 推送一晚氧减指数（模块 #11；手表没有三星数据可算，只能看推过去的结果）
                pushOdiSummary()
                // 能量评分/呼吸暂停：与 ODI 同批推送。
                // ⚠ HELLO 与 DONE **两处都必须调** —— 只调 HELLO 那一处的话，
                // 刚同步读到的数据算出的新结果要等下次连上才更新，而且是**静默**的。
                pushEnergySummary()
                pushApneaSummary()
                // 三星扩展指标（活动/饮食/血糖/心律不齐）：同样 HELLO 与 DONE 都要推
                pushExtrasSummary(context)
                // 双方都在线：自动发起一次同步
                sendFrame(Frame(FrameKinds.SYNC_REQ, System.currentTimeMillis().toString()))
            }

            FrameKinds.DATA -> runCatching {
                val msg = json.decodeFromString(SyncMessage.serializer(), frame.data)
                val inserted = SyncRepository.insertSync(msg)
                val parts = mutableListOf(ResText.of(R.string.s_b07e17ca, inserted))

                // 心电不写 Health Connect，也不写三星健康：
                // Health Connect 的记录类型里根本没有 ECG / 心律条目（只有 HeartRate、HRV、
                // 血氧、血压等），三星健康侧的 ECG 也只对白名单伙伴开放，第三方写入必然失败。
                // 所以心电（波形 + 结论）只存在手表和手机本地，UI 上也已明确告知。
                if (msg.type == RecordType.ECG) {
                    parts += ResText.of(R.string.s_1a20c6c6)
                } else if (msg.type == RecordType.TEMP) {
                    // 体温同样**刻意不中转**，理由与心电不同：
                    // 它不是"写不进去"，而是"写进去是错的"。手表量的是**腕部皮肤温度**（约 30~36 ℃），
                    // 而三星健康的 `BODY_TEMPERATURE` 语义是"体温"。把皮肤温度当体温写进去，
                    // 会在三星健康里留下一条看起来正常、实际偏低 1~3 ℃ 的体温记录 —— 比不写更糟。
                    parts += ResText.of(R.string.s_e3ffabef)
                } else if (msg.type == RecordType.BIA) {
                    // 体成分**刻意不中转**，理由与心电、体温都不同：
                    // 不是"写不进去"（三星健康的 BODY_COMPOSITION 自身就是可写的），
                    // 而是写进去会**重复**：手机端本来就会从三星健康读回它自己的体成分记录，
                    // 一旦把手表这条也写进三星健康，下一次同步就会以 `samsung_body` 的身份
                    // 再落一行一模一样的值 —— 一次测量变成两条记录，看列表的人会以为测了两次。
                    // 所以手表测的留在 `watch_bia` 一侧，两端各自展示、来源清楚。
                    parts += ResText.of(R.string.s_d9278b92)
                } else if (msg.type == RecordType.PPG) {
                    // 原始光信号**刻意不中转**，理由与心电最接近、但更硬：
                    // 三星健康与 Health Connect 都没有"原始光信号"这一类条目
                    // （它们只有算好的血氧/心率），而原始的 LED 光强 ADC 计数既没有物理单位、
                    // 跨设备也不可解读 —— 想中转都无处可写。
                    // 所以波形只存在手表与手机本地，供后续自研算法（呼吸率/压力/PTT）使用。
                    parts += ResText.of(R.string.s_1f9ab582)
                } else if (msg.type == RecordType.HRV) {
                    // HRV 记录的载体是**心电的逐拍间期**，而心电本身就写不进 HC/三星健康
                    // （HC 的记录类型里没有 ECG/心律，三星健康的 ECG 只对白名单伙伴开放）。
                    // 更根本的是：HC 那个 HRV 类型是 `HeartRateVariabilityRmssdRecord` ——
                    // 它只装一个**算好的 RMSSD 数值**，如果把手表这条推进去，
                    // 将来手机端从 HC 读回来时会变成一条来源为"samsung/HC"的 HRV 记录，
                    // 与手表这条在列表里并列出现、看起来像测了两次，且看不出哪条是我们自己算的。
                    // 所以它也只存本机，口径与来源都留在自己这边。
                    parts += ResText.of(R.string.s_c50841b0)
                } else if (msg.type == RecordType.MF_BIA) {
                    // 多频阻抗**刻意不中转**，而且理由比心电更直接：三星健康与 Health Connect
                    // **都没有生物电阻抗这一类条目**（HC 只有体成分的成品值，没有阻抗谱）。
                    // 想写也无处可写，所以原始值只存在于手表与手机本地。
                    parts += ResText.of(R.string.s_03aac65f)
                } else {
                    // 中转写入 1：三星健康（需三星写权限，可能平台限制）
                    val written = runCatching {
                        SamsungHealthReader.relayWriteWithCount(context, msg)
                    }.getOrDefault(-1)
                    // 中转写入 2：Health Connect（安卓官方通道，无白名单限制）
                    val hcWritten = runCatching {
                        HealthConnectManager.relayWriteWithCount(context, msg)
                    }.getOrDefault(-1)
                    when {
                        written > 0 -> parts += ResText.of(R.string.s_ba714f40, written)
                        written == 0 -> {
                            // ⚠ 三元的回落值先取出来再拼 —— 别在字符串模板里嵌 ASCII 引号，
                            //   那会让文案提取器在**内层引号处截断**，翻译对象与展示串脱钩。
                            // ⚠ `why` 也要是 ResText：平台侧给的是**已定**的串（raw），
                            //   回落文案是**本地化**的（of）—— 混成一个 String 会让回落那条
                            //   在切语言后停在旧语言。
                            // ⚠ 回落文案与平台原文**都**已经是 ResText（见字段声明），
                            //   不要再包一层 raw —— 那会把本地化那条重新冻成字符串。
                            val why = SamsungHealthReader.lastWriteError
                                ?: ResText.of(R.string.s_00354263)
                            parts += ResText.of(R.string.s_6394586e, why)
                        }
                        else -> {
                            // ⚠⚠ 这里原来一律写「三星健康未授权」——但 -1 的真实含义只是
                            //   "没有写入权限"，而写权限按三星策略**本来就拿不到**。
                            //   结果就是：授权页显示"已全部授权"，状态卡却说"未授权"（用户报的就是这个）。
                            //   现在：读权限在 ⇒ 说"只读"；读权限也没有 ⇒ 才说"未授权"。
                            val readOk = runCatching {
                                SamsungHealthReader.hasReadAccess(context)
                            }.getOrDefault(false)
                            parts += if (readOk) ResText.of(R.string.s_dc5651a9)
                            else ResText.of(R.string.s_f2f623a5)
                        }
                    }
                    when {
                        hcWritten > 0 -> parts += ResText.of(R.string.s_acbc8f47, hcWritten)
                        hcWritten == 0 -> {
                            // ⚠ 同上面三星那一支：平台串走 raw、回落文案走 of。
                            // ⚠ 同上面三星那一支。
                            val why = HealthConnectManager.lastError
                                ?: ResText.of(R.string.s_00354263)
                            parts += ResText.of(R.string.s_2879d837, why)
                        }
                        // ⚠ 同理：-1 只是"没有 HC 写权限"，不能直接说"未授权"
                        else -> parts += ResText.of(R.string.s_dbd6cf10)
                    }
                }
                // ⚠ 分隔符必须**跟着语言走**，所以不能 `joinToString` 把它定成一个 String ——
                //   用 `ResText.join`：分隔符与各条目一样是迟到绑定的（见 `ResText.Sep`）。
                SyncRepository.setEvent(ResText.join(parts, ResText.LIST_SEP))
                sendFrame(
                    Frame(
                        FrameKinds.ACK,
                        json.encodeToString(
                            AckMessage.serializer(),
                            AckMessage(msg.type, msg.upTo, inserted),
                        ),
                    )
                )
            }

            FrameKinds.DONE -> runCatching {
                SyncRepository.onDone(json.decodeFromString(HelloMessage.serializer(), frame.data))
                // 同步完成后推送最新统计与血氧记录（含刚写入三星健康的数据）
                pushHourStats(context)
                pushSpo2Records(context)
                pushSleepSummary(context)
                // 档案也重推一次：用户可能刚在三星健康里改了体重，而手表只在连上那一刻拿到过旧值
                pushBiaProfile(context)
                // 经期摘要也重推一次：用户可能刚在手机端记了一次新经期
                pushCycleSummary(context)
                // 评分同样重推：刚同步上来的心电会改变基线，分数必须跟着变
                pushStressSummary(context)
                // 氧减摘要也重推一次：这一次刚读到的夜间血氧可能刚算出新的一晚
                pushOdiSummary()
                // 能量评分/呼吸暂停：与 ODI 同批推送。
                // ⚠ HELLO 与 DONE **两处都必须调** —— 只调 HELLO 那一处的话，
                // 刚同步读到的数据算出的新结果要等下次连上才更新，而且是**静默**的。
                pushEnergySummary()
                pushApneaSummary()
                // 扩展指标也重推：刚读到的步数/血糖/心律不齐就是这一帧的内容，
                // 只推 HELLO 那一处的话，这一次同步刚读到的值要等下次连上才更新（静默）。
                pushExtrasSummary(context)
            }

            FrameKinds.ECG_WAVE -> runCatching {
                // **双路**原始波形（调试用）：存为 CSV 到 `下载/VitalBridge/CSV`。
                // 载荷 `ecg:<csv>|ppg:<csv>|fs:<hz>|rp:<下标>`；兼容旧的纯 CSV（只有心电）。
                // ⚠ 兼容分支不是洁癖：升级途中手表与手机的版本可能不一致。
                val payload = frame.data
                val result = saveEcgCsv(context, payload)
                // ⚠ **三种结局分开告知**：新写成 / 早导出过 / 没写成。
                //   尤其"重复"不能报成失败 —— 文件确实在，就是他手里那份。
                SyncRepository.setEvent(
                    when (result) {
                        is EcgExport.SaveResult.Created -> ResText.of(R.string.s_59ed9a46, result.path)
                        is EcgExport.SaveResult.Duplicate -> ResText.of(R.string.s_38682199, result.path)
                        EcgExport.SaveResult.Failed -> ResText.of(R.string.s_8224ac1a)
                    }
                )
                // ⚗⚗ **回执给手表**（2026-10-05 用户要求「手表上导出成功后也做个弹窗，
                //   告诉导出是否成功和导出位置」）。
                //
                // ⚠⚠ 没有这条回执，手表**结构上不可能**给出"成功"——
                //   它只知道帧发出去了（`sendFrame` 的返回值），而落盘全在这一侧。
                //   ⇒ 手表那边原先只能显示"已上传 N 点"，那是**发送状态**、不是**落盘结果**；
                //     用户的原话正是「每次点了都不知道导出成功没有」。
                // ⚠ 载荷格式（2026-10-05 扩展）：`<状态>|<路径>`，状态取 `new` / `dup` / `fail`。
                //   扩展的原因就是本次的去重：手表要能说清"**没有新文件**，但你那份在哪"，
                //   只传路径的话它只能一律显示"成功"（那会让人以为刚生成了新文件）。
                // ⚠ **必须兼容旧格式**（纯路径、无前缀）：升级途中两端版本可能不一致，
                //   手表端按"含不含 `|`"分流，见 `wear/BluetoothSync` 的解析。
                // ⚠ 回执失败（蓝牙已断）**不再重试**：手表那边有超时兜底，
                //   而且用户重按一次会比"后台偷偷补发"更符合他对这个按钮的预期。
                sendFrame(Frame(FrameKinds.ECG_WAVE_ACK, ackPayload(result)))
            }
        }
    }

    /**
     * 把 `ECG_WAVE` 载荷转成 CSV 文本。**纯解析**（不碰文件系统、不去重）。
     *
     * 格式：第一行 `#` 元信息（采样率/点数/列名/`rpeaks`，供离线复算），随后每行
     * `心电mV,脉搏波原始计数`。**脉搏波列为空 = 这个包 SDK 没给这个键**；
     * `-1` 会原样写出（那是 SDK 自己的"这一包没有脉搏波"标记）。
     * ⚠ 别把 `-1` 与"空"混成一个意思 —— 2026-09-24 的一次返工就是因为这个。
     *
     * @return CSV 文本；载荷里没有心电数据时返回 `null`。
     */
    private fun buildEcgCsvText(payload: String): String? {
        if (payload.isEmpty()) return null

        val ecgPart: String
        val ppgPart: String?
        var fs = 0
        var rpPart: String? = null
        if (payload.startsWith("ecg:")) {
            val seg = payload.split('|')
            ecgPart = seg.getOrNull(0)?.removePrefix("ecg:") ?: ""
            ppgPart = seg.getOrNull(1)?.removePrefix("ppg:")
            fs = seg.getOrNull(2)?.removePrefix("fs:")?.toIntOrNull() ?: 0
            rpPart = seg.getOrNull(3)?.removePrefix("rp:")
        } else {
            ecgPart = payload
            ppgPart = null
        }
        val ecg = ecgPart.split(',')
        if (ecg.isEmpty() || ecg[0].isEmpty()) return null
        val ppg = ppgPart?.split(',')
        val MISSING = Int.MIN_VALUE

        val sb = StringBuilder(ecg.size * 18)
        sb.append("# fs=").append(fs).append("Hz samples=").append(ecg.size)
        sb.append(if (ppg != null) " columns=ecg_mv,ppg_raw" else " columns=ecg_mv")
        // R 峰一并写进头部：离线分析直接用生产检出结果，不自己再写一个 QRS 检测器
        if (!rpPart.isNullOrEmpty()) sb.append(" rpeaks=").append(rpPart)
        sb.append('\n')
        for (i in ecg.indices) {
            sb.append(ecg[i])
            if (ppg != null) {
                sb.append(',')
                // 空 = 我们没有这个键；其余（含 -1、0）原样写出
                val v = ppg.getOrNull(i)?.trim()?.toIntOrNull()
                if (v != null && v != MISSING) sb.append(v)
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * 双路原始波形 → CSV → 落盘（**含内容去重**）。
     *
     * ⚗⚗ 2026-10-05：落盘与去重**统一交给 [EcgExport.saveCsv]** ——
     *   "手机端本地导出"与"手表上传"本来就是同一件事（把一份 CSV 写进
     *   `下载/VitalBridge/CSV`、且同一份内容不重复写），各写一套必然漂移
     *   （最典型的漂移就是"手机端去重了、手表端没去重"，而用户会先发现后者）。
     *   ⇒ 本函数只负责解析载荷；**落盘/去重的判据只有一份**。
     */
    private fun saveEcgCsv(context: Context, payload: String): EcgExport.SaveResult {
        val csv = runCatching { buildEcgCsvText(payload) }.getOrNull()
            ?: return EcgExport.SaveResult.Failed
        return EcgExport.saveCsv(
            context, csv, EcgExport.WATCH_NAME_PREFIX, System.currentTimeMillis(),
        )
    }

    /**
     * 回执载荷：`<状态>|<路径>`。
     *
     * ⚠ 状态用**短标签**而不是整句文案：文案要随语言变，而这是**协议字段**
     *   （手表端自己按标签查它那边的翻译）。把句子塞进协议，一改文案就两端对不上。
     */
    private fun ackPayload(r: EcgExport.SaveResult): String = when (r) {
        is EcgExport.SaveResult.Created -> "new|" + r.path
        is EcgExport.SaveResult.Duplicate -> "dup|" + r.path
        EcgExport.SaveResult.Failed -> "fail|"
    }

    /** 读取三星健康的近期血氧记录并推送给手表。 */
    private suspend fun pushSpo2Records(context: Context) {
        runCatching {
            val records = SamsungHealthReader.readSpo2Records(context)
            if (records.isEmpty()) return
            val msg = Spo2RecordsMessage(records = records)
            sendFrame(
                Frame(
                    FrameKinds.SPO2_RECORDS,
                    json.encodeToString(Spo2RecordsMessage.serializer(), msg),
                )
            )
        }
    }

    /** 读取三星健康当日小时统计并推送给手表（含昨晚睡眠平均心率）。 */
    private suspend fun pushHourStats(context: Context) {
        runCatching {
            val buckets = SamsungHealthReader.readTodayHourly(context)
            if (buckets.isEmpty()) return
            val msg = HourStatsMessage(
                buckets = buckets,
                dateEpochDay = LocalDate.now().toEpochDay(),
                sleepAvg = runCatching {
                    SamsungHealthReader.readSleepAvg(context)
                }.getOrNull(),
            )
            sendFrame(
                Frame(
                    FrameKinds.HOUR_STATS,
                    json.encodeToString(HourStatsMessage.serializer(), msg),
                )
            )
        }
    }

    /**
     * 读取最近一晚的睡眠摘要并推送给手表。
     *
     * 拿不到数据（没授权 / 三星健康里没有睡眠记录）时**不推送**，而不是推一个空摘要：
     * 空摘要会把手表上已有的内容抹掉，而那多半只是这一次读取没成功。
     * 真到了第二天，手表侧靠 [SleepSummaryMessage.dateEpochDay] 自行判定过期。
     */
    private suspend fun pushSleepSummary(context: Context) {
        runCatching {
            val night = SamsungHealthReader.readSleepNight(context) ?: return
            val msg = SleepSummaryMessage(
                dateEpochDay = LocalDate.now().toEpochDay(),
                night = night,
            )
            sendFrame(
                Frame(
                    FrameKinds.SLEEP_SUMMARY,
                    json.encodeToString(SleepSummaryMessage.serializer(), msg),
                )
            )
        }
    }

    /**
     * 读取用户档案（身高/体重/性别/年龄）并推送给手表 —— 手表端测 BIA 的前提。
     *
     * 为什么必须由手机推：手表上没有三星健康 Data SDK，读不到 `USER_PROFILE`；
     * 而三星的 `getHealthTracker(BIA_ON_DEMAND, profile)` 要求一整套资料，缺一项就取不到 tracker。
     * 反过来让用户在手表上转表冠一个数字一个数字地输身高体重，显然不如手机上读一次。
     *
     * 走**蓝牙直连**（RFCOMM）而不是 Wearable Data Layer：手表端只在 `BluetoothSync`
     * 里处理这些主动推送帧（`hour_stats`/`spo2_records`/`sleep_summary` 同样如此），
     * Data Layer 那条通道手表侧只监听了 sync_req / ack，推过去没人接。
     *
     * ⚠ `profile == null` 时**照样推送**（帧里带一个 null），这与血氧/睡眠那条
     * "拿不到就不推"的纪律**刻意相反**：那两类是"这次没读到，别把表上已有内容抹掉"；
     * 而档案是一个**状态** —— 手机端确实没有资料时，手表必须知道这件事，
     * 才能显示「请先在手机端同步资料」，而不是一直等一个永远不来的帧。
     * 手表侧收到 null 会**清掉本地缓存**（见 `BiaProfileStore.clear`），
     * 避免拿着已经不再成立的旧身高体重继续测。
     */
    suspend fun pushBiaProfile(context: Context) {
        if (current == null) return
        runCatching {
            // 读不到就用上次读到过的（资料不会常变；没资料手表测不了体成分）
            val p = SamsungHealthReader.readBiaProfile(context)?.also {
                HealthMemory.saveProfile(context, it)
            } ?: HealthMemory.loadProfile(context)
            val msg = BiaProfileMessage(profile = p)
            sendFrame(
                Frame(
                    FrameKinds.BIA_PROFILE,
                    json.encodeToString(BiaProfileMessage.serializer(), msg),
                )
            )
        }
    }

    /**
     * 读取本机经期记录，算好一行摘要推给手表（模块 #13）。
     *
     * 经期数据只在手机端录入/导入（没有传感器能测），手表只显示一行概览。
     * 这里**总是推送**（即使经期追踪没开启、或没有记录）—— 与档案推送同理：
     * 摘要是一个**状态**，"没开启"这件事手表必须知道，才能显示"未开启"，
     * 而不是一直挂着上一次的旧摘要。
     */
    /**
     * 读取本机 HRV 与体温记录，算好压力/疲劳评分推给手表（模块 #12）。
     *
     * 与 [pushCycleSummary] 一样**总是推送**（即使算不出来也推 `available=false`）：
     * 摘要是一个状态，"这次算不出来"手表必须知道，否则它会一直挂着上一次的分数 ——
     * 而分数是相对基线的，基线每多一条记录就变一次，旧分数的误导性比旧经期日期更大。
     */
    suspend fun pushStressSummary(context: Context) {
        if (current == null) return
        runCatching {
            val hrv = SyncRepository.database.hrvDao().allOnce()
            val temps = SyncRepository.database.temperatureDao().allOnce()
            val msg = StressSummary.build(hrv, temps)
            sendFrame(
                Frame(
                    FrameKinds.STRESS_SUMMARY,
                    json.encodeToString(StressSummaryMessage.serializer(), msg),
                )
            )
        }
    }

    /**
     * 推送最近一晚的氧减指数摘要（模块 #11）。
     *
     * 与压力/经期**有一点关键不同：没有记录时什么都不推**。
     * 那两条"算不出来也推 available=false"，因为它们的状态是"当前这一刻是否成立"，
     * 推空是正确的更新；而 ODI 是**一晚一份的历史事实** —— 手上一条都没有，
     * 说明这台手机还没同步过任何睡眠数据，此时推 available=false 会把手表上
     * 一份本来有效的旧摘要抹掉（"空读取不许清屏"那条纪律）。
     *
     * 有记录但那一晚算不出结论（监测时长不够等）时**照推**：
     * 那是一个真实的结论，手表该显示原因而不是继续挂着更早那一晚的数。
     */
    suspend fun pushOdiSummary() {
        if (current == null) return
        runCatching {
            val e = SyncRepository.database.sleepOdiDao().latestOnce() ?: return@runCatching
            val msg = OdiSummaryMessage(
                available = e.odiPerHour != null,
                dateEpochDay = e.dateEpochDay,
                startMs = e.startMs,
                endMs = e.endMs,
                odiPerHour = e.odiPerHour ?: 0.0,
                events = e.events ?: 0,
                monitoredMinutes = e.monitoredMinutes ?: 0.0,
                t90Pct = e.t90Pct ?: 0.0,
                band = e.odiPerHour?.let { Odi.bandOf(it) } ?: "",
                reason = e.reason,
                computedAt = e.computedAt,
            )
            sendFrame(
                Frame(FrameKinds.ODI_SUMMARY, json.encodeToString(OdiSummaryMessage.serializer(), msg))
            )
        }
    }

    /**
     * 推能量评分摘要 —— **一个帧里装两个分**：三星自己的，与本 App 自算的。
     *
     * **算不出来也推**（`available` / `ownAvailable` 为 false）：它是「今天的当前状态」，
     * 推空是**正确**的更新 —— 手表据此显示"本机没有这项数据"，而不是继续挂着几天前的分数
     * 冒充今天（与压力/经期同一条，与 [pushOdiSummary] 相反）。
     *
     * 分数的组装**一律走 [EnergySummary]**（手机端界面也调它）：分数没有绝对标尺，
     * 两边各算各的就会出现手表 71、手机 73 这种没人能判断谁对的情况。
     */
    suspend fun pushEnergySummary() {
        if (current == null) return
        runCatching {
            val db = SyncRepository.database
            // 三星那一份：最近一天。「最近 7 天」那条趋势线在手表上已经不用了
            // （圆屏放不下有意义的趋势图，明细在手机端），所以不再推 recent。
            val samsung = db.energyScoreDao().latestOnce()
            // 自研那一份：需要**全部**睡眠夜行（基线要历史，趋势也要）
            val msg = EnergySummary.build(
                nights = db.sleepNightDao().allOnce(),
                samsung = samsung,
            )
            sendFrame(
                Frame(
                    FrameKinds.ENERGY_SUMMARY,
                    json.encodeToString(EnergySummaryMessage.serializer(), msg),
                )
            )
        }
    }

    /**
     * 推「三星扩展指标」摘要（活动 / 饮食 / 血糖 / 心律不齐）。
     *
     * ⚠ 它是「**今天的当前状态**」，所以**读不到也要推** —— 推空是正确的更新，
     * 手表据此显示"本机没有这项数据"，而不是继续挂着几天前的数。
     * （与能量/压力/经期同一条，与 ODI / 呼吸暂停**相反**：那两个是"既成事实"。）
     *
     * ⚠ **每块各自容忍失败**，原因写进对应 note —— 一块读不到不该拖垮另外三块。
     *
     * ⚠ 「今日合计」的日界只走 [SamsungHealthReader.todayStartMs]，
     * 与手机端各页面**同一个定义**（见那个函数的注释）。
     */
    suspend fun pushExtrasSummary(context: android.content.Context) {
        if (current == null) return
        runCatching {
            val todayMs = SamsungHealthReader.todayStartMs()
            val today = java.time.LocalDate.now().toEpochDay()

            // ── 活动：步数/消耗/时长/距离走聚合（它本身就是"今日"口径）──
            var actNote = ""
            var totals: ActivityTotals? = null
            runCatching { SamsungHealthReader.readActivityTotals(context) }
                .onSuccess { totals = it }
                .onFailure { actNote = briefOf(it) }

            var floors: Float? = null
            runCatching { SamsungHealthReader.readFloorsClimbed(context) }
                .onSuccess { l ->
                    floors = l.filter { it.atMs >= todayMs }
                        .takeIf { it.isNotEmpty() }
                        ?.sumOf { it.floors.toDouble() }?.toFloat()
                }
                .onFailure { if (actNote.isEmpty()) actNote = briefOf(it) }

            var lastEx: ExerciseEntry? = null
            runCatching { SamsungHealthReader.readExercises(context, days = 7) }
                .onSuccess { l -> lastEx = l.maxByOrNull { it.startMs } }
                .onFailure { if (actNote.isEmpty()) actNote = briefOf(it) }

            // ── 目标（六类里属于活动/饮食的那五个）──
            var goalNote = ""
            var goals: DailyGoals? = null
            runCatching { SamsungHealthReader.readGoals(context) }
                .onSuccess { goals = it }
                .onFailure { goalNote = briefOf(it) }

            // ── 饮食：与手机端「饮食」页**同一个日界、同一个算法** ──
            var dietNote = ""
            var kcal: Float? = null
            var water: Float? = null
            runCatching { SamsungHealthReader.readNutrition(context) }
                .onSuccess { l ->
                    kcal = l.filter { it.atMs >= todayMs }
                        .sumOf { it.calories.toDouble() }.toFloat()
                }
                .onFailure { dietNote = briefOf(it) }
            runCatching { SamsungHealthReader.readWaterIntake(context) }
                .onSuccess { l ->
                    water = l.filter { it.atMs >= todayMs }
                        .sumOf { it.ml.toDouble() }.toFloat()
                }
                .onFailure { if (dietNote.isEmpty()) dietNote = briefOf(it) }

            // ── 血糖：最近一条 ──
            var gluNote = ""
            var glu: GlucoseReading? = null
            runCatching { SamsungHealthReader.readBloodGlucose(context) }
                .onSuccess { l -> glu = l.maxByOrNull { it.atMs } }
                .onFailure { gluNote = briefOf(it) }

            // ── 心律不齐：最近一条 + "读成功过没有"（后者决定界面能不能说"没有记录"）──
            var ihrnNote = ""
            var ihrn: IhrnEntry? = null
            var ihrnReadable = false
            runCatching { SamsungHealthReader.readIhrn(context) }
                .onSuccess { l ->
                    ihrnReadable = true
                    ihrn = l.maxByOrNull { it.atMs }
                }
                .onFailure { ihrnNote = briefOf(it) }

            val msg = ExtrasSummaryMessage(
                atMs = System.currentTimeMillis(),
                dateEpochDay = today,
                steps = totals?.steps,
                activeKcal = totals?.activeKcal,
                activeMs = totals?.activeMs,
                distanceM = totals?.distanceM,
                floors = floors,
                lastExerciseName = lastEx?.let { it.customTitle ?: it.typeName },
                lastExerciseMs = lastEx?.durationMs,
                lastExerciseAtMs = lastEx?.startMs ?: 0L,
                stepGoal = goals?.steps,
                activeKcalGoal = goals?.activeKcal,
                activeMsGoalMs = goals?.activeMs,
                kcalToday = kcal,
                waterMlToday = water,
                kcalGoal = goals?.nutritionKcal,
                waterGoalMl = goals?.waterMl,
                glucoseMmol = glu?.mmolPerL,
                glucoseAtMs = glu?.atMs ?: 0L,
                glucoseMeal = glu?.meal.orEmpty(),
                ihrnReadable = ihrnReadable,
                ihrnStatus = ihrn?.status.orEmpty(),
                ihrnAtMs = ihrn?.atMs ?: 0L,
                activityNote = actNote,
                goalNote = goalNote,
                dietNote = dietNote,
                glucoseNote = gluNote,
                ihrnNote = ihrnNote,
            )
            sendFrame(
                Frame(
                    FrameKinds.EXTRAS_SUMMARY,
                    json.encodeToString(ExtrasSummaryMessage.serializer(), msg),
                )
            )
        }
    }

    /** 错误一句话概括（`SamsungHealthReader.brief()` 是私有的，这里自己包一层）。 */
    private fun briefOf(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message}"

    /**
     * 推睡眠呼吸暂停摘要。
     *
     * ⚠ 与能量评分**相反**：**一条记录都没有时什么都不推**。
     * 这是「某一晚的既成事实」—— 手表上已有一份是有效的，推空会把它抹掉。
     * 与 [pushOdiSummary] 完全同一条纪律。
     */
    suspend fun pushApneaSummary() {
        if (current == null) return
        runCatching {
            val e = SyncRepository.database.sleepApneaDao().latestOnce() ?: return@runCatching
            val msg = ApneaSummaryMessage(
                sign = e.sign,
                dateEpochDay = e.dateEpochDay,
                startMs = e.startMs,
                endMs = e.endMs ?: 0L,
                atMs = System.currentTimeMillis(),
            )
            sendFrame(
                Frame(
                    FrameKinds.APNEA_SUMMARY,
                    json.encodeToString(ApneaSummaryMessage.serializer(), msg),
                )
            )
        }
    }

    suspend fun pushCycleSummary(context: Context) {
        if (current == null) return
        runCatching {
            val enabled = CycleSettings.isEnabled(context)
            val entries = if (enabled) {
                SyncRepository.database.menstrualDao().allOnce().map { it.toEntry() }
            } else {
                emptyList()
            }
            val today = LocalDate.now().toEpochDay()
            val msg = if (!enabled) {
                CycleSummaryMessage(available = false)
            } else {
                val r = Cycle.analyze(entries, today)
                when (r) {
                    is Cycle.Result.NotApplicable -> CycleSummaryMessage(
                        available = false,
                    )
                    is Cycle.Result.Ok -> CycleSummaryMessage(
                        available = true,
                        lastStartEpochDay = r.lastStartEpochDay,
                        nextStartEpochDay = r.prediction?.nextStartEpochDay,
                        spreadDays = r.prediction?.spreadDays ?: 0,
                        inFertileWindow = r.inFertileWindow,
                        medianCycleDays = r.medianCycleDays,
                    )
                }
            }
            sendFrame(
                Frame(
                    FrameKinds.CYCLE_SUMMARY,
                    json.encodeToString(CycleSummaryMessage.serializer(), msg),
                )
            )
        }
    }
    // ⚠ 2026-10-26 `sendOtaWindow()` 已删：它唯一的用途是让手表**建 RFCOMM 监听**，
    //   而蓝牙推包整条链路已经移除（首次安装走「安装」页的无线 ADB，更新靠表自己下载）。


    /**
     * 写一帧。
     *
     * ⚠⚠ 必须 `synchronized`（2026-10-28 加）：现在除了同步流程，还有**用户点"检查更新"**
     *   这条独立路径也会写（`WATCH_UPDATE_REQ`）。帧是换行分隔的 JSON，
     *   两个线程各写半行就会拼出一行非法 JSON ⇒ 对端整帧丢弃，而且**不报错**
     *   （表现只是"偶尔少一条帧"，极难复现定位）。
     */
    private val writeLock = Any()

    fun sendFrame(frame: Frame): Boolean = synchronized(writeLock) {
        val conn = current ?: return false
        return try {
            conn.out.write(FrameCodec.encode(frame).toByteArray(Charsets.UTF_8))
            conn.out.flush()
            true
        } catch (_: Exception) {
            conn.open = false
            false
        }
    }
}
