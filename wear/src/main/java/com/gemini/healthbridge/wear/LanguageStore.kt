/*
 * ⚠⚠ 本文件是**手机端 LanguageStore 的镜像**（模块 #15，2026-09-29）。
 *
 * 两端包名不同（phone / wear），无法共享同一个文件，但**逻辑必须逐字一致** ——
 * 只有偏好名与键名相同，两端才会记住同一个选择；只有机制相同，两端才会一起生效。
 *
 * **改任意一边必须同步改另一边**，并用同一套改法重新生成（本文件由手机版改包名生成）。
 * 已知会漂移的点： 里的 /LANG=en_US.UTF-8
LC_CTYPE="C.UTF-8"
LC_NUMERIC="C.UTF-8"
LC_TIME="C.UTF-8"
LC_COLLATE="C.UTF-8"
LC_MONETARY="C.UTF-8"
LC_MESSAGES="C.UTF-8"
LC_ALL=C.UTF-8/ 三件套。
 */
package com.gemini.healthbridge.wear

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.gemini.healthbridge.core.Language
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.Text

/**
 * 界面语言的持久化与"首次启动匹配手机语言"（模块 #15，2026-09-29）。
 *
 * ## 三条规则
 *
 * ① **首次启动**（偏好里没有记录）时，按系统语言列表逐个匹配，命中
 *    [Language] 支持的语言就用它；一个都不命中回落 [Language.DEFAULT]。
 *    匹配结果**立即写入偏好** —— 之后不再看系统语言。
 * ② **之后只认偏好**：用户在「用户」页选过语言就固定用它，
 *    即使之后改了手机系统语言，App 也不会自己变。
 * ③ **切换即时生效**：靠 [set] 写偏好 + `attachBaseContext` 重新套用配置 +
 *    `Activity.recreate()` 重建界面，不需要杀进程。
 *
 * ## ⚠⚠ 为什么不用 `updateConfiguration` + `recreate`（2026-09-29 踩过）
 *
 * 第一版是这么写的：`context.resources.updateConfiguration(cfg)` 改资源配置，
 * 再 `activity.recreate()`。**在 API 24+ 上无效** —— `Resources.updateConfiguration`
 * 从那版起只作用于**当前那个 Resources 实例**，而 `recreate()` 重建 Activity 时
 * `ResourcesManager` 会**重新按系统配置生成一份全新的 Resources**，
 * 于是我们改的那份被直接丢弃，界面仍是系统语言。
 * 现场表现：**选完语言后除了选择弹窗里那一行，界面一个字都不变**。
 *
 * 正确做法是**在 Context 生成的最早时机套用配置**：
 *   · [wrap] 在 `Activity.attachBaseContext` 里把语言写进一个新的 `Context`，
 *     这个 Context 会贯穿该 Activity 的整个生命周期（`getResources()` 走它）；
 *   · `recreate()` 后 `attachBaseContext` **会再被调用一次**，于是新 Activity
 *     拿到的是新语言的 Context —— 这才是"重建后仍然生效"的原因。
 *
 * ⚠ 这也解释了为什么必须两端都写 `attachBaseContext`：只改 Application 的话，
 * Activity 的 Context 仍是系统配置那份，界面不会变。
 *
 * ## 为什么不用 AppCompatDelegate.setApplicationLocales
 *
 * 那套机制要求 Activity 继承 `AppCompatActivity`（或至少接入 AppCompat 的
 * locale 分发），而本项目两端的 Activity 都是 `ComponentActivity`（纯 Compose，
 * 没引 appcompat）。引入 appcompat 只为换语言会带来一个整包依赖与主题体系改造，
 * 收益不抵成本。我们改用**更轻的自持方案**：偏好存字符串 tag + `attachBaseContext`。
 */

private const val PREF = "language_settings"

/** 偏好键。值为 [Language.tag]（如 `zh-Hant`）。 */
private const val KEY_LANG = "lang"

