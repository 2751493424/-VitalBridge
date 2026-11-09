// 由 v161 补丁生成：PAT 失败原因的「落库中文 → 当前语言」反查表。
//
// ⚠⚠ 为什么需要它：`patNote` 是**手表测量时**写进库的字符串（Ptt 用 `Text` 按当时的界面语言渲染成中文），
//   手机端切到英文/韩文时它不会变 —— 于是界面上出现"英文界面里夹一段中文"。
//   与 `EcgNote.detail` 同一套办法：**不改库**，显示时按模板反查、用当前语言重渲染。
package com.gemini.healthbridge.core

import java.util.Locale

object PttNote {

    /**
     * 片段组合表（与 `Ptt.analyze` 里拼 `NotApplicable` 的顺序一一对应）。
     * ⚠ 长的放前面：否则短组合会把长组合的前缀吃掉。
     */
    private val COMPOSITIONS: List<List<String>> = listOf(
        // 两路心率对不上 + 说明
        listOf("s_22250a66", "s_1cfc6dcd"),
        // 没有连续可用时段 + 说明
        listOf("s_b8e28369", "s_353d9c43"),
        // 逐拍离散度超限（截图里那条）
        listOf("s_48c84e5c", "s_16400509"),
        // 单片段
        listOf("s_65744483"),
        listOf("s_fe02b6d2"),
        listOf("s_9e978bdb"),
        listOf("s_9c2b2915"),
        // ⚠ 「没采到脉搏波」(s_8035af21) 是**手表端** wear 资源，core 表里没有 ⇒ 反查不到，
        //   这种会原样显示（少见），要修得把该串也加进 core 表。
    )

    private val PH = Regex("%(\\d+)[\u0024]s")

    /** 把落库的 `patNote` 翻成**当前语言**；认不出来时原样返回。 */
    fun reason(note: String?): String? {
        if (note.isNullOrBlank()) return note
        // 手表会在原因后面追加「【诊断】…」这一段（纯数字诊断，给手表结果页看的）。
        // 手机端不展示它：① 用户不需要 ② 它的模板在手表侧拼装，无法反查翻译。
        val head = cutDiagnosisTail(note)
        if (head != note) {
            // 只有原因部分，且能翻译就翻；翻不出来就退回"整段原样"（至少不丢信息）
            translate(head)?.let { return it }
        }
        return translate(head) ?: note
    }

    /** 截掉尾部「【诊断】/【數字】/[Diagnosis]…」那种诊断段。 */
    private fun cutDiagnosisTail(s: String): String {
        val idx = s.indexOf('\n')
        if (idx < 0) return s
        // 任何以「【」或「[」开头的后续段都算诊断尾巴
        var i = idx
        while (i >= 0 && i < s.length) {
            val rest = s.substring(i + 1)
            if (rest.startsWith("【") || rest.startsWith("[")) return s.substring(0, i)
            i = s.indexOf('\n', i + 1)
        }
        return s
    }

    private fun translate(head: String): String? {
        for (keys in COMPOSITIONS) {
            for (lang in Language.entries) {
                val rx = regexFor(keys, lang) ?: continue
                val m = rx.matchEntire(head) ?: continue
                return render(keys, m.groupValues.drop(1), Text.lang)
            }
        }
        return null
    }

    private fun regexFor(keys: List<String>, lang: Language): Regex? {
        val sb = StringBuilder()
        for (k in keys) {
            val tpl = templateOf(k, lang) ?: return null
            if (tpl.second.isEmpty()) {
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

    /** ⚠ 无插值的片段在 `TABLE` 里、带插值的在 `TPL` 里（见 `EcgNote.templateOf` 的教训）。 */
    private fun templateOf(key: String, lang: Language): Pair<String, List<String>>? {
        val tpl = Text.tpl(key, lang)
        if (tpl != null) return tpl to PH.findAll(tpl).map { it.value }.toList()
        val plain = Text.ofOrNull(key, lang) ?: return null
        return plain to emptyList()
    }

    private fun literal(s: String): String = s.replace("%%", "%")

    private fun render(keys: List<String>, args: List<String>, lang: Language): String? {
        val sb = StringBuilder()
        var ai = 0
        for (k in keys) {
            val (tpl, phs) = templateOf(k, lang) ?: return null
            if (phs.isEmpty()) {
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
