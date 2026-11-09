package com.gemini.healthbridge.phone

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * 无线 ADB（2026-10-26）：**把手表安装包经 WiFi 装进手表**。
 *
 * ## 为什么取代蓝牙传输
 *
 * 蓝牙传输（RFCOMM 推字节）只在"**手表上已经有脉迹、并且正在 accept**"时才成立：
 * 服务端在表端 Activity 进程里，还得先下发 `OTA_WINDOW` 通知它建监听、再等 2 秒……
 * 一串前置条件，任何一环不满足就是 `BluetoothSocketException`。
 * 而蓝牙连着时**表本来就有网络**（手机做 BT 代理）⇒ 让表自己 HTTP 下载最省事。
 * 真正蓝牙救不了的是"**表上什么都没有**"（首次安装）：那时连 accept 的人都没有。
 * ⇒ 首次安装只能走 ADB；装过一次之后，之后都靠表自己下载更新。
 *
 * ## 三条约束（踩过才知道）
 *
 * 1. ⚠⚠ **配对（pair）与连接（connect）是两条不同的 mDNS 服务、两个不同端口**：
 *    `_adb-tls-pairing._tcp` 用于"用配对码配对"，`_adb-tls-connect._tcp` 才是数据口。
 *    ⚠ 端口每次开关无线调试都会变 ⇒ **不能缓存 IP:端口**，必须现扫。
 * 2. ⚠⚠ **配对只需要做一次**：配对成功后，本机这份证书就被手表记住了，
 *    之后"点连接"就能直连。
 * 3. ⚠⚠ **身份是"这台手机"的，不是"这个 App 安装包"的**（2026-11-03 改）：
 *    以前用的是内置在 `assets/adb/adbkey.pem` 里的**明文私钥** —— 打进 APK 就等于公开，
 *    谁装过本 App 都能拿它去连一只授权过的手表。现在改成首次使用时在设备上现场生成
 *    （`SelfSignedCert` 手写 DER + `AdbIdentity` 落盘，见两者注释）。
 *    ⚠ **代价：升级到这一版之后，旧密钥失效 ⇒ 手表要重新配对一次。**
 *    这不是缺陷，是"不再共用公开私钥"的必然结果；`human()` 里对
 *    `AdbAuthenticationFailedException` 已经给出"重新配对"的指引。
 *    ⚠ 顺带还有一个好处：不同手机的身份不再相同，一台机器的密钥泄露不会波及别人。
 *
 * ⚠ `AdbMdns` 的回调在主线程，Flow 的更新都在主线程做，不需要额外切线程。
 */
object WatchAdb : AbsAdbConnectionManager() {

    private const val TAG = "WatchAdb"
    /** 手表包名（`wear/build.gradle.kts` 的 applicationId，**不是**手机端的） */
    const val WEAR_PKG = "com.vitalbridge.free.wear"

    enum class Phase {
        /** 没扫、没连 */
        Idle,
        /** 正在 mDNS 扫描 */
        Scanning,
        /** 正在 TCP/TLS 连接 */
        Connecting,
        /** 连上了，但手表要求先配对（需要用户输入 6 位配对码） */
        NeedsPairing,
        Connected,
        /** 下载/安装中（UI 用 [progress] 显示进度） */
        Busy,
        Failed,
    }

    /** 手动连接的结果（扫不到时的兜底通路）。 */
    enum class Manual {
        /** 连上了 */
        Connected,
        /** 配对成功，但端口变了且没扫到新端口 ⇒ 让用户回手表看新端口 */
        Paired,
        Failed,
    }

    /** 发现到的一台设备。`pairingPort == null` 表示只看到数据口（多半已经配对过）。 */
    data class Device(val host: String, val connectPort: Int, val pairingPort: Int?)

