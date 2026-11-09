package com.gemini.healthbridge.core

/**
 * 界面语言（模块 #15，2026-09-29）。
 *
 * ## 为什么在 core 里定义
 *
 * 免责声明、口径说明这些文案的主体在 `core`（`Energy.DISCLAIMER` / `Stress.DISCLAIMER` …），
 * 而 `core` 是**不碰 Android 资源系统的纯逻辑库** —— 它没有 `Context`，用不了
 * `getString(R.string.xxx)`。所以 core 自己持有一份「按语言取文案」的常量表，
 * 由调用方（app / wear）把当前语言传进来。
 *
 * ⚠ 这里**不放界面文案的全量翻译**（那些在两端 `strings.xml` 里）。这里只放
 * **core 自己产出、再由界面直接展示的字符串** —— 即各种 `DISCLAIMER` / `*_NOTE`
 * 以及需要随语言变化的枚举标签。
 *
 * ## 四条语言
 *
 * 简体中文（默认、也是所有文案的原文）、繁体中文、英文、韩语。
 *
 * ⚠ **`ZH_HANS` 的文案必须与改造前的原文逐字相同** —— 它是基准。翻译从它出发，
 * 且台架会用「原文就是 zh-Hans」这条不变式来校验（见离线校验台架）。
 */
enum class Language(
    /** BCP-47 标签，用于写进 SharedPreferences、也是界面选择项的持久化值。 */
    val tag: String,
    /** 选择弹窗里给用户看的名字（**用该语言自己的写法**，不翻译）。 */
    val displayName: String,
) {
    ZH_HANS("zh-Hans", "简体中文"),
    ZH_HANT("zh-Hant", "繁體中文"),
    EN("en", "English"),
    KO("ko", "한국어"),
    ;

    /**
     * 转成 `java.util.Locale`，供两端设置资源配置用。
     *
     * ⚠ 中文必须带上**脚本**（`zh-Hans` / `zh-Hant`）—— 只给 `zh-CN` / `zh-TW` 时，
     * 某些系统会把 "CN" 当地区而非脚本，简体资源目录 `values-zh-rCN` 与
     * 繁体 `values-zh-rTW` 的选择在部分机型上不一致。带上脚本最稳。
     */
    fun toLocale(): java.util.Locale = java.util.Locale.forLanguageTag(tag)

    /**
     * **列表分隔符**（拼接完整句子用）：中文全角逗号，其余语言半角逗号 + 空格。
     *
     * ⚠ 这不是"文案"而是**标点**，所以不进译文表 —— 但**也不能写死在代码里**：
     * 英文界面上出现「`Sync 3 records，Samsung Health is not authorized`」这种全角逗号
     * 就是明显的半成品，而它**不报错、也难以发现**（截图里才看得见）。
     *
     * ⚠ 判据用 [Language] 而**不是** `Locale.getDefault()` —— 界面语言是用户选的，
     * 与系统语言无关（见本文件顶部说明）。
     */
    fun listSeparator(): String = if (this == ZH_HANS || this == ZH_HANT) "，" else ", "

    /** **并列项分隔符**：中文顿号，其余语言半角逗号 + 空格。 */
    fun itemSeparator(): String = if (this == ZH_HANS || this == ZH_HANT) "、" else ", "

    companion object {
        /** 默认语言。系统语言识别不出来时的回落项。 */
        val DEFAULT: Language = ZH_HANS

        /**
         * 从 BCP-47 标签解析语言。
         *
         * 兼容 Android 系统给的常见形态：`zh-Hans-CN` / `zh-Hant-TW` / `zh-CN` / `zh-TW` /
         * `zh` / `en-US` / `ko-KR`。**中文必须区分简繁**（不能把 `zh-TW` 当简体），
         * 所以先看有没有 `Hant`/`Hans` 显式标记，没有再看地区码。
         *
         * @return 命中就返回对应语言；完全不支持的语言返回 [DEFAULT]。
         */
        fun fromTag(raw: String?): Language = parseOrNull(raw) ?: DEFAULT

        /**
         * 与 [fromTag] 相同，但**识别不出来时返回 null 而不是回落**。
         *
         * 首次启动匹配系统语言时用这个：拿到的可能是 `fr-FR` 这种我们不支持的语言，
         * 此时应该**继续往下试列表里的下一个**（系统语言列表可能有多个），
         * 全都不中才用 [DEFAULT]（在调用方判断）。
         */
        fun parseOrNull(raw: String?): Language? {
            val s = raw?.trim()?.lowercase() ?: return null
            if (s.isEmpty()) return null
            // 拆成 lang / script / region 三段（BCP-47 用 - 或 _ 连接）
            val parts = s.split('-', '_')
            val lang = parts.getOrNull(0) ?: return null
            val script = parts.getOrNull(1)?.takeIf { it.length == 4 }
            val region = parts.drop(1).firstOrNull { it.length == 2 || it.length == 3 }?.let {
                // 纯数字（UN M49）不算地区
                if (it.all { c -> c.isDigit() }) null else it
            }
            return when (lang) {
                "zh" -> when {
                    // 显式脚本标记最优先
                    script == "hant" -> ZH_HANT
                    script == "hans" -> ZH_HANS
                    // 没有脚本标记时按地区猜：
                    //   港澳台 → 繁体；其余（含 CN/SG/MY/无地区）→ 简体
                    // ⚠ 这里**只列确实通行繁体的地区**，不列"非简体"的地区码 ——
                    // 没列到的一律回落简体，避免把 zh-HK 之外的地区误判成繁体。
                    region in HANT_REGIONS -> ZH_HANT
                    else -> ZH_HANS
                }
                "en" -> EN
                "ko" -> KO
                else -> null
            }
        }

        /** 通行繁体中文的地区码。其余中文地区按简体处理。 */
        private val HANT_REGIONS = setOf("tw", "hk", "mo")
    }
}
