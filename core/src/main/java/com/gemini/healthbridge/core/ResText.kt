package com.gemini.healthbridge.core

/**
 * 一条**还没求值**的界面文案 —— 资源 id + 参数，可以是若干段的拼接。
 *
 * ## 为什么必须有这个东西（2026-10-02 用户截图实付）
 *
 * **「已求值的字符串」不能存进长期状态。**
 *
 * 手机端首页顶部有两行紧挨着的状态文字：`Watch not connected` 与
 * `蓝牙: 等待手表连接…`。用户切到英文后看到的却是「上一行英文、下一行中文」——
 * 因为它们是**两条完全不同的取值路径**：
 *
 * · 上一行是 `stringResource(R.string.s_46fde64a)`：在 Compose 里**现取**，跟随 locale；
 * · 下一行是 `PhoneBtServer._status` 这个 `MutableStateFlow<String>` 的**值** ——
 *   它在「蓝牙状态变化」那一刻就把文案求好了存进去，**之后没有任何时机重算**。
 *   于是切语言后它一直停在旧语言，直到下一次蓝牙状态变化。
 *
 * 根因一句话：`StateFlow` 里存的是**求值结果**，而求值只发生一次。
 * 这与 core 那条「派生量一律不落库」「容器里不能放求值表达式」是同一类错 ——
 * **不报错、不崩溃，只是悄悄显示上一门语言。**
 *
 * ## 纪律
 *
 * **状态里只记「要说什么」（key + 参数），求值推迟到渲染那一刻。**
 * 渲染发生在 Compose 重组里，而重组由语言变化驱动，所以切语言自然就对了。
 *
 * ## 用法
 *
 * ```kotlin
 * // 数据层：只记「要说什么」
 * private val _status = MutableStateFlow(ResText.of(R.string.s_23b174e0))
 * val status: StateFlow<ResText> = _status
 * _status.value = ResText.of(R.string.s_c60f3406, name)
 *
 * // 界面层：求值（两端各有一个 `resText(...)` Composable 扩展）
 * Text(resText(btStatus))
 * ```
 *
 * ⚠ **`Raw` 是逃生口，不是常规用法。** 它装的是已经算好的串，**切语言不会变**。
 * 只用于「本来就与语言无关」的内容：设备名、用户自己输入的回显、外部传入的消息。
 *
 * ⚠ 这个类型放在 `core` 是刻意的（app / wear **共用一份定义**）：它只用 `Int` 表达
 * 资源 id，不引入任何 Android 依赖，core 仍是纯 Kotlin 库。
 */
sealed class ResText {

    /**
     * 已经算好的原样文本。**与语言无关**的内容才用它。
     *
     * ⚠ 用它就意味着「放弃跟随语言」—— 每次都可能成为下一条截图。
     * 只有在内容确实不随语言变（设备名 / 用户输入）时才用。
     */
    data class Raw(val text: String) : ResText()

    /**
     * 一条资源串 + 参数。
     *
     * ⚠ 参数**类型要保持原样**，不要统一转成 `String`：资源串里的 `%d`/`%1$d`
     * 需要 `Int`，传 `String` 会在 `String.format` 处崩（而不是编译错）。
     *
     * ⚠ 参数**本身也可以是一条 [ResText]**（嵌套本地化片段，例如
     * `已记录经期：%s` 里的 `%s` 是一个按语言格式化的日期）。求值时会先把
     * 嵌套片段求值再喂给 `String.format`；直接塞一个**已求值的串**进来
     * 就等于放弃跟随语言，是这整套机制要根治的那个错。
     */
    data class Res(val id: Int, val args: List<Any?> = emptyList()) : ResText()

    /**
     * 一条 **core 文案表**里的 key + 参数（[Text.of] / [Text.fmt] 那一张表）。
     *
     * 与 [Res] 的分工：`app`/`wear` 里**能看见 `R` 的地方用 [Res]**；
     * 纯 Kotlin 层（数据层、core 自己的函数、手表端传感器管理器）看不见 `R`，
     * 只能用 [Core]。两张表的 key 是同一套 `s_%08x`，只是取值入口不同。
     *
     * ⚠ 用 [Text.of] 还是 [Text.fmt] 由 `args` 是否为空决定，所以**无插值**的条目
     * 也不要传空参数的变参（等价，不必纠结）。
     */
    data class Core(val key: String, val args: List<Any?> = emptyList()) : ResText()

    /**
     * 并列项分隔符。
     *
     * ⚠ 分隔符**本身也是随语言变的**（中文「、」/「，」，其余语言半角逗号 + 空格），
     * 所以它不能当成一个 `String` 在拼接时定死 —— 那会让「中文分隔符 + 英文条目」
     * 这种混搭出现在切换语言之后。凡是用分隔符连接**若干本地化片段**的地方，
     * 一律用 [join]，不要用 `joinToString`。
     *
     * @param list `true` = 句子之间用（[Language.listSeparator]）；
     *   `false` = 并列名词之间用（[Language.itemSeparator]）。
     */
    data class Sep(val list: Boolean = false) : ResText()

    /** 若干段拼接（顺序即输出顺序）。 */
    data class Cat(val parts: List<ResText>) : ResText()

    /** 拼接：`a + b`。左边已是 [Cat] 时直接接上，避免嵌套成树。 */
    operator fun plus(other: ResText): ResText = when (this) {
        is Cat -> Cat(parts + other)
        else -> Cat(listOf(this, other))
    }