    val phase = MutableStateFlow(Phase.Idle)
    val devices = MutableStateFlow<List<Device>>(emptyList())
    /** 连上后读到的型号，如 `SM-L310` */
    val deviceName = MutableStateFlow("")
    /** 手表上已装的版本号（0 = 没装） */
    val watchVersion = MutableStateFlow(0L)
    val progress = MutableStateFlow(0f)
    /** 失败的原始原因（技术串，UI 里用「失败：%s」包一层） */
    val errorDetail = MutableStateFlow<String?>(null)
    /** 需要配对时，提示用户去哪个端口配 */
    val pairingHint = MutableStateFlow<Device?>(null)

    /** 上次连成功的 IP / 端口（下次自动填好，只需改端口） */
    val lastHost = MutableStateFlow("")
    val lastPort = MutableStateFlow(0)

    /**
     * 连接还剩几分钟（10 分钟租约，见 [startKeeper]）。
     *
     * ⚠ 用户要求（2026-10-27）：**连上就保 10 分钟**，中途切页/切到后台不许断，
     *   除非进程被系统杀掉。到期或掉线才断。这里把剩余时间暴露给界面，
     *   让"还能用多久"是可预期的，而不是突然断掉。
     */
    val remainMin = MutableStateFlow(0)

    /**
     * 中性提示（**不是错误**）：连接到期 / 中途掉线。
     *
     * ⚠ 装的是**字符串资源 id**（0 = 无），不是没本地化的中文串 ——
     *   提示是给用户读的，必须跟着语言走（项目里"落库/状态里不存已求值文案"的同一条纪律）。
     */
    val notice = MutableStateFlow(0)

    private var appCtx: Context? = null
    private var key: PrivateKey? = null
    private var cert: Certificate? = null

    private var mdnsConnect: AdbMdns? = null
    private var mdnsPairing: AdbMdns? = null

    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var scanTimeout: Runnable? = null
    private var udp: MdnsScanner? = null

    /**
     * ⚠⚠ **自己持有的一条 TLS 连接**（不用父类那份）。
     *
     * 根因（2026-10-26 真机确认）：`AbsAdbConnectionManager.connect(host, port)` 内部是
     * `AdbConnection.connect(t, unit, **false**)` —— **明文 ADB**；
     * 而 Android 11+ 的无线调试端口**只接受 TLS**（`connectTls()` 才传 `true`）。
     * 于是"扫到了端口、点连接必然失败"（真机报 `connect() 返回 false`）。
     * `connectTls()` 又只能靠系统的 mDNS 自己发现（那台机器上它不工作）⇒ 只能自己 create + connect(true)。
     */
    private var tlsConn: io.github.muntashirakon.adb.AdbConnection? = null

    /** 心跳/到期看门狗（进程活着就一直跑；进程被杀自然就没了，正合"除非后台被删"） */
    private var keeper: kotlinx.coroutines.Job? = null
    private var expireAtMs = 0L

    /** 连接租约：10 分钟（用户定的规则） */
    private const val CONNECT_TTL_MS = 10 * 60_000L
    /**
     * 心跳间隔。
     *
     * ⚠ 为什么必须有：手机进 Doze / Wi-Fi 省电后，空闲 TCP 会被**静默**掐掉，
     *   而 TLS 层不会立刻报错 —— 用户下次点安装才会发现"连不上"。
     *   每 40 秒发一条 echo，既能探活，也能让系统认为这条连接在用。
     */
    private const val HEARTBEAT_MS = 40_000L

    private const val SP = "watch_adb"
    private const val K_HOST = "last_host"
    private const val K_PORT = "last_port"

    /** host -> Device（两个 mDNS 服务各自只报自己那个端口，这里合并到同一条） */
    private val found = LinkedHashMap<String, Device>()

    // ── 库要求的三个抽象属性 ──────────────────────────────────────────────

    override fun getPrivateKey(): PrivateKey = key ?: error("WatchAdb 未初始化 key")
    override fun getCertificate(): Certificate = cert ?: error("WatchAdb 未初始化 cert")
    override fun getDeviceName(): String = "VitalBridge"

