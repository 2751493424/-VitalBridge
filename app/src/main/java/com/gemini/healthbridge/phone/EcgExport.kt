package com.gemini.healthbridge.phone

import android.content.Context
import com.gemini.healthbridge.core.db.EcgWaveEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 心电波形的 CSV 导出（手机端）。
 *
 * 2026-10-05 用户要求：「手机端心电现在不能导出，给每个心电记录打开后增加导出模块」。
 *
 * ## 为什么不去复用 `PhoneBtServer.saveEcgCsv`
 *
 * 那份处理的是**手表经蓝牙发来的 payload**（`ecg:…|ppg:…|fs:…|rp:…`），其中
 * `ppg_raw` 是手表 PPG 的**原始整数量**，并且带 `rpeaks`（手表端的 QRS 检出结果）。
 *
 * 而手机端这份的数据来自**本地库**：
 * - 心电 = [EcgWaveEntity.waveJson]（逗号分隔的 mV 值）
 * - PPG = [PpgWaveEntity.greenWave]（**归一化**绿光，DC 的倍数、6 位小数）
 *
 * ⇒ 两者**输入不同、口径也不同**。硬凑成一个函数，就得在函数里判断"数据是从哪来的"，
 *   那比两个入口更难维护。所以分开，但 **CSV 头部格式保持同一套**
 *   （`# fs=..Hz samples=.. columns=..`）—— 离线脚本只认头部，不关心谁生成的。
 *
 * ## ⚠⚠ 两条必须守住的诚实性
 *
 * 1. **列名要如实**：手机端的 PPG 是**归一化**值，所以列名写 `ppg_green_norm`，
 *    **不是** `ppg_raw`。写成 `ppg_raw` 会让人以为那是手表的原始 ADC 读数 ——
 *    这正是本项目最忌讳的「**数值看着正常、但量的不是那个量**」那一类错误。
 * 2. **不写 `rpeaks` 头**：QRS 检出只在手表端（`wear` 的 `EcgSignal`），
 *    手机端没有那份实现。与其在手机端再写一个检测器（两份算法必然漂移），
 *    不如**老实地不写这个头**。需要 rpeaks 请用手表端的导出。
 */
object EcgExport {

    /** 落盘目标里的文件名前缀（**手机端本地心电**）。 */
    const val PHONE_NAME_PREFIX = "vitalbridge_ecg_"

    /**
     * 一次落盘的结果。
     *
     * ⚗⚗ **为什么把"重复"单独列成一态**（2026-10-05 用户要求「不再导出一份多余的文件」）：
     *   去重之后"点了导出"有三种**互斥**结局 —— 新写成 / 早写过了 / 没写成。
     *   压成 `null` 与非 `null` 两种，界面就**说不出**"为什么没多出文件"，
     *   而用户恰恰是**先发现目录里没有新文件**才来问的。
     *
     * ⚠ [Duplicate] **不是错误**：文件确实在，就是他手里那份 ⇒ 文案必须给路径，
     *   否则他会以为"又失败了"。
     */
    sealed interface SaveResult {
        /** 新写入了一份：[path] 是落盘位置。 */
        data class Created(val path: String) : SaveResult

        /** 这份内容之前导出过 ⇒ **没有写第二份**：[path] 是**那份已有文件**的位置。 */
        data class Duplicate(val path: String) : SaveResult

        /** 确实没写成（无内容 / 落盘报错）。 */
        data object Failed : SaveResult
    }

    /**
     * 手表端上传过来的双路波形用的前缀（**来源可辨**）。
     *
     * ⚗ 为什么两套前缀：两者落在**同一个目录**里，而内容口径不同 ——
     *   手机端这份 PPG 是**归一化**值、且没有 `rpeaks`；
     *   手表端那份 PPG 是**原始计数**、并且带手表端检出的 `rpeaks`
     *   （见本文件开头那两条诚实性说明）。
     * ⇒ 只靠时间戳区分来源，用户和离线脚本都无从判断；前缀是**零成本**的区分手段。
     * ⚠ 两者都以 `vitaltrace_` 开头：它们是同一个 App 的产物，排序时挨在一起。
     */
    const val WATCH_NAME_PREFIX = "vitalbridge_watch_ecg_"

    /**
     * 落盘子目录（相对「下载」）＝ `VitalBridge/CSV`。
     *
     * ⚠ 父目录复用 [DataBackup.DIR_NAME]（`VitalBridge`）而不是再写一遍字面量：
     *   备份与波形导出放在**同一个 App 目录**下，用户找起来是一处；
     *   写成两个字面量的话，哪天改了其中一个，文件就散到两处去了。
     * ⚠ 子目录用 `CSV` 而不是直接扔在 `VitalBridge/` 根下：
     *   根目录已经有备份 JSON，混在一起会越来越乱（2026-10-05 用户明确要求这个路径）。
     */
    private const val SUB_DIR = "CSV"

