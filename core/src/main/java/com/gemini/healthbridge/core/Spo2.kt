package com.gemini.healthbridge.core

import kotlin.math.roundToInt

/**
 * 血氧换算与纵轴口径。
 *
 * 为什么单独抽成一个**纯函数对象**：这里踩过一个真实且代价不小的坑 ——
 * 三星健康 Data SDK 的血氧字段
 *（`DataType.BloodOxygenType.OXYGEN_SATURATION`、`OxygenSaturation.oxygenSaturation`、
 * 老版 `HealthConstants.OxygenSaturation.SPO2`）官方文档都写明是
 * **"represented as percentage"，取值域 0–100**，而早期实现按"0~1 小数"又乘了 100：
 * 真实 97% → 9700 → 被 `coerceIn(0,100)` 压成 100。结果是**整晚每个点都是 100%**，
 * 折线贴在图表顶边、界面上看起来"根本没读到三星健康的数据"。
 *
 * 这类"量纲/尺度搞错"的错误在这个项目里已经出现过不止一次（幅值判据、频带能量比），
 * 共同特点是**编译期完全看不出来、只会在真机上表现为"数据看起来不对"**。
 * 所以把它做成不依赖 Android 的纯逻辑，接进离线 JVM 断言（离线校验台架），
 * 让它在下一次有人改动换算时立刻失败。
 */
object Spo2 {

    /** 物理上可能的血氧取值域（%）。低于 50% 已属危重，但保留余量不做业务判断。 */
    const val MIN_PCT = 0
    const val MAX_PCT = 100

    /**
     * 把三星健康给的血氧原始值换算成整数百分比。
     *
     * 主口径是 **0–100 的百分比**（与官方文档一致）。同时对 0–1 的**小数**口径做兼容：
     * 生理上 SpO2 ≤ 1% 不可能存活，所以 ≤ 1 的值只可能是小数写法，乘 100 一定是对的。
     * 两种口径因此都能自愈，不必依赖某一版 SDK 的实际行为。
     *
     * @return 0..100 的整数；非法输入（NaN / 负数）返回 0
     */
    fun percent(raw: Float): Int {
        if (raw.isNaN() || raw <= 0f) return 0
        val pct = if (raw <= 1f) raw * 100f else raw
        return pct.roundToInt().coerceIn(MIN_PCT, MAX_PCT)
    }

    /**
     * 血氧柱状图的纵轴范围。
     *
     * 刻意不复用给心率用的"至少 50 跨度"策略 —— 那会画出 70~120 这种
     * "血氧能到 120%"的轴。这里的规则是：贴着数据、以 5 为步长、跨度至少 10，
     * 上界不超过 [MAX_PCT]。
     *
     * 一个容易写错的地方：当数据都在 100 附近（例如整晚都是 100%）时，
     * **向下扩展**才补得出跨度 —— 上界已经被生理上限 100 顶住，往上加是加不动的。
     * 第一版就写成了"往上加"，于是 `hi` 被夹回 100、跨度仍然是 5，
     * 而且那条分支看起来还"处理过了"。是离线校验台架的不变式断言把它抓出来的。
     *
     * @param vals 参与取范围的数值（通常是一天里各小时的 min/max）；空列表返回 90..100
     */
    fun bounds(vals: List<Int>): IntRange {
        if (vals.isEmpty()) return 90..100
        val dataLo = vals.min()
        val dataHi = vals.max()

        // 上界：向上取到 5 的倍数并留一点头顶空间，但不得超过 100。
        // 数据本身就越界时（修正后不应出现）至少要装得下，否则柱子会画到画布外。
        var hi = (((dataHi + 4) / 5) * 5).coerceAtMost(MAX_PCT)
        if (hi < dataHi) hi = dataHi

        // 下界：向下取到 5 的倍数，跨度不足 10 时**向下**扩展补齐。
        var lo = (((dataLo - 2) / 5) * 5)
        if (hi - lo < 10) lo = hi - 10
        if (lo < MIN_PCT) lo = MIN_PCT

        // 夹到 0 之后跨度又可能不足（只在异常低的数据上触发），此时只能抬上界。
        if (hi - lo < 10) hi = (lo + 10).coerceAtMost(MAX_PCT)

        return lo..hi
    }
}