    /**
     * 现在是否连着（含安装中）。切页/重进页面时用它决定"要不要重新扫描"。
     *
     * ⚠ 必须 `override` 父类那个：父类的 `isConnected()` 看的是**它自己**持有的连接，
     *   而本项目为了让"无线调试端口只吃 TLS"能连上，自己另建了一条（见 [tlsConn]）
     *   ⇒ 父类那个永远是 false，拿它做判断会一直以为"没连上"。
     */
    override fun isConnected(): Boolean =
        phase.value == Phase.Connected || phase.value == Phase.Busy

    /**
     * 在 **IO 线程**把 ADB 身份准备好。
     *
     * ⚠ 为什么需要它：身份是**首次使用时生成**的（`AdbIdentity`），而生成一对 RSA 2048
     *   在这类手机上要几十到几百毫秒。`startScan` 是 UI 直接调的、跑在主线程，
     *   若把生成放在那里，用户点「扫描」会感觉按钮迟钝一下（而且看不出原因）。
     *   ⇒ 进页面时先 `prepare()`（协程，不阻塞 UI），之后的 `ensureReady` 只读缓存。
     *
     * ⚠ `ensureReady` 里仍然保留同步兜底：`prepare` 还没跑完用户就点了按钮时，
     *   顶多等这一次生成，功能不会错。
     */
    suspend fun prepare(ctx: Context) {
        withContext(Dispatchers.IO) {
            runCatching { AdbIdentity.loadOrCreate(ctx.applicationContext) }
        }
    }

    /** 读设备的 ADB 身份（**首次使用时现场生成**，见 `AdbIdentity`）。幂等。 */
    fun ensureReady(ctx: Context) {
        appCtx = ctx.applicationContext
        if (key != null) return
        val c = ctx.applicationContext
        // ⚠⚠ 2026-11-03：**不再读 `assets/adb/*.pem`** —— 那是打包进 APK 的明文私钥，
        //   等于把所有安装共用一个公开身份（详见 `AdbIdentity` 的类注释）。
        //   代价：升级到这一版之后手表不认旧密钥，需要重新配对一次。
        val id = AdbIdentity.loadOrCreate(c)
        key = id.key
        cert = id.cert
        // ⚠ API 值只影响 shell 协议版本；手表（Wear OS 5）远高于 24，用手机的没有副作用。
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
        // ⚠ 必须让"未授权"抛异常：否则 connect() 只返回 false，
        //   UI 只能显示"连不上"，分不清是"没配对"还是"没开无线调试"。
        setThrowOnUnauthorised(true)

        // 上次连过的设备：预填 IP/端口（端口每次会变，所以只是个起点）
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        lastHost.value = sp.getString(K_HOST, "") ?: ""
        lastPort.value = sp.getInt(K_PORT, 0)
    }

    // ── 扫描 ─────────────────────────────────────────────────────────────

