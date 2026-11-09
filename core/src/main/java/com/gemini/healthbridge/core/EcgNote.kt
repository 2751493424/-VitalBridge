package com.gemini.healthbridge.core

import java.util.Locale

/**
 * 心电记录的**显示层**：把库里那两个中文串翻成当前语言。
 *
 * ## 库里存的是什么
 *
 * `ecg_records` 有两列是**中文原样落库**的：
 *
 * | 列 | 内容 | 为什么是中文 |
 * |---|---|---|
 * | `classification` | `窦性心律` / `房颤提示` / `心率偏高` / `心率偏低` / `无法判定` | **落库值**：与手机端同源的判定结果，两端与历史记录都按它对齐 |
 * | `note` | 一整句说明（`节律整齐，心率 74 次/分，未检出房颤特征`） | `EcgAnalyzer` 生成时按**当时的语言**拼出来 |
 *
 * ⚠⚠ 两个都不能改成"按当前语言落库"：
 * · `classification` 是**判定结果**，日文/英文/繁体只是它的显示名 —— 翻掉它，
 *   历史记录与新记录就对不上，筛选/统计也失效（而且**不报错**）。
 * · `note` 更糟：它按**生成那一刻的语言**落库，于是"手表当时是英文 + 用户后来切中文"
 *   会得到一条英文历史，反之亦然 —— 一条记录长什么样取决于**测量时**的语言。
 *
 * ## 所以：落库保持原文，显示时翻译
 *
 * [classLabel] 查表换显示名；[detail] 把 `note` 翻回当前语言。
 *
 * ## [detail] 怎么翻：按"它是哪几个 key 拼起来的"反推
 *
 * `note` 不是一条独立的文案，而是 `EcgAnalyzer` 里 **1~4 个 `Text` 片段拼起来**的
 * （例如房颤那条 = `s_7c50fd86` + `s_55365a28` + `s_2b45c431`）。
 * 所以这里维护一张**片段组合表**：对每种组合、每种语言，用该语言的模板生成一个正则
 * （`%N$s` 换成捕获组），拿 `note` 去匹配；命中就把捕获到的**数值原样**塞进
 * **当前语言**的模板重新渲染。
 *
 * 三个好处：
 * ① **不改库、不迁移** —— 老记录也一起变；
 * ② 数值（心率、相干性、占比…）是**从原文里抠出来的**，不需要额外的列；
 * ③ 输入语言无所谓：四种语言的模板都试一遍，所以"当时存的是英文"也能翻回来。
 *
 * ⚠ 认不出来就**原样返回** —— 宁可显示一句旧语言，也不要在界面上留空。
 * 新增 `EcgAnalyzer` 的说明文案时，**必须**把它加进 [COMPOSITIONS]，否则那条记录
 * 会永远停在落库时的语言（**不报错**）。
 */
object EcgNote {

    // ═══════════════════════ 落库值（中文常量）═══════════════════════
    //
    // ⚠⚠ **要比较就用这几个常量，绝不要拿显示名（`stringResource` / `Text.of`）去比。**
    //    改造时踩过一次：手表结果页的配色原本写的是
    //        when (e.classification) { "窦性心律" -> 绿; "房颤提示" -> 红; … }
    //    字面量被搬进 `strings.xml` 后变成
    //        when (e.classification) { stringResource(R.string.s_88992735) -> 绿; … }
    //    而 `classification` 仍是中文 —— 英文界面下两个值永远不相等，
    //    **每次都落到 `else`**（结论颜色全变灰），而且**不报任何错**。
    //    这正是"判 DANGER 要看值去哪"那条纪律的反面教材：搬字面量时把比较键也搬走了。
    //    门禁见多语言一致性校验第 [3] 节。

    /** 落库值：窦性心律。 */
    const val SINUS = "窦性心律"

    /** 落库值：检出房颤特征。 */
    const val AFIB = "房颤提示"

    /** 落库值：心率偏高。 */
    const val HR_HIGH = "心率偏高"

    /** 落库值：心率偏低。 */
    const val HR_LOW = "心率偏低"

    /** 落库值：无法判定。 */
    const val UNDETERMINED = "无法判定"

    /** `classification` → `Text` key。落库值本身不动。 */
    private val CLASS_KEYS: Map<String, String> = mapOf(
        SINUS to "s_88992735",
        AFIB to "s_52524593",
        HR_HIGH to "s_f357ba15",
        HR_LOW to "s_f3576e8b",
        UNDETERMINED to "s_c34c0523",
    )

    /**
     * `note` 的**片段组合表**（与 `EcgAnalyzer` 里的拼接一一对应）。
     *
     * ⚠ 顺序无关，但**每条组合都要列全**；先试长组合（片段多的），避免短组合
     * 把长组合的前缀吃掉。
     */
    private val COMPOSITIONS: List<List<String>> = listOf(
        // 信号质量不足（相干性…、锐度…、峰值因子…，检出…拍），请用指腹…
        listOf("s_29600ac3", "s_62814298", "s_843d374b", "s_a2a7026f"),
        // 心电波形过弱（幅度… mV），请确认指腹…
        listOf("s_0772dd89", "s_36929cac"),
        // 房颤：节律不齐（RR 相对变异…%，相邻差异超 20% 占比…%），检出房颤特征；…
        listOf("s_7c50fd86", "s_55365a28", "s_2b45c431"),
        // 心率偏低：节律整齐…（40~50 次/分）；静息心动过缓…
        listOf("s_f083cd00", "s_0dafe869"),
        // 单片段
        listOf("s_19ce3b05"),
        listOf("s_bdcc99a8"),
        listOf("s_bf0f539a"),
        listOf("s_f2ee522f"),
        listOf("s_8b6b05b4"),
        listOf("s_0b0a6764"),
        listOf("s_81fe396e"),
        listOf("s_72a90bae"),
        listOf("s_c18d7cc3"),
        listOf("s_76e736e1"),
    )