/** 偏好里表示"还没选过"的哨兵值。UI 上「跟随系统」用它。 */
const val LANG_FOLLOW_SYSTEM = "system"

object LanguageStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /**
     * 当前语言。**读取即初始化**：偏好为空时执行"首次启动匹配系统语言"并把结果写回。
     *
     * ⚠ 这个函数有副作用（首次调用会写偏好），是**刻意**的 —— 让"首次启动"这件事
     * 只需要一个入口，不必另起一个 init 流程而在各处漏调。副作用幂等：只在键不存在时写。
     *
     * ⚠ 传进来的 `context` 如果已经被 [wrap] 套过语言，**不要用它取系统语言列表**
     * （`resources.configuration.locales` 会是套过之后的值）。匹配系统语言要走
     * [wrap] 之前的原始 Context —— 实践中首次匹配发生在 `Application.onCreate`，
     * 那里拿到的天然是系统配置，没问题。
     */
    fun current(context: Context): Language {
        val l = resolve(context)
        // ⚠⚠ core 的文案（`Text.of` / `Text.fmt`）读的是 `Text.lang`。
        //    这里不同步，就成了「界面换了语言、core 那部分文案还是旧语言」——
        //    而且**不报错**，只是看起来像换了一半。
        //    统一放在这一处，所有调用点（`rememberLang()` / `wrap()` / `Application.onCreate`）
        //    自动生效。
        //    ⚠⚠ **2026-10-02 补**：这一行原先只加在 `set()` 里、**漏了这里** ——
        //    于是手表**全新安装**（或任何一次冷启动）时 `Text.lang` 停在默认的 `ZH_HANS`，
        //    英文/韩文手表上 core 产出的那一半文案仍是中文，直到用户手动切一次语言。
        //    手机端一直是对的（它从一开始就把这行放在 `current()` 里），只有手表漏了。
        Text.lang = l
        return l
    }

    /** [current] 的纯计算部分（不带同步 `Text.lang` 的副作用）。 */
    private fun resolve(context: Context): Language {
        val p = prefs(context)
        val saved = p.getString(KEY_LANG, null)
        if (saved != null && saved != LANG_FOLLOW_SYSTEM) {
            // 已选过：直接认它。解析失败（比如以后删了某种语言）才回落。
            return Language.parseOrNull(saved) ?: Language.DEFAULT
        }
        if (saved == LANG_FOLLOW_SYSTEM) {
            // 用户显式选了「跟随系统」：每次都按系统语言现算。
            return matchSystem(context)
        }
        // 从没选过：首次启动 —— 匹配系统语言并写回（之后就不再变）。
        val matched = matchSystem(context)
        p.edit().putString(KEY_LANG, matched.tag).apply()
        return matched
    }

    /** 按系统语言列表匹配。命中就返回；一个都不命中回落默认。 */
    private fun matchSystem(context: Context): Language {
        val tags = systemLanguageTags(context)
        for (t in tags) {
            Language.parseOrNull(t)?.let { return it }
        }
        return Language.DEFAULT
    }

    /**
     * 取系统语言标签列表。
     *
     * API 24+ 用 `LocaleList`（用户在设置里可能排了多个偏好语言），
     * 更早的版本退回单个 `Locale`。本项目 minSdk 29，实际只会走 LocaleList 这路，
     * 但保留回落以免以后调 minSdk。
     */
    private fun systemLanguageTags(context: Context): List<String> {
        val cfg = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = cfg.locales
            (0 until list.size()).mapNotNull { list.get(it)?.toLanguageTag() }
        } else {
            @Suppress("DEPRECATION")
            listOf(cfg.locale?.toLanguageTag().orEmpty())
        }
    }

    /** 已保存的原始值（UI 上用：判断是否显示「跟随系统」）。 */
    fun rawTag(context: Context): String =
        prefs(context).getString(KEY_LANG, null) ?: LANG_FOLLOW_SYSTEM

    /**
     * 用户主动选语言。只写偏好。
     *
     * ⚠ 生效靠调用方随后 `activity.recreate()` —— 重建时 `attachBaseContext`
     * 会读到新偏好并套用新语言（见 [wrap]）。
     */
    fun set(context: Context, lang: Language) {
        prefs(context).edit().putString(KEY_LANG, lang.tag).apply()
        // ⚠ 必须**一起**同步 core 的当前语言：只写偏好时，重建后的界面是新的、
        //   而 core 产出的文案（`Text.of` / `Text.fmt`）还是旧的 —— 静默地各说各话。
        Text.lang = lang
    }

    /**
     * 把当前语言套进一个新的 `Context`，供 `attachBaseContext` 使用。
     *
     * 用法（两端 Activity 都必须有）：
     * ```
     * override fun attachBaseContext(newBase: Context) {
     *     super.attachBaseContext(LanguageStore.wrap(newBase))
     * }
     * ```
     *
     * ⚠⚠ **必须包在 `attachBaseContext` 里**，不能图省事在 `onCreate` 里
     * `updateConfiguration` —— 那在 API 24+ 上会被 `recreate()` 重置（见文件头）。
     */
    fun wrap(base: Context): Context {
        val lang = current(base)
        val locale = lang.toLocale()
        val cfg = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cfg.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            cfg.locale = locale
        }
        // ⚠ 两个 setLocale 不能少：`createConfigurationContext` 只保证 `resources`
        // 是新的，`Configuration.locale` 与 `LocaleList` 都得显式设，
        // 否则部分机型上 `locale` 会留着旧值（界面语言对了、`Locale.getDefault()` 没对）。
        @Suppress("DEPRECATION")
        cfg.locale = locale
        cfg.setLayoutDirection(locale)
        return base.createConfigurationContext(cfg)
    }

    /**
     * **按当前语言取字符串**（**数据层专用**，2026-10-01）。
     *
     * ## 为什么不能用 `context.getString(...)`
     *
     * 数据层传进来的多半是 `applicationContext` —— 它的 configuration 是**系统语言**，
     * 不是用户在「用户」页选的语言。直接 `getString` 会得到系统语言，
     * 于是「界面全变了、这些提示还是系统语言」。
     *
     * 这里先用 [wrap] 把 locale 套好再取，与 Compose 里的 `stringResource` 同源
     * （后者用的是 `attachBaseContext` 套过的 Activity context）。
     *
     * ## 为什么缓存
     *
     * `createConfigurationContext` 会复制一整套 `Resources`，每次取串都建一个是白白的开销。
     * 按**语言**缓存一个就够 —— 语言变了（`set()` / `current()` 里同步过）自然重建。
     *
     * ⚠ 与 core 的 `Text.lang` 是**同一时刻**被设置的（都在 [current] / [set] 里），
     * 所以「core 的文案」与「这里取的文案」不会各说各话。
     */
    fun string(context: Context, id: Int, vararg args: Any?): String {
        val lang = current(context)
        val wrapped = cachedWrapped?.takeIf { it.first == lang }?.second
            ?: wrap(context).also { cachedWrapped = lang to it }
        return if (args.isEmpty()) {
            wrapped.getString(id)
        } else {
            wrapped.getString(id, *args)
        }
    }

    /** [string] 用的「按语言缓存」槽位。 */
    @Volatile
    private var cachedWrapped: Pair<Language, Context>? = null
}