    /**
     * 开始扫描同网络下的 ADB 服务（两个服务一起扫）。
     *
     * ⚠ 扫描**一直开着**直到 [stopScan]：用户常常先开这一页、再去手表上打开无线调试，
     *   扫一轮就停会让"打开了却一直显示找不到"。
     */
    fun startScan(ctx: Context) {
        ensureReady(ctx)
        // ⚠⚠ 已经连着就**什么都不做**：`startScan` 会把 phase 改成 Scanning，
        //   而界面是按 phase 判断"已连接/未连接"的 ⇒ 切一下标签页再回来，
        //   连接明明还在，界面却显示"未连接"（用户 2026-10-27 报的就是这个）。
        if (isConnected()) return
        stopScan()
        found.clear()
        devices.value = emptyList()
        errorDetail.value = null
        notice.value = 0
        phase.value = Phase.Scanning

        // ⚠⚠ 启动失败**必须说出来**：`runCatching` 静默吞掉的话，界面会永远停在"正在扫描"，
        //   用户以为"手表没开无线调试"（真机踩过一次：ColorOS 上 NSD 起不来）。
        mdnsConnect = AdbMdns(ctx, AdbMdns.SERVICE_TYPE_TLS_CONNECT) { addr, port ->
            // ⚠ 库是 Java 写的、`InetAddress` 没有 @NonNull ⇒ Kotlin 看成平台类型（可能为 null）
            val h = addr?.hostAddress ?: return@AdbMdns
            onFoundHost(h, port, pairing = false)
        }.also {
            runCatching { it.start() }.onFailure { e ->
                Log.w(TAG, "mdns connect 启动失败", e)
                errorDetail.value = "mDNS 无法启动：${brief(e)}"
            }
        }

        mdnsPairing = AdbMdns(ctx, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { addr, port ->
            val h = addr?.hostAddress ?: return@AdbMdns
            onFoundHost(h, port, pairing = true)
        }.also {
            runCatching { it.start() }.onFailure { e ->
                Log.w(TAG, "mdns pairing 启动失败", e)
            }
        }

        // ⚠⚠ 再开一条**自建 UDP mDNS**（见 `MdnsScanner` 的 KDoc）：系统 `NsdManager`
        //   在部分 ROM 上静默返回空（真机实测一次都没回调）。两条通道并行，谁先到算谁。
        udp = MdnsScanner(
            onFound = { host, port, pairing -> onFoundHost(host, port, pairing) },
            // ⚠ 诊断串**只进日志**，不上界面（用户 2026-10-27：「用户不需要看见」）
            onDiag = { d -> Log.d(TAG, d) },
        ).also { it.start() }

        // ⚠ 18 秒还没结果就切回 Idle：否则界面一直转圈，用户不知道"就是没扫到"。
        //   （mDNS 在多播被路由器挡掉 / 开了 AP 隔离时确实收不到，只能手输 IP。）
        scanTimeout?.let { ui.removeCallbacks(it) }
        scanTimeout = Runnable {
            if (phase.value == Phase.Scanning && devices.value.isEmpty()) {
                phase.value = Phase.Idle
            }
        }.also { ui.postDelayed(it, 18_000) }
    }

    fun stopScan() {
        scanTimeout?.let { ui.removeCallbacks(it) }
        scanTimeout = null
        runCatching { udp?.stop() }; udp = null
        runCatching { mdnsConnect?.stop() }
        runCatching { mdnsPairing?.stop() }
        mdnsConnect = null
        mdnsPairing = null
        if (phase.value == Phase.Scanning) phase.value = Phase.Idle
    }

    /**
     * 连到 `host:port`，**强制走 TLS**。
     *
     * ⚠ 返回 false 只表示"连上但协议没通"，认证失败会抛 `AdbAuthenticationFailedException`；
     *   需要配对会抛 `AdbPairingRequiredException`。
     */
    private fun connectTls(host: String, port: Int): Boolean {
        runCatching { tlsConn?.close() }
        tlsConn = null
        val pk = key ?: error("未初始化密钥")
        val ce = cert ?: error("未初始化证书")
        val c = io.github.muntashirakon.adb.AdbConnection.create(
            host, port, pk, ce, android.os.Build.VERSION.SDK_INT,
        )
        c.setDeviceName("VitalBridge")
        val ok = c.connect(15, TimeUnit.SECONDS, /* tls = */ true)
        if (ok) {
            tlsConn = c
        } else {
            runCatching { c.close() }
        }
        return ok
    }

    /** 打开一条 ADB 流（shell: / exec: ...），**走自己那条 TLS 连接**。 */
    private fun openCmd(name: String): io.github.muntashirakon.adb.AdbStream {
        val c = tlsConn ?: error("未连接")
        return c.open(name)
    }

    private fun onFoundHost(host: String, port: Int, pairing: Boolean) {
        if (host.isBlank()) return
        val old = found[host]
        val merged = if (pairing) {
            Device(host, old?.connectPort ?: 0, port)
        } else {
            Device(host, port, old?.pairingPort)
        }
        found[host] = merged
        devices.value = found.values.toList()
        if (phase.value == Phase.Scanning && merged.connectPort > 0) {
            // 有数据口就够了：能直接连（已配对）或连了再要配对码
        }
    }

    // ── 连接 / 配对 ───────────────────────────────────────────────────────

    /** 连一台设备。返回 `null` = 成功；非空 = 失败原因。需要配对时 phase 会切到 [Phase.NeedsPairing]。 */
    suspend fun connectTo(dev: Device): String? = withContext(Dispatchers.IO) {
        phase.value = Phase.Connecting
        errorDetail.value = null
        pairingHint.value = null
        try {
            // ⚠ 没有数据口（只扫到配对口）时不能硬连：先让用户配对。
            if (dev.connectPort <= 0) {
                pairingHint.value = dev
                phase.value = Phase.NeedsPairing
                return@withContext null
            }
            val ok = connectTls(dev.host, dev.connectPort)
            if (!ok) {
                // 再试一次：刚开无线调试时 adbd 可能还在起
                Thread.sleep(700)
                if (!connectTls(dev.host, dev.connectPort)) {
                    fail("连不上 ${dev.host}:${dev.connectPort}（端口可能已变，点「重新扫描」或手填新端口）")
                    return@withContext errorDetail.value
                }
            }
            onConnected(dev)
            null
        } catch (e: AdbPairingRequiredException) {
            Log.i(TAG, "需要配对: ${dev.host}")
            pairingHint.value = dev
            phase.value = Phase.NeedsPairing
            null
        } catch (e: Exception) {
            Log.w(TAG, "连接失败", e)
            phase.value = Phase.Failed
            errorDetail.value = human(e)
            errorDetail.value
        }
    }

    /** 用 6 位配对码配对，成功后自动连接。 */
    suspend fun pairAndConnect(dev: Device, code: String): String? = withContext(Dispatchers.IO) {
        phase.value = Phase.Connecting
        errorDetail.value = null
        try {
            val port = dev.pairingPort
                ?: return@withContext fail("没有配对端口（请确认手表上打开了「无线调试 → 使用配对码配对设备」）")
            val paired = pair(dev.host, port, code.trim())
            if (!paired) return@withContext fail("配对被拒绝（配对码可能过期了，回手表上重新点一次）")
            // 配对成功后必须**重新发现**数据口：配对会重启 adbd，端口会变。
            pairingHint.value = null
            var target = dev
            repeat(12) {
                Thread.sleep(700)
                val fresh = found[dev.host]
                if (fresh != null && fresh.connectPort > 0) {
                    target = fresh
                    return@repeat
                }
            }
            if (target.connectPort <= 0) {
                return@withContext fail("配对成功，但还没扫到数据端口，请稍等再点「连接」")
            }
            if (!connectTls(target.host, target.connectPort)) {
                return@withContext fail("配对成功，但连接失败（请确认手表仍开着无线调试）")
            }
            onConnected(target)
            null
        } catch (e: Exception) {
            Log.w(TAG, "配对失败", e)
            fail(human(e))
        }
    }

    /**
     * **手动连接**（扫不到时的兜底）。
     *
     * 为什么必须有它：mDNS 在"路由器开了 AP 隔离 / 关掉多播 / 双频分离"时收不到任何服务，
     * 而手表屏幕上明明就写着 IP、端口和配对码 —— 让用户填进去比让他改路由器现实得多。
     *
     * ⚠⚠ **配对端口 ≠ 连接端口**，而且**每次开关无线调试都会变**：
     *   · 填了 `code` ⇒ 当作"配对"处理，配对后端口会重开，我们去 mDNS 里等新端口；
     *     等不到就返回 [Manual.Paired]，让用户把手表主页面那个端口填进来再点「连接」。
     *   · 没填 `code` ⇒ 当作"已经配对过，直接连"。
     */
    suspend fun manual(host: String, port: Int, code: String?): Manual = withContext(Dispatchers.IO) {
        phase.value = Phase.Connecting
        errorDetail.value = null
        val h = host.trim()
        if (h.isEmpty() || port !in 1..65535) {
            fail("IP 或端口不合法")
            return@withContext Manual.Failed
        }
        if (!code.isNullOrBlank()) {
            // ── 先配对 ──
            try {
                if (!pair(h, port, code.trim())) {
                    fail("配对被拒绝：配对码可能已过期，回手表上重新打开一次配对页")
                    return@withContext Manual.Failed
                }
                var target = found[h]?.takeIf { it.connectPort > 0 }
                repeat(10) {
                    if (target != null) return@repeat
                    Thread.sleep(600)
                    target = found[h]?.takeIf { it.connectPort > 0 }
                }
                val t = target
                if (t == null) {
                    phase.value = Phase.Idle
                    return@withContext Manual.Paired
                }
                if (!connectTls(t.host, t.connectPort)) {
                    fail("配对成功，但连不上 ${t.host}:${t.connectPort}")
                    return@withContext Manual.Failed
                }
                onConnected(t)
                return@withContext Manual.Connected
            } catch (e: Exception) {
                fail(brief(e))
                return@withContext Manual.Failed
            }
        }
        // ── 直接连 ──
        try {
            var ok = connectTls(h, port)
            if (!ok) {
                Thread.sleep(600)
                ok = connectTls(h, port)
            }
            if (!ok) {
                fail("连不上 $h:$port（手表还在开无线调试吗？端口每次都会变）")
                return@withContext Manual.Failed
            }
            onConnected(Device(h, port, null))
            Manual.Connected
        } catch (e: AdbPairingRequiredException) {
            phase.value = Phase.Idle
            errorDetail.value = "需要配对：请填上面的配对码，先点「配对」"
            Manual.Failed
        } catch (e: Exception) {
            fail(human(e))
            Manual.Failed
        }
    }

    private fun fail(msg: String): String {
        errorDetail.value = msg
        // ⚠⚠ **装包失败不等于断开**：`tlsConn` 还在就保持「已连接」，
        //   否则界面会翻成"未连接"、把安装按钮禁掉、还会重开扫描（真机上看着像断线）。
        phase.value = if (tlsConn != null) Phase.Connected else Phase.Failed
        return msg
    }

    private fun onConnected(dev: Device) {
        // ⚠⚠ **连上 = `connect()` 返回 true**。后面读型号/版本全属于"锦上添花"，
        //   任何一步失败都**不许**把这次连接判成失败（真机踩过：读版本抛 Stream closed，
        //   用户看到"连接失败"，其实已经连上了）。
        runCatching { afterConnected(dev) }
        phase.value = Phase.Connected
        notice.value = 0
        errorDetail.value = null
        // 连上就停扫，省电（再点「重新扫描」会重开）
        runCatching { mdnsConnect?.stop() }
        runCatching { mdnsPairing?.stop() }
        runCatching { udp?.stop() }; udp = null
        // 10 分钟租约 + 心跳探活
        startKeeper()
    }

    /** 连上后要补的信息（型号 / 手表上已装版本）。**失败只记日志**，不影响"已连接"。 */
    private fun afterConnected(dev: Device) {
        // 记住这台设备，下次进来先填好（端口会变，但 IP 基本不变）
        runCatching {
            appCtx?.getSharedPreferences(SP, Context.MODE_PRIVATE)?.edit()
                ?.putString(K_HOST, dev.host)?.putInt(K_PORT, dev.connectPort)?.apply()
            lastHost.value = dev.host
            lastPort.value = dev.connectPort
        }
        val model = runCatching { shell("getprop ro.product.model").trim() }.getOrDefault("")
        val name = model.ifBlank { dev.host }
        deviceName.value = name
        // ⚠ 读不到就当"未知（0）"，界面要说"读不到"，不要假装"没装"
        watchVersion.value = runCatching { installedVersion() }.getOrDefault(0L)
    }

    /** 断开。⚠ 手表端无线调试不会因此关闭。 */
    override fun disconnect() {
        stopKeeper()
        // ⚠ 只能调 super：本方法同名，写 `disconnect()` 就是无限递归
        runCatching { super.disconnect() }
        runCatching { tlsConn?.close() }
        tlsConn = null
        deviceName.value = ""
        watchVersion.value = 0L
        progress.value = 0f
        remainMin.value = 0
        phase.value = Phase.Idle
    }

    // ── 连接租约（10 分钟保活）───────────────────────────────────────────

    /**
     * 起看门狗：每 [HEARTBEAT_MS] 探活一次，[CONNECT_TTL_MS] 后自动断开。
     *
     * ⚠⚠ 为什么不是"切页就断"（原来的行为）：用户连着表在装包/看版本，
     *   切到「功能」看一眼再回来就断了，得重新扫、重新填端口 —— 端口每次都变，
     *   这等于逼他每次都手输。⇒ 改成**有明确期限的租约**：
     *   · 进程活着就一直续着（前台/后台都算），
     *   · 到期或中途掉线才断，并把原因告诉用户（[notice]）。
     */
    private fun startKeeper() {
        stopKeeper()
        expireAtMs = System.currentTimeMillis() + CONNECT_TTL_MS
        remainMin.value = (CONNECT_TTL_MS / 60_000L).toInt()
        keeper = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        ).launch {
            while (true) {
                kotlinx.coroutines.delay(HEARTBEAT_MS)
                val left = expireAtMs - System.currentTimeMillis()
                ui.post { remainMin.value = ((left + 59_999L) / 60_000L).toInt().coerceAtLeast(0) }
                if (left <= 0L) {
                    ui.post {
                        runCatching { disconnect() }
                        notice.value = R.string.s_d4e03a98
                    }
                    return@launch
                }
                // 探活：这条命令在表上什么都不改，只用来确认连接还活着
                val ok = runCatching { shell("echo 1") }.isSuccess
                if (!ok) {
                    ui.post {
                        runCatching { disconnect() }
                        notice.value = R.string.s_c92be0f9
                    }
                    return@launch
                }
            }
        }
    }

