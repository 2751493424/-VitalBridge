package com.gemini.healthbridge.phone

import android.content.Context
import com.gemini.healthbridge.core.BiaProfileDto

/**
 * 三星健康的「上一次已知状态」：权限快照 + 用户档案。
 *
 * ## 为什么需要（2026-11-04 用户报的两个问题）
 *
 * ① **权限会「假性丢失」**：三星健康一旦被系统强停 / 清后台，`snapshot()` 就连不上、返回 `null`。
 *    而界面把 `null` 折成了 `false`（`snap?.granted ?: false`）⇒ 用户页显示「还有 1 项未授权」。
 *    用户点开三星健康，权限又「有了」—— 同一份授权在两个界面之间反复横跳，他完全不知道该信哪个。
 *
 * ② **用户资料会「读不到」**：`readBiaProfile()` 失败返回 `null` ⇒「用户资料 读不到」。
 *    但资料本身没有任何变化，只是三星健康没在跑。
 *
 * ## 策略：粘性（读到过就记住）
 *
 * · 读到了 → **立刻覆盖**记忆（无论 true 还是 false）。SDK 明确答了 `false` 就是用户真的撤了权限，
 *   必须如实反映，不能因为"以前是 true"就一直骗自己。
 * · 读不到（`null`）→ **回退到记忆值**。「问不出来」≠「没有」。
 * · 从来没有记忆过 + 读不到 → 仍然是 `null`，界面据 `samsungUnreadable` 说「连接失败」，
 *   **不要**催用户重新授权（那是一个他做了也没用的动作）。
 *
 * ⚠ 这一层只影响**显示**，不参与任何业务判断：真正读数据时仍然实时问 SDK，
 *   读不到就当次没有。缓存只是"别把没问到说成没有"。
 */
object HealthMemory {

    private const val PREF = "health_memory"
    private const val K_HAS = "snap_has"
    private const val K_GRANTED = "snap_granted"
    private const val K_SLEEP = "snap_sleep"
    private const val K_SKIN = "snap_skin"
    private const val K_BIA = "snap_bia"
    private const val K_PROFILE = "snap_profile"

    private const val P_HAS = "profile_has"
    private const val P_H = "profile_h"
    private const val P_W = "profile_w"
    private const val P_G = "profile_g"
    private const val P_A = "profile_a"

    private fun p(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ── 权限快照 ──────────────────────────────────────────────

    /** 记住一次**真实读到**的快照。只应在 `snapshot()` 返回非 null 时调用。 */
    fun saveSnapshot(ctx: Context, s: SamsungHealthReader.Snapshot) {
        p(ctx).edit()
            .putBoolean(K_HAS, true)
            .putBoolean(K_GRANTED, s.granted)
            .putBoolean(K_SLEEP, s.sleep)
            .putBoolean(K_SKIN, s.skinTemp)
            .putBoolean(K_BIA, s.bia)
            .putBoolean(K_PROFILE, s.profile)
            .apply()
    }

    /** 上一次真实读到的快照；从未读到过则返回 null。 */
    fun loadSnapshot(ctx: Context): SamsungHealthReader.Snapshot? {
        val sp = p(ctx)
        if (!sp.getBoolean(K_HAS, false)) return null
        return SamsungHealthReader.Snapshot(
            granted = sp.getBoolean(K_GRANTED, false),
            sleep = sp.getBoolean(K_SLEEP, false),
            skinTemp = sp.getBoolean(K_SKIN, false),
            bia = sp.getBoolean(K_BIA, false),
            profile = sp.getBoolean(K_PROFILE, false),
        )
    }

    // ── 用户档案 ──────────────────────────────────────────────

    /**
     * 记住一份**真实读到**的档案。
     *
     * ⚠ 只在值真的变了时才写盘：这个函数会在每次同步时被调，
     *   无脑写等于每分钟一次磁盘 IO（手表常连着，同步很频繁）。
     */
    fun saveProfile(ctx: Context, dto: BiaProfileDto) {
        val sp = p(ctx)
        val same = sp.getBoolean(P_HAS, false) &&
            sp.getFloat(P_H, 0f) == dto.heightCm &&
            sp.getFloat(P_W, 0f) == dto.weightKg &&
            sp.getInt(P_G, -1) == dto.gender &&
            sp.getInt(P_A, 0) == dto.age
        if (same) return
        sp.edit()
            .putBoolean(P_HAS, true)
            .putFloat(P_H, dto.heightCm)
            .putFloat(P_W, dto.weightKg)
            .putInt(P_G, dto.gender)
            .putInt(P_A, dto.age)
            .apply()
    }

    /** 上次读到过的档案；从未读到过则返回 null。 */
    fun loadProfile(ctx: Context): BiaProfileDto? {
        val sp = p(ctx)
        if (!sp.getBoolean(P_HAS, false)) return null
        val dto = BiaProfileDto(
            heightCm = sp.getFloat(P_H, 0f),
            weightKg = sp.getFloat(P_W, 0f),
            gender = sp.getInt(P_G, 0),
            age = sp.getInt(P_A, 0),
        )
        // ⚠ 仍然过一遍合法性：缓存可能是老版本写进去的、或者被外部改坏
        return dto.takeIf { it.isValid() }
    }
}