    /** `classification` 的显示名；认不出来原样返回。 */
    fun classLabel(cls: String?): String? {
        if (cls == null) return null
        val k = CLASS_KEYS[cls] ?: return cls
        return Text.ofOrNull(k, Text.lang) ?: cls
    }

    /**
     * 把落库的 `note` 翻成**当前语言**。
     *
     * @return 翻译后的句子；认不出组合（或 `note` 为空）时**原样返回**。
     */
    fun detail(note: String?): String? {
        if (note.isNullOrBlank()) return note
        for (keys in COMPOSITIONS) {
            for (lang in Language.entries) {
                val rx = regexFor(keys, lang) ?: continue
                val m = rx.matchEntire(note) ?: continue
                return render(keys, m.groupValues.drop(1), Text.lang) ?: note
            }
        }
        return note
    }

    /** 把这条 `note` 的**规范形式**写出来（zh-Hans）—— 写入侧用它落库。 */
    fun canonicalDetail(note: String?): String? {
        if (note.isNullOrBlank()) return note
        for (keys in COMPOSITIONS) {
            for (lang in Language.entries) {
                val rx = regexFor(keys, lang) ?: continue
                val m = rx.matchEntire(note) ?: continue
                return render(keys, m.groupValues.drop(1), Language.ZH_HANS) ?: note
            }
        }
        return note
    }

    // ═══════════════════════ 内部 ═══════════════════════

    /** 生成后的模板里，`Text.kt` 的占位符就是 `%1$s` / `%2$s` 这种。 */
    // ⚠⚠ 这里**不能**用生字符串 `"""%(\d+)\$s"""` —— Kotlin 的生字符串（triple-quoted）
    //    **不支持 `\$` 转义**（只有普通字符串支持），`$s` 会被当成模板变量，
    //    直接编译报 `Unresolved reference 's'`。
    //    用普通字符串 + 字符类 `[$]` 表达字面 `$`，既避开模板又不用转义。
    private val PH = Regex("%(\\d+)[$]s")

    /** 把一条 `note` 的片段组合，在给定语言下变成一个"字面量 + 捕获组"的正则。 */
    private fun regexFor(keys: List<String>, lang: Language): Regex? {
        val sb = StringBuilder()
        for (k in keys) {
            val tpl = templateOf(k, lang) ?: return null
            if (tpl.second.isEmpty()) {          // 无插值的片段：整段当字面量
                sb.append(Regex.escape(literal(tpl.first)))
                continue
            }
            var last = 0
            for (m in PH.findAll(tpl.first)) {
                sb.append(Regex.escape(literal(tpl.first.substring(last, m.range.first))))
                sb.append("(.+?)")
                last = m.range.last + 1
            }
            sb.append(Regex.escape(literal(tpl.first.substring(last))))
        }
        return Regex(sb.toString())
    }

    /**
     * 取一段的"模板 + 占位符个数"。
     *
     * ⚠⚠ **无插值的片段不在 `TPL` 里，在 `TABLE` 里**（`Text.kt` 分两张表）。
     * 只查 `Text.tpl` 会得到 null，于是**任何含无插值片段的组合都永远匹配不上**
     * （`EcgNote` 里 14 条组合有 12 条都含这种片段）—— 表现就是"这句还是中文"，
     * **不报错**。这个 bug 是离线校验台架台架抓出来的。
     */
    private fun templateOf(key: String, lang: Language): Pair<String, List<String>>? {
        val tpl = Text.tpl(key, lang)
        if (tpl != null) return tpl to PH.findAll(tpl).map { it.value }.toList()
        val plain = Text.ofOrNull(key, lang) ?: return null
        return plain to emptyList()
    }

    /**
     * 模板里的字面量部分。
     *
     * ⚠ `String.format` 会把 `%%` 渲染成单个 `%`，而**正则要匹配的是渲染后的文本** ——
     * 所以比对前必须先还原成 `%`，否则带百分号的那条（`占比 20%`）永远匹配不上。
     */
    private fun literal(s: String): String = s.replace("%%", "%")

    /** 用**目标语言**的模板重新渲染，参数按捕获组的顺序塞回去。 */
    private fun render(keys: List<String>, args: List<String>, lang: Language): String? {
        val sb = StringBuilder()
        var ai = 0
        for (k in keys) {
            val (tpl, phs) = templateOf(k, lang) ?: return null
            if (phs.isEmpty()) {                 // 无插值：捕获组为 0，直接取该语言的整段
                sb.append(tpl)
                continue
            }
            if (phs.size > args.size - ai) return null
            val sub = args.subList(ai, ai + phs.size)
            ai += phs.size
            sb.append(String.format(Locale.ROOT, tpl, *sub.toTypedArray()))
        }
        return sb.toString()
    }
}
