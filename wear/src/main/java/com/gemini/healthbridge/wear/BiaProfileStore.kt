package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.BiaProfileDto

/**
 * 手表本地缓存的 BIA 用户资料（身高/体重/性别/年龄）。
 *
 * ## 为什么资料要缓存在本地而不是每次现取
 * 资料由手机经蓝牙推送（手表没有三星健康 Data SDK，读不到用户档案）。
 * 而 BIA 测量要求 `getHealthTracker(BIA_ON_DEMAND, TrackerUserProfile)` ——
 * 取 tracker 的那一刻就必须有资料，不能等一次蓝牙往返。所以手机一推来就落盘，
 * 测量时直接读本地。
 *
 * ## 为什么只存 SharedPreferences 不存数据库
 * 它是**单条配置**，不是时序记录：只有一个"当前值"，写入即覆盖，没有历史可查。
 * 放进 Room 就要为它建表、写迁移、写 DAO、加进备份 —— 成本远大于收益
 * （而且"当前资料"进备份也没有意义，换台手机就该重新从三星健康读一次）。
 *
 * 存的是**已校验过**的资料：`save()` 会先过 `BiaProfileDto.isValid()`，
 * 不合法就整条丢弃并返回 false，避免把脏资料留在盘上、下次测量直接换来状态码 18。
 */
object BiaProfileStore {

    private const val PREF = "vitaltrace_bia"
    private const val KEY_HEIGHT = "height_cm"
    private const val KEY_WEIGHT = "weight_kg"
    private const val KEY_GENDER = "gender"
    private const val KEY_AGE = "age"
    private const val KEY_SAVED_AT = "saved_at"

    /** 写入并返回是否被接受（不合法则原样保留旧值，不做半更新）。 */
    fun save(context: Context, profile: BiaProfileDto): Boolean {
        if (!profile.isValid()) return false
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit()
            .putFloat(KEY_HEIGHT, profile.heightCm)
            .putFloat(KEY_WEIGHT, profile.weightKg)
            .putInt(KEY_GENDER, profile.gender)
            .putInt(KEY_AGE, profile.age)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    /**
     * 读回资料。四项缺任何一项都返回 null ——
     * 部分可用的资料比没有资料更危险：`TrackerUserProfile.Builder` 允许只设一部分
     * （未设的字段会是 0），于是"身高 0cm"这种资料会一路走到 SDK，
     * 拿到一个看似成功、实际无意义的结果。宁可返回 null 让界面提示"资料不全"。
     */
    fun load(context: Context): BiaProfileDto? {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_HEIGHT) || !sp.contains(KEY_WEIGHT)) return null
        if (!sp.contains(KEY_GENDER) || !sp.contains(KEY_AGE)) return null
        val p = BiaProfileDto(
            heightCm = sp.getFloat(KEY_HEIGHT, 0f),
            weightKg = sp.getFloat(KEY_WEIGHT, 0f),
            gender = sp.getInt(KEY_GENDER, -1),
            age = sp.getInt(KEY_AGE, -1),
        )
        return if (p.isValid()) p else null
    }

    /** 资料写入时间（毫秒），用于在界面上标明"这份资料是什么时候同步过来的"。 */
    fun savedAt(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_SAVED_AT, 0L)

    /**
     * 清空缓存。
     *
     * 手机推来一个 `profile = null` 的帧时调用 —— 那表示**手机端也没有资料了**
     * （用户在三星健康里清空了档案，或用户档案读权限被撤销）。
     *
     * 为什么必须真的清掉而不是"保持旧值继续用"：资料是"配置"而不是时序数据，
     * 只有一个当前值。留着旧资料会让手表继续用一份**已经不再成立**的身高体重去测量，
     * 得到一条看起来正常、实际错误的体成分记录 —— 比直接提示"缺资料"难发现得多。
     * 清掉之后界面会走"缺资料 → 请先在手机端同步资料"那条分支，用户看得见原因。
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