    private fun stopKeeper() {
        runCatching { keeper?.cancel() }
        keeper = null
        expireAtMs = 0L
        remainMin.value = 0
    }

    /** 查询手表上已装版本（0 = 没装）。 */
    fun installedVersion(): Long {
        val out = shell("dumpsys package $WEAR_PKG")
        val m = Regex("versionCode=(\\d+)").find(out) ?: return 0L
        return m.groupValues[1].toLongOrNull() ?: 0L
    }

    // ── shell / 安装 ──────────────────────────────────────────────────────

    /**
     * 跑一条 shell 命令并读回完整输出（阻塞；**必须**在 IO 线程调）。
     *
     * ⚠⚠⚠ **库的怪癖：命令正常结束时会对端 CLSE，而 `AdbStream.read()` 此时抛
     *   `IOException("Stream closed.")` 而不是返回 -1**（`AdbStream.read` 里
     *   `if (mIsClosed) throw ...` 排在 `mPendingClose` 判断之前）。
     *   于是「一条成功的 `getprop`」看起来像"连接失败"——
     *   真机上表现为：连上后立刻弹「失败：Stream closed.」、手表版本永远读成 0
     *   ⇒ 引导里一直说"还没检测到手表版"、还误报"检测到新版"。
     *   ⇒ 这里把这个特定异常**当成正常结束**（其余 IOException 照常抛）。
     */
    private fun shell(cmd: String): String = readAllTolerant(openCmd("shell:$cmd"))