/**
 * 取当前语言，供 Composable 里写免责文案用。
 *
 * 用法（放在 Composable 顶部，读一次即可）：
 * ```
 * val lang = rememberLang()
 * Text(Disclaimer.energy(lang))
 * ```
 *
 * ⚠ 刻意**不用 `remember`**：这个调用是读一个 SharedPreferences 字符串 + 一次
 * `parseOrNull`，纳秒级，`remember` 省下来的时间还不够它自己的开销。
 * 而用 `remember` 反而要操心 key：`LocalContext.current` 在 `recreate()` 后
 * 仍是同一个对象，当 key 不会触发重算。直接每次读，语义最直白、绝不会读到陈旧值。
 */
@androidx.compose.runtime.Composable
fun rememberLang(): Language =
    LanguageStore.current(androidx.compose.ui.platform.LocalContext.current)

/**
 * 把一条**待求值**的界面文案 [ResText] 渲染成字符串（当前语言）。
 *
 * ⚠⚠ **本文件是手机端 `LanguageStore.kt` 的镜像，这个函数也必须逐字一致。**
 *
 * ## 为什么要这个函数
 *
 * 数据层（`BluetoothSync` / `SyncEngine` 等）**不把文案存成字符串**，
 * 只存资源 id + 参数 —— 因为字符串一旦存进 `StateFlow` 就再也不会重算，
 * 切语言后它停在旧语言（用户 2026-10-02 截图：手机端同一屏
 * `Watch not connected` 是英文、紧挨着的 `蓝牙: 等待手表连接…` 是中文，
 * 后者就是 `StateFlow<String>` 里的旧值）。
 *
 * 求值必须发生在**渲染那一刻**，而渲染由 Compose 重组驱动 ——
 * 语言变化必然导致 `recreate()` + 重组，于是自然就对了。
 *
 * ⚠ [ResText.Raw] 分支装的是已算好的串，**切语言不会变**；
 *   只有「本来就与语言无关」的内容（设备名等）才该走它。
 * ⚠ 参数**本身还可以是一条 [ResText]**（如「已记录经期：%s」里的日期）：
 *   求值前会先递归求值，否则那条嵌套片段会停在旧语言。
 * ⚠ 参数兜空用 `map { it ?: "" }`：`stringResource` 的 vararg 是**非空** `Any`，
 *   而 [ResText.Res.args] 允许 null（数据层拿到的值常常可空）。
 */
