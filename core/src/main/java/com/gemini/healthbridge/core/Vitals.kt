package com.gemini.healthbridge.core

import kotlin.math.roundToInt

/**
 * 「按天的每小时桶」的编解码（2026-10-13，DB v22 起 `daily_vitals.buckets` 用它）。
 *
 * ## 为什么要有它
 *
 * 用户的原话（2026-10-13）：「**不该是每天软件跟三星健康同步后数据都保留在了本地，
 * 以后访问历史都是直接从本地读取嘛**。要是本地也没有数据，再尝试从三星健康读取」。
 *
 * 在那之前，心率/血氧两张**点表**只覆盖「今天」（血氧只有 48 小时内的点记录），
 * 于是翻历史时几乎每条都落进"现读三星"那条路 —— 慢、离线看不到，
 * 而且同一晚的睡眠页里明明有统计值（那份在 `sleep_nights`）。这是一处**架构不一致**。
 *
 * ## 存什么、不存什么
 *
 * 存**每小时一个桶**（一天最多 24 条），**不存逐点序列**：
 * 睡眠期血氧是每分钟一个点、一晚 400+ 条，落进点表会把「最近记录」列表整个刷屏
 * （这是 `syncToday` 里早就定下的一条判断）。而**画图/概览要的正是每小时的最低~最高**，
 * 所以按小时的桶既够用、又把一天的体量压到几十字节。
 *
 * ⚠ 桶里**不带"这一小时是不是睡眠"**：那个由显示时按本地 `sleep_nights` 的会话区间推算
 * （见 `overlapsSleep`）—— 睡眠口径只有一处，存进桶里就成了第二处。
 *
 * ## 格式
 *
 * `h:min:max:avg` 用 `;` 隔开、按 `hour` 升序，例如 `7:58:72:65;8:60:80:70`。
 * ⚠ 解码**认不出来的片段直接丢掉**（宁少一根柱，也不要抛异常崩在渲染里）——
 * 库里存的东西会被各种版本读写过，解码必须比编码宽容。
 */
/**
 * 存储用的「每小时桶」。
 *
 * ⚠⚠ 与 `SyncProtocol.HourBucket`（手表协议里的同名结构）**刻意分开**：
 *   那个带 `@Serializable`、属于**协议**（字段一改就影响两端通信），
 *   这个属于**存储**（字段一改要写迁移）。两者恰好长得一样，但变更的理由完全不同 ——
 *   合成一个类型会让"改协议"和"改库"变成同一件事，而它们的兼容成本差得远。
 *   ⚠ 顺带的好处：这个文件因此**不依赖 kotlinx-serialization**，
 *     可以单独编进 JVM 校验台架逐条验编解码。
 */
data class VitalBucket(val hour: Int, val min: Int, val max: Int, val avg: Int)

object Vitals {

    /** 一天的桶 → 一行字符串（按 `hour` 升序，空列表给空串）。 */
    fun encodeBuckets(buckets: List<VitalBucket>): String =
        buckets.sortedBy { it.hour }
            .joinToString(";") { "${it.hour}:${it.min}:${it.max}:${it.avg}" }

    /**
     * 一行字符串 → 桶。
     *
     * ⚠ 解析失败（段数不对 / 不是数字 / hour 越界）**逐段丢掉**，不抛异常：
     *   这一段数据是"锦上添花"的缓存，不值得为它崩掉整个页面。
     */
    fun decodeBuckets(s: String): List<VitalBucket> {
        if (s.isBlank()) return emptyList()
        return s.split(';').mapNotNull { seg ->
            val p = seg.split(':')
            if (p.size != 4) return@mapNotNull null
            val h = p[0].toIntOrNull() ?: return@mapNotNull null
            val mn = p[1].toIntOrNull() ?: return@mapNotNull null
            val mx = p[2].toIntOrNull() ?: return@mapNotNull null
            val av = p[3].toIntOrNull() ?: return@mapNotNull null
            if (h !in 0..23) null else VitalBucket(h, mn, mx, av)
        }.sortedBy { it.hour }
    }

    /**
     * 把 `(时刻, 值)` 采样点按**本地日的小时**聚合（0..23）。
     *
     * `day0` = 那一天**本地** 0 点的毫秒。**窗口外的点直接丢掉** ——
     * 一个点落在 25 点（跨天了）说明调用方给错了窗口，硬算成某一格反而会撒谎。
     *
     * ⚠ 用 `(t - day0) / 3600_000` 直接算而不是走 `LocalDateTime`：
     *   与界面那张图（`bucketByHour`）**同一套算法**，两处必须一致 ——
     *   否则同一天在"今天页"和"历史页"上会落进不同的格子。
     *   代价是夏令时切换那天会偏一格，与既有实现一致（这个目标用户没有夏令时）。
     *
     * ⚠⚠ **上面那句 `filter { … }` 不能省**（台架抓出来的）：
     *   Kotlin 的整数除法**向零截断**，所以"差 1 分钟到 day0"的点会算成**第 0 小时**
     *   （`-60000 / 3600000 == 0`），而不是被丢掉。那会把**前一天**的一个点混进今天的 0 点柱子里 ——
     *   看着一切正常，量的是另一天的数。所以先按时间戳**硬筛**窗口，再分桶。
     */
    fun bucketsOf(points: List<Pair<Long, Int>>, day0: Long): List<VitalBucket> =
        points.filter { it.first >= day0 && it.first < day0 + 24 * 3_600_000L }
            .groupBy { ((it.first - day0) / 3_600_000L).toInt() }
            .filterKeys { it in 0..23 }
            .map { (h, pts) ->
                val v = pts.map { it.second }
                VitalBucket(h, v.min(), v.max(), v.average().roundToInt())
            }
            .sortedBy { it.hour }

    /** 桶的最低/最高/平均（概览卡那三个数）。空列表给 null —— **不要给 0**。 */
    fun minOf(buckets: List<VitalBucket>): Int? = buckets.minOfOrNull { it.min }

    fun maxOf(buckets: List<VitalBucket>): Int? = buckets.maxOfOrNull { it.max }

    fun avgOf(buckets: List<VitalBucket>): Int? =
        buckets.takeIf { it.isNotEmpty() }?.map { it.avg }?.average()?.roundToInt()

    /**
     * 采样点总数（用来解释"这根柱为什么这么细"）。
     *
     * ⚠ 与 [bucketsOf] 用**同一个**窗口判据（按时间戳硬筛，不靠整数除法）——
     *   两处口径不一致时，"图上 5 根柱、条数写着 6"这种自相矛盾不会报错。
     */
    fun sampleCountOf(points: List<Pair<Long, Int>>, day0: Long): Int =
        points.count { it.first >= day0 && it.first < day0 + 24 * 3_600_000L }
}