    companion object {
        /** `ResText.of(R.string.s_x)` / `ResText.of(R.string.s_x, arg1, arg2)`。 */
        fun of(id: Int, vararg args: Any?): ResText = Res(id, args.toList())

        /** `ResText.core("s_x")` / `ResText.core("s_x", arg1, arg2)`（core 文案表）。 */
        fun core(key: String, vararg args: Any?): ResText = Core(key, args.toList())

        /** 已算好的串（**与语言无关**的内容才用，见 [Raw] 的说明）。 */
        fun raw(text: String): ResText = Raw(text)

        /** 并列名词分隔符（中文「、」/ 其余 ", "）。 */
        val ITEM_SEP: ResText = Sep(list = false)

        /** 句子分隔符（中文「，」/ 其余 ", "）。 */
        val LIST_SEP: ResText = Sep(list = true)

        val EMPTY: ResText = Raw("")

        /**
         * 用 [sep] 连接若干本地化片段。
         *
         * ⚠ 不要用 `joinToString` 替代：它会把分隔符**在拼接那一刻**定成一个 `String`，
         * 于是切语言后分隔符停在旧语言（而条目已经换成新语言）。
         * ⚠ 也不要在 lambda 里调 `resText` —— `joinToString` 的 lambda 不是 inline，
         * 里面调 `@Composable` 函数编译不过（本项目踩过）。
         */
        fun join(parts: List<ResText>, sep: ResText = ITEM_SEP): ResText {
            val out = ArrayList<ResText>(parts.size * 2)
            for (i in parts.indices) {
                if (i > 0) out.add(sep)
                out.add(parts[i])
            }
            return Cat(out)
        }
    }
}

/**
 * 结构上是不是「还没说话」的空文案。
 *
 * ⚠ 只有 [ResText.Raw] 可能为空 —— 状态里「尚无内容」的默认值就是 `ResText.EMPTY`
 * （即 `Raw("")`）。`Res`/`Core` 即使译文是空串也**不**算空：那说明译文有问题，
 * 应当让它照常显示，而不是静默被回落文案顶掉。
 *
 * ⚠ 它替代的是 `String.isEmpty()/isBlank()` 在**待求值文案**上的用法：
 * 那些地方（如 `st.statusText.ifEmpty { 回落文案 }`）不能用 `resText()` ——
 * 它们常常在 `onClick` 这类**非 Composable** 的 lambda 里。
 */
val ResText.isBlankText: Boolean get() = this is ResText.Raw && text.isBlank()

/**
 * 在**没有 Android 资源表**的场合求值（台架、`core` 内部要把待求值文案落成 `String` 时）。
 *
 * 两端 `LanguageStore.resText` 是它的 `@Composable` 兄弟 —— 那个版本多处理一条
 * [ResText.Res]（能拿到 `R`），其余分支语义**必须与本函数一致**。
 *
 * ⚠ 碰到 [ResText.Res] 会**抛异常**：`Res` 只存了资源 id，core 侧无从取值。
 *   这比返回空串好 —— 空串会静默地让一句话消失，而抛异常当场指出哪条文案走错了通道。
 *   ⚠ 也因此：core 自己**只能**产出 [ResText.Core]/[ResText.Sep]/[ResText.Raw]，
 *   凡是可能被 core 求值的 `ResText` 都不要塞 `Res` 进去。
 */
fun ResText.coreText(): String = when (this) {
    is ResText.Raw -> text
    is ResText.Core -> coreTextOf(key, coreArgs(args))
    is ResText.Sep -> if (list) Text.lang.listSeparator() else Text.lang.itemSeparator()
    is ResText.Cat -> {
        val sb = StringBuilder()
        for (p in parts) sb.append(p.coreText())
        sb.toString()
    }
    is ResText.Res -> error(
        "ResText.coreText: 这条文案含 Android 资源串（id=$id），core 侧看不见资源表；" +
            "请改由两端 LanguageStore.resText 求值"
    )
}

/**
 * [ResText.Core.args] 的参数表：嵌套的 [ResText] 先**按 core 侧求值**递归算出来，
 * 其余原样（只把 null 兜成 `""`，保证 `String.format` 拿到非空表）。
 */
private fun coreArgs(args: List<Any?>): List<Any> {
    val out = ArrayList<Any>(args.size)
    for (a in args) out.add(if (a is ResText) a.coreText() else (a ?: ""))
    return out
}

/**
 * 取一条 **core 文案表**里的文案并填参数。
 *
 * ⚠⚠ **两张表都要查**：绝大多数有插值的条目在**模板表**（`Text.fmt`），
 * 但历史迁移按「变量是否内联」分类，**无插值表里也留着少量整串 `%d` 的条目**
 * （例：`s_bd36935e` = `%d年%d月%d日`，调用侧写的是 `Text.of(key).format(...)`）。
 * 只查模板表会让这类 key 在**渲染那一刻**才抛 `error("未登记的模板 key")` ——
 * 而它平时不抛，只有真正被渲染到才炸。
 *
 * ⚠ 只用 `Locale.ROOT`：避免某些区域把数字换成非拉丁数字（项目统一纪律，
 * 与 [Text.fmt] 一致）。
 *
 * ⚠ 这是 [ResText.Core] 的**唯一**取值入口 —— 两端 `LanguageStore.resText`
 * 与 [coreText] 都必须走它，否则两处会漂移。
 */
fun coreTextOf(key: String, args: List<Any?> = emptyList()): String {
    val a = args.toTypedArray()
    return when {
        Text.TPL_KEYS.contains(key) -> Text.fmt(key, *a)
        args.isEmpty() -> Text.of(key)
        else -> java.lang.String.format(java.util.Locale.ROOT, Text.of(key), *a)
    }
}
