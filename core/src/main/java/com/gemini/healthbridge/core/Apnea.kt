package com.gemini.healthbridge.core

/**
 * 睡眠呼吸暂停检测的展示约定。
 *
 * 数据来自**「三星健康监测器」**（另一个 App），用户在该应用里开启检测并授权同步之后，
 * 才会进到三星健康、才轮得到我们读（见 `SamsungHealthReader.readSleepApnea`）。
 *
 * ## 为什么单独放一个 core 文件
 * 三态枚举的**文案必须两端完全一致**：手机端和手表端都会显示它，各自写一套迟早会出现
 * 「同一份数据在两个地方说法不同」。这里是**纯文案与归一化**，没有算法 ——
 * 不是"忘了写台架"，是本来就没有可算的东西（同样的情况还有能量评分）。
 *
 * ## 措辞是一条红线
 * `DETECTED_SIGN` 只回答"这次有没有检测到**迹象**"，不回答"你有没有病"：
 *   · `NOT_DETECTED` = 这次没检测到迹象 —— **不等于"没有睡眠呼吸暂停"**；
 *   · `UNDEFINED`    = 没判定出来 —— 更不能当成阴性。
 * 所以文案一律带「迹象」，并且**不给任何"正常/异常"的判断词**。
 */
object Apnea {

    const val DETECTED = "DETECTED"
    const val NOT_DETECTED = "NOT_DETECTED"
    const val UNDEFINED = "UNDEFINED"

    /**
     * 归一化：只认识这三个取值，其余（三星以后新增的、或者读到脏数据）
     * 一律落回 [UNDEFINED]。
     *
     * 这样上层的 `when` 永远只需要处理三种情况，**不会因为三星加了一个枚举值就崩**，
     * 也不会把一个读不懂的值当成"没问题"。
     */
    fun normalize(sign: String?): String = when (sign) {
        DETECTED, NOT_DETECTED, UNDEFINED -> sign
        else -> UNDEFINED
    }

    /** 三态的显示文案。 */
    fun label(sign: String): String = when (normalize(sign)) {
        DETECTED -> Text.of("s_77410ca5")
        NOT_DETECTED -> Text.of("s_f550b283")
        else -> Text.of("s_1b2e0280")
    }

    /** 只有 [DETECTED] 用警示色：另外两种都不是"好消息"，但也都**不是结论**。 */
    fun isAlarming(sign: String): Boolean = normalize(sign) == DETECTED

    /** 免责：手机与手表共用同一句，不各写一遍。 */
    val NOT_A_DIAGNOSIS: String get() = Disclaimer.apnea(Text.lang)

    /** 拿不到数据时显示的说明（区域 / 机型门槛）。 */
    val UNAVAILABLE_NOTE: String get() =
        Text.of("s_1e763f3e") +
            Text.of("s_7dae3cf4")

    /**
     * 与自研氧减指数（ODI）的关系。
     *
     * 两个数**会出现在同一个页面上**，所以必须说清它们不是一回事，
     * 否则用户会拿一个去核对另一个、然后以为其中一个坏了。
     */
    val VS_ODI_NOTE: String get() =
        Text.of("s_94f3b1f4") +
            Text.of("s_74e3d025") +
            Text.of("s_a6019732") +
            Text.of("s_372f9cac")
}