    /**
     * 落盘子目录的相对路径（供 MediaStore 的 `RELATIVE_PATH` 用）。
     *
     * ⚠⚠ **唯一的一份拼法**：手机端本地导出与手表端上传的落盘**都用它**。
     *   两处各写一遍字面量的话，哪天改歪一个，文件就散到两个目录里，
     *   而用户会看到"有的能在我找的那个文件夹里、有的不在"。
     */
    fun relativeDir(): String = android.os.Environment.DIRECTORY_DOWNLOADS +
        "/" + DataBackup.DIR_NAME + "/" + SUB_DIR

    /**
     * 给用户看的完整路径（`下载/VitalBridge/CSV/<文件名>`）。
     *
     * ⚠ 与 [relativeDir] **同一套拼法** —— "提示里写 A、文件其实在 B"
     *   是最容易被投诉的一类错误，而这种错误恰恰来自两处各拼一遍。
     */
    fun displayPath(name: String): String = relativeDir() + "/" + name

    /**
     * 构造 CSV 文本。**纯逻辑**（不碰 Context / 文件系统）。
     *
     * @param waveJson 逗号分隔的 mV 值（`EcgWaveEntity.waveJson` 原样传入）。
     * @param greenWave 逗号分隔的**归一化**绿光值；没有则为 `null`（列只剩心电）。
     * @param sampleRateHz 实测采样率。`null` 或 ≤0 时头部写 `fs=?` ——
     *   ⚠ **不猜、不填默认值**：采样率错了，后面所有时间量（RR、PAT）都会跟着错。
     */
    fun buildCsv(
        waveJson: String,
        greenWave: String?,
        sampleRateHz: Int?,
    ): String {
        val ecg = waveJson.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ecg.isEmpty()) return ""
        // ⚠ PPG 与心电的**采样率可能不同**（两条通道各自采集），长度也可能不等。
        //   所以只按**下标**对齐、短的一方到了末尾就不写 —— 不做插值、
        //   不重复填最后一个值（那会造出并不存在的样本点）。
        val ppg = greenWave?.split(',')?.map { it.trim() }?.takeIf { it.isNotEmpty() }
        val fs = sampleRateHz?.takeIf { it > 0 }

