package com.gemini.healthbridge.phone

/**
 * 三星健康扩展指标的**读取结果类型**（2026-09-23）。
 *
 * ## 为什么这些不落库
 *
 * 这一批全部是**三星健康自己的数据**，而且都能**按时间范围重新读**（`readData` /
 * `aggregateData` 都支持指定区间）—— 不存在项目里那条「过期即永失」的问题
 * （对比：`sleep_odi` 与 `sleep_nights` 必须落库，因为三星的读取入口**只给得到最近一晚**）。
 *
 * 所以本文件里的东西**只在内存里存在**，打开页面时现读、离开就丢。
 * 好处是不需要任何 DB 迁移、不污染备份；代价是每次进页面都要读一次三星（本地 IPC，很快）。
 *
 * ## 为什么不带 `source`
 *
 * 这些数据的来源**就是三星健康**，没有第二个来源可以混淆 —— 与 `spo2` 那种
 * 「手表测的 / 三星读的混在一起」完全不同，所以不需要来源列。
 *
 * ## 字段全部"能空就空"
 *
 * 三星的字段大多是可选的（一次运动可能没有距离、一餐可能只有热量没记蛋白）。
 * **拿不到就是 null，绝不拿 0 顶** —— 0 会被下游当成"真的是 0"，
 * 而它其实只是"没记"。这条与 `SleepNightEntity` 的可空统计量是同一条纪律。
 */

/**
 * 扩展指标的**稳定 id**，用于查失败原因（`SamsungHealthReader.extraError(id)`）。
 *
 * 为什么用字符串常量而不是枚举：失败原因要跨「读取层 → 界面层」传递，
 * 而界面只需要"拿这个 key 问一下有没有错"，不需要认识类型。
 * 常量集中在这里，避免两边各写一遍字符串（写错一个字母就成了**永远查不到错误**，
 * 界面上表现为"没有数据"，而实际是"读失败了" —— 正是本项目最忌讳的那种静默）。
 */
object ExtraId {
    const val GLUCOSE = "glucose"
    const val CORE_TEMP = "core_temp"
    const val EXERCISE = "exercise"
    const val NUTRITION = "nutrition"
    const val WATER = "water"
    const val FLOORS = "floors"
    const val IHRN = "ihrn"
    const val STEPS = "steps"
    const val ACTIVITY_SUMMARY = "activity_summary"
    const val GOAL_STEPS = "goal_steps"
    const val GOAL_SLEEP = "goal_sleep"
    const val GOAL_ACTIVE_CAL = "goal_active_cal"
    const val GOAL_ACTIVE_TIME = "goal_active_time"
    const val GOAL_WATER = "goal_water"
    const val GOAL_NUTRITION = "goal_nutrition"
}

/**
 * 一条血糖记录。⚠ 单位 **mmol/L**（见 `core/SamsungExtras.kt` 的说明）。
 *
 * ⚠ 存的是**三星的原始枚举名**（`UNDEFINED`/`BREAKFAST`/`VENOUS`…），不是译文 ——
 * 译文随语言变，拿它当判据（如"是不是未标注"）在非中文下必然失配（模块 #15）。
 * 显示时用 `Glucose.mealLabel(raw)` 取译文；判"未标注"用 `SamsungExtras.isUndefined(raw)`。
 */
data class GlucoseReading(
    val atMs: Long,
    val mmolPerL: Float,
    /** 情境原始值（`MEAL_STATUS` 的枚举名，如 `BEFORE_MEAL`）。 */
    val meal: String,
    /** 采样来源原始值（`SAMPLE_SOURCE_TYPE`，如 `VENOUS`）。 */
    val source: String,
    /** 样本类型原始值（`MEASUREMENT_TYPE`，如 `WHOLE_BLOOD`）。 */
    val sample: String,
)

/**
 * 一条核心体温（`BODY_TEMPERATURE`）。
 *
 * ⚠ **这不是我们测的**，而是**三星健康里已有的核心体温记录** —— 来源是合作设备
 * （体温计、体温贴）或用户手动录入。手表自己产不出这个量（它给的是
 * `SKIN_TEMPERATURE`，即腕部皮温，两者差 1~3℃）。
 */
data class CoreTempReading(val atMs: Long, val celsius: Float)

/**
 * 一次运动记录。
 *
 * 三星的一个 `ExerciseType` 数据点里可以装**多个** `ExerciseSession`
 * （铁人三项就是游泳+骑车+跑步三段），所以读取层会把它们**摊平**成多条。
 * [groupStartMs] 保留原始数据点的时间，便于把同一场比赛的三段认出来。
 */
data class ExerciseEntry(
    val groupStartMs: Long,
    val startMs: Long,
    val endMs: Long,
    /** 三星的枚举名，已翻译成中文（未知名保留原文形式）。 */
    val typeName: String,
    /** 用户自定义标题（三星没有合适的预定义类型时才有）。 */
    val customTitle: String?,
    val durationMs: Long,
    val calories: Float,
    /** **米**。没有记录距离的运动类型为空。 */
    val distanceM: Float?,
    val meanHr: Float?,
    val maxHr: Float?,
    /** 可计数的运动（跳绳/深蹲等）的次数。 */
    val count: Int?,
)

/** 一条营养记录（用户自己在三星健康里记的）。 */
data class NutritionEntry(
    val atMs: Long,
    /** 餐次（早餐/午餐…），已翻译。 */
    val mealType: String,
    val title: String?,
    val calories: Float,
    val carbohydrateG: Float?,
    val proteinG: Float?,
    val fatG: Float?,
)

/** 一条饮水记录。单位 **mL**。 */
data class WaterEntry(val atMs: Long, val ml: Float)

/** 一条爬楼记录。单位 **层**。 */
data class FloorEntry(val atMs: Long, val floors: Float)

/** 一条心律不齐通知记录。`status` 原样是 `DETECTED` / `UNDEFINED`。 */
data class IhrnEntry(val atMs: Long, val status: String)

/**
 * 今天的活动量汇总（**聚合取数**）。
 *
 * 全部可空：三星健康里没有数据（手机没带、没开通权限）时就是 null。
 * 界面上要如实说"没有数据"，**不能显示 0** —— 0 步会被读成"今天一步没走"。
 */
data class ActivityTotals(
    /** 活动消耗（千卡）。三星把「活动消耗」与「总消耗（含基础代谢）」分开给。 */
    val activeKcal: Float?,
    val totalKcal: Float?,
    /** 活动时长。 */
    val activeMs: Long?,
    /** **米**。 */
    val distanceM: Float?,
    /** 步数。 */
    val steps: Long?,
)

/**
 * 六类每日目标（**聚合取数**）。
 *
 * 全部可空 —— 用户在三星健康里没设过就是 null。**我们不会替他编一个默认目标**
 * （那会变成"我们觉得你应该走一万步"，是越权）。
 */
data class DailyGoals(
    val steps: Int?,
    val activeKcal: Int?,
    val activeMs: Long?,
    val waterMl: Float?,
    val nutritionKcal: Float?,
    /** 目标就寝时间，形如 `23:30`。 */
    val bedTime: String?,
    /** 目标起床时间。 */
    val wakeTime: String?,
)
