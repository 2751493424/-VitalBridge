package com.gemini.healthbridge.core

import com.gemini.healthbridge.core.db.SleepNightEntity

/**
 * 睡眠阶段段的**紧凑编解码**（落库到 [SleepNightEntity.stagesJson]）。
 *
 * ## 为什么不直接序列化 `List<SleepStageDto>`
 *
 * 落库的是**历史**，一存就是几百晚。直接序列化一个数据类，一晚的 JSON 长这样：
 * ```
 * [{"startMs":1759600000000,"endMs":1759600300000,"kind":"light"}, ...]
 * ```
 * 十几段就是 800 多字符，其中 26 个字符里有 13 个是每个都相同的 `1759600000` 前缀。
 * 而存成「相对入睡时刻的偏移 + 时长 + kind 首字母」：
 * ```
 * 0,300,l|300,600,d|900,120,l
 * ```
 * 同样十几段只要 60 来个字符 —— **量级差十几倍**。
 *
 * ⚠ 这个"能省"不是可有可无的优化：一晚 400 行 × 十几段，省下来的正是
 * "要不要为它单独建索引/分表"那条分界线。既然一行能压到几十字节，就不必分表。
 *
 * ## 格式
 *
 * `偏移秒,时长秒,kind首字母` 三段一组，组间用 `|` 分隔，整体是一行纯文本。
 * - **偏移**相对 [SleepNightEntity.startMs]（所以读取时**必须**把 startMs 一起给）；
 * - **秒**而不是毫秒：阶段段的边界精度本来就是秒级（三星的 epoch 是分钟级），
 *   毫秒的三位尾数全是 0，存了也白存；
 * - **kind 首字母**：`l`ight / `d`eep / `r`em / `a`wake / `u`nknown，一一对应不歧义。
 *
 * ## 为什么用逗号和竖线而不是 JSON 数组
 *
 * 手写解析、手写生成，**不引入序列化器**，因为这段数据**只在两个地方用**
 * （写入时编码、读取时解码），而且格式是自解释的定长三段 ——
 * 用 JSON 的代价是每个字段一个引号一个冒号，收益是"不用手写解析"，
 * 而这里的手写解析只有二十行、有台架逐条钉住。
 *
 * ⚠⚠ **解码必须"容错"而不是"抛异常"**：这段文本是**历史数据**，
 * 一旦将来格式看错（或者用户从旧备份恢复到新版本），抛异常会让整个睡眠页打不开。
 * ⇒ 解析不了的**整段丢掉**，能解析的照常返回；全丢就返回空列表。
 * 这与本项目对"不可信输入"的一贯处理一致（公告 JSON 同款）。
 */
object SleepStages {

    /** 组内分隔符。 */
    private const val FIELD = ','

    /** 组间分隔符。 */
    private const val ROW = '|'

    /**
     * 编码。空输入返回 **null**（而不是空串）：
     * 落库时空串会被下游误判成"有数据但内容为空"，
     * 而 null 明确表示"没有可存的阶段" ⇒ 写入方据此**不覆盖**已有值。
     */
    fun encode(stages: List<SleepStageDto>, startMs: Long): String? {
        if (stages.isEmpty()) return null
        val sb = StringBuilder()
        for (s in stages) {
            // 偏移与时长都可能因为上游数据脏而变成负数；夹到 0 而不是丢掉整段 ——
            // 丢掉会让阶梯图出现"凭空少了一段"的假洞，夹到 0 至少保住时长与类型。
            val offSec = ((s.startMs - startMs) / 1000L).coerceAtLeast(0L)
            val durSec = (s.durationMs / 1000L).coerceAtLeast(0L)
            val k = letterOf(s.kind) ?: continue
            if (sb.isNotEmpty()) sb.append(ROW)
            sb.append(offSec).append(FIELD).append(durSec).append(FIELD).append(k)
        }
        return sb.takeIf { it.isNotEmpty() }?.toString()
    }

    /**
     * 解码。[startMs] **必须**是那一晚的 [SleepNightEntity.startMs]，
     * 因为存的是相对偏移 —— 传错会让整晚的阶段平移到别的时间上（不报错、只是错）。
     */
    fun decode(text: String?, startMs: Long): List<SleepStageDto> {
        if (text.isNullOrBlank()) return emptyList()
        val out = ArrayList<SleepStageDto>()
        for (row in text.split(ROW)) {
            if (row.isEmpty()) continue
            val f = row.split(FIELD)
            if (f.size != 3) continue
            val offSec = f[0].toLongOrNull() ?: continue
            val durSec = f[1].toLongOrNull() ?: continue
            val kind = kindOf(f[2]) ?: continue
            if (durSec <= 0L) continue
            val s = startMs + offSec * 1000L
            out.add(SleepStageDto(startMs = s, endMs = s + durSec * 1000L, kind = kind))
        }
        return out
    }

    /**
     * `kind` 值 → 首字母。未知值返回 null（**整段丢掉**，而不是落到 `unknown`）：
     * 落成 `unknown` 会在图上画出一个色块，而它其实是我们不认识的类型 ——
     * 让人以为"那段时间有记录但没分类"，与事实不符。
     */
    private fun letterOf(kind: String): Char? = when (kind) {
        SleepStageKind.AWAKE -> 'a'
        SleepStageKind.LIGHT -> 'l'
        SleepStageKind.DEEP -> 'd'
        SleepStageKind.REM -> 'r'
        SleepStageKind.UNKNOWN -> 'u'
        else -> null
    }

    /** 首字母 → `kind` 值。认不出的字母返回 null（整段丢掉）。 */
    private fun kindOf(letter: String): String? = when (letter) {
        "a" -> SleepStageKind.AWAKE
        "l" -> SleepStageKind.LIGHT
        "d" -> SleepStageKind.DEEP
        "r" -> SleepStageKind.REM
        "u" -> SleepStageKind.UNKNOWN
        else -> null
    }
}

/**
 * 从若干晚的 [SleepNightEntity] 统计**各阶段占比**的历史序列（趋势页的素材）。
 *
 * ⚠ 只收 `stagesJson` **非空**的那些晚：NULL 表示"那晚没存过阶段"，
 * 把它当成"各阶段占比 0"会把趋势线拽出一个假的零点 ——
 * 这正是本项目反复强调的「**没有 ≠ 0**」。
 *
 * @return 按 `dateEpochDay` 升序的 (日, 各阶段时长毫秒)。空表示一晚都没有存过。
 */
fun sleepStageHistory(nights: List<SleepNightEntity>): List<Pair<Long, Map<String, Long>>> {
    val out = ArrayList<Pair<Long, Map<String, Long>>>()
    for (n in nights) {
        val text = n.stagesJson ?: continue
        val stages = SleepStages.decode(text, n.startMs)
        if (stages.isEmpty()) continue
        val totals = LinkedHashMap<String, Long>()
        for (s in stages) {
            if (s.kind == SleepStageKind.UNKNOWN) continue
            totals[s.kind] = (totals[s.kind] ?: 0L) + s.durationMs
        }
        if (totals.isEmpty()) continue
        out.add(n.dateEpochDay to totals)
    }
    return out.sortedBy { it.first }
}