    /** 读到流结束；把库那个"正常关流却抛 Stream closed"的怪癖当 EOF。 */
    private fun readAllTolerant(stream: io.github.muntashirakon.adb.AdbStream): String {
        val buf = java.io.ByteArrayOutputStream()
        val b = ByteArray(16 * 1024)
        try {
            stream.openInputStream().use { input ->
                while (true) {
                    val n = try {
                        input.read(b)
                    } catch (e: java.io.IOException) {
                        if (isStreamClosedQuirk(e)) break else throw e
                    }
                    if (n < 0) break
                    buf.write(b, 0, n)
                }
            }
        } catch (e: java.io.IOException) {
            if (!isStreamClosedQuirk(e)) throw e
        }
        return buf.toString(Charsets.UTF_8.name())
    }

    /** `AdbStream` 在对端关流后抛的 `IOException("Stream closed.")` —— 语义上就是 EOF。 */
    private fun isStreamClosedQuirk(e: Throwable): Boolean =
        e is java.io.IOException && e.message?.contains("Stream closed") == true

    /**
     * 把 APK 装进手表。
     *
     * ⚠ 用 `exec:pm install -r -S <字节数>`：`-S` 表示"从标准输入读安装包"，
     *   这样**不用先 push 到 `/data/local/tmp`**（省一次 4.7MB 写盘，也不用管 sync 协议）。
     * ⚠ 写完必须关流（CLSE）再读结果，否则 `pm` 会一直等输入。
     */
    suspend fun installApk(file: File): String? = withContext(Dispatchers.IO) {
        phase.value = Phase.Busy
        progress.value = 0f
        errorDetail.value = null
        try {
            val size = file.length()
            if (size <= 0) return@withContext fail("安装包是空的")
            val stream = openCmd("exec:pm install -r -S $size")
            stream.openOutputStream().use { out ->
                file.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        progress.value = (sent.toFloat() / size).coerceIn(0f, 1f)
                    }
                    out.flush()
                }
            }
            val resp = readAllTolerant(stream)
            Log.i(TAG, "pm install: ${resp.trim()}")
            if (!resp.contains("Success")) return@withContext fail(resp.trim().ifBlank { "安装失败（没有输出）" })
            watchVersion.value = runCatching { installedVersion() }.getOrDefault(0L)
            progress.value = 1f
            phase.value = Phase.Connected
            null
        } catch (e: Exception) {
            Log.w(TAG, "安装失败", e)
            fail(brief(e))
        }
    }

    /** 把库里的异常翻译成用户能照做的动作。 */
    private fun human(e: Throwable): String = when (e) {
        is io.github.muntashirakon.adb.AdbAuthenticationFailedException ->
            "手表不认这个密钥：请在手表上重新打开「无线调试 → 使用配对码配对设备」，填新配对码点「配对」"
        is io.github.muntashirakon.adb.AdbPairingRequiredException ->
            "这台手表还没配对：请填配对码，先点「配对」"
        is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "连不上手表（${e.javaClass.simpleName}）：确认手表仍开着「无线调试」，端口每次都会变"
        // ⚠ TLS 握手被对端中止时库只给 "Connection failed"（底层 SSLException 被吞了）：
        //   最常见的原因就是**手表不认这台手机的密钥** ⇒ 直接告诉用户去配对。
        else -> if (brief(e).contains("Connection failed", ignoreCase = true)) {
            "手表没接受这台手机（密钥未被信任）：请用「配对」输入手表上的 6 位配对码"
        } else {
            brief(e)
        }
    }

    private fun brief(e: Throwable): String {
        val m = e.message?.trim().takeUnless { it.isNullOrEmpty() }
        return m ?: e.javaClass.simpleName
    }
}