@androidx.compose.runtime.Composable
fun resText(r: ResText): String = when (r) {
    is ResText.Raw -> r.text
    is ResText.Res ->
        if (r.args.isEmpty()) androidx.compose.ui.res.stringResource(r.id)
        else androidx.compose.ui.res.stringResource(r.id, *resolveArgs(r.args).toTypedArray())
    // core 文案表（纯 Kotlin 层看不见 `R`，只能用 core key）。
    // ⚠ 走 core 的 `coreTextOf`，**不要**在这里自己写 `Text.of/fmt` 的选择逻辑 ——
    //   那样会与 `ResText.coreText()` 分头漂移（两张表都要查这个坑就在那儿）。
    is ResText.Core -> com.gemini.healthbridge.core.coreTextOf(r.key, resolveArgs(r.args))
    // 分隔符也随语言变，所以它同样是**迟到绑定**的一段。
    is ResText.Sep ->
        if (r.list) com.gemini.healthbridge.core.Text.lang.listSeparator()
        else com.gemini.healthbridge.core.Text.lang.itemSeparator()
    is ResText.Cat -> {
        // ⚠ 不能用 `joinToString` —— 它的 lambda 不是 inline，
        //   而在普通 lambda 里调用 `@Composable` 函数编译不过。
        val sb = StringBuilder()
        for (part in r.parts) sb.append(resText(part))
        sb.toString()
    }
}

/**
 * 求值 [ResText.Res.args] / [ResText.Core.args]：参数**本身是 [ResText]** 就递归求值，
 * 其余原样返回（只把 null 兜成 `""`，因为 `stringResource` 的 vararg 是非空 `Any`）。
 *
 * ⚠ 参数**类型必须保持原样**：资源串里的 `%d` 要 `Int`，统一转成 `String`
 *   会崩在 `String.format`（**不是编译错**）。所以这里除了「替换 ResText」
 *   与「兜 null」之外**不做任何转换**。
 */
@androidx.compose.runtime.Composable
private fun resolveArgs(args: List<Any?>): List<Any> {
    val out = ArrayList<Any>(args.size)
    for (a in args) out.add(if (a is ResText) resText(a) else (a ?: ""))
    return out
}