        val sb = StringBuilder(ecg.size * 16)
        sb.append("# fs=").append(fs?.toString() ?: "?").append("Hz")
        sb.append(" samples=").append(ecg.size)
        sb.append(if (ppg != null) " columns=ecg_mv,ppg_green_norm" else " columns=ecg_mv")
        sb.append('\n')
        for (i in ecg.indices) {
            sb.append(ecg[i])
            if (ppg != null) {
                sb.append(',')
                // 缺失就留空列（不是填 0）——0 是一个合法读数，填 0 等于伪造数据点。
                ppg.getOrNull(i)?.let { sb.append(it) }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * 落盘到 `下载/VitalBridge/CSV`（**含内容去重**）。手机端本地导出与手表上传都走这里。
     *
     * ## 流程
     *
     * 1. 算内容指纹并**查台账**：命中 ⇒ 返回 [SaveResult.Duplicate]（**不写第二份文件**）；
     * 2. 没命中 ⇒ 写盘，成功后**记台账**；
     * 3. 异常 ⇒ [SaveResult.Failed]。
     *
     * ⚠ 去重放在**落盘之前**，不是"写完再删重的" —— 后者会先产出多余文件，
     *   删失败就留下两份（而那正是用户要避免的）。
     * ⚠ **只对"写入"去重**，不限制「打开保存位置」：那个按钮点几次都该正常。
     *
     * ⚠ 用 MediaStore 而不是自建路径：从 API 29 起应用无法随便往公共目录写文件，
     *   MediaStore 是唯一**不需要权限**的合法途径（`minSdk` 已是 29，不必再判版本）。
     *
     * @param namePrefix 决定来源（[PHONE_NAME_PREFIX] / [WATCH_NAME_PREFIX]）。
     * @param nameTimeMs 写进文件名的时间（取"这份数据是什么时候的"，不是"此刻几点"）。
     */
    fun saveCsv(
        context: Context,
        csv: String,
        namePrefix: String,
        nameTimeMs: Long,
    ): SaveResult {
        if (csv.isEmpty()) return SaveResult.Failed
        // ⚗⚗ 先查台账：同一份内容**不再写第二份**
        val fingerprint = EcgExportStore.fingerprint(csv)
        EcgExportStore.find(context, fingerprint)?.let { path ->
            // ⚠⚠ **台账说"导出过"还不够，得核实那份文件还在不在**。
            //   用户完全可能去文件管理器把 CSV 删了、再回来点导出（很自然的动作）——
            //   只信台账的话，他会**永远**看到"已导出过"而再也拿不到文件，
            //   而且找不到任何补救办法（只能清 App 数据）。那不是"去重"，是卡死。
            //   ⇒ 文件已不在 ⇒ 当作没导出过，往下走（重新写，台账被覆盖）。
            if (stillExists(context, path)) return SaveResult.Duplicate(path)
        }
        return runCatching {
            val name = namePrefix +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nameTimeMs)) + ".csv"
            val resolver = context.contentResolver
            val cv = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv")
                // ⚠⚠ **必须声明 RELATIVE_PATH**，否则文件落在「下载」根目录下 ——
                //   而用户是按 `下载/VitalBridge/CSV` 去找的（2026-10-05 实测反馈：
                //   他在那个目录里看到一个空文件夹）。不写这一行的症状是
                //   "导出成功了，但目标目录里什么都没有"。
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, relativeDir())
                // ⚠ 先标 PENDING、写完再清 —— 否则其它 App 可能读到**半截文件**
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: return@runCatching SaveResult.Failed
            resolver.openOutputStream(uri)?.use { os ->
                os.write(csv.toByteArray(Charsets.UTF_8))
            } ?: return@runCatching SaveResult.Failed
            resolver.update(
                uri,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                },
                null, null,
            )
            // ⚠ 返回给用户看的路径要与实际落盘位置**逐段一致**（下载/父目录/子目录/文件名），
            //   否则"提示里写 A、文件其实在 B" —— 他照着去找会找不到。
            val path = displayPath(name)
            // ⚠ 台账记在**写盘成功之后**：先记后写的话，写失败会留下"已导出"的假记录
            //   ⇒ 用户再点就再也导不出来（而且没有任何报错）。
            EcgExportStore.remember(context, fingerprint, path, nameTimeMs)
            SaveResult.Created(path)
        }.getOrElse { SaveResult.Failed }
    }

    /**
     * 那份已导出的文件现在还在不在。
     *
     * ⚠ 只按 `DISPLAY_NAME` 查，不带目录条件：文件名里带 `yyyyMMdd_HHmmss`，
     *   重名概率极低；而 `RELATIVE_PATH` 在不同设备上"带不带结尾斜杠"并不一致，
     *   用它做等值比较反而会**查不到 → 误判成已删除 → 又写一份**（本末倒置）。
     * ⚠ 查询异常一律按"**在**"处理（返回 true）：宁可漏掉一次重新导出，
     *   也不要因为一次查询失败就产出重复文件（用户要的正是"别多出文件"）。
     */
    private fun stillExists(context: Context, displayPath: String): Boolean = runCatching {
        val name = displayPath.substringAfterLast('/')
        if (name.isEmpty()) return@runCatching true
        context.contentResolver.query(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Downloads._ID),
            android.provider.MediaStore.Downloads.DISPLAY_NAME + " = ?",
            arrayOf(name),
            null,
        )?.use { c -> c.moveToFirst() } ?: false
    }.getOrDefault(true)

    /**
     * 打开「下载」里的落盘目录（2026-10-05 用户要求「在导出 CSV 按钮旁边加打开保存位置」）。
     *
     * ## 三级兜底（与项目里其他"跳外部"的入口同一条纪律：**绝不做成点了没反应的死键**）
     *
     * 1. 精确打开那个子目录（`DocumentsContract` + `externalstorage.documents`）——
     *    Android 上"打开某个文件夹"的标准途径；
     * 2. 退化：`ACTION_VIEW_DOWNLOADS`（打开「下载」页，用户自己再点进去）；
     * 3. 都不行 ⇒ 返回 false，由调用方**明说文件在哪**。
     *
     * ⚠⚠ **不先 `resolveActivity` 再发**：Android 11+ 的包可见性会让它返回 null
     *   （除非清单里为这个 intent 声明 `<queries>`），于是"机器上明明有文件管理器，
     *   却被判成没有"。⇒ 直接发出去 + `runCatching` 兜底，绕开这个坑。
     *
     * @return 是否成功发出某个 intent。`false` 时调用方**必须**告诉用户路径，不能静默。
     */
    fun openFolder(context: Context): Boolean {
        // ① 精确目录
        runCatching {
            // ⚠ 同一套拼法（`primary:` 是外部存储的卷标，与 RELATIVE_PATH 无关）
            val docId = "primary:" + relativeDir()
            val uri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", docId,
            )
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(uri, "vnd.android.document/directory")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onSuccess { return true }
        // ② 退化：打开「下载」
        return runCatching {
            // ⚠ 用**字符串常量**而不是 `Intent.ACTION_VIEW_DOWNLOADS`：
            //   后者是 API 29 才加的符号，编译期解析不到（实测报 Unresolved reference）。
            //   字符串形式在所有版本上等价，且不需要任何 SDK 判断。
            context.startActivity(
                android.content.Intent("android.intent.action.VIEW_DOWNLOADS")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    /**
     * 心电与 PPG 的时间戳容差（毫秒）—— 同一会话两条通道的写入先后。
     *
     * ⚠ 调用方用它构造**范围查询**（`measuredAt ∈ [t-容差, t+容差]`），
     *   而**不要**用等值匹配：两条通道落库时间戳会差几秒，
     *   `measuredAt = t` 几乎必然落空。
     * ⚠ 也不要放宽到"整个会话之外"：那会把别的时刻的 PPG 配上来，
     *   两路样本点在时间上对不齐，而离线分析**无从发现**。
     */
    const val PPG_MATCH_MS = 15_000L
}
