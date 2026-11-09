package com.gemini.healthbridge.core

/**
 * **core 层的界面文案常量表**（模块 #15 多语言，2026-09-30）。
 *
 * ## ⚠⚠ 本文件是**生成物**，不要手工编辑
 *
 * 由文案生成器从译文库生成。
 * 要改文案请改译文库再重新生成；直接改这里会在下次生成时被覆盖。
 *
 * ## 为什么 core 要自带一张表
 *
 * `core` 是**零 Android 依赖**的纯逻辑库 —— 没有 `Context`，用不了
 * `getString(R.string.x)`，而且它被 app 与 wear **两端共用**。
 * 所以 core 自己持有文案。这与 `Disclaimer.kt` 是同一个模式，本文件是它的**全量延伸**。
 *
 * ## ⚠⚠ 语言从哪来：读**当前语言** `Text.lang`，不是逐函数传参
 *
 * 2026-09-30 定的方案：core 内部读 `Text.lang`（两端在启动与切换语言时同步设置），
 * **core 的函数签名一律不加 `lang` 参数**，两端的调用点因此**零改动**。
 *
 * 另一条路（沿用 `Disclaimer.energy(l)` 那样逐函数传参）要改 core 上千处签名 +
 * 两端几百处调用点，工作量与风险都高一个量级，故不取。
 *
 * ⚠ 代价：core 里出现一处全局可变状态。当前两端都是**单进程、单用户**，
 * 且 UI 层本身有 `LanguageStore` 的 StateFlow 触发重组（切语言必然重组、重读文案），
 * 所以读到的总是当前语言。将来若要多语言并行（比如同时给两块屏渲染不同语言），
 * 再给那条链路单独加显式参数即可 —— 两套 API 可以并存。
 *
 * ## 两类条目的落地方式不同
 *
 * - **无插值条目** → 进 `TABLE`，`Text.of(key)` 直接取。
 * - **有插值条目**（如 `"响应过大（$declared 字节）"`）→ 进 `TPL`，值是**带 `%N$s` 的模板**，
 *   调用方 `Text.fmt(key, declared)`。
 *   ⚠ 序号**以 zhHans 为权威**：各语言译文的**语序可以不同**（韩语常把变量提前），
 *   若各语言各自按出现顺序编号，值就会张冠李戴，而且**不报错、静默**。
 *   这与 `gen_strings.py` 里是同一条纪律（那里踩过：英文里的字面 `%` 被当成
 *   说明符，`occurred` 的首字母被吃掉、还多出一个占位符 → `String.format` 抛异常**崩屏**）。
 *   ⚠ 模板**不能**拆成固定片段拼接 —— 那样会把语序锁死，韩语就没法把变量提前。
 *
 * ## 四条纪律
 *
 * ① `zhHans` 必须与改造前源文件里的原文**逐字相同** —— 它是基准，
 *    由译文校验回源逐字核验。
 *    ⚠ 有插值的那批存的是**模板**（`%1$s`），所以那一路的核验是
 *    「译文库的 zhHans 经同一套模板化后与这里逐字相同」（结构 + 文本都比）。
 * ② key 是**文案内容的短哈希**（`s_%08x`），不依赖行号 —— 行号一漂移 key 就变，译文全丢。
 *    所以**改了原文等于换了 key**，必须同步更新译文库（`--verify` 会报出来）。
 * ③ 医疗免责类文案**单独在 `Disclaimer.kt`**，不并进本表 ——
 *    那份表带着「逐条校对、不许软化」的纪律，混在一起会被稀释。
 *    ⚠ 本表里**也不该有**它们：core 源文件里的那些常量现在直接指向 `Disclaimer`，
 *    避免同一段免责出现两份译文（两份必然漂移）。
 * ④ 换行与引号都按 Kotlin 字面量转义；相邻字面量自动拼接，故长文案是折行写的。
 */
object Text {

    /**
     * **当前语言**（模块 #15）。两端在启动与切换语言时同步设置。
     *
     * ⚠ `@Volatile`：它在 UI 线程被写、可能被后台线程读（如数据层生成提示文案）。
     * 只保证可见性 —— 这里只有「读一个引用」的竞态，没有需要原子性的复合操作。
     *
     * ⚠ 默认 [Language.ZH_HANS] 是刻意的：没设置过时退回原文，
     * **不会**出现「文案变成看不懂的语言」或空串。
     */
    @Volatile
    var lang: Language = Language.ZH_HANS

    /** 全部**无插值**文案。key 是原文的短哈希，值是四语言译文。顺序按 key 排便于 diff。 */
    private val TABLE: Map<String, Map<Language, String>> = mapOf(
        "s_00004e2d" to mapOf(
        Language.ZH_HANS to
            "中",
        Language.ZH_HANT to
            "中",
        Language.EN to
            "Medium",
        Language.KO to
            "보통",
        ),
        "s_00004f4e" to mapOf(
        Language.ZH_HANS to
            "低",
        Language.ZH_HANT to
            "低",
        Language.EN to
            "Low",
        Language.KO to
            "낮음",
        ),
        "s_0000591a" to mapOf(
        Language.ZH_HANS to
            "多",
        Language.ZH_HANT to
            "多",
        Language.EN to
            "Heavy",
        Language.KO to
            "많음",
        ),
        "s_00005c11" to mapOf(
        Language.ZH_HANS to
            "少",
        Language.ZH_HANT to
            "少",
        Language.EN to
            "Light",
        Language.KO to
            "적음",
        ),
        "s_00009ad8" to mapOf(
        Language.ZH_HANS to
            "高",
        Language.ZH_HANT to
            "高",
        Language.EN to
            "High",
        Language.KO to
            "높음",
        ),
        "s_00285a3f" to mapOf(
        Language.ZH_HANS to
            "上次",
        Language.ZH_HANT to
            "上次",
        Language.EN to
            "Last time",
        Language.KO to
            "지난번",
        ),
        "s_00285fad" to mapOf(
        Language.ZH_HANS to
            "中度",
        Language.ZH_HANT to
            "中度",
        Language.EN to
            "Moderate",
        Language.KO to
            "중등도",
        ),
        "s_00286c2c" to mapOf(
        Language.ZH_HANS to
            "一般",
        Language.ZH_HANT to
            "一般",
        Language.EN to
            "Fair",
        Language.KO to
            "보통",
        ),
        "s_00288b39" to mapOf(
        Language.ZH_HANS to
            "举腿",
        Language.ZH_HANT to
            "舉腿",
        Language.EN to
            "Leg raises",
        Language.KO to
            "레그 레이즈",
        ),
        "s_0028aa87" to mapOf(
        Language.ZH_HANS to
            "今天",
        Language.ZH_HANT to
            "今天",
        Language.EN to
            "Today",
        Language.KO to
            "오늘",
        ),
        "s_0028b743" to mapOf(
        Language.ZH_HANS to
            "今日",
        Language.ZH_HANT to
            "今日",
        Language.EN to
            "Today",
        Language.KO to
            "오늘",
        ),
        "s_0028d622" to mapOf(
        Language.ZH_HANS to
            "休息",
        Language.ZH_HANT to
            "休息",
        Language.EN to
            "Rest",
        Language.KO to
            "휴식",
        ),
        "s_002903ad" to mapOf(
        Language.ZH_HANS to
            "体水",
        Language.ZH_HANT to
            "體水",
        Language.EN to
            "Body water",
        Language.KO to
            "체수분",
        ),
        "s_00292946" to mapOf(
        Language.ZH_HANS to
            "体重",
        Language.ZH_HANT to
            "體重",
        Language.EN to
            "Weight",
        Language.KO to
            "체중",
        ),
        "s_002967bb" to mapOf(
        Language.ZH_HANS to
            "偏低",
        Language.ZH_HANT to
            "偏低",
        Language.EN to
            "Low",
        Language.KO to
            "낮음",
        ),
        "s_0029b345" to mapOf(
        Language.ZH_HANS to
            "偏高",
        Language.ZH_HANT to
            "偏高",
        Language.EN to
            "High",
        Language.KO to
            "높음",
        ),
        "s_002a0ae5" to mapOf(
        Language.ZH_HANS to
            "其它",
        Language.ZH_HANT to
            "其它",
        Language.EN to
            "Other",
        Language.KO to
            "기타",
        ),
        "s_002a3078" to mapOf(
        Language.ZH_HANS to
            "全血",
        Language.ZH_HANT to
            "全血",
        Language.EN to
            "Whole blood",
        Language.KO to
            "전혈",
        ),
        "s_002a4113" to mapOf(
        Language.ZH_HANS to
            "冰球",
        Language.ZH_HANT to
            "冰球",
        Language.EN to
            "Ice hockey",
        Language.KO to
            "아이스하키",
        ),
        "s_002a5568" to mapOf(
        Language.ZH_HANS to
            "刚刚",
        Language.ZH_HANT to
            "剛剛",
        Language.EN to
            "Just now",
        Language.KO to
            "방금",
        ),
        "s_002a703c" to mapOf(
        Language.ZH_HANS to
            "初步",
        Language.ZH_HANT to
            "初步",
        Language.EN to
            "Preliminary",
        Language.KO to
            "예비",
        ),
        "s_002a816f" to mapOf(
        Language.ZH_HANS to
            "划船",
        Language.ZH_HANT to
            "划船",
        Language.EN to
            "Rowing",
        Language.KO to
            "조정",
        ),
        "s_002b115d" to mapOf(
        Language.ZH_HANS to
            "卧推",
        Language.ZH_HANT to
            "臥推",
        Language.EN to
            "Bench press",
        Language.KO to
            "벤치프레스",
        ),
        "s_002b36e8" to mapOf(
        Language.ZH_HANS to
            "午餐",
        Language.ZH_HANT to
            "午餐",
        Language.EN to
            "Lunch",
        Language.KO to
            "점심 식사",
        ),
        "s_002b375e" to mapOf(
        Language.ZH_HANS to
            "卷腹",
        Language.ZH_HANT to
            "捲腹",
        Language.EN to
            "Crunches",
        Language.KO to
            "크런치",
        ),
        "s_002d43b9" to mapOf(
        Language.ZH_HANS to
            "垒球",
        Language.ZH_HANT to
            "壘球",
        Language.EN to
            "Softball",
        Language.KO to
            "소프트볼",
        ),
        "s_002ddec6" to mapOf(
        Language.ZH_HANS to
            "壁球",
        Language.ZH_HANT to
            "壁球",
        Language.EN to
            "Squash",
        Language.KO to
            "스쿼시",
        ),
        "s_002e2bbe" to mapOf(
        Language.ZH_HANS to
            "如常",
        Language.ZH_HANT to
            "如常",
        Language.EN to
            "As usual",
        Language.KO to
            "평소와 같음",
        ),
        "s_002f7712" to mapOf(
        Language.ZH_HANS to
            "尊巴",
        Language.ZH_HANT to
            "尊巴",
        Language.EN to
            "Zumba",
        Language.KO to
            "줌바",
        ),
        "s_002f91b9" to mapOf(
        Language.ZH_HANS to
            "射箭",
        Language.ZH_HANT to
            "射箭",
        Language.EN to
            "Archery",
        Language.KO to
            "양궁",
        ),
        "s_00308491" to mapOf(
        Language.ZH_HANS to
            "帆板",
        Language.ZH_HANT to
            "帆板",
        Language.EN to
            "Windsurfing",
        Language.KO to
            "윈드서핑",
        ),
        "s_00309f4b" to mapOf(
        Language.ZH_HANS to
            "帆船",
        Language.ZH_HANT to
            "帆船",
        Language.EN to
            "Sailing",
        Language.KO to
            "요트",
        ),
        "s_0030c06c" to mapOf(
        Language.ZH_HANS to
            "常规",
        Language.ZH_HANT to
            "常規",
        Language.EN to
            "Regular",
        Language.KO to
            "일반",
        ),
        "s_0031034b" to mapOf(
        Language.ZH_HANS to
            "弯举",
        Language.ZH_HANT to
            "彎舉",
        Language.EN to
            "Biceps curls",
        Language.KO to
            "바이셉 컬",
        ),
        "s_0031531b" to mapOf(
        Language.ZH_HANS to
            "徒步",
        Language.ZH_HANT to
            "徒步",
        Language.EN to
            "Hiking",
        Language.KO to
            "하이킹",
        ),
        "s_00317450" to mapOf(
        Language.ZH_HANS to
            "心率",
        Language.ZH_HANT to
            "心率",
        Language.EN to
            "Heart Rate",
        Language.KO to
            "심박수",
        ),
        "s_00317d70" to mapOf(
        Language.ZH_HANS to
            "很高",
        Language.ZH_HANT to
            "很高",
        Language.EN to
            "Very high",
        Language.KO to
            "매우 높음",
        ),
        "s_0032c064" to mapOf(
        Language.ZH_HANS to
            "手球",
        Language.ZH_HANT to
            "手球",
        Language.EN to
            "Handball",
        Language.KO to
            "핸드볼",
        ),
        "s_0032dc13" to mapOf(
        Language.ZH_HANS to
            "拉伸",
        Language.ZH_HANT to
            "拉伸",
        Language.EN to
            "Stretching",
        Language.KO to
            "스트레칭",
        ),
        "s_0032f454" to mapOf(
        Language.ZH_HANS to
            "拳击",
        Language.ZH_HANT to
            "拳擊",
        Language.EN to
            "Boxing",
        Language.KO to
            "복싱",
        ),
        "s_003308ab" to mapOf(
        Language.ZH_HANS to
            "指尖",
        Language.ZH_HANT to
            "指尖",
        Language.EN to
            "Fingertip",
        Language.KO to
            "손끝",
        ),
        "s_00334d36" to mapOf(
        Language.ZH_HANS to
            "推举",
        Language.ZH_HANT to
            "推舉",
        Language.EN to
            "Overhead press",
        Language.KO to
            "프레스",
        ),
        "s_003367b9" to mapOf(
        Language.ZH_HANS to
            "排球",
        Language.ZH_HANT to
            "排球",
        Language.EN to
            "Volleyball",
        Language.KO to
            "배구",
        ),
        "s_0033739f" to mapOf(
        Language.ZH_HANS to
            "换项",
        Language.ZH_HANT to
            "換項",
        Language.EN to
            "Transition",
        Language.KO to
            "전환",
        ),
        "s_00340ba9" to mapOf(
        Language.ZH_HANS to
            "攀岩",
        Language.ZH_HANT to
            "攀岩",
        Language.EN to
            "Rock climbing",
        Language.KO to
            "암벽 등반",
        ),
        "s_00343638" to mapOf(
        Language.ZH_HANS to
            "放松",
        Language.ZH_HANT to
            "放鬆",
        Language.EN to
            "Cool-down",
        Language.KO to
            "정리 운동",
        ),
        "s_0034acd2" to mapOf(
        Language.ZH_HANS to
            "昨晚",
        Language.ZH_HANT to
            "昨晚",
        Language.EN to
            "Last night",
        Language.KO to
            "지난밤",
        ),
        "s_0034bf4b" to mapOf(
        Language.ZH_HANS to
            "早餐",
        Language.ZH_HANT to
            "早餐",
        Language.EN to
            "Breakfast",
        Language.KO to
            "아침 식사",
        ),
        "s_0034da66" to mapOf(
        Language.ZH_HANS to
            "暂无",
        Language.ZH_HANT to
            "暫無",
        Language.EN to
            "None",
        Language.KO to
            "없음",
        ),
        "s_0034f91e" to mapOf(
        Language.ZH_HANS to
            "晚餐",
        Language.ZH_HANT to
            "晚餐",
        Language.EN to
            "Dinner",
        Language.KO to
            "저녁 식사",
        ),
        "s_00354263" to mapOf(
        Language.ZH_HANS to
            "未知",
        Language.ZH_HANT to
            "未知",
        Language.EN to
            "Unknown",
        Language.KO to
            "알 수 없음",
        ),
        "s_00356a00" to mapOf(
        Language.ZH_HANS to
            "板球",
        Language.ZH_HANT to
            "板球",
        Language.EN to
            "Cricket",
        Language.KO to
            "크리켓",
        ),
        "s_00358d5b" to mapOf(
        Language.ZH_HANS to
            "标准≥5分",
        Language.ZH_HANT to
            "標準≥5分",
        Language.EN to
            "Standard ≥5min",
        Language.KO to
            "표준 ≥5분",
        ),
        "s_00361779" to mapOf(
        Language.ZH_HANS to
            "棒球",
        Language.ZH_HANT to
            "棒球",
        Language.EN to
            "Baseball",
        Language.KO to
            "야구",
        ),
        "s_003751e1" to mapOf(
        Language.ZH_HANS to
            "正常",
        Language.ZH_HANT to
            "正常",
        Language.EN to
            "Normal",
        Language.KO to
            "정상",
        ),
        "s_00375c61" to mapOf(
        Language.ZH_HANS to
            "武术",
        Language.ZH_HANT to
            "武術",
        Language.EN to
            "Martial arts",
        Language.KO to
            "무술",
        ),
        "s_00386ea6" to mapOf(
        Language.ZH_HANS to
            "浮潜",
        Language.ZH_HANT to
            "浮潛",
        Language.EN to
            "Snorkeling",
        Language.KO to
            "스노클링",
        ),
        "s_0038d0c5" to mapOf(
        Language.ZH_HANS to
            "深蹲",
        Language.ZH_HANT to
            "深蹲",
        Language.EN to
            "Squats",
        Language.KO to
            "스쿼트",
        ),
        "s_0038e8ef" to mapOf(
        Language.ZH_HANS to
            "游艇",
        Language.ZH_HANT to
            "遊艇",
        Language.EN to
            "Boating",
        Language.KO to
            "보트",
        ),
        "s_003906a3" to mapOf(
        Language.ZH_HANS to
            "滑冰",
        Language.ZH_HANT to
            "滑冰",
        Language.EN to
            "Ice skating",
        Language.KO to
            "아이스 스케이트",
        ),
        "s_00392127" to mapOf(
        Language.ZH_HANS to
            "滑水",
        Language.ZH_HANT to
            "滑水",
        Language.EN to
            "Water skiing",
        Language.KO to
            "수상스키",
        ),
        "s_00393b47" to mapOf(
        Language.ZH_HANS to
            "漂流",
        Language.ZH_HANT to
            "漂流",
        Language.EN to
            "Rafting",
        Language.KO to
            "래프팅",
        ),
        "s_00394bdd" to mapOf(
        Language.ZH_HANS to
            "滑雪",
        Language.ZH_HANT to
            "滑雪",
        Language.EN to
            "Skiing",
        Language.KO to
            "스키",
        ),
        "s_003a57f2" to mapOf(
        Language.ZH_HANS to
            "热身",
        Language.ZH_HANT to
            "熱身",
        Language.EN to
            "Warm-up",
        Language.KO to
            "준비 운동",
        ),
        "s_003bda51" to mapOf(
        Language.ZH_HANS to
            "瑜伽",
        Language.ZH_HANT to
            "瑜伽",
        Language.EN to
            "Yoga",
        Language.KO to
            "요가",
        ),
        "s_003d68f0" to mapOf(
        Language.ZH_HANS to
            "睡前",
        Language.ZH_HANT to
            "睡前",
        Language.EN to
            "Before bed",
        Language.KO to
            "취침 전",
        ),
        "s_003dc43d" to mapOf(
        Language.ZH_HANS to
            "短时1-5分",
        Language.ZH_HANT to
            "短時1-5分",
        Language.EN to
            "Short 1-5min",
        Language.KO to
            "단기 1-5분",
        ),
        "s_003e020d" to mapOf(
        Language.ZH_HANS to
            "硬拉",
        Language.ZH_HANT to
            "硬舉",
        Language.EN to
            "Deadlifts",
        Language.KO to
            "데드리프트",
        ),
        "s_003f2de7" to mapOf(
        Language.ZH_HANS to
            "空腹",
        Language.ZH_HANT to
            "空腹",
        Language.EN to
            "Fasting",
        Language.KO to
            "공복",
        ),
        "s_003fdecd" to mapOf(
        Language.ZH_HANS to
            "篮球",
        Language.ZH_HANT to
            "籃球",
        Language.EN to
            "Basketball",
        Language.KO to
            "농구",
        ),
        "s_00411e2f" to mapOf(
        Language.ZH_HANS to
            "红光",
        Language.ZH_HANT to
            "紅光",
        Language.EN to
            "Red light",
        Language.KO to
            "적색광",
        ),
        "s_004125fc" to mapOf(
        Language.ZH_HANS to
            "红外",
        Language.ZH_HANT to
            "紅外",
        Language.EN to
            "Infrared",
        Language.KO to
            "적외선",
        ),
        "s_00414b0c" to mapOf(
        Language.ZH_HANS to
            "经期",
        Language.ZH_HANT to
            "經期",
        Language.EN to
            "Menstruation",
        Language.KO to
            "월경",
        ),
        "s_00414dc6" to mapOf(
        Language.ZH_HANS to
            "绿光",
        Language.ZH_HANT to
            "綠光",
        Language.EN to
            "Green light",
        Language.KO to
            "녹색광",
        ),
        "s_00419a76" to mapOf(
        Language.ZH_HANS to
            "网球",
        Language.ZH_HANT to
            "網球",
        Language.EN to
            "Tennis",
        Language.KO to
            "테니스",
        ),
        "s_00425545" to mapOf(
        Language.ZH_HANS to
            "肥胖",
        Language.ZH_HANT to
            "肥胖",
        Language.EN to
            "Obese",
        Language.KO to
            "비만",
        ),
        "s_0042923b" to mapOf(
        Language.ZH_HANS to
            "腿举",
        Language.ZH_HANT to
            "腿舉",
        Language.EN to
            "Leg press",
        Language.KO to
            "레그 프레스",
        ),
        "s_0043184a" to mapOf(
        Language.ZH_HANS to
            "良好",
        Language.ZH_HANT to
            "良好",
        Language.EN to
            "Good",
        Language.KO to
            "양호",
        ),
        "s_00436405" to mapOf(
        Language.ZH_HANS to
            "芭蕾",
        Language.ZH_HANT to
            "芭蕾",
        Language.EN to
            "Ballet",
        Language.KO to
            "발레",
        ),
        "s_00460c4b" to mapOf(
        Language.ZH_HANS to
            "血压",
        Language.ZH_HANT to
            "血壓",
        Language.EN to
            "Blood Pressure",
        Language.KO to
            "혈압",
        ),
        "s_004624e7" to mapOf(
        Language.ZH_HANS to
            "血氧",
        Language.ZH_HANT to
            "血氧",
        Language.EN to
            "Blood Oxygen",
        Language.KO to
            "혈중 산소",
        ),
        "s_00462606" to mapOf(
        Language.ZH_HANS to
            "血浆",
        Language.ZH_HANT to
            "血漿",
        Language.EN to
            "Plasma",
        Language.KO to
            "혈장",
        ),
        "s_004626c5" to mapOf(
        Language.ZH_HANS to
            "血清",
        Language.ZH_HANT to
            "血清",
        Language.EN to
            "Serum",
        Language.KO to
            "혈청",
        ),
        "s_0046ded7" to mapOf(
        Language.ZH_HANS to
            "规律",
        Language.ZH_HANT to
            "規律",
        Language.EN to
            "Regular",
        Language.KO to
            "규칙적",
        ),
        "s_0048ee3f" to mapOf(
        Language.ZH_HANS to
            "走路",
        Language.ZH_HANT to
            "走路",
        Language.EN to
            "Walking",
        Language.KO to
            "걷기",
        ),
        "s_0048f69c" to mapOf(
        Language.ZH_HANS to
            "足球",
        Language.ZH_HANT to
            "足球",
        Language.EN to
            "Football",
        Language.KO to
            "축구",
        ),
        "s_0048fcdc" to mapOf(
        Language.ZH_HANS to
            "超重",
        Language.ZH_HANT to
            "超重",
        Language.EN to
            "Overweight",
        Language.KO to
            "과체중",
        ),
        "s_0048fd58" to mapOf(
        Language.ZH_HANS to
            "跑步",
        Language.ZH_HANT to
            "跑步",
        Language.EN to
            "Running",
        Language.KO to
            "달리기",
        ),
        "s_0049224c" to mapOf(
        Language.ZH_HANS to
            "跳绳",
        Language.ZH_HANT to
            "跳繩",
        Language.EN to
            "Jump rope",
        Language.KO to
            "줄넘기",
        ),
        "s_00492577" to mapOf(
        Language.ZH_HANS to
            "跳舞",
        Language.ZH_HANT to
            "跳舞",
        Language.EN to
            "Dancing",
        Language.KO to
            "춤추기",
        ),
        "s_00499c59" to mapOf(
        Language.ZH_HANS to
            "身高",
        Language.ZH_HANT to
            "身高",
        Language.EN to
            "Height",
        Language.KO to
            "신장",
        ),
        "s_0049ca97" to mapOf(
        Language.ZH_HANS to
            "轻度",
        Language.ZH_HANT to
            "輕度",
        Language.EN to
            "Mild",
        Language.KO to
            "경도",
        ),
        "s_0049ea18" to mapOf(
        Language.ZH_HANS to
            "运动",
        Language.ZH_HANT to
            "運動",
        Language.EN to
            "Exercise",
        Language.KO to
            "운동",
        ),
        "s_004afa8d" to mapOf(
        Language.ZH_HANS to
            "重度",
        Language.ZH_HANT to
            "重度",
        Language.EN to
            "Severe",
        Language.KO to
            "중증",
        ),
        "s_004b2568" to mapOf(
        Language.ZH_HANS to
            "重要",
        Language.ZH_HANT to
            "重要",
        Language.EN to
            "Important",
        Language.KO to
            "중요",
        ),
        "s_004d7267" to mapOf(
        Language.ZH_HANS to
            "随机",
        Language.ZH_HANT to
            "隨機",
        Language.EN to
            "Random",
        Language.KO to
            "무작위",
        ),
        "s_004dd2fa" to mapOf(
        Language.ZH_HANS to
            "静息",
        Language.ZH_HANT to
            "靜息",
        Language.EN to
            "Resting",
        Language.KO to
            "안정",
        ),
        "s_004df394" to mapOf(
        Language.ZH_HANS to
            "静脉",
        Language.ZH_HANT to
            "靜脈",
        Language.EN to
            "Venous",
        Language.KO to
            "정맥",
        ),
        "s_004ea57d" to mapOf(
        Language.ZH_HANS to
            "餐前",
        Language.ZH_HANT to
            "餐前",
        Language.EN to
            "Before meal",
        Language.KO to
            "식전",
        ),
        "s_004ea73e" to mapOf(
        Language.ZH_HANS to
            "餐后",
        Language.ZH_HANT to
            "餐後",
        Language.EN to
            "After meal",
        Language.KO to
            "식후",
        ),
        "s_004eb072" to mapOf(
        Language.ZH_HANS to
            "飞盘",
        Language.ZH_HANT to
            "飛盤",
        Language.EN to
            "Frisbee",
        Language.KO to
            "프리스비",
        ),
        "s_004fa07f" to mapOf(
        Language.ZH_HANS to
            "骑行",
        Language.ZH_HANT to
            "騎行",
        Language.EN to
            "Cycling",
        Language.KO to
            "사이클링",
        ),
        "s_004fb29f" to mapOf(
        Language.ZH_HANS to
            "骑马",
        Language.ZH_HANT to
            "騎馬",
        Language.EN to
            "Horseback riding",
        Language.KO to
            "승마",
        ),
        "s_0106b64c" to mapOf(
        Language.ZH_HANS to
            "前一日的活动都读不到。这个分数就是它们的加权结果，",
        Language.ZH_HANT to
            "前一日的活動都讀不到。這個分數就是它們的加權結果，",
        Language.EN to
            "and the previous day's activity could all not be read. This score is the weighted" +
            "result of those inputs, ",
        Language.KO to
            "그리고 전날의 활동을 모두 읽을 수 없습니다. 이 점수는 그 입력들의 가중 결과이므로 ",
        ),
        "s_01fd3d49" to mapOf(
        Language.ZH_HANS to
            "本机的「表体温度」不是室温（佩戴时被手腕与机身加热、摘下后降得很慢），",
        Language.ZH_HANT to
            "本機的「錶體溫度」不是室溫（佩戴時被手腕與機身加熱、摘下後降得很慢），",
        Language.EN to
            "This device's \"body temperature\" is not room temperature (when worn it is heated" +
            "by the wrist and the device body, and it cools very slowly after being taken off),",
        Language.KO to
            "이 기기의 '시계 본체 온도'는 실내 온도가 아닙니다(착용 시 손목과 기기 본체에 의해 가열되고, 벗은 뒤에는 천천히 식습니다). ",
        ),
        "s_02877b4a" to mapOf(
        Language.ZH_HANS to
            "结构性缺席：换测量姿势或时机都改变不了。",
        Language.ZH_HANT to
            "結構性缺席：換測量姿勢或時機都改變不了。",
        Language.EN to
            "structurally absent: changing the measuring posture or timing cannot fix it.",
        Language.KO to
            "구조적으로 불가능합니다: 측정 자세나 시점을 바꿔도 달라지지 않습니다.",
        ),
        "s_03801779" to mapOf(
        Language.ZH_HANS to
            "铁人三项",
        Language.ZH_HANT to
            "鐵人三項",
        Language.EN to
            "Triathlon",
        Language.KO to
            "트라이애슬론",
        ),
        "s_0380254a" to mapOf(
        Language.ZH_HANS to
            "铁人两项",
        Language.ZH_HANT to
            "鐵人兩項",
        Language.EN to
            "Duathlon",
        Language.KO to
            "듀애슬론",
        ),
        "s_03a4f643" to mapOf(
        Language.ZH_HANS to
            "心跳间隔变化超过 0.05 秒的心跳占比，越高说明变化越明显",
        Language.ZH_HANT to
            "心跳間隔變化超過 0.05 秒的心跳佔比，越高說明變化越明顯",
        Language.EN to
            "The share of heartbeats whose interval changes by more than 0.05 s; higher means" +
            "the variation is more pronounced",
        Language.KO to
            "심장 박동 간격 변화가 0.05초를 넘는 박동의 비율로, 높을수록 변동이 더 뚜렷함을 뜻합니다",
        ),
        "s_03f2666a" to mapOf(
        Language.ZH_HANS to
            "这一晚没有睡眠期心率统计（三星健康可能没开连续心率监测），",
        Language.ZH_HANT to
            "這一晚沒有睡眠期心率統計（三星健康可能沒開連續心率監測），",
        Language.EN to
            "There is no sleeping heart rate statistic for this night (continuous heart rate" +
            "monitoring may be off in Samsung Health), ",
        Language.KO to
            "이 날 밤에는 수면 심박수 통계가 없습니다(Samsung Health에서 연속 심박 모니터링이 꺼져 있을 수 있습니다). ",
        ),
        "s_04e694e6" to mapOf(
        Language.ZH_HANS to
            "每往后推一个周期误差就再累积一次，排三个月以后的日历只是把不确定画成了确定的格子",
        Language.ZH_HANT to
            "每往後推一個週期誤差就再累積一次，排三個月以後的日曆只是把不確定畫成了確定的格子",
        Language.EN to
            "each additional cycle accumulates the error again, and laying out a calendar three" +
            "months ahead merely draws uncertainty as definite boxes",
        Language.KO to
            "주기를 하나 더 밀 때마다 오차가 다시 누적되며, 석 달 뒤 달력을 그리는 것은 불확실성을 확정된 칸으로 그려내는 것에 불과합니다",
        ),
        "s_058cce79" to mapOf(
        Language.ZH_HANS to
            "常见原因：用了全角标点（，。：；）当分隔符，或末尾多了逗号，或托管的那份被写成数组，或返回的其实是网页",
        Language.ZH_HANT to
            "常見原因：用了全角標點（，。：；）當分隔符，或末尾多了逗號，或託管的那份被寫成陣列，或回傳的其實是網頁",
        Language.EN to
            "Common causes: a full-width punctuation mark used as a delimiter, a trailing" +
            "comma, the hosted JSON written as an array, or what is returned being a web page",
        Language.KO to
            "흔한 원인: 전각 문장부호를 구분자로 사용했거나, 끝에 쉼표가 남아 있거나, 호스팅된 JSON이 배열로 작성되었거나, 반환된 것이 실제로는 웹" +
            "페이지인 경우입니다",
        ),
        "s_05eb16ac" to mapOf(
        Language.ZH_HANS to
            "共享服务未连接，本次未采集光信号",
        Language.ZH_HANT to
            "共享服務未連線，本次未採集光訊號",
        Language.EN to
            "Shared service not connected; no optical signal captured this time",
        Language.KO to
            "공유 서비스가 연결되지 않아 이번에는 광신호를 수집하지 않았습니다",
        ),
        "s_07a3f009" to mapOf(
        Language.ZH_HANS to
            "摄入热量与饮水量只能由你自己在三星健康里记录（三星健康 → 饮食 / 水），",
        Language.ZH_HANT to
            "攝入熱量與飲水量只能由你自己在三星健康裡記錄（三星健康 → 飲食 / 水），",
        Language.EN to
            "Calories eaten and water intake can only be logged by you in Samsung Health" +
            "(Samsung Health → Food / Water), ",
        Language.KO to
            "섭취 칼로리와 물 섭취량은 본인이 Samsung Health에 직접 기록해야 합니다(Samsung Health → 음식 / 물). ",
        ),
        "s_0807a04c" to mapOf(
        Language.ZH_HANS to
            "静息心率 55 和 75 都可能是某个人的常态，只有跟他自己比才有意义。",
        Language.ZH_HANT to
            "靜息心率 55 和 75 都可能是某個人的常態，只有跟他自己比才有意義。",
        Language.EN to
            "a resting heart rate of 55 and 75 can both be normal for someone; it is only" +
            "meaningful in comparison with that person's own baseline.",
        Language.KO to
            "안정 심박수 55와 75 모두 어떤 사람에게는 정상일 수 있으며, 그 사람 자신의 기준선과 비교할 때만 의미가 있습니다.",
        ),
        "s_08fe60bf" to mapOf(
        Language.ZH_HANS to
            "皮肤温度",
        Language.ZH_HANT to
            "皮膚溫度",
        Language.EN to
            "Skin temperature",
        Language.KO to
            "피부 온도",
        ),
        "s_092fe929" to mapOf(
        Language.ZH_HANS to
            "或者你没在三星健康里开通过相关权限。",
        Language.ZH_HANT to
            "或者你沒在三星健康裡開通過相關權限。",
        Language.EN to
            "or you have not granted the relevant permissions in Samsung Health.",
        Language.KO to
            "Samsung Health에서 관련 권한을 허용하지 않았기 때문입니다.",
        ),
        "s_09c58a2b" to mapOf(
        Language.ZH_HANS to
            "而手表点检给的是单次绝对温度 —— 把绝对值填进基线字段等于替别人",
        Language.ZH_HANT to
            "而手錶點檢給的是單次絕對溫度 —— 把絕對值填進基線欄位等於替別人",
        Language.EN to
            "whereas the watch's spot check gives a single absolute temperature — putting an" +
            "absolute value into the baseline field is to ",
        Language.KO to
            "시계 점검은 단일 절대 온도를 제공합니다 — 절대값을 기준선 필드에 넣는 것은 ",
        ),
        "s_0a53a9a3" to mapOf(
        Language.ZH_HANS to
            "（事件通常持续 10~60 秒），但看不到呼吸本身 —— 呼吸是 0.2~0.4 Hz，",
        Language.ZH_HANT to
            "（事件通常持續 10~60 秒），但看不到呼吸本身 —— 呼吸是 0.2~0.4 Hz，",
        Language.EN to
            "(events usually last 10–60 seconds), but not enough to see respiration itself —" +
            "respiration is 0.2–0.4 Hz, ",
        Language.KO to
            "(이벤트는 보통 10~60초 지속), 호흡 자체를 보기에는 부족합니다 — 호흡은 0.2~0.4 Hz이며 ",
        ),
        "s_0ad3f3f9" to mapOf(
        Language.ZH_HANS to
            "直排轮滑",
        Language.ZH_HANT to
            "直排輪滑",
        Language.EN to
            "Inline skating",
        Language.KO to
            "인라인 스케이트",
        ),
        "s_0b0a6764" to mapOf(
        Language.ZH_HANS to
            "未检测到心电特征，请确认手指按住表冠",
        Language.ZH_HANT to
            "未檢測到心電特徵，請確認手指按住錶冠",
        Language.EN to
            "No ECG features detected; make sure your finger is pressing the crown",
        Language.KO to
            "심전도 특징이 감지되지 않았습니다. 손가락으로 크라운을 누르고 있는지 확인하세요",
        ),
        "s_0b2c9226" to mapOf(
        Language.ZH_HANS to
            "这类数据只能由产生它的那台设备写。",
        Language.ZH_HANT to
            "這類資料只能由產生它的那台裝置寫。",
        Language.EN to
            "this kind of data can only be written by the device that produced it.",
        Language.KO to
            "이런 데이터는 그것을 생성한 기기만 쓸 수 있습니다.",
        ),
        "s_0b5a9d34" to mapOf(
        Language.ZH_HANS to
            "需要至少每秒一次的连续采样，所以这里不给呼吸频率。",
        Language.ZH_HANT to
            "需要至少每秒一次的連續取樣，所以這裡不給呼吸頻率。",
        Language.EN to
            "which requires continuous sampling at least once per second, so no respiratory" +
            "rate is given here.",
        Language.KO to
            "최소 초당 1회의 연속 샘플링이 필요하므로 여기서는 호흡수를 제공하지 않습니다.",
        ),
        "s_0b5cd081" to mapOf(
        Language.ZH_HANS to
            "查看详情",
        Language.ZH_HANT to
            "查看詳情",
        Language.EN to
            "View details",
        Language.KO to
            "상세 보기",
        ),
        "s_0ba331e8" to mapOf(
        Language.ZH_HANS to
            "心率相关的三项基线取你最近 14 晚里有睡眠期心率的那几晚的中位数，",
        Language.ZH_HANT to
            "心率相關的三項基線取你最近 14 晚裡有睡眠期心率的那幾晚的中位數，",
        Language.EN to
            "The baselines for the three heart-rate-related items take the median of those" +
            "nights among your last 14 that have a sleeping heart rate, ",
        Language.KO to
            "심박 관련 세 항목의 기준선은 최근 14일 밤 중 수면 심박수가 있는 날들의 중앙값을 사용하며 ",
        ),
        "s_0bfd6c0a" to mapOf(
        Language.ZH_HANS to
            "「睡眠心率」与「睡眠心率变化」两项都未参与",
        Language.ZH_HANT to
            "「睡眠心率」與「睡眠心率變化」兩項都未參與",
        Language.EN to
            "\"Sleeping heart rate\" and \"Sleeping heart rate variability\" both did not" +
            "participate",
        Language.KO to
            "'수면 심박수'와 '수면 심박 변동' 두 항목 모두 참여하지 않았습니다",
        ),
        "s_0c43c180" to mapOf(
        Language.ZH_HANS to
            "常见静息范围",
        Language.ZH_HANT to
            "常見靜息範圍",
        Language.EN to
            "Usual resting range",
        Language.KO to
            "일반적인 안정 범위",
        ),
        "s_0d707fd1" to mapOf(
        Language.ZH_HANS to
            "三星账号，并且只在部分国家/地区提供 —— 拿不到不是你的机器有问题。",
        Language.ZH_HANT to
            "三星帳號，並且只在部分國家/地區提供 —— 拿不到不是你的機器有問題。",
        Language.EN to
            "a Samsung account, and it is only available in some countries/regions — not" +
            "getting it does not mean your device is faulty.",
        Language.KO to
            "삼성 계정이 필요하며 일부 국가/지역에서만 제공됩니다 — 받지 못한다고 해서 기기에 문제가 있는 것은 아닙니다.",
        ),
        "s_0d8a73ea" to mapOf(
        Language.ZH_HANS to
            "频域：0.04~0.40Hz 全带，LF/HF 有效",
        Language.ZH_HANT to
            "頻域：0.04~0.40Hz 全帶，LF/HF 有效",
        Language.EN to
            "Frequency domain: full band 0.04–0.40 Hz, LF/HF valid",
        Language.KO to
            "주파수 영역: 0.04~0.40Hz 전대역, LF/HF 유효",
        ),
        "s_0dafe869" to mapOf(
        Language.ZH_HANS to
            "静息心动过缓，若伴随头晕乏力请咨询医生",
        Language.ZH_HANT to
            "靜息心動過緩，若伴隨頭暈乏力請諮詢醫生",
        Language.EN to
            "Resting bradycardia; consult a doctor if accompanied by dizziness or fatigue",
        Language.KO to
            "안정 시 서맥입니다. 어지럼이나 무기력이 동반되면 의사와 상담하세요",
        ),
        "s_0ebf7268" to mapOf(
        Language.ZH_HANS to
            "手指碰到了金属边框",
        Language.ZH_HANT to
            "手指碰到了金屬邊框",
        Language.EN to
            "Finger touched the metal bezel",
        Language.KO to
            "손가락이 금속 베젤에 닿았습니다",
        ),
        "s_0ef2fb0b" to mapOf(
        Language.ZH_HANS to
            "① 表带偏紧佩戴并静置 10 分钟以上（刚戴上或正在动时，读数能差好几度）；",
        Language.ZH_HANT to
            "① 錶帶偏緊佩戴並靜置 10 分鐘以上（剛戴上或正在動時，讀數能差好幾度）；",
        Language.EN to
            "① wear the band somewhat snugly and stay still for 10+ minutes (right after" +
            "putting it on or while moving, the reading can differ by several degrees); ",
        Language.KO to
            "① 밴드를 다소 조이듯 착용하고 10분 이상 정지 상태를 유지하세요(막 착용했거나 움직이는 중에는 판독값이 몇 도까지 차이 날 수 있습니다). ",
        ),
        "s_0f089134" to mapOf(
        Language.ZH_HANS to
            "收缩压与舒张压都要填",
        Language.ZH_HANT to
            "收縮壓與舒張壓都要填",
        Language.EN to
            "Both systolic and diastolic pressure are required",
        Language.KO to
            "수축기압과 이완기압을 모두 입력해야 합니다",
        ),
        "s_0f402dc7" to mapOf(
        Language.ZH_HANS to
            "把中指压在手表的 2 点钟按键上，两根手指都要压住金属部分，全程别松开。",
        Language.ZH_HANT to
            "把中指壓在手錶的 2 點鐘按鍵上，兩根手指都要壓住金屬部分，全程別鬆開。",
        Language.EN to
            "Press your middle finger on the watch's 2 o'clock button; both fingers must press" +
            "the metal part and stay there the whole time.",
        Language.KO to
            "중지를 시계의 2시 버튼에 대세요. 두 손가락 모두 금속 부분을 눌러야 하며 처음부터 끝까지 떼지 마세요.",
        ),
        "s_0f44df15" to mapOf(
        Language.ZH_HANS to
            "负重徒步",
        Language.ZH_HANT to
            "負重徒步",
        Language.EN to
            "Rucking",
        Language.KO to
            "배낭 하이킹",
        ),
        "s_0f7492a6" to mapOf(
        Language.ZH_HANS to
            "十几毫秒 —— 与血压无关。所以它能反映「相对你自己平时的变化」，",
        Language.ZH_HANT to
            "十幾毫秒 —— 與血壓無關。所以它能反映「相對你自己平時的變化」，",
        Language.EN to
            "by more than ten milliseconds — unrelated to blood pressure. So it can reflect" +
            "\"the change relative to your own usual state\", ",
        Language.KO to
            "십수 밀리초씩 변합니다 — 혈압과는 무관합니다. 따라서 '본인의 평소 대비 변화'는 나타낼 수 있지만 ",
        ),
        "s_100f80e8" to mapOf(
        Language.ZH_HANS to
            "这通常意味着历史里有漏记或重复录入（例如同一段经期记了两次、或漏记了一次），",
        Language.ZH_HANT to
            "這通常意味著歷史裡有漏記或重複錄入（例如同一段經期記了兩次、或漏記了一次），",
        Language.EN to
            "This usually means there are missed or duplicate entries in the history (for" +
            "example, one period logged twice, or one missed), ",
        Language.KO to
            "이는 보통 기록에 누락이나 중복 입력이 있음을 뜻합니다(예: 같은 월경을 두 번 기록했거나 한 번 누락). ",
        ),
        "s_10445e65" to mapOf(
        Language.ZH_HANS to
            "核心体温",
        Language.ZH_HANT to
            "核心體溫",
        Language.EN to
            "Core body temperature",
        Language.KO to
            "심부 체온",
        ),
        "s_11e83ed2" to mapOf(
        Language.ZH_HANS to
            "预测（历史充足）",
        Language.ZH_HANT to
            "預測（歷史充足）",
        Language.EN to
            "Prediction (sufficient history)",
        Language.KO to
            "예측(기록 충분)",
        ),
        "s_1244c4e1" to mapOf(
        Language.ZH_HANS to
            "每有一个 4 小时时段走到 500 步以上加 20 分。",
        Language.ZH_HANT to
            "每有一個 4 小時時段走到 500 步以上加 20 分。",
        Language.EN to
            "each 4-hour slot in which you take 500+ steps adds 20 points.",
        Language.KO to
            "4시간 구간마다 500걸음 이상 걸으면 20점이 더해집니다.",
        ),
        "s_126417f4" to mapOf(
        Language.ZH_HANS to
            "可以与历史记录比较时留个心眼",
        Language.ZH_HANT to
            "可以與歷史記錄比較時留個心眼",
        Language.EN to
            "so keep this in mind when comparing with historical records",
        Language.KO to
            "기록과 비교할 때 이 점을 염두에 두세요",
        ),
        "s_13178dc3" to mapOf(
        Language.ZH_HANS to
            "收缩压与舒张压都要填正整数",
        Language.ZH_HANT to
            "收縮壓與舒張壓都要填正整數",
        Language.EN to
            "Systolic and diastolic pressure must both be positive integers",
        Language.KO to
            "수축기압과 이완기압은 모두 양의 정수로 입력해야 합니다",
        ),
        "s_13769b90" to mapOf(
        Language.ZH_HANS to
            "（缺一个可用的室温输入），换房间、换测量姿势都改变不了。",
        Language.ZH_HANT to
            "（缺一個可用的室溫輸入），換房間、換測量姿勢都改變不了。",
        Language.EN to
            "(a usable room-temperature input is missing), and changing rooms or measuring" +
            "postures cannot fix it.",
        Language.KO to
            "(사용할 수 있는 실내 온도 입력이 없음), 방을 바꾸거나 측정 자세를 바꿔도 달라지지 않습니다.",
        ),
        "s_1388a03b" to mapOf(
        Language.ZH_HANS to
            "三星那个由它自己的模型算出，口径与权重都不公开，还要求 Galaxy Watch7 以上 + ",
        Language.ZH_HANT to
            "三星那個由它自己的模型算出，口徑與權重都不公開，還要求 Galaxy Watch7 以上 + ",
        Language.EN to
            "Samsung's is computed by its own model, whose definition and weights are not" +
            "published; it also requires a Galaxy Watch7 or later plus ",
        Language.KO to
            "삼성의 것은 자체 모델로 계산되며 기준과 가중치가 공개되지 않습니다. 또한 Galaxy Watch7 이상과 ",
        ),
        "s_139b525d" to mapOf(
        Language.ZH_HANS to
            "繁體中文",
        Language.ZH_HANT to
            "繁體中文",
        Language.EN to
            "Traditional Chinese",
        Language.KO to
            "중국어 번체",
        ),
        "s_13bf0427" to mapOf(
        Language.ZH_HANS to
            "这四个频点（5/10/50/250 kHz）是三星传感器直接给出的原始值，不是我们估算的。",
        Language.ZH_HANT to
            "這四個頻點（5/10/50/250 kHz）是三星感測器直接給出的原始值，不是我們估算的。",
        Language.EN to
            "These four frequencies (5/10/50/250 kHz) are raw values given directly by the" +
            "Samsung sensor, not estimated by us.",
        Language.KO to
            "이 네 주파수(5/10/50/250 kHz)는 삼성 센서가 직접 제공하는 원시값이며 저희가 추정한 것이 아닙니다.",
        ),
        "s_14aabf5e" to mapOf(
        Language.ZH_HANS to
            "乒乓球",
        Language.ZH_HANT to
            "乒乓球",
        Language.EN to
            "Table tennis",
        Language.KO to
            "탁구",
        ),
        "s_14b7044c" to mapOf(
        Language.ZH_HANS to
            "不规律",
        Language.ZH_HANT to
            "不規律",
        Language.EN to
            "Irregular",
        Language.KO to
            "불규칙",
        ),
        "s_14dfdb00" to mapOf(
        Language.ZH_HANS to
            "交谊舞",
        Language.ZH_HANT to
            "交誼舞",
        Language.EN to
            "Ballroom dancing",
        Language.KO to
            "사교댄스",
        ),
        "s_14e60291" to mapOf(
        Language.ZH_HANS to
            "但 SDNN 统计的是整段的慢变趋势，窗口越长越大 —— 拿 30 秒的 SDNN 去比",
        Language.ZH_HANT to
            "但 SDNN 統計的是整段的慢變趨勢，視窗越長越大 —— 拿 30 秒的 SDNN 去比",
        Language.EN to
            "SDNN measures the slow-varying trend of the whole segment and grows with the" +
            "window — comparing a 30-second SDNN against ",
        Language.KO to
            "SDNN은 전체 구간의 완만한 변화 추세를 측정하며 구간이 길수록 커집니다 — 30초의 SDNN을 ",
        ),
        "s_1507fa78" to mapOf(
        Language.ZH_HANS to
            "体脂率",
        Language.ZH_HANT to
            "體脂率",
        Language.EN to
            "Body Fat Percentage",
        Language.KO to
            "체지방률",
        ),
        "s_150c24f6" to mapOf(
        Language.ZH_HANS to
            "侧平举",
        Language.ZH_HANT to
            "側平舉",
        Language.EN to
            "Lateral raises",
        Language.KO to
            "사이드 레터럴 레이즈",
        ),
        "s_1517b700" to mapOf(
        Language.ZH_HANS to
            "自研呼吸率是派生量、刻意不落库（改算法则全部历史口径一起改），",
        Language.ZH_HANT to
            "自研呼吸率是派生量、刻意不落庫（改演算法則全部歷史口徑一起改），",
        Language.EN to
            "The in-house respiratory rate is a derived quantity that is deliberately not" +
            "stored (changing the algorithm changes the definition for the whole history at" +
            "once), ",
        Language.KO to
            "자체 개발 호흡수는 파생량으로 의도적으로 저장하지 않습니다(알고리즘을 바꾸면 전체 이력의 기준이 함께 바뀝니다). ",
        ),
        "s_15196ead" to mapOf(
        Language.ZH_HANS to
            "俯卧撑",
        Language.ZH_HANT to
            "俯臥撐",
        Language.EN to
            "Push-ups",
        Language.KO to
            "푸시업",
        ),
        "s_1525ee2b" to mapOf(
        Language.ZH_HANS to
            "睡眠期间皮肤温度",
        Language.ZH_HANT to
            "睡眠期間皮膚溫度",
        Language.EN to
            "Skin temperature during sleep",
        Language.KO to
            "수면 중 피부 온도",
        ),
        "s_153bba54" to mapOf(
        Language.ZH_HANS to
            "保龄球",
        Language.ZH_HANT to
            "保齡球",
        Language.EN to
            "Bowling",
        Language.KO to
            "볼링",
        ),
        "s_154eed04" to mapOf(
        Language.ZH_HANS to
            "健美操",
        Language.ZH_HANT to
            "健美操",
        Language.EN to
            "Aerobics",
        Language.KO to
            "에어로빅",
        ),
        "s_15bdaecc" to mapOf(
        Language.ZH_HANS to
            "前平举",
        Language.ZH_HANT to
            "前平舉",
        Language.EN to
            "Front raises",
        Language.KO to
            "프론트 레이즈",
        ),
        "s_15c0a307" to mapOf(
        Language.ZH_HANS to
            "划船机",
        Language.ZH_HANT to
            "划船機",
        Language.EN to
            "Rowing machine",
        Language.KO to
            "로잉머신",
        ),
        "s_15d3ed9f" to mapOf(
        Language.ZH_HANS to
            "这段时间一个 IBI 都没有。可能是表带太松、手腕在动，",
        Language.ZH_HANT to
            "這段時間一個 IBI 都沒有。可能是錶帶太鬆、手腕在動，",
        Language.EN to
            "Not a single IBI was captured in this period. The band may be too loose or your" +
            "wrist was moving,",
        Language.KO to
            "이 기간에 IBI가 하나도 없습니다. 밴드가 너무 헐거웠거나 손목이 움직였을 수 있고,",
        ),
        "s_15f16ede" to mapOf(
        Language.ZH_HANS to
            "加餐后",
        Language.ZH_HANT to
            "加餐後",
        Language.EN to
            "After snack",
        Language.KO to
            "간식 후",
        ),
        "s_1612ac5f" to mapOf(
        Language.ZH_HANS to
            "卵泡期",
        Language.ZH_HANT to
            "濾泡期",
        Language.EN to
            "Follicular phase",
        Language.KO to
            "난포기",
        ),
        "s_161d6b05" to mapOf(
        Language.ZH_HANS to
            "午餐前",
        Language.ZH_HANT to
            "午餐前",
        Language.EN to
            "Before lunch",
        Language.KO to
            "점심 식사 전",
        ),
        "s_161d6cc6" to mapOf(
        Language.ZH_HANS to
            "午餐后",
        Language.ZH_HANT to
            "午餐後",
        Language.EN to
            "After lunch",
        Language.KO to
            "점심 식사 후",
        ),
        "s_16400509" to mapOf(
        Language.ZH_HANS to
            "取中位数也没有代表性，这次不报数。",
        Language.ZH_HANT to
            "取中位數也沒有代表性，這次不報數。",
        Language.EN to
            "Even the median would not be representative, so no value is reported this time.",
        Language.KO to
            "중앙값을 취해도 대표성이 없어 이번에는 값을 보고하지 않습니다.",
        ),
        "s_164af88b" to mapOf(
        Language.ZH_HANS to
            "呼吸率",
        Language.ZH_HANT to
            "呼吸率",
        Language.EN to
            "Respiratory rate",
        Language.KO to
            "호흡수",
        ),
        "s_164b7696" to mapOf(
        Language.ZH_HANS to
            "呼啦圈",
        Language.ZH_HANT to
            "呼啦圈",
        Language.EN to
            "Hula hoop",
        Language.KO to
            "훌라후프",
        ),
        "s_1684bc2b" to mapOf(
        Language.ZH_HANS to
            "BIA 给的体水是升，而 HC 的 BodyWaterMassRecord 要质量。",
        Language.ZH_HANT to
            "BIA 給的體水是升，而 HC 的 BodyWaterMassRecord 要質量。",
        Language.EN to
            "BIA gives body water in litres, whereas HC's BodyWaterMassRecord needs mass.",
        Language.KO to
            "BIA가 제공하는 체수분은 리터인데 HC의 BodyWaterMassRecord는 질량을 요구합니다.",
        ),
        "s_16e9e122" to mapOf(
        Language.ZH_HANS to
            "回力球",
        Language.ZH_HANT to
            "回力球",
        Language.EN to
            "Racquetball",
        Language.KO to
            "라켓볼",
        ),
        "s_170baf92" to mapOf(
        Language.ZH_HANS to
            "正在采集，请保持静止…",
        Language.ZH_HANT to
            "正在採集，請保持靜止…",
        Language.EN to
            "Collecting, please stay still…",
        Language.KO to
            "수집 중입니다. 움직이지 마세요…",
        ),
        "s_177b7417" to mapOf(
        Language.ZH_HANS to
            "HRV 没有通用的「正常范围」：它随年龄单调下降（20 岁与 70 岁能差几倍），",
        Language.ZH_HANT to
            "HRV 沒有通用的「正常範圍」：它隨年齡單調下降（20 歲與 70 歲能差幾倍），",
        Language.EN to
            "HRV has no universal \"normal range\": it declines monotonically with age (a" +
            "20-year-old and a 70-year-old can differ several-fold), ",
        Language.KO to
            "HRV에는 보편적인 '정상 범위'가 없습니다: 나이가 들수록 단조 감소하며(20세와 70세는 몇 배 차이 날 수 있습니다), ",
        ),
        "s_18c32df8" to mapOf(
        Language.ZH_HANS to
            "已停止",
        Language.ZH_HANT to
            "已停止",
        Language.EN to
            "Stopped",
        Language.KO to
            "중지됨",
        ),
        "s_18e5ec4c" to mapOf(
        Language.ZH_HANS to
            "并且这些记录必须覆盖 36 小时以上 —— HRV 有昼夜节律，同一个时段测出来的数只代表那个时段。",
        Language.ZH_HANT to
            "並且這些記錄必須涵蓋 36 小時以上 —— HRV 有晝夜節律，同一個時段測出來的數只代表那個時段。",
        Language.EN to
            "and these records must span more than 36 hours — HRV has a circadian rhythm, so" +
            "numbers measured in the same time window only represent that window.",
        Language.KO to
            "그리고 이 기록들은 36시간 이상을 포괄해야 합니다 — HRV에는 일주기 리듬이 있어 같은 시간대에 측정한 수치는 그 시간대만 나타냅니다.",
        ),
        "s_190be50b" to mapOf(
        Language.ZH_HANS to
            "开合跳",
        Language.ZH_HANT to
            "開合跳",
        Language.EN to
            "Jumping jacks",
        Language.KO to
            "팔벌려뛰기",
        ),
        "s_191cd3cc" to mapOf(
        Language.ZH_HANS to
            "弓步蹲",
        Language.ZH_HANT to
            "弓步蹲",
        Language.EN to
            "Lunges",
        Language.KO to
            "런지",
        ),
        "s_1946bb13" to mapOf(
        Language.ZH_HANS to
            "我们不会替你编一个目标 —— 在三星健康里设一个，这一项下次就能算",
        Language.ZH_HANT to
            "我們不會替你編一個目標 —— 在三星健康裡設一個，這一項下次就能算",
        Language.EN to
            "We will not invent a goal for you — set one in Samsung Health and this item can be" +
            "computed next time",
        Language.KO to
            "저희가 목표를 임의로 만들지는 않습니다 — Samsung Health에서 하나 설정하시면 다음번에 이 항목을 계산할 수 있습니다",
        ),
        "s_199a541d" to mapOf(
        Language.ZH_HANS to
            "这段光信号没有可用的采样率，无法换算时间",
        Language.ZH_HANT to
            "這段光訊號沒有可用的取樣率，無法換算時間",
        Language.EN to
            "This light signal has no usable sampling rate, so time cannot be converted",
        Language.KO to
            "이 광신호에는 사용할 수 있는 샘플링 레이트가 없어 시간으로 환산할 수 없습니다",
        ),
        "s_19c08973" to mapOf(
        Language.ZH_HANS to
            "（不是均值 —— 一次漏记或重复录入就能把均值带偏好几天）。只预测下一次：",
        Language.ZH_HANT to
            "（不是平均值 —— 一次漏記或重複錄入就能把平均值帶偏好幾天）。只預測下一次：",
        Language.EN to
            "(not the mean — one missed or duplicate entry can skew the mean by several days)." +
            "Only the next one is predicted: ",
        Language.KO to
            "(평균값이 아닙니다 — 한 번의 누락이나 중복 입력만으로도 평균이 며칠씩 치우칠 수 있습니다). 다음 한 번만 예측합니다: ",
        ),
        "s_19d91ee5" to mapOf(
        Language.ZH_HANS to
            "基线记录里的 RMSSD 或心率无效，无法构造基线",
        Language.ZH_HANT to
            "基線記錄裡的 RMSSD 或心率無效，無法建構基線",
        Language.EN to
            "The RMSSD or heart rate in the baseline records is invalid, so a baseline cannot" +
            "be constructed",
        Language.KO to
            "기준선 기록의 RMSSD 또는 심박수가 유효하지 않아 기준선을 만들 수 없습니다",
        ),
        "s_1a3dd220" to mapOf(
        Language.ZH_HANS to
            "排卵期",
        Language.ZH_HANT to
            "排卵期",
        Language.EN to
            "Ovulation",
        Language.KO to
            "배란기",
        ),
        "s_1aa2fc0e" to mapOf(
        Language.ZH_HANS to
            "但绝对刻度的那些（睡眠时长/血氧/皮温/定时/活动）照样算得出来",
        Language.ZH_HANT to
            "但絕對刻度的那些（睡眠時長/血氧/皮溫/定時/活動）照樣算得出來",
        Language.EN to
            "but the absolute-scale ones (sleep duration / blood oxygen / skin temperature /" +
            "timing / activity) can still be computed",
        Language.KO to
            "다만 절대 척도를 쓰는 항목(수면 시간/혈중 산소/피부 온도/시각/활동)은 그대로 계산됩니다",
        ),
        "s_1ae1809e" to mapOf(
        Language.ZH_HANS to
            "无数据",
        Language.ZH_HANT to
            "無資料",
        Language.EN to
            "No data",
        Language.KO to
            "데이터 없음",
        ),
        "s_1af50f45" to mapOf(
        Language.ZH_HANS to
            "无记录",
        Language.ZH_HANT to
            "無記錄",
        Language.EN to
            "No records",
        Language.KO to
            "기록 없음",
        ),
        "s_1afe35ae" to mapOf(
        Language.ZH_HANS to
            "早餐前",
        Language.ZH_HANT to
            "早餐前",
        Language.EN to
            "Before breakfast",
        Language.KO to
            "아침 식사 전",
        ),
        "s_1afe376f" to mapOf(
        Language.ZH_HANS to
            "早餐后",
        Language.ZH_HANT to
            "早餐後",
        Language.EN to
            "After breakfast",
        Language.KO to
            "아침 식사 후",
        ),
        "s_1b055489" to mapOf(
        Language.ZH_HANS to
            "普拉提",
        Language.ZH_HANT to
            "普拉提",
        Language.EN to
            "Pilates",
        Language.KO to
            "필라테스",
        ),
        "s_1b1bcca7" to mapOf(
        Language.ZH_HANS to
            "晚餐前",
        Language.ZH_HANT to
            "晚餐前",
        Language.EN to
            "Before dinner",
        Language.KO to
            "저녁 식사 전",
        ),
        "s_1b1bce68" to mapOf(
        Language.ZH_HANS to
            "晚餐后",
        Language.ZH_HANT to
            "晚餐後",
        Language.EN to
            "After dinner",
        Language.KO to
            "저녁 식사 후",
        ),
        "s_1b2b096c" to mapOf(
        Language.ZH_HANS to
            "曲棍球",
        Language.ZH_HANT to
            "曲棍球",
        Language.EN to
            "Field hockey",
        Language.KO to
            "필드 하키",
        ),
        "s_1b2e0280" to mapOf(
        Language.ZH_HANS to
            "未判定",
        Language.ZH_HANT to
            "未判定",
        Language.EN to
            "Not determined",
        Language.KO to
            "미판정",
        ),
        "s_1b348fa9" to mapOf(
        Language.ZH_HANS to
            "未开启",
        Language.ZH_HANT to
            "未開啟",
        Language.EN to
            "Off",
        Language.KO to
            "꺼짐",
        ),
        "s_1b36a6a9" to mapOf(
        Language.ZH_HANS to
            "未指定",
        Language.ZH_HANT to
            "未指定",
        Language.EN to
            "Unspecified",
        Language.KO to
            "지정되지 않음",
        ),
        "s_1b3946f7" to mapOf(
        Language.ZH_HANS to
            "未标注",
        Language.ZH_HANT to
            "未標註",
        Language.EN to
            "Unlabeled",
        Language.KO to
            "미표시",
        ),
        "s_1b3c1daa" to mapOf(
        Language.ZH_HANS to
            "未测量",
        Language.ZH_HANT to
            "未測量",
        Language.EN to
            "Not measured",
        Language.KO to
            "측정 안 함",
        ),
        "s_1b42d4b6" to mapOf(
        Language.ZH_HANS to
            "有记录",
        Language.ZH_HANT to
            "有記錄",
        Language.EN to
            "Has records",
        Language.KO to
            "기록 있음",
        ),
        "s_1b4b78df" to mapOf(
        Language.ZH_HANS to
            "未记录",
        Language.ZH_HANT to
            "未記錄",
        Language.EN to
            "Not recorded",
        Language.KO to
            "기록 없음",
        ),
        "s_1bb768e1" to mapOf(
        Language.ZH_HANS to
            "椭圆机",
        Language.ZH_HANT to
            "橢圓機",
        Language.EN to
            "Elliptical",
        Language.KO to
            "일립티컬",
        ),
        "s_1c09faf3" to mapOf(
        Language.ZH_HANS to
            "橄榄球",
        Language.ZH_HANT to
            "橄欖球",
        Language.EN to
            "Rugby",
        Language.KO to
            "럭비",
        ),
        "s_1c7effca" to mapOf(
        Language.ZH_HANS to
            "整段记录里心跳间隔的整体波动幅度，记录越长这个数越大",
        Language.ZH_HANT to
            "整段記錄裡心跳間隔的整體波動幅度，記錄越長這個數越大",
        Language.EN to
            "The overall spread of heartbeat intervals across the whole record; the longer the" +
            "record, the larger this number",
        Language.KO to
            "전체 기록에서 심장 박동 간격의 전반적 변동 폭으로, 기록이 길수록 이 수치가 커집니다",
        ),
        "s_1c8de7dc" to mapOf(
        Language.ZH_HANS to
            "心率受体位与咖啡因影响，皮温受环境与佩戴影响最大。这里的 60 分不等于任何权威量表的 60 分。",
        Language.ZH_HANT to
            "心率受體位與咖啡因影響，皮溫受環境與佩戴影響最大。這裡的 60 分不等於任何權威量表的 60 分。",
        Language.EN to
            "heart rate is affected by posture and caffeine, and skin temperature is most" +
            "affected by environment and wear. A score of 60 here is not equal to 60 on any" +
            "authoritative scale.",
        Language.KO to
            "심박수는 자세와 카페인의 영향을, 피부 온도는 환경과 착용 상태의 영향을 가장 크게 받습니다. 여기의 60점은 어떤 권위 있는 척도의 60점과도" +
            "같지 않습니다.",
        ),
        "s_1cbab561" to mapOf(
        Language.ZH_HANS to
            "波比跳",
        Language.ZH_HANT to
            "波比跳",
        Language.EN to
            "Burpees",
        Language.KO to
            "버피",
        ),
        "s_1cc4e238" to mapOf(
        Language.ZH_HANS to
            "活动中",
        Language.ZH_HANT to
            "活動中",
        Language.EN to
            "Active",
        Language.KO to
            "활동 중",
        ),
        "s_1cc68da0" to mapOf(
        Language.ZH_HANS to
            "如果公告内容有更新，下次打开会立刻提醒。",
        Language.ZH_HANT to
            "如果公告內容有更新，下次開啟會立刻提醒。",
        Language.EN to
            "If the announcement content is updated, you will be reminded immediately the next" +
            "time you open the app.",
        Language.KO to
            "공지 내용이 업데이트되면 다음에 앱을 열 때 즉시 알림됩니다.",
        ),
        "s_1cd3775d" to mapOf(
        Language.ZH_HANS to
            "若周期长期短于 21 天、长于 35 天，或突然明显改变，请咨询医生",
        Language.ZH_HANT to
            "若週期長期短於 21 天、長於 35 天，或突然明顯改變，請諮詢醫生",
        Language.EN to
            "if cycles are persistently shorter than 21 days, longer than 35 days, or change" +
            "suddenly and markedly, consult a doctor",
        Language.KO to
            "주기가 장기간 21일 미만이거나 35일을 넘거나 갑자기 뚜렷하게 변하면 의사와 상담하세요",
        ),
        "s_1cd5b7f7" to mapOf(
        Language.ZH_HANS to
            "「经期开始日」，或让手机从 Health Connect 读入已有的经期记录",
        Language.ZH_HANT to
            "「經期開始日」，或讓手機從 Health Connect 讀入已有的經期記錄",
        Language.EN to
            "as a \"period start date\", or let the phone read existing period records from" +
            "Health Connect",
        Language.KO to
            "'월경 시작일'로 기록하거나, 휴대폰이 Health Connect에서 기존 월경 기록을 읽어오도록 하세요",
        ),
        "s_1cfc6dcd" to mapOf(
        Language.ZH_HANS to
            "脉搏波那一路这次没测准，时延也跟着不可信。",
        Language.ZH_HANT to
            "脈搏波那一路這次沒測準，時延也跟著不可信。",
        Language.EN to
            "The pulse-wave signal was not measured accurately this time, so the time delay is" +
            "also unreliable.",
        Language.KO to
            "맥파 갈래가 이번에 정확히 측정되지 않아 시간 지연도 신뢰할 수 없습니다.",
        ),
        "s_1d2ef35c" to mapOf(
        Language.ZH_HANS to
            "滑冰跳",
        Language.ZH_HANT to
            "滑冰跳",
        Language.EN to
            "Skater jumps",
        Language.KO to
            "스케이터 점프",
        ),
        "s_1d39145c" to mapOf(
        Language.ZH_HANS to
            "滑旱冰",
        Language.ZH_HANT to
            "滑旱冰",
        Language.EN to
            "Roller skating",
        Language.KO to
            "롤러 스케이트",
        ),
        "s_1d4650f3" to mapOf(
        Language.ZH_HANS to
            "滑翔伞",
        Language.ZH_HANT to
            "滑翔傘",
        Language.EN to
            "Paragliding",
        Language.KO to
            "패러글라이딩",
        ),
        "s_1d72fec0" to mapOf(
        Language.ZH_HANS to
            "易孕窗口由「下次经期 − 14 天」反推排卵日，再取排卵前 5 天到后 1 天算出来。",
        Language.ZH_HANT to
            "易孕窗口由「下次經期 − 14 天」反推排卵日，再取排卵前 5 天到後 1 天算出來。",
        Language.EN to
            "The fertile window is derived by back-calculating the ovulation day as \"next" +
            "period − 14 days\", then taking from 5 days before to 1 day after ovulation.",
        Language.KO to
            "가임기는 '다음 월경 − 14일'로 배란일을 역산한 뒤 배란 5일 전부터 1일 후까지로 산출합니다.",
        ),
        "s_1d9234c9" to mapOf(
        Language.ZH_HANS to
            "划独木舟",
        Language.ZH_HANT to
            "划獨木舟",
        Language.EN to
            "Canoeing",
        Language.KO to
            "카누",
        ),
        "s_1da38aa9" to mapOf(
        Language.ZH_HANS to
            "越野滑雪",
        Language.ZH_HANT to
            "越野滑雪",
        Language.EN to
            "Cross-country skiing",
        Language.KO to
            "크로스컨트리 스키",
        ),
        "s_1dcb8d91" to mapOf(
        Language.ZH_HANS to
            "腕部皮肤温度无效（拿不到或超出物理区间），无法估算",
        Language.ZH_HANT to
            "腕部皮膚溫度無效（拿不到或超出物理區間），無法估算",
        Language.EN to
            "Wrist skin temperature is invalid (unavailable or outside the physical range), so" +
            "it cannot be estimated",
        Language.KO to
            "손목 피부 온도가 유효하지 않아(얻을 수 없거나 물리적 범위를 벗어남) 추정할 수 없습니다",
        ),
        "s_1df0d975" to mapOf(
        Language.ZH_HANS to
            "这段逐拍间期里没有落在呼吸频段的周期成分（相关度过低）",
        Language.ZH_HANT to
            "這段逐拍間期裡沒有落在呼吸頻段的週期成分（相關度過低）",
        Language.EN to
            "There is no periodic component in the respiratory band in this beat-to-beat" +
            "interval (correlation too low)",
        Language.KO to
            "이 박동 간 간격에는 호흡 대역에 해당하는 주기 성분이 없습니다(상관도가 너무 낮음)",
        ),
        "s_1e1be93a" to mapOf(
        Language.ZH_HANS to
            "爬楼机",
        Language.ZH_HANT to
            "爬樓機",
        Language.EN to
            "Stair mill",
        Language.KO to
            "스테어 클라이머",
        ),
        "s_1e1beaaf" to mapOf(
        Language.ZH_HANS to
            "爬楼梯",
        Language.ZH_HANT to
            "爬樓梯",
        Language.EN to
            "Stair climbing",
        Language.KO to
            "계단 오르기",
        ),
        "s_1e734a9f" to mapOf(
        Language.ZH_HANS to
            "睡眠定时",
        Language.ZH_HANT to
            "睡眠定時",
        Language.EN to
            "Sleep timing",
        Language.KO to
            "수면 시각",
        ),
        "s_1e75792b" to mapOf(
        Language.ZH_HANS to
            "睡眠心率",
        Language.ZH_HANT to
            "睡眠心率",
        Language.EN to
            "Sleep heart rate",
        Language.KO to
            "수면 심박수",
        ),
        "s_1e763f3e" to mapOf(
        Language.ZH_HANS to
            "本机没有这项数据。它由「三星健康监测器」产生（要在那个应用里开启检测并授权同步），",
        Language.ZH_HANT to
            "本機沒有這項資料。它由「三星健康監測器」產生（要在那個應用程式裡開啟偵測並授權同步），",
        Language.EN to
            "This data is not available on this device. It is produced by Samsung Health" +
            "Monitor (you must enable detection in that app and grant sync permission), ",
        Language.KO to
            "이 기기에는 이 데이터가 없습니다. 이는 Samsung Health Monitor에서 생성됩니다(해당 앱에서 감지를 켜고 동기화를 허용해야 합니다). ",
        ),
        "s_1ee11d0d" to mapOf(
        Language.ZH_HANS to
            "田径跑",
        Language.ZH_HANT to
            "田徑跑",
        Language.EN to
            "Track running",
        Language.KO to
            "트랙 달리기",
        ),
        "s_1f3478b3" to mapOf(
        Language.ZH_HANS to
            "4 点钟方向的手指没放上",
        Language.ZH_HANT to
            "4 點鐘方向的手指沒放上",
        Language.EN to
            "The finger at the 4 o'clock position is not placed",
        Language.KO to
            "4시 방향 손가락이 올려지지 않았습니다",
        ),
        "s_1f3636f7" to mapOf(
        Language.ZH_HANS to
            "登山跑",
        Language.ZH_HANT to
            "登山跑",
        Language.EN to
            "Mountain climbers",
        Language.KO to
            "마운틴 클라이머",
        ),
        "s_1f3e379b" to mapOf(
        Language.ZH_HANS to
            "皮划艇",
        Language.ZH_HANT to
            "皮划艇",
        Language.EN to
            "Kayaking",
        Language.KO to
            "카약",
        ),
        "s_1fa6fc3a" to mapOf(
        Language.ZH_HANS to
            "心率变化 8% / 皮肤温度 8% / 时间一致性 8% / 规律性 8% / 定时 8% / 活动持续性 8%）",
        Language.ZH_HANT to
            "心率變化 8% / 皮膚溫度 8% / 時間一致性 8% / 規律性 8% / 定時 8% / 活動持續性 8%）",
        Language.EN to
            "heart-rate variability 8% / skin temperature 8% / time consistency 8% / regularity" +
            "8% / timing 8% / activity spread 8%)",
        Language.KO to
            "심박 변동 8% / 피부 온도 8% / 시간 일관성 8% / 규칙성 8% / 시각 8% / 활동 지속성 8%)",
        ),
        "s_20094b3e" to mapOf(
        Language.ZH_HANS to
            "测量未完成",
        Language.ZH_HANT to
            "測量未完成",
        Language.EN to
            "Measurement not completed",
        Language.KO to
            "측정 미완료",
        ),
        "s_20567069" to mapOf(
        Language.ZH_HANS to
            "步数 / 距离 / 热量 / 爬楼 / 运动 / 饮食 / 饮水 / 血糖 / 睡眠",
        Language.ZH_HANT to
            "步數 / 距離 / 熱量 / 爬樓 / 運動 / 飲食 / 飲水 / 血糖 / 睡眠",
        Language.EN to
            "Steps / distance / calories / floors / exercise / food / water / blood glucose /" +
            "sleep",
        Language.KO to
            "걸음 수 / 거리 / 칼로리 / 층수 / 운동 / 음식 / 물 / 혈당 / 수면",
        ),
        "s_21aa99b9" to mapOf(
        Language.ZH_HANS to
            "羽毛球",
        Language.ZH_HANT to
            "羽毛球",
        Language.EN to
            "Badminton",
        Language.KO to
            "배드민턴",
        ),
        "s_21e2d129" to mapOf(
        Language.ZH_HANS to
            "背伸展",
        Language.ZH_HANT to
            "背伸展",
        Language.EN to
            "Back extensions",
        Language.KO to
            "백 익스텐션",
        ),
        "s_22185287" to mapOf(
        Language.ZH_HANS to
            "腿屈伸",
        Language.ZH_HANT to
            "腿屈伸",
        Language.EN to
            "Leg extensions",
        Language.KO to
            "레그 익스텐션",
        ),
        "s_2219cdc2" to mapOf(
        Language.ZH_HANS to
            "腿弯举",
        Language.ZH_HANT to
            "腿彎舉",
        Language.EN to
            "Leg curls",
        Language.KO to
            "레그 컬",
        ),
        "s_2229dde2" to mapOf(
        Language.ZH_HANS to
            "臂屈伸",
        Language.ZH_HANT to
            "臂屈伸",
        Language.EN to
            "Triceps extensions",
        Language.KO to
            "트라이셉 익스텐션",
        ),
        "s_2261972f" to mapOf(
        Language.ZH_HANS to
            "平均周期长度至少要两次开始日才能算",
        Language.ZH_HANT to
            "平均週期長度至少要兩次開始日才能算",
        Language.EN to
            "the average cycle length needs at least two start dates to be computed",
        Language.KO to
            "평균 주기 길이는 최소 두 번의 시작일이 있어야 계산할 수 있습니다",
        ),
        "s_22d706ec" to mapOf(
        Language.ZH_HANS to
            "预测按平均周期长度外推，只对下一次有意义",
        Language.ZH_HANT to
            "預測按平均週期長度外推，只對下一次有意義",
        Language.EN to
            "The prediction extrapolates by average cycle length and is only meaningful for the" +
            "next one",
        Language.KO to
            "예측은 평균 주기 길이로 외삽하며 다음 한 번에만 의미가 있습니다",
        ),
        "s_23dd6bc3" to mapOf(
        Language.ZH_HANS to
            "三星健康里没有数据时这里就是空的 —— 通常是因为手机/手表没带在身上，",
        Language.ZH_HANT to
            "三星健康裡沒有資料時這裡就是空的 —— 通常是因為手機/手錶沒帶在身上，",
        Language.EN to
            "When Samsung Health has no data this section is empty — usually because the" +
            "phone/watch was not carried, ",
        Language.KO to
            "Samsung Health에 데이터가 없으면 이 부분은 비어 있습니다 — 보통 휴대폰/시계를 소지하지 않았거나 ",
        ),
        "s_24cfdfaa" to mapOf(
        Language.ZH_HANS to
            "读不到",
        Language.ZH_HANT to
            "讀不到",
        Language.EN to
            "Unavailable",
        Language.KO to
            "읽을 수 없음",
        ),
        "s_2547b572" to mapOf(
        Language.ZH_HANS to
            "公开水域游泳",
        Language.ZH_HANT to
            "公開水域游泳",
        Language.EN to
            "Open-water swimming",
        Language.KO to
            "오픈워터 수영",
        ),
        "s_254c8cea" to mapOf(
        Language.ZH_HANS to
            "超短时<1分",
        Language.ZH_HANT to
            "超短時<1分",
        Language.EN to
            "Ultra-short <1min",
        Language.KO to
            "초단기 <1분",
        ),
        "s_254ca7f8" to mapOf(
        Language.ZH_HANS to
            "M月d日",
        Language.ZH_HANT to
            "M月d日",
        Language.EN to
            "MMM d",
        Language.KO to
            "M월 d일",
        ),
        "s_255a0b42" to mapOf(
        Language.ZH_HANS to
            "跑步机",
        Language.ZH_HANT to
            "跑步機",
        Language.EN to
            "Treadmill",
        Language.KO to
            "러닝머신",
        ),
        "s_2566f88a" to mapOf(
        Language.ZH_HANS to
            "三星健康开放的是「每分钟一个点」的血氧序列。这个粒度足以看见一次呼吸事件造成的血氧下降",
        Language.ZH_HANT to
            "三星健康開放的是「每分鐘一個點」的血氧序列。這個粒度足以看見一次呼吸事件造成的血氧下降",
        Language.EN to
            "What Samsung Health exposes is a blood-oxygen series of \"one point per minute\"." +
            "This granularity is enough to see the blood-oxygen drop caused by a respiratory" +
            "event ",
        Language.KO to
            "Samsung Health가 제공하는 것은 '1분에 한 점'의 혈중 산소 시계열입니다. 이 세분성은 호흡 이벤트로 인한 혈중 산소 저하를 보기에" +
            "충분하지만 ",
        ),
        "s_256a4770" to mapOf(
        Language.ZH_HANS to
            "踏步机",
        Language.ZH_HANT to
            "踏步機",
        Language.EN to
            "Stair climber",
        Language.KO to
            "스텝퍼",
        ),
        "s_275a48f6" to mapOf(
        Language.ZH_HANS to
            "还没在三星健康里设目标",
        Language.ZH_HANT to
            "還沒在三星健康裡設目標",
        Language.EN to
            "No goal set in Samsung Health yet",
        Language.KO to
            "Samsung Health에 아직 목표를 설정하지 않음",
        ),
        "s_2869663e" to mapOf(
        Language.ZH_HANS to
            "剧烈活动",
        Language.ZH_HANT to
            "劇烈活動",
        Language.EN to
            "Vigorous activity",
        Language.KO to
            "격렬한 활동",
        ),
        "s_28bb70ff" to mapOf(
        Language.ZH_HANS to
            "高尔夫",
        Language.ZH_HANT to
            "高爾夫",
        Language.EN to
            "Golf",
        Language.KO to
            "골프",
        ),
        "s_28bef91b" to mapOf(
        Language.ZH_HANS to
            "高抬腿",
        Language.ZH_HANT to
            "高抬腿",
        Language.EN to
            "High knees",
        Language.KO to
            "무릎 높이 들기",
        ),
        "s_29176bba" to mapOf(
        Language.ZH_HANS to
            "分数是相对你自己的：50 分 = 与你近期的中位数持平，每 25 分对应一个「显著偏离单位」",
        Language.ZH_HANT to
            "分數是相對你自己的：50 分 = 與你近期的中位數持平，每 25 分對應一個「顯著偏離單位」",
        Language.EN to
            "The score is relative to yourself: 50 points = level with your own recent median," +
            "and every 25 points corresponds to one \"significant deviation unit\" ",
        Language.KO to
            "점수는 본인 기준의 상대값입니다: 50점 = 본인의 최근 중앙값과 같은 수준, 25점마다 '유의한 편차 단위' 하나에 해당합니다 ",
        ),
        "s_29592a3c" to mapOf(
        Language.ZH_HANS to
            "「睡眠定时」只看晚不晚：24:00 之前入睡都是满分，之后每 30 分钟扣 20 分，",
        Language.ZH_HANT to
            "「睡眠定時」只看晚不晚：24:00 之前入睡都是滿分，之後每 30 分鐘扣 20 分，",
        Language.EN to
            "\"Sleep timing\" only considers how late it is: falling asleep before 24:00 is" +
            "full marks, after that 20 points are deducted every 30 minutes, ",
        Language.KO to
            "'수면 시각'은 얼마나 늦었는지만 봅니다: 24:00 이전에 잠들면 만점, 이후에는 30분마다 20점 감점, ",
        ),
        "s_29bbdf7c" to mapOf(
        Language.ZH_HANS to
            "黄体期",
        Language.ZH_HANT to
            "黃體期",
        Language.EN to
            "Luteal phase",
        Language.KO to
            "황체기",
        ),
        "s_29f56f80" to mapOf(
        Language.ZH_HANS to
            "本 App 读过来显示。手表与手机都测不出你吃了多少、喝了多少。",
        Language.ZH_HANT to
            "本 App 讀過來顯示。手錶與手機都測不出你吃了多少、喝了多少。",
        Language.EN to
            "and this app reads it to display. Neither a watch nor a phone can measure how much" +
            "you ate or drank.",
        Language.KO to
            "이 앱은 그것을 읽어서 표시합니다. 시계와 휴대폰 모두 얼마나 먹고 마셨는지는 측정할 수 없습니다.",
        ),
        "s_2a0d6978" to mapOf(
        Language.ZH_HANS to
            "而睡眠心率、心率变化、睡眠时间一致性用相对你自己的刻度 ——",
        Language.ZH_HANT to
            "而睡眠心率、心率變化、睡眠時間一致性用相對你自己的刻度 ——",
        Language.EN to
            "whereas sleeping heart rate, heart-rate variability, and sleep-time consistency" +
            "use scales relative to yourself — ",
        Language.KO to
            "반면 수면 심박수, 심박 변동, 수면 시간 일관성은 본인을 기준으로 한 상대 척도를 사용합니다 — ",
        ),
        "s_2a1c4318" to mapOf(
        Language.ZH_HANS to
            "HRV（RMSSD）",
        Language.ZH_HANT to
            "HRV（RMSSD）",
        Language.EN to
            "HRV (RMSSD)",
        Language.KO to
            "HRV(RMSSD)",
        ),
        "s_2b45c431" to mapOf(
        Language.ZH_HANS to
            "检出房颤特征；筛查结果，不等于确诊",
        Language.ZH_HANT to
            "檢出房顫特徵；篩查結果，不等於確診",
        Language.EN to
            "Atrial fibrillation features detected; a screening result, not a diagnosis",
        Language.KO to
            "심방세동 특징이 감지되었습니다. 선별 결과이며 확진이 아닙니다",
        ),
        "s_2cd16ee1" to mapOf(
        Language.ZH_HANS to
            "但仍用于计算周期长度",
        Language.ZH_HANT to
            "但仍用於計算週期長度",
        Language.EN to
            "but they are still used to compute the cycle length",
        Language.KO to
            "다만 주기 길이 계산에는 여전히 사용됩니다",
        ),
        "s_2cf6c6c7" to mapOf(
        Language.ZH_HANS to
            "三星这一侧没有数据，暂时无法对照",
        Language.ZH_HANT to
            "三星這一側沒有資料，暫時無法對照",
        Language.EN to
            "There is no data on Samsung's side, so no comparison is possible for now",
        Language.KO to
            "삼성 측에 데이터가 없어 현재로는 비교할 수 없습니다",
        ),
        "s_2cf84eb4" to mapOf(
        Language.ZH_HANS to
            "动感单车",
        Language.ZH_HANT to
            "動感單車",
        Language.EN to
            "Spinning",
        Language.KO to
            "스피닝",
        ),
        "s_2f54a686" to mapOf(
        Language.ZH_HANS to
            "⚠ 本预测基于历史周期的统计外推，不是医学判断，不能用于避孕，也不能作为备孕依据。",
        Language.ZH_HANT to
            "⚠ 本預測基於歷史週期的統計外推，不是醫學判斷，不能用於避孕，也不能作為備孕依據。",
        Language.EN to
            "⚠ This prediction is a statistical extrapolation from historical cycles. It is not" +
            "a medical judgment, must not be used for contraception, and must not be used as a" +
            "basis for trying to conceive.",
        Language.KO to
            "⚠ 이 예측은 과거 주기의 통계적 외삽이며 의학적 판단이 아닙니다. 피임에 사용할 수 없고, 임신을 계획하는 근거로도 사용할 수 없습니다.",
        ),
        "s_2f9c1094" to mapOf(
        Language.ZH_HANS to
            "更早窗口的 RMSSD 无效",
        Language.ZH_HANT to
            "更早視窗的 RMSSD 無效",
        Language.EN to
            "The RMSSD of the earlier window is invalid",
        Language.KO to
            "이전 구간의 RMSSD가 유효하지 않습니다",
        ),
        "s_2ff9a9ef" to mapOf(
        Language.ZH_HANS to
            "② 避开阳光直射、风、冷热源；",
        Language.ZH_HANT to
            "② 避開陽光直射、風、冷熱源；",
        Language.EN to
            "② avoid direct sunlight, wind, and heat/cold sources; ",
        Language.KO to
            "② 직사광선, 바람, 냉난방 기기를 피하세요. ",
        ),
        "s_30b42c1d" to mapOf(
        Language.ZH_HANS to
            "美式足球",
        Language.ZH_HANT to
            "美式足球",
        Language.EN to
            "American football",
        Language.KO to
            "미식축구",
        ),
        "s_32647f95" to mapOf(
        Language.ZH_HANS to
            "睡眠心率与心率变化 = 与你自己的基线持平得 50 分；",
        Language.ZH_HANT to
            "睡眠心率與心率變化 = 與你自己的基線持平得 50 分；",
        Language.EN to
            "sleeping heart rate and its variability = 50 points when level with your own" +
            "baseline; ",
        Language.KO to
            "수면 심박수와 심박 변동 = 본인 기준선과 같으면 50점; ",
        ),
        "s_33af02f6" to mapOf(
        Language.ZH_HANS to
            "三星健康开放的是分钟级/分箱聚合数据（心率分箱、血氧分钟级），采样率不足以看见 0.2~0.4 Hz 的呼吸；",
        Language.ZH_HANT to
            "三星健康開放的是分鐘級/分箱聚合資料（心率分箱、血氧分鐘級），取樣率不足以看見 0.2~0.4 Hz 的呼吸；",
        Language.EN to
            "What Samsung Health exposes is minute-level / binned aggregate data (binned heart" +
            "rate, minute-level blood oxygen), whose sampling rate is insufficient to see" +
            "respiration at 0.2–0.4 Hz; ",
        Language.KO to
            "Samsung Health가 제공하는 것은 분 단위/구간 집계 데이터(심박수 구간, 분 단위 혈중 산소)로, 샘플링 레이트가 0.2~0.4 Hz의" +
            "호흡을 보기에 부족합니다. ",
        ),
        "s_33b31c74" to mapOf(
        Language.ZH_HANS to
            "中途没戴表睡觉的话，这个数停留在最后一次佩戴的那晚",
        Language.ZH_HANT to
            "中途沒戴錶睡覺的話，這個數停留在最後一次佩戴的那晚",
        Language.EN to
            "if you slept without the watch in between, this number stays at the last night you" +
            "wore it",
        Language.KO to
            "중간에 시계를 착용하지 않고 잤다면 이 수치는 마지막으로 착용한 날 밤에 머물러 있습니다",
        ),
        "s_33f97ee3" to mapOf(
        Language.ZH_HANS to
            "这次测量条件正常，可以和你的历史记录比较着看",
        Language.ZH_HANT to
            "這次測量條件正常，可以和你的歷史記錄比較著看",
        Language.EN to
            "The measurement conditions were normal this time, so it can be compared with your" +
            "historical records",
        Language.KO to
            "이번 측정 조건은 정상이므로 기록과 비교해 보셔도 됩니다",
        ),
        "s_35b9c7af" to mapOf(
        Language.ZH_HANS to
            "（多半是没授权）。本机记录不受影响。",
        Language.ZH_HANT to
            "（多半是沒授權）。本機記錄不受影響。",
        Language.EN to
            " (most likely not authorized). The on-device record is unaffected.",
        Language.KO to
            "(대개 권한이 없음). 기기 내 기록은 영향을 받지 않습니다.",
        ),
        "s_3659827b" to mapOf(
        Language.ZH_HANS to
            "公告内容是空的",
        Language.ZH_HANT to
            "公告內容是空的",
        Language.EN to
            "The announcement content is empty",
        Language.KO to
            "공지 내용이 비어 있습니다",
        ),
        "s_36929cac" to mapOf(
        Language.ZH_HANS to
            "请确认指腹完整覆盖表冠电极并保持静止",
        Language.ZH_HANT to
            "請確認指腹完整覆蓋錶冠電極並保持靜止",
        Language.EN to
            "Make sure your fingertip fully covers the crown electrode and stay still",
        Language.KO to
            "손가락 지문이 크라운 전극을 완전히 덮고 있는지 확인하고 움직이지 마세요",
        ),
        "s_36c9fb9f" to mapOf(
        Language.ZH_HANS to
            "基线取你最近 20 条可比记录的中位数（数值有效、且当时没在明显活动），",
        Language.ZH_HANT to
            "基線取你最近 20 條可比記錄的中位數（數值有效、且當時沒在明顯活動），",
        Language.EN to
            "The baseline takes the median of your last 20 comparable records (valid values," +
            "and not clearly active at the time), ",
        Language.KO to
            "기준선은 최근 20개의 비교 가능한 기록(값이 유효하고 당시 뚜렷한 활동이 없던)의 중앙값을 사용합니다. ",
        ),
        "s_372f9cac" to mapOf(
        Language.ZH_HANS to
            "两者口径不同、不能互相替代，也不该拿来互相印证。",
        Language.ZH_HANT to
            "兩者口徑不同、不能互相替代，也不該拿來互相印證。",
        Language.EN to
            "The two use different definitions, cannot substitute for each other, and should" +
            "not be used to corroborate one another.",
        Language.KO to
            "두 값은 기준이 다르므로 서로 대체할 수 없고, 서로 교차 검증에 사용해서도 안 됩니다.",
        ),
        "s_37fb9008" to mapOf(
        Language.ZH_HANS to
            "力量器械",
        Language.ZH_HANT to
            "力量器械",
        Language.EN to
            "Strength machines",
        Language.KO to
            "근력 기구",
        ),
        "s_387b3920" to mapOf(
        Language.ZH_HANS to
            "。本模块的分数全部是「你这次比你平时怎么样」，",
        Language.ZH_HANT to
            "。本模組的分數全部是「你這次比你平時怎麼樣」，",
        Language.EN to
            ". Every score in this module is about \"how this time compares with your usual\", ",
        Language.KO to
            ". 이 모듈의 모든 점수는 '이번이 평소와 비교해 어떤가'를 나타내므로 ",
        ),
        "s_38ceba63" to mapOf(
        Language.ZH_HANS to
            "这段数据里推断的成分太多，继续算出来的 RMSSD 反映的是伪迹而不是心跳。",
        Language.ZH_HANT to
            "這段資料裡推斷的成分太多，繼續算出來的 RMSSD 反映的是偽跡而不是心跳。",
        Language.EN to
            "Too much of this data is inferred; the RMSSD computed from it would reflect" +
            "artifacts rather than heartbeats.",
        Language.KO to
            "이 데이터에는 추정된 부분이 너무 많아 계속 계산한 RMSSD는 심장 박동이 아니라 아티팩트를 반영하게 됩니다.",
        ),
        "s_39d32e2b" to mapOf(
        Language.ZH_HANS to
            "时区或系统时间可能不对；请以显示的日期为准",
        Language.ZH_HANT to
            "時區或系統時間可能不對；請以顯示的日期為準",
        Language.EN to
            "the time zone or system time may be wrong; go by the date shown",
        Language.KO to
            "시간대나 시스템 시간이 잘못되었을 수 있습니다. 표시된 날짜를 기준으로 하세요",
        ),
        "s_3a4ebfe7" to mapOf(
        Language.ZH_HANS to
            "同一公告每 2 天最多自动提醒一次；在下面点开看过的会标为已读，但重要通知仍会按节奏再提醒。",
        Language.ZH_HANT to
            "同一公告每 2 天最多自動提醒一次；在下面點開看過的會標為已讀，但重要通知仍會按節奏再提醒。",
        Language.EN to
            "The same announcement is auto-reminded at most once every 2 days; ones you open" +
            "below are marked as read, but important notices will still be re-reminded on" +
            "schedule.",
        Language.KO to
            "같은 공지는 2일에 최대 한 번만 자동 알림됩니다. 아래에서 열어 본 것은 읽음으로 표시되지만, 중요한 공지는 예정된 주기에 따라 다시 알림됩니다.",
        ),
        "s_3a960d46" to mapOf(
        Language.ZH_HANS to
            "四个频点的阻抗不满足「频率越高阻抗越低」这一物理规律 —— ",
        Language.ZH_HANT to
            "四個頻點的阻抗不滿足「頻率越高阻抗越低」這一物理規律 —— ",
        Language.EN to
            "The impedances at the four frequencies do not obey the physical rule that \"the" +
            "higher the frequency, the lower the impedance\" — ",
        Language.KO to
            "네 주파수의 임피던스가 '주파수가 높을수록 임피던스가 낮다'는 물리 법칙을 따르지 않습니다 — ",
        ),
        "s_3b55155d" to mapOf(
        Language.ZH_HANS to
            "暂时没有公告。\\n有重要更新时会在打开 App 时提醒你。",
        Language.ZH_HANT to
            "暫時沒有公告。\\n有重要更新時會在開啟 App 時提醒你。",
        Language.EN to
            "No announcements for now.\\nYou will be notified when you open the app if there is" +
            "an important update.",
        Language.KO to
            "현재 공지가 없습니다.\\n중요한 업데이트가 있으면 앱을 열 때 알려드립니다.",
        ),
        "s_3c79d300" to mapOf(
        Language.ZH_HANS to
            "而压力回答的是「这一次怎么样」—— 两者问的不是同一个问题。",
        Language.ZH_HANT to
            "而壓力回答的是「這一次怎麼樣」—— 兩者問的不是同一個問題。",
        Language.EN to
            "whereas stress answers \"how is it this time\" — the two ask different questions.",
        Language.KO to
            "스트레스는 '이번은 어떤가'를 답합니다 — 둘은 같은 질문을 하는 것이 아닙니다.",
        ),
        "s_3e1bbbfe" to mapOf(
        Language.ZH_HANS to
            "可能是测量期间有活动，也可能这一段本来就没采到呼吸调制",
        Language.ZH_HANT to
            "可能是測量期間有活動，也可能這一段本來就沒採到呼吸調製",
        Language.EN to
            "either there was activity during the measurement, or this segment simply did not" +
            "capture respiratory modulation",
        Language.KO to
            "측정 중 활동이 있었거나, 이 구간에서 호흡 변조가 수집되지 않았을 수 있습니다",
        ),
        "s_3ee2f8a1" to mapOf(
        Language.ZH_HANS to
            "多频阻抗不能用来自己算体脂率：三星的体成分模型没有公开，照抄文献回归式会得到",
        Language.ZH_HANT to
            "多頻阻抗不能用來自己算體脂率：三星的體成分模型沒有公開，照抄文獻迴歸式會得到",
        Language.EN to
            "Multi-frequency impedance cannot be used to compute body fat percentage yourself:" +
            "Samsung's body-composition model is not published, and copying a regression" +
            "equation from the literature would yield ",
        Language.KO to
            "다주파 임피던스로 체지방률을 직접 계산할 수는 없습니다: 삼성의 체성분 모델이 공개되지 않았고, 문헌의 회귀식을 그대로 옮기면 ",
        ),
        "s_411d3b89" to mapOf(
        Language.ZH_HANS to
            "这些条件决定「腕部皮肤温度」这个读数本身能不能用：",
        Language.ZH_HANT to
            "這些條件決定「腕部皮膚溫度」這個讀數本身能不能用：",
        Language.EN to
            "These conditions determine whether the \"wrist skin temperature\" reading itself" +
            "is usable: ",
        Language.KO to
            "이 조건들은 '손목 피부 온도' 판독값 자체를 쓸 수 있는지를 결정합니다: ",
        ),
        "s_42dc3e3e" to mapOf(
        Language.ZH_HANS to
            "检测到睡眠呼吸暂停的迹象",
        Language.ZH_HANT to
            "檢測到睡眠呼吸中止的跡象",
        Language.EN to
            "Signs of sleep apnea detected",
        Language.KO to
            "수면 무호흡 징후가 감지되었습니다",
        ),
        "s_43357054" to mapOf(
        Language.ZH_HANS to
            "结束日不能早于开始日",
        Language.ZH_HANT to
            "結束日不能早於開始日",
        Language.EN to
            "The end date cannot be earlier than the start date",
        Language.KO to
            "종료일은 시작일보다 이를 수 없습니다",
        ),
        "s_43b9e868" to mapOf(
        Language.ZH_HANS to
            "本次活动状态未判定（刚打开 App 不足一个采样窗口），无法确认是不是在静息下测的",
        Language.ZH_HANT to
            "本次活動狀態未判定（剛開啟 App 不足一個取樣視窗），無法確認是不是在靜息下測的",
        Language.EN to
            "The activity state for this measurement was not determined (the app had just" +
            "opened, less than one sampling window), so it cannot be confirmed whether it was" +
            "measured at rest",
        Language.KO to
            "이번 활동 상태는 판정되지 않았습니다(앱을 막 열어 하나의 샘플링 구간에 못 미침). 안정 상태에서 측정했는지 확인할 수 없습니다",
        ),
        "s_43bad093" to mapOf(
        Language.ZH_HANS to
            "而本机的「表体温度」不是室温 —— 佩戴时它被手腕与机身加热，",
        Language.ZH_HANT to
            "而本機的「錶體溫度」不是室溫 —— 佩戴時它被手腕與機身加熱，",
        Language.EN to
            "and this device's \"body temperature\" is not room temperature — when worn it is" +
            "heated by the wrist and the device body, ",
        Language.KO to
            "이 기기의 '시계 본체 온도'는 실내 온도가 아닙니다 — 착용 시 손목과 기기 본체에 의해 가열되므로 ",
        ),
        "s_43c2a678" to mapOf(
        Language.ZH_HANS to
            "已获取读数…",
        Language.ZH_HANT to
            "已取得讀數…",
        Language.EN to
            "Reading acquired…",
        Language.KO to
            "측정값 획득…",
        ),
        "s_459b1e9f" to mapOf(
        Language.ZH_HANS to
            "请先核对历史记录",
        Language.ZH_HANT to
            "請先核對歷史記錄",
        Language.EN to
            "please review the history first",
        Language.KO to
            "먼저 기록을 확인해 주세요",
        ),
        "s_45e031cb" to mapOf(
        Language.ZH_HANS to
            "单板滑雪",
        Language.ZH_HANT to
            "單板滑雪",
        Language.EN to
            "Snowboarding",
        Language.KO to
            "스노보드",
        ),
        "s_46332262" to mapOf(
        Language.ZH_HANS to
            "本页只做阻抗谱本身能自证的量。",
        Language.ZH_HANT to
            "本頁只做阻抗譜本身能自證的量。",
        Language.EN to
            "and this page only covers quantities that the impedance spectrum itself can support.",
        Language.KO to
            "이 페이지는 임피던스 스펙트럼 자체로 입증할 수 있는 양만 다룹니다.",
        ),
        "s_4667ccf9" to mapOf(
        Language.ZH_HANS to
            "从心电 R 峰到手腕脉搏波足的时间。它描述的是「心脏一收缩，血液多久传到手腕」。",
        Language.ZH_HANT to
            "從心電 R 峰到手腕脈搏波足的時間。它描述的是「心臟一收縮，血液多久傳到手腕」。",
        Language.EN to
            "The time from the ECG R peak to the foot of the wrist pulse wave. It describes" +
            "\"once the heart contracts, how long blood takes to reach the wrist\".",
        Language.KO to
            "심전도 R파에서 손목 맥파의 발까지의 시간입니다. '심장이 수축하면 혈액이 손목까지 얼마나 걸리는가'를 나타냅니다.",
        ),
        "s_469ff754" to mapOf(
        Language.ZH_HANS to
            "（阻抗比的绝对水平个体差异很大，只有和自己比才有意义）。",
        Language.ZH_HANT to
            "（阻抗比的絕對水平個體差異很大，只有和自己比才有意義）。",
        Language.EN to
            "(the absolute level of the impedance ratio varies greatly between individuals, so" +
            "it is only meaningful in comparison with yourself).",
        Language.KO to
            "(임피던스 비율의 절대 수준은 개인차가 매우 커서 본인과 비교할 때만 의미가 있습니다).",
        ),
        "s_46b7635c" to mapOf(
        Language.ZH_HANS to
            "= 皮肤到环境的热流），α 是「组织热阻 ÷ 环境热阻」。α 取自 Gammel 的按部位标定表：",
        Language.ZH_HANT to
            "= 皮膚到環境的熱流），α 是「組織熱阻 ÷ 環境熱阻」。α 取自 Gammel 的按部位標定表：",
        Language.EN to
            "= heat flow from skin to environment), and α is \"tissue thermal resistance ÷" +
            "environmental thermal resistance\". α is taken from Gammel's site-calibrated" +
            "table: ",
        Language.KO to
            "= 피부에서 환경으로의 열류), α는 '조직 열저항 ÷ 환경 열저항'입니다. α는 Gammel의 부위별 보정표에서 가져왔습니다: ",
        ),
        "s_46e5cca5" to mapOf(
        Language.ZH_HANS to
            "「活动持续性」把前一天切成 6 段、每段 4 小时，走到 500 步以上算这一段有活动，",
        Language.ZH_HANT to
            "「活動持續性」把前一天切成 6 段、每段 4 小時，走到 500 步以上算這一段有活動，",
        Language.EN to
            "\"Activity spread\" splits the previous day into 6 slots of 4 hours each; taking" +
            "500+ steps counts as activity in that slot, ",
        Language.KO to
            "'활동 지속성'은 전날을 4시간씩 6구간으로 나누어 500걸음 이상 걸으면 그 구간에 활동이 있는 것으로 봅니다, ",
        ),
        "s_47724116" to mapOf(
        Language.ZH_HANS to
            "指标能互相比较的前提是记录时长接近：RMSSD 是逐拍差分，30 秒也能算；",
        Language.ZH_HANT to
            "指標能互相比較的前提是記錄時長接近：RMSSD 是逐拍差分，30 秒也能算；",
        Language.EN to
            "Indices can only be compared with each other if the record lengths are similar:" +
            "RMSSD is a beat-to-beat difference and can be computed even from 30 seconds; ",
        Language.KO to
            "지표를 서로 비교하려면 기록 길이가 비슷해야 합니다: RMSSD는 박동 간 차분이라 30초로도 계산할 수 있지만 ",
        ),
        "s_47f96278" to mapOf(
        Language.ZH_HANS to
            "没有读到有效结果。按标准姿势再来一次：手臂抬起、腋下张开、",
        Language.ZH_HANT to
            "沒有讀到有效結果。按標準姿勢再來一次：手臂抬起、腋下張開、",
        Language.EN to
            "No valid result was read. Try again in the standard posture: arm raised, armpit" +
            "open, ",
        Language.KO to
            "유효한 결과를 읽지 못했습니다. 표준 자세로 다시 시도하세요: 팔을 들고, 겨드랑이를 벌리고, ",
        ),
        "s_493055f7" to mapOf(
        Language.ZH_HANS to
            "这次是边走边测的，反映的是活动状态下的心跳，建议静坐 2~3 分钟后再测一次",
        Language.ZH_HANT to
            "這次是邊走邊測的，反映的是活動狀態下的心跳，建議靜坐 2~3 分鐘後再測一次",
        Language.EN to
            "This was measured while walking and reflects heartbeats in an active state; it is" +
            "recommended to sit still for 2–3 minutes and measure again",
        Language.KO to
            "이번은 걸으면서 측정한 것으로 활동 상태의 심장 박동을 반영합니다. 2~3분간 앉아서 안정한 뒤 다시 측정하시길 권합니다",
        ),
        "s_49fb8329" to mapOf(
        Language.ZH_HANS to
            "而低频与高频的阻抗之比反映了细胞外液占总水量的比重。本页就是把这层信息量出来。",
        Language.ZH_HANT to
            "而低頻與高頻的阻抗之比反映了細胞外液占總水量的比重。本頁就是把這層資訊量出來。",
        Language.EN to
            "and the ratio of low-frequency to high-frequency impedance reflects the share of" +
            "extracellular fluid in total body water. This page measures exactly that.",
        Language.KO to
            "저주파와 고주파 임피던스의 비율은 세포외액이 총 체수분에서 차지하는 비중을 반영합니다. 이 페이지는 바로 그 정보를 측정합니다.",
        ),
        "s_4a0d8965" to mapOf(
        Language.ZH_HANS to
            "或本机固件这个会话不给 IBI_LIST。",
        Language.ZH_HANT to
            "或本機固件這個會話不給 IBI_LIST。",
        Language.EN to
            "or this device's firmware does not provide IBI_LIST for this session.",
        Language.KO to
            "또는 이 기기의 펌웨어가 이 세션에서 IBI_LIST를 제공하지 않을 수 있습니다.",
        ),
        "s_4a5f2a58" to mapOf(
        Language.ZH_HANS to
            "可比记录不足",
        Language.ZH_HANT to
            "可比記錄不足",
        Language.EN to
            "Not enough comparable records",
        Language.KO to
            "비교 가능한 기록 부족",
        ),
        "s_4b1b71aa" to mapOf(
        Language.ZH_HANS to
            "⚠ 寒战期腕温可能反而偏低：发烧起病时外周血管收缩保热，手和腕会变凉、人会打寒战。",
        Language.ZH_HANT to
            "⚠ 寒顫期腕溫可能反而偏低：發燒起病時周邊血管收縮保熱，手和腕會變涼、人會打寒顫。",
        Language.EN to
            "⚠ During the chills phase wrist temperature may actually be low: when a fever" +
            "begins, peripheral vessels constrict to conserve heat, so the hands and wrists" +
            "grow cold and you shiver.",
        Language.KO to
            "⚠ 오한기에는 손목 온도가 오히려 낮을 수 있습니다: 발열이 시작될 때 말초 혈관이 수축해 열을 보존하므로 손과 손목이 차가워지고 오한이 납니다.",
        ),
        "s_4bf49945" to mapOf(
        Language.ZH_HANS to
            "因为「太早上床」没有公认的坏处。",
        Language.ZH_HANT to
            "因為「太早上床」沒有公認的壞處。",
        Language.EN to
            "because \"going to bed too early\" has no generally accepted downside.",
        Language.KO to
            "왜냐하면 '너무 일찍 잠자리에 드는 것'에는 일반적으로 인정된 해로움이 없기 때문입니다.",
        ),
        "s_4c446b6a" to mapOf(
        Language.ZH_HANS to
            "睡眠时长、最低血氧、睡眠心率、心率变化、皮肤温度、入睡时刻、",
        Language.ZH_HANT to
            "睡眠時長、最低血氧、睡眠心率、心率變化、皮膚溫度、入睡時刻、",
        Language.EN to
            "Sleep duration, lowest blood oxygen, sleeping heart rate, heart-rate variability," +
            "skin temperature, sleep-onset time, ",
        Language.KO to
            "수면 시간, 최저 혈중 산소, 수면 심박수, 심박 변동, 피부 온도, 잠든 시각, ",
        ),
        "s_4cd880bc" to mapOf(
        Language.ZH_HANS to
            "体脂率超出合理范围（资料可能不真实）",
        Language.ZH_HANT to
            "體脂率超出合理範圍（資料可能不真實）",
        Language.EN to
            "Body fat percentage outside the plausible range (profile data may be inaccurate)",
        Language.KO to
            "체지방률이 합리적 범위를 벗어남(프로필 정보가 사실과 다를 수 있음)",
        ),
        "s_4e79e13e" to mapOf(
        Language.ZH_HANS to
            "公告不影响其它功能使用，下次打开会自动重试。",
        Language.ZH_HANT to
            "公告不影響其它功能使用，下次開啟會自動重試。",
        Language.EN to
            "Announcements do not affect other features; it will retry automatically next time" +
            "you open the app.",
        Language.KO to
            "공지는 다른 기능 사용에 영향을 주지 않으며, 다음에 앱을 열 때 자동으로 다시 시도됩니다.",
        ),
        "s_4f52088e" to mapOf(
        Language.ZH_HANS to
            "睡眠期最低血氧 95% = 100 分、88% = 0 分；",
        Language.ZH_HANT to
            "睡眠期最低血氧 95% = 100 分、88% = 0 分；",
        Language.EN to
            "lowest blood oxygen during sleep 95% = 100 points, 88% = 0 points; ",
        Language.KO to
            "수면 중 최저 혈중 산소 95% = 100점, 88% = 0점; ",
        ),
        "s_502eff02" to mapOf(
        Language.ZH_HANS to
            "文献里的「5 分钟正常值」会系统性偏低。所以每条记录都标出它的信度分级。",
        Language.ZH_HANT to
            "文獻裡的「5 分鐘正常值」會系統性偏低。所以每條記錄都標出它的信度分級。",
        Language.EN to
            "the \"5-minute normal values\" in the literature would come out systematically" +
            "low. So every record is labeled with its confidence tier.",
        Language.KO to
            "문헌의 '5분 정상값'과 비교하면 체계적으로 낮게 나옵니다. 그래서 모든 기록에 신뢰도 등급을 표시합니다.",
        ),
        "s_504cfacb" to mapOf(
        Language.ZH_HANS to
            "只跟你自己的历史比，别跟别人比。",
        Language.ZH_HANT to
            "只跟你自己的歷史比，別跟別人比。",
        Language.EN to
            "compare only with your own history, not with others.",
        Language.KO to
            "본인의 기록과만 비교하고 다른 사람과 비교하지 마세요.",
        ),
        "s_50573d28" to mapOf(
        Language.ZH_HANS to
            "这几晚的口径不一致（绝对值与偏差混在一起了）—— 两种量纲相减没有意义，",
        Language.ZH_HANT to
            "這幾晚的口徑不一致（絕對值與偏差混在一起了）—— 兩種量綱相減沒有意義，",
        Language.EN to
            "These nights use inconsistent definitions (absolute values mixed with deviations)" +
            "— subtracting two different dimensions is meaningless, ",
        Language.KO to
            "이 날 밤들은 기준이 일치하지 않습니다(절대값과 편차가 섞여 있음) — 서로 다른 차원을 빼는 것은 의미가 없습니다. ",
        ),
        "s_51d9b49c" to mapOf(
        Language.ZH_HANS to
            "本机没有这项数据",
        Language.ZH_HANT to
            "本機沒有這項資料",
        Language.EN to
            "This device has no data for this",
        Language.KO to
            "이 기기에는 해당 데이터가 없습니다",
        ),
        "s_51ebed62" to mapOf(
        Language.ZH_HANS to
            "皮温−环境温",
        Language.ZH_HANT to
            "皮溫−環境溫",
        Language.EN to
            "Skin temp − ambient temp",
        Language.KO to
            "피부 온도 − 환경 온도",
        ),
        "s_52422292" to mapOf(
        Language.ZH_HANS to
            "腕表不是整夜都在记录，太短的片段算出的指数波动极大",
        Language.ZH_HANT to
            "腕錶不是整夜都在記錄，太短的片段算出的指數波動極大",
        Language.EN to
            "The watch does not record all night, and an index computed from too short a" +
            "segment fluctuates wildly",
        Language.KO to
            "시계가 밤새 내내 기록하지는 않으며, 너무 짧은 구간에서 계산한 지수는 변동이 매우 큽니다",
        ),
        "s_52524593" to mapOf(
        Language.ZH_HANS to
            "房颤提示",
        Language.ZH_HANT to
            "房顫提示",
        Language.EN to
            "Possible atrial fibrillation",
        Language.KO to
            "심방세동 가능성",
        ),
        "s_5295c44b" to mapOf(
        Language.ZH_HANS to
            "逐拍变异的统计量在这么少的拍数上无法稳定，请延长测量时间",
        Language.ZH_HANT to
            "逐拍變異的統計量在這麼少的拍數上無法穩定，請延長測量時間",
        Language.EN to
            "Beat-to-beat variability statistics cannot be stable on so few beats; please" +
            "measure for longer",
        Language.KO to
            "박동 간 변동 통계량은 이렇게 적은 박동 수에서는 안정될 수 없으므로 측정 시간을 늘려 주세요",
        ),
        "s_52996d20" to mapOf(
        Language.ZH_HANS to
            "血氧饱和度",
        Language.ZH_HANT to
            "血氧飽和度",
        Language.EN to
            "Blood Oxygen Saturation",
        Language.KO to
            "혈중 산소 포화도",
        ),
        "s_52d5dc28" to mapOf(
        Language.ZH_HANS to
            "轻度活动",
        Language.ZH_HANT to
            "輕度活動",
        Language.EN to
            "Light activity",
        Language.KO to
            "가벼운 활동",
        ),
        "s_54435864" to mapOf(
        Language.ZH_HANS to
            "各自的刻度、权重与更新时机都不同。放在一起只是让你手上有两把尺子可以对照着看。",
        Language.ZH_HANT to
            "各自的刻度、權重與更新時機都不同。放在一起只是讓你手上有兩把尺子可以對照著看。",
        Language.EN to
            "their scales, weights, and update timing all differ. Placing them together only" +
            "gives you two rulers to compare against each other.",
        Language.KO to
            "각자의 척도, 가중치, 갱신 시점이 모두 다릅니다. 함께 놓은 것은 두 개의 자를 비교해 볼 수 있게 하기 위함일 뿐입니다.",
        ),
        "s_5451ff6d" to mapOf(
        Language.ZH_HANS to
            "在更短的记录上算出来的功率值不稳定，不如不给",
        Language.ZH_HANT to
            "在更短的記錄上算出來的功率值不穩定，不如不給",
        Language.EN to
            "power values computed from shorter records are unstable, so it is better not to" +
            "give them",
        Language.KO to
            "더 짧은 기록에서 계산한 파워 값은 불안정하므로 제공하지 않는 편이 낫습니다",
        ),
        "s_5510c76e" to mapOf(
        Language.ZH_HANS to
            "三星那个字段的语义是核心体温，而手表给的是腕部皮温（低 1~3℃）。",
        Language.ZH_HANT to
            "三星那個欄位的語義是核心體溫，而手錶給的是腕部皮溫（低 1~3℃）。",
        Language.EN to
            "Samsung's field means core body temperature, whereas the watch gives wrist skin" +
            "temperature (1–3°C lower).",
        Language.KO to
            "삼성의 필드는 심부 체온을 뜻하는데 시계가 제공하는 것은 손목 피부 온도(1~3°C 낮음)입니다.",
        ),
        "s_55a26212" to mapOf(
        Language.ZH_HANS to
            "响应是空的",
        Language.ZH_HANT to
            "回應是空的",
        Language.EN to
            "The response was empty",
        Language.KO to
            "응답이 비어 있습니다",
        ),
        "s_56e188da" to mapOf(
        Language.ZH_HANS to
            "它不是血压。这个时间 = 血液从心脏传到手腕的时间（PTT）加上心脏开始收缩前的",
        Language.ZH_HANT to
            "它不是血壓。這個時間 = 血液從心臟傳到手腕的時間（PTT）加上心臟開始收縮前的",
        Language.EN to
            "It is not blood pressure. This time = the time for blood to travel from the heart" +
            "to the wrist (PTT) plus the ",
        Language.KO to
            "이것은 혈압이 아닙니다. 이 시간 = 혈액이 심장에서 손목까지 전달되는 시간(PTT)에 심장이 수축을 시작하기 전의 ",
        ),
        "s_57be7f04" to mapOf(
        Language.ZH_HANS to
            "本机不支持体温测量（需要 Galaxy Watch5 或更新机型）。",
        Language.ZH_HANT to
            "本機不支援體溫測量（需要 Galaxy Watch5 或更新機型）。",
        Language.EN to
            "Temperature measurement is not supported on this device (Galaxy Watch5 or newer" +
            "required).",
        Language.KO to
            "이 기기에서는 체온 측정을 지원하지 않습니다(Galaxy Watch5 이상 필요).",
        ),
        "s_5a292991" to mapOf(
        Language.ZH_HANS to
            "去脂体重",
        Language.ZH_HANT to
            "去脂體重",
        Language.EN to
            "Lean Body Mass",
        Language.KO to
            "제지방량",
        ),
        "s_5a303dec" to mapOf(
        Language.ZH_HANS to
            "。测量时手臂尽量别动。",
        Language.ZH_HANT to
            "。測量時手臂盡量別動。",
        Language.EN to
            ". Try to keep your arm still during the measurement.",
        Language.KO to
            ". 측정 중에는 팔을 되도록 움직이지 마세요.",
        ),
        "s_5acb4bf1" to mapOf(
        Language.ZH_HANS to
            "中指压 2 点钟键、无名指压 4 点钟键、两只手不要互相碰到。",
        Language.ZH_HANT to
            "中指壓 2 點鐘鍵、無名指壓 4 點鐘鍵、兩隻手不要互相碰到。",
        Language.EN to
            "middle finger on the 2 o'clock button, ring finger on the 4 o'clock button, and" +
            "the two hands must not touch each other.",
        Language.KO to
            "중지는 2시 버튼, 약지는 4시 버튼에 대고, 두 손이 서로 닿지 않게 하세요.",
        ),
        "s_5ba9fac6" to mapOf(
        Language.ZH_HANS to
            "HC 的 SkinTemperatureRecord 语义是「基线 + 一串偏差」，",
        Language.ZH_HANT to
            "HC 的 SkinTemperatureRecord 語義是「基線 + 一串偏差」，",
        Language.EN to
            "HC's SkinTemperatureRecord means \"baseline + a series of deviations\", ",
        Language.KO to
            "HC의 SkinTemperatureRecord는 '기준선 + 일련의 편차'를 뜻하며 ",
        ),
        "s_5bd1b808" to mapOf(
        Language.ZH_HANS to
            "多路彼此印证才给高置信；只有一路且不强时直接不给数。",
        Language.ZH_HANT to
            "多路彼此印證才給高置信；只有一路且不強時直接不給數。",
        Language.EN to
            "High confidence is only given when multiple signals corroborate each other; with" +
            "only one weak line, no value is given at all.",
        Language.KO to
            "여러 신호가 서로 뒷받침할 때만 높은 신뢰도를 부여하며, 한 갈래뿐이고 강하지 않으면 값을 아예 제공하지 않습니다.",
        ),
        "s_5c852f43" to mapOf(
        Language.ZH_HANS to
            "定向越野",
        Language.ZH_HANT to
            "定向越野",
        Language.EN to
            "Orienteering",
        Language.KO to
            "오리엔티어링",
        ),
        "s_5cf300a5" to mapOf(
        Language.ZH_HANS to
            "这个分用到两天的信息：昨晚的恢复（睡了多久、血氧有没有掉、夜间心率比平时高还是低、",
        Language.ZH_HANT to
            "這個分用到兩天的資訊：昨晚的恢復（睡了多久、血氧有沒有掉、夜間心率比平時高還是低、",
        Language.EN to
            "This score draws on two days of information: last night's recovery (how long you" +
            "slept, whether blood oxygen dropped, whether the nighttime heart rate was higher" +
            "or lower than usual, ",
        Language.KO to
            "이 점수는 이틀치 정보를 사용합니다: 어젯밤의 회복(얼마나 잤는지, 혈중 산소가 떨어졌는지, 야간 심박수가 평소보다 높았는지 낮았는지, ",
        ),
        "s_5d3beb60" to mapOf(
        Language.ZH_HANS to
            "本次未检测到迹象",
        Language.ZH_HANT to
            "本次未檢測到跡象",
        Language.EN to
            "No signs detected this time",
        Language.KO to
            "이번에는 징후가 감지되지 않았습니다",
        ),
        "s_5e6053dd" to mapOf(
        Language.ZH_HANS to
            "脉搏要么留空，要么填正整数",
        Language.ZH_HANT to
            "脈搏要麼留空，要麼填正整數",
        Language.EN to
            "Pulse must be either left blank or entered as a positive integer",
        Language.KO to
            "맥박은 비워 두거나 양의 정수로 입력해야 합니다",
        ),
        "s_5f1ca040" to mapOf(
        Language.ZH_HANS to
            "阻抗不稳定",
        Language.ZH_HANT to
            "阻抗不穩定",
        Language.EN to
            "Impedance unstable",
        Language.KO to
            "임피던스 불안정",
        ),
        "s_5f77a5e3" to mapOf(
        Language.ZH_HANS to
            "本页先检出每个心搏，把每一拍压成这三个数，再在呼吸频段（0.1~0.6 Hz）里找周期。",
        Language.ZH_HANT to
            "本頁先檢出每個心搏，把每一拍壓成這三個數，再在呼吸頻段（0.1~0.6 Hz）裡找週期。",
        Language.EN to
            "This page first detects each heartbeat, compresses each beat into these three" +
            "numbers, and then looks for a cycle in the respiratory band (0.1–0.6 Hz).",
        Language.KO to
            "이 페이지는 먼저 각 심장 박동을 검출해 각 박동을 이 세 수치로 압축한 뒤, 호흡 대역(0.1~0.6 Hz)에서 주기를 찾습니다.",
        ),
        "s_5fbcb1c4" to mapOf(
        Language.ZH_HANS to
            "没有给绝对的正常范围 —— RMSSD 随年龄、体质、体位差异可达数倍，固定阈值只会误报。",
        Language.ZH_HANT to
            "沒有給絕對的正常範圍 —— RMSSD 隨年齡、體質、體位差異可達數倍，固定閾值只會誤報。",
        Language.EN to
            "No absolute normal range is given — RMSSD can vary several-fold with age, fitness," +
            "and posture, so fixed thresholds would only produce false alarms.",
        Language.KO to
            "절대적인 정상 범위는 제시하지 않습니다 — RMSSD는 나이, 체력, 자세에 따라 몇 배까지 차이 나므로 고정 임계값은 오경보만 낳습니다.",
        ),
        "s_5fff284a" to mapOf(
        Language.ZH_HANS to
            "表带太松",
        Language.ZH_HANT to
            "錶帶太鬆",
        Language.EN to
            "Band too loose",
        Language.KO to
            "밴드가 너무 헐거움",
        ),
        "s_600eb0b3" to mapOf(
        Language.ZH_HANS to
            "。HRV 越高通常表示身体越放松，但这个数因人而异 —— ",
        Language.ZH_HANT to
            "。HRV 越高通常表示身體越放鬆，但這個數因人而異 —— ",
        Language.EN to
            ". A higher HRV usually means the body is more relaxed, but this number varies from" +
            "person to person — ",
        Language.KO to
            ". HRV가 높을수록 보통 몸이 더 이완되었음을 뜻하지만 이 수치는 사람마다 다릅니다 — ",
        ),
        "s_60b82276" to mapOf(
        Language.ZH_HANS to
            "疲劳用的是趋势而不是单次值：近期窗口与更早窗口各取若干条静息记录，",
        Language.ZH_HANT to
            "疲勞用的是趨勢而不是單次值：近期視窗與更早視窗各取若干條靜息記錄，",
        Language.EN to
            "Fatigue uses a trend rather than a single value: it takes several resting records" +
            "from a recent window and an earlier window respectively, ",
        Language.KO to
            "피로는 단일 값이 아니라 추세를 사용합니다: 최근 구간과 이전 구간에서 각각 여러 개의 안정 시 기록을 뽑아 ",
        ),
        "s_60c8c2e5" to mapOf(
        Language.ZH_HANS to
            "雪鞋行走",
        Language.ZH_HANT to
            "雪鞋行走",
        Language.EN to
            "Snowshoeing",
        Language.KO to
            "스노슈잉",
        ),
        "s_61254d1a" to mapOf(
        Language.ZH_HANS to
            "静息心率",
        Language.ZH_HANT to
            "靜息心率",
        Language.EN to
            "Resting heart rate",
        Language.KO to
            "안정 심박수",
        ),
        "s_6162c32e" to mapOf(
        Language.ZH_HANS to
            "睡眠时间一致性",
        Language.ZH_HANT to
            "睡眠時間一致性",
        Language.EN to
            "Sleep-time consistency",
        Language.KO to
            "수면 시간 일관성",
        ),
        "s_61744b9a" to mapOf(
        Language.ZH_HANS to
            "不能互相印证。心电结论以你自己的那次测量为准。",
        Language.ZH_HANT to
            "不能互相印證。心電結論以你自己的那次測量為準。",
        Language.EN to
            "and cannot corroborate each other. For the ECG conclusion, rely on your own" +
            "measurement.",
        Language.KO to
            "서로 교차 검증할 수 없습니다. 심전도 결론은 본인이 직접 측정한 결과를 기준으로 하십시오.",
        ),
        "s_62df67b1" to mapOf(
        Language.ZH_HANS to
            "只有跟自己的平时比才有意义。三星健康的皮肤温度也是这两个口径。",
        Language.ZH_HANT to
            "只有跟自己的平時比才有意義。三星健康的皮膚溫度也是這兩個口徑。",
        Language.EN to
            "so it is only meaningful in comparison with your own usual value. Samsung Health's" +
            "skin temperature uses these same two qualifications.",
        Language.KO to
            "본인의 평소 값과 비교할 때만 의미가 있습니다. Samsung Health의 피부 온도도 이 두 가지 기준을 사용합니다.",
        ),
        "s_6395a9c0" to mapOf(
        Language.ZH_HANS to
            "缺少可用的表体温度（模型需要皮温与另一个温度输入；",
        Language.ZH_HANT to
            "缺少可用的錶體溫度（模型需要皮溫與另一個溫度輸入；",
        Language.EN to
            "No usable body temperature is available (the model needs skin temperature and" +
            "another temperature input; ",
        Language.KO to
            "사용할 수 있는 시계 본체 온도가 없습니다(모델은 피부 온도와 또 하나의 온도 입력이 필요하며, ",
        ),
        "s_64083297" to mapOf(
        Language.ZH_HANS to
            "本次读数超出电极能测的范围。换个姿势（手臂抬起来、离开身体）再测一次。",
        Language.ZH_HANT to
            "本次讀數超出電極能測的範圍。換個姿勢（手臂抬起來、離開身體）再測一次。",
        Language.EN to
            "This reading is outside what the electrodes can measure. Change posture (raise" +
            "your arm away from your body) and measure again.",
        Language.KO to
            "이번 판독값이 전극이 측정할 수 있는 범위를 벗어났습니다. 자세를 바꿔(팔을 들어 몸에서 떼고) 다시 측정하세요.",
        ),
        "s_6420b6ae" to mapOf(
        Language.ZH_HANS to
            "周期会受压力、睡眠、疾病、跨时区旅行、体重变化与药物影响；",
        Language.ZH_HANT to
            "週期會受壓力、睡眠、疾病、跨時區旅行、體重變化與藥物影響；",
        Language.EN to
            "Cycles are affected by stress, sleep, illness, travel across time zones, weight" +
            "change, and medication; ",
        Language.KO to
            "주기는 스트레스, 수면, 질병, 시차 이동, 체중 변화, 약물의 영향을 받습니다. ",
        ),
        "s_64b3e2ef" to mapOf(
        Language.ZH_HANS to
            "脉搏幅度",
        Language.ZH_HANT to
            "脈搏幅度",
        Language.EN to
            "Pulse amplitude",
        Language.KO to
            "맥박 진폭",
        ),
        "s_668d3da0" to mapOf(
        Language.ZH_HANS to
            "活动相关的两项来自三星健康的步数（你手机与手表的计步），不是本 App 自己的",
        Language.ZH_HANT to
            "活動相關的兩項來自三星健康的步數（你手機與手錶的計步），不是本 App 自己的",
        Language.EN to
            "The two activity-related items come from Samsung Health's step count (your phone" +
            "and watch step counting), not from this app's own ",
        Language.KO to
            "활동 관련 두 항목은 Samsung Health의 걸음 수(휴대폰과 시계의 걸음 측정)에서 오며, 이 앱의 ",
        ),
        "s_669f9294" to mapOf(
        Language.ZH_HANS to
            "每有一段加 20 分（5 段即满分，人不可能 24 小时都在活动）。",
        Language.ZH_HANT to
            "每有一段加 20 分（5 段即滿分，人不可能 24 小時都在活動）。",
        Language.EN to
            "and each active slot adds 20 points (5 slots is full marks; no one can be active" +
            "24 hours a day).",
        Language.KO to
            "활동 구간마다 20점이 더해집니다(5구간이면 만점, 사람이 24시간 내내 활동할 수는 없습니다).",
        ),
        "s_66a98cff" to mapOf(
        Language.ZH_HANS to
            "活动量在这里只做门控、不参与打分：加速度只有打开 App 期间才采（Wear OS 不允许后台常驻采样），",
        Language.ZH_HANT to
            "活動量在這裡只做門控、不參與打分：加速度只有開啟 App 期間才採（Wear OS 不允許後台常駐取樣），",
        Language.EN to
            "Activity is only used for gating here and does not enter the score: the" +
            "accelerometer only samples while the app is open (Wear OS does not allow always-on" +
            "background sampling), ",
        Language.KO to
            "활동량은 여기서 게이팅에만 쓰이며 점수에 들어가지 않습니다: 가속도는 앱이 열려 있는 동안에만 수집됩니다(Wear OS는 백그라운드 상시 샘플링을" +
            "허용하지 않습니다). ",
        ),
        "s_686b5d69" to mapOf(
        Language.ZH_HANS to
            "这些因素各自就能造成 ±2~3℃，量级比发烧在腕部的信号还大。",
        Language.ZH_HANT to
            "這些因素各自就能造成 ±2~3℃，量級比發燒在腕部的訊號還大。",
        Language.EN to
            "and each of these factors alone can cause ±2–3°C, larger than the signal a fever" +
            "produces at the wrist.",
        Language.KO to
            "이 요인들 각각이 ±2~3°C를 만들 수 있어, 발열이 손목에 남기는 신호보다 큽니다.",
        ),
        "s_6a241335" to mapOf(
        Language.ZH_HANS to
            "把无名指压在手表的 4 点钟按键上，两根手指都要压住金属部分，全程别松开。",
        Language.ZH_HANT to
            "把無名指壓在手錶的 4 點鐘按鍵上，兩根手指都要壓住金屬部分，全程別鬆開。",
        Language.EN to
            "Press your ring finger on the watch's 4 o'clock button; both fingers must press" +
            "the metal part and stay there the whole time.",
        Language.KO to
            "약지를 시계의 4시 버튼에 대세요. 두 손가락 모두 금속 부분을 눌러야 하며 처음부터 끝까지 떼지 마세요.",
        ),
        "s_6ac70683" to mapOf(
        Language.ZH_HANS to
            "正在连接 Health Sensor Service…",
        Language.ZH_HANT to
            "正在連線 Health Sensor Service…",
        Language.EN to
            "Connecting to Health Sensor Service…",
        Language.KO to
            "Health Sensor Service에 연결하는 중…",
        ),
        "s_6c4a8adc" to mapOf(
        Language.ZH_HANS to
            "逐拍间期里的伪迹（漏检/多检）会让 RMSSD 凭空翻几倍，所以数据先过一遍伪迹修正：",
        Language.ZH_HANT to
            "逐拍間期裡的偽跡（漏檢/多檢）會讓 RMSSD 憑空翻幾倍，所以資料先過一遍偽跡修正：",
        Language.EN to
            "Artifacts in the beat-to-beat intervals (missed/extra detections) can make RMSSD" +
            "several times larger out of nowhere, so the data first goes through artifact" +
            "correction: ",
        Language.KO to
            "박동 간 간격의 아티팩트(누락/과검출)는 RMSSD를 몇 배로 부풀릴 수 있으므로 데이터를 먼저 아티팩트 보정 과정에 통과시킵니다: ",
        ),
        "s_6c6715f2" to mapOf(
        Language.ZH_HANS to
            "总分是这些分量的加权平均，50 分大致对应「每一项都刚够」。",
        Language.ZH_HANT to
            "總分是這些分量的加權平均，50 分大致對應「每一項都剛夠」。",
        Language.EN to
            "The total is the weighted average of these components; roughly 50 points" +
            "corresponds to \"just enough on every item\".",
        Language.KO to
            "총점은 이 성분들의 가중 평균이며, 50점은 대략 '모든 항목이 겨우 충족'에 해당합니다.",
        ),
        "s_6ce3380e" to mapOf(
        Language.ZH_HANS to
            "不要把它当成十个维度都看过了",
        Language.ZH_HANT to
            "不要把它當成十個維度都看過了",
        Language.EN to
            "do not treat it as if all ten dimensions had been evaluated",
        Language.KO to
            "열 개 차원을 모두 확인한 것으로 여기지 마세요",
        ),
        "s_6d6882fc" to mapOf(
        Language.ZH_HANS to
            "回填的意义是「让别处用上真实数据」，不是把近似值伪装成测量值。",
        Language.ZH_HANT to
            "回填的意義是「讓別處用上真實資料」，不是把近似值偽裝成測量值。",
        Language.EN to
            "the point of backfilling is \"to let other places use real data\", not to disguise" +
            "an approximation as a measured value.",
        Language.KO to
            "되쓰기의 취지는 '다른 곳에서 실제 데이터를 쓰게 하는 것'이지, 근사값을 측정값으로 가장하는 것이 아닙니다.",
        ),
        "s_6d7bdaff" to mapOf(
        Language.ZH_HANS to
            "%.1f 次/小时",
        Language.ZH_HANT to
            "%.1f 次/小時",
        Language.EN to
            "%.1f /h",
        Language.KO to
            "%.1f회/시간",
        ),
        "s_6f0802d8" to mapOf(
        Language.ZH_HANS to
            "会被当成同一段经期。请确认是不是重复记录",
        Language.ZH_HANT to
            "會被當成同一段經期。請確認是不是重複記錄",
        Language.EN to
            "It will be treated as the same period. Please confirm whether this is a duplicate" +
            "record",
        Language.KO to
            "같은 월경으로 처리됩니다. 중복 기록인지 확인해 주세요",
        ),
        "s_6f4833e9" to mapOf(
        Language.ZH_HANS to
            "缺了全部输入没有别的办法可以补 —— ",
        Language.ZH_HANT to
            "缺了全部輸入沒有別的辦法可以補 —— ",
        Language.EN to
            "and with all inputs missing there is no other way to make it up — ",
        Language.KO to
            "모든 입력이 빠진 경우 달리 보완할 방법이 없습니다 — ",
        ),
        "s_6f73fa57" to mapOf(
        Language.ZH_HANS to
            "睡眠时间一致性未参与：基线还差 ",
        Language.ZH_HANT to
            "睡眠時間一致性未參與：基線還差 ",
        Language.EN to
            "Sleep-time consistency did not participate: the baseline is still short by ",
        Language.KO to
            "수면 시간 일관성은 참여하지 않았습니다: 기준선이 아직 ",
        ),
        "s_6fe79626" to mapOf(
        Language.ZH_HANS to
            "入睡时刻与平时差 20 分钟以内满分；近 14 晚入睡时刻相差 30 分钟以内满分；",
        Language.ZH_HANT to
            "入睡時刻與平時差 20 分鐘以內滿分；近 14 晚入睡時刻相差 30 分鐘以內滿分；",
        Language.EN to
            "sleep-onset time within 20 minutes of your usual time scores full marks; within a" +
            "30-minute spread across the last 14 nights scores full marks; ",
        Language.KO to
            "잠든 시각이 평소와 20분 이내 차이면 만점, 최근 14일 밤의 잠든 시각 편차가 30분 이내면 만점; ",
        ),
        "s_7005f5ed" to mapOf(
        Language.ZH_HANS to
            "想和长期趋势比，建议测满 2 分钟以上",
        Language.ZH_HANT to
            "想和長期趨勢比，建議測滿 2 分鐘以上",
        Language.EN to
            "to compare with a long-term trend, measure for a full 2 minutes or more",
        Language.KO to
            "장기 추세와 비교하려면 2분 이상 측정하시길 권합니다",
        ),
        "s_7030b86e" to mapOf(
        Language.ZH_HANS to
            "还没有建立起属于你自己的基线：",
        Language.ZH_HANT to
            "還沒有建立起屬於你自己的基線：",
        Language.EN to
            "A baseline of your own has not been established yet: ",
        Language.KO to
            "본인만의 기준선이 아직 구축되지 않았습니다: ",
        ),
        "s_70fd5793" to mapOf(
        Language.ZH_HANS to
            "节律不齐时 RR 的变异主要来自心律失常本身，RMSSD、SDNN 都会明显偏大，",
        Language.ZH_HANT to
            "節律不齊時 RR 的變異主要來自心律不整本身，RMSSD、SDNN 都會明顯偏大，",
        Language.EN to
            "with an irregular rhythm the RR variability comes mainly from the arrhythmia" +
            "itself, and both RMSSD and SDNN become markedly larger, ",
        Language.KO to
            "리듬이 불규칙하면 RR 변동은 주로 부정맥 자체에서 비롯되어 RMSSD와 SDNN이 모두 뚜렷하게 커집니다. ",
        ),
        "s_725e51b2" to mapOf(
        Language.ZH_HANS to
            "这里的 60 分不等于任何权威量表的 60 分。",
        Language.ZH_HANT to
            "這裡的 60 分不等於任何權威量表的 60 分。",
        Language.EN to
            "A score of 60 here is not equal to 60 on any authoritative scale.",
        Language.KO to
            "여기의 60점은 어떤 권위 있는 척도의 60점과도 같지 않습니다.",
        ),
        "s_72a6f25c" to mapOf(
        Language.ZH_HANS to
            "公告内容格式不对，已忽略本次更新",
        Language.ZH_HANT to
            "公告內容格式不對，已忽略本次更新",
        Language.EN to
            "The announcement content is malformed; this update was ignored",
        Language.KO to
            "공지 내용의 형식이 올바르지 않아 이번 업데이트를 무시했습니다",
        ),
        "s_72a90bae" to mapOf(
        Language.ZH_HANS to
            "心跳波形不清晰，请保持手指贴紧表冠",
        Language.ZH_HANT to
            "心跳波形不清晰，請保持手指貼緊錶冠",
        Language.EN to
            "The heartbeat waveform is unclear; keep your finger pressed firmly on the crown",
        Language.KO to
            "심박 파형이 선명하지 않습니다. 손가락을 크라운에 밀착한 채 유지하세요",
        ),
        "s_7377e23a" to mapOf(
        Language.ZH_HANS to
            "近期窗口的心率无效",
        Language.ZH_HANT to
            "近期視窗的心率無效",
        Language.EN to
            "The heart rate of the recent window is invalid",
        Language.KO to
            "최근 구간의 심박수가 유효하지 않습니다",
        ),
        "s_741d9380" to mapOf(
        Language.ZH_HANS to
            "数字可以参考，但重新测一次会更稳",
        Language.ZH_HANT to
            "數字可以參考，但重新測一次會更穩",
        Language.EN to
            "The number can be used as a reference, but measuring again would be more reliable",
        Language.KO to
            "이 수치는 참고할 수 있지만 다시 측정하면 더 안정적입니다",
        ),
        "s_745fd0da" to mapOf(
        Language.ZH_HANS to
            "所以「今晚腕温不高」不能用来排除发烧。",
        Language.ZH_HANT to
            "所以「今晚腕溫不高」不能用來排除發燒。",
        Language.EN to
            "So \"wrist temperature is not high tonight\" cannot be used to rule out a fever.",
        Language.KO to
            "따라서 '오늘 밤 손목 온도가 높지 않다'는 발열을 배제하는 근거가 될 수 없습니다.",
        ),
        "s_74643f37" to mapOf(
        Language.ZH_HANS to
            "两只手互相碰到了",
        Language.ZH_HANT to
            "兩隻手互相碰到了",
        Language.EN to
            "The two hands are touching each other",
        Language.KO to
            "두 손이 서로 닿았습니다",
        ),
        "s_7482300d" to mapOf(
        Language.ZH_HANS to
            "睡眠期最低血氧",
        Language.ZH_HANT to
            "睡眠期最低血氧",
        Language.EN to
            "Lowest blood oxygen during sleep",
        Language.KO to
            "수면 중 최저 혈중 산소",
        ),
        "s_7482759f" to mapOf(
        Language.ZH_HANS to
            "它不含主观感受 —— 困不困、累不累只有你自己知道。",
        Language.ZH_HANT to
            "它不含主觀感受 —— 睏不睏、累不累只有你自己知道。",
        Language.EN to
            "It does not include subjective feelings — only you know whether you are sleepy or" +
            "tired.",
        Language.KO to
            "주관적인 느낌은 포함하지 않습니다 — 졸린지 피곤한지는 본인만 알 수 있습니다.",
        ),
        "s_74e3d025" to mapOf(
        Language.ZH_HANS to
            "（数血氧掉 ≥3% 的次数，反映睡眠呼吸的紊乱程度）；这一条是三星的筛查信号，",
        Language.ZH_HANT to
            "（數血氧掉 ≥3% 的次數，反映睡眠呼吸的紊亂程度）；這一條是三星的篩檢訊號，",
        Language.EN to
            "(counting how many times blood oxygen drops by ≥3%, reflecting how disturbed your" +
            "breathing is during sleep); this entry is Samsung's screening signal, ",
        Language.KO to
            "(혈중 산소가 ≥3% 떨어진 횟수를 세어 수면 중 호흡의 불규칙한 정도를 반영합니다). 이 항목은 삼성의 선별 신호로, ",
        ),
        "s_751897a0" to mapOf(
        Language.ZH_HANS to
            "相邻两拍之和约等于参考值的判为多检并合并。修正与丢弃占比超过两成直接不出结果。",
        Language.ZH_HANT to
            "相鄰兩拍之和約等於參考值的判為多檢並合併。修正與丟棄佔比超過兩成直接不出結果。",
        Language.EN to
            "and adjacent beats whose sum is about equal to the reference are taken as extra" +
            "detections and merged. If correction plus discarding exceeds 20%, no result is" +
            "produced at all.",
        Language.KO to
            "인접한 두 박동의 합이 기준값과 거의 같은 것은 과검출로 보고 병합합니다. 보정과 폐기 비율이 20%를 넘으면 결과를 내지 않습니다.",
        ),
        "s_75a541ad" to mapOf(
        Language.ZH_HANS to
            "本 App 不做血压判定，也不诊断任何疾病。",
        Language.ZH_HANT to
            "本 App 不做血壓判定，也不診斷任何疾病。",
        Language.EN to
            "This app does not judge blood pressure or diagnose any disease.",
        Language.KO to
            "이 앱은 혈압을 판정하지 않으며 어떤 질병도 진단하지 않습니다.",
        ),
        "s_75a559a6" to mapOf(
        Language.ZH_HANS to
            "这个量级的逐拍跳变在生理上不存在，说明伪迹没有被清干净。",
        Language.ZH_HANT to
            "這個量級的逐拍跳變在生理上不存在，說明偽跡沒有被清乾淨。",
        Language.EN to
            "Beat-to-beat jumps of this magnitude do not exist physiologically, which means" +
            "artifacts were not cleaned out completely.",
        Language.KO to
            "이 정도 크기의 박동 간 급변은 생리적으로 존재하지 않으므로 아티팩트가 완전히 제거되지 않았음을 뜻합니다.",
        ),
        "s_768c0cef" to mapOf(
        Language.ZH_HANS to
            "缺少用户资料（身高/体重/性别/年龄）。请先在手机端「体成分」页填好并同步给手表。",
        Language.ZH_HANT to
            "缺少使用者資料（身高/體重/性別/年齡）。請先在手機端「體成分」頁填好並同步給手錶。",
        Language.EN to
            "User profile missing (height / weight / sex / age). Fill it in on the phone's" +
            "\"Body composition\" page and sync it to the watch first.",
        Language.KO to
            "사용자 프로필(키/몸무게/성별/나이)이 없습니다. 먼저 휴대폰의 「체성분」 페이지에서 입력하고 워치로 동기화하세요.",
        ),
        "s_76e736e1" to mapOf(
        Language.ZH_HANS to
            "信号质量差（心跳漏检过多），请保持静止重新测量",
        Language.ZH_HANT to
            "訊號品質差（心跳漏檢過多），請保持靜止重新測量",
        Language.EN to
            "Poor signal quality (too many missed beats); stay still and measure again",
        Language.KO to
            "신호 품질 불량(심박 누락 과다). 움직이지 말고 다시 측정하세요",
        ),
        "s_76ec48cc" to mapOf(
        Language.ZH_HANS to
            "无关因素污染」分配的工程权重，没有经过任何临床标定：HRV 反映自主神经对心率的直接调节，",
        Language.ZH_HANT to
            "無關因素污染」分配的工程權重，沒有經過任何臨床標定：HRV 反映自律神經對心率的直接調節，",
        Language.EN to
            "contaminated by irrelevant factors\"; they have not been clinically calibrated:" +
            "HRV reflects the autonomic nervous system's direct modulation of heart rate, ",
        Language.KO to
            "무관한 요인에 오염되기 어려운가'를 기준으로 배분한 공학적 가중치이며 임상적 보정을 거치지 않았습니다: HRV는 자율신경이 심박수를 직접 조절하는" +
            "정도를 반영하고, ",
        ),
        "s_7706b535" to mapOf(
        Language.ZH_HANS to
            "生理区间外的剔除；约 2~3 倍参考值的判为漏检并拆开；",
        Language.ZH_HANT to
            "生理區間外的剔除；約 2~3 倍參考值的判為漏檢並拆開；",
        Language.EN to
            "values outside the physiological range are removed; those about 2–3 times the" +
            "reference are taken as missed beats and split; ",
        Language.KO to
            "생리적 범위를 벗어난 값은 제거하고, 기준값의 약 2~3배인 것은 누락으로 보고 분할합니다. ",
        ),
        "s_77410ca5" to mapOf(
        Language.ZH_HANS to
            "检测到迹象",
        Language.ZH_HANT to
            "偵測到跡象",
        Language.EN to
            "Signs detected",
        Language.KO to
            "징후 감지됨",
        ),
        "s_778ebdad" to mapOf(
        Language.ZH_HANS to
            "请将手指按住表冠…",
        Language.ZH_HANT to
            "請將手指按住錶冠…",
        Language.EN to
            "Press your finger on the crown…",
        Language.KO to
            "손가락으로 크라운을 눌러주세요…",
        ),
        "s_7854f345" to mapOf(
        Language.ZH_HANS to
            "ODI = 事件数 ÷ 实际监测到的小时数（腕表不是整夜都在记录，所以分母用监测时长而不是整段睡眠时长）。",
        Language.ZH_HANT to
            "ODI = 事件數 ÷ 實際監測到的小時數（腕錶不是整夜都在記錄，所以分母用監測時長而不是整段睡眠時長）。",
        Language.EN to
            "ODI = number of events ÷ actual hours monitored (the watch does not record all" +
            "night, so the denominator uses monitored time rather than total sleep duration).",
        Language.KO to
            "ODI = 이벤트 수 ÷ 실제 모니터링한 시간 수(시계가 밤새 내내 기록하지는 않으므로 분모는 전체 수면 시간이 아니라 모니터링 시간을 사용합니다).",
        ),
        "s_78b881ed" to mapOf(
        Language.ZH_HANS to
            "这是筛查信号，不是诊断。它只提示存在这类迹象的可能性，",
        Language.ZH_HANT to
            "這是篩檢訊號，不是診斷。它只提示存在這類跡象的可能性，",
        Language.EN to
            "This is a screening signal, not a diagnosis. It only indicates the possibility" +
            "that such signs are present, ",
        Language.KO to
            "이것은 선별 신호이며 진단이 아닙니다. 이러한 징후가 있을 가능성만 알려주며, ",
        ),
        "s_795f8409" to mapOf(
        Language.ZH_HANS to
            "不是与这次心电同时测的",
        Language.ZH_HANT to
            "不是與這次心電同時測的",
        Language.EN to
            "it was not measured at the same time as this ECG",
        Language.KO to
            "이번 심전도와 동시에 측정된 것이 아닙니다",
        ),
        "s_795fb8f1" to mapOf(
        Language.ZH_HANS to
            "而「压力」分那边心率偏高 = 负荷更大 = 分更高 —— 两个分的 50 分含义不同。",
        Language.ZH_HANT to
            "而「壓力」分那邊心率偏高 = 負荷更大 = 分更高 —— 兩個分的 50 分含義不同。",
        Language.EN to
            "whereas on the \"stress\" score a higher heart rate means a greater load and a" +
            "higher score — 50 points means different things on the two scores.",
        Language.KO to
            "반면 '스트레스' 점수에서는 심박수가 높을수록 부하가 크고 점수가 높습니다 — 두 점수의 50점은 의미가 다릅니다.",
        ),
        "s_7a163a38" to mapOf(
        Language.ZH_HANS to
            "低于常见静息范围",
        Language.ZH_HANT to
            "低於常見靜息範圍",
        Language.EN to
            "Below the usual resting range",
        Language.KO to
            "일반적인 안정 범위보다 낮음",
        ),
        "s_7a2c369a" to mapOf(
        Language.ZH_HANS to
            "山地骑行",
        Language.ZH_HANT to
            "山地騎行",
        Language.EN to
            "Mountain biking",
        Language.KO to
            "산악 자전거",
        ),
        "s_7a97d0e5" to mapOf(
        Language.ZH_HANS to
            "它与「睡眠时间一致性」不是一回事：后者比的是跟你自己平时偏了多少（早睡也扣），",
        Language.ZH_HANT to
            "它與「睡眠時間一致性」不是一回事：後者比的是跟你自己平時偏了多少（早睡也扣），",
        Language.EN to
            "It is not the same as \"sleep-time consistency\": the latter compares how far you" +
            "deviate from your own usual time (going to bed early is penalized too), ",
        Language.KO to
            "'수면 시간 일관성'과는 다른 것입니다: 후자는 본인의 평소 시각에서 얼마나 벗어났는지를 비교합니다(일찍 자는 것도 감점). ",
        ),
        "s_7af20d18" to mapOf(
        Language.ZH_HANS to
            "逐拍间期是靠「心搏」采样的，拍数不够就没有足够的采样点看呼吸",
        Language.ZH_HANT to
            "逐拍間期是靠「心搏」取樣的，拍數不夠就沒有足夠的取樣點看呼吸",
        Language.EN to
            "Beat-to-beat intervals are sampled by heartbeats, so too few beats means not" +
            "enough sample points to see respiration",
        Language.KO to
            "박동 간 간격은 '심장 박동'으로 샘플링되므로 박동 수가 부족하면 호흡을 볼 샘플 포인트가 부족합니다",
        ),
        "s_7b64e99a" to mapOf(
        Language.ZH_HANS to
            "记录还不够，暂不给预测",
        Language.ZH_HANT to
            "記錄還不夠，暫不給預測",
        Language.EN to
            "Not enough records yet, so no prediction for now",
        Language.KO to
            "기록이 아직 부족하여 예측을 제공하지 않습니다",
        ),
        "s_7bed1055" to mapOf(
        Language.ZH_HANS to
            "评分的输入就是这几个数，缺了它们没有别的办法可以补",
        Language.ZH_HANT to
            "評分的輸入就是這幾個數，缺了它們沒有別的辦法可以補",
        Language.EN to
            "The scoring inputs are exactly these numbers; with them missing there is no other" +
            "way to make it up",
        Language.KO to
            "점수의 입력은 바로 이 수치들이며, 빠지면 달리 보완할 방법이 없습니다",
        ),
        "s_7c0f90fb" to mapOf(
        Language.ZH_HANS to
            "内部错误：资料在连接前丢失",
        Language.ZH_HANT to
            "內部錯誤：資料在連線前遺失",
        Language.EN to
            "Internal error: profile was lost before connecting",
        Language.KO to
            "내부 오류: 연결 전에 프로필이 유실되었습니다",
        ),
        "s_7ca32566" to mapOf(
        Language.ZH_HANS to
            "正常范围",
        Language.ZH_HANT to
            "正常範圍",
        Language.EN to
            "Normal range",
        Language.KO to
            "정상 범위",
        ),
        "s_7cb34856" to mapOf(
        Language.ZH_HANS to
            "是按「哪个量更直接、更不易被无关因素污染」分配的工程权重，没有经过任何临床标定。",
        Language.ZH_HANT to
            "是按「哪個量更直接、更不易被無關因素污染」分配的工程權重，沒有經過任何臨床標定。",
        Language.EN to
            "are engineering weights assigned on the basis of \"which quantity is more direct" +
            "and less easily contaminated by irrelevant factors\"; they have not been" +
            "clinically calibrated.",
        Language.KO to
            "는 '어떤 양이 더 직접적이고 무관한 요인에 오염되기 어려운가'를 기준으로 배분한 공학적 가중치이며, 임상적 보정을 거치지 않았습니다.",
        ),
        "s_7da1abeb" to mapOf(
        Language.ZH_HANS to
            "表带系紧一点，让手表背面完全贴住手腕，再重测。",
        Language.ZH_HANT to
            "錶帶繫緊一點，讓手錶背面完全貼住手腕，再重測。",
        Language.EN to
            "Tighten the band so the back of the watch is fully in contact with your wrist," +
            "then measure again.",
        Language.KO to
            "밴드를 조금 더 조여 시계 뒷면이 손목에 완전히 밀착되게 한 뒤 다시 측정하세요.",
        ),
        "s_7dae3cf4" to mapOf(
        Language.ZH_HANS to
            "而且只在部分国家/地区与机型上提供。",
        Language.ZH_HANT to
            "而且只在部分國家/地區與機型上提供。",
        Language.EN to
            "and it is only available in some countries/regions and on some models.",
        Language.KO to
            "또한 일부 국가/지역과 일부 기종에서만 제공됩니다.",
        ),
        "s_7e273884" to mapOf(
        Language.ZH_HANS to
            "并与你自己近期记录的中位数比较 —— 纵向看趋势才有意义，横向比数字没有意义。",
        Language.ZH_HANT to
            "並與你自己近期記錄的中位數比較 —— 縱向看趨勢才有意義，橫向比數字沒有意義。",
        Language.EN to
            "and it is compared with the median of your own recent records — only a" +
            "longitudinal view of the trend is meaningful; comparing numbers across people is" +
            "not.",
        Language.KO to
            "본인의 최근 기록 중앙값과 비교합니다 — 종적으로 추세를 보는 것만 의미가 있으며, 사람 간 숫자 비교는 의미가 없습니다.",
        ),
        "s_7e561ca2" to mapOf(
        Language.ZH_HANS to
            "读不到前一日的步数（那天可能没带手机/手表），「前一日的活动」未参与",
        Language.ZH_HANT to
            "讀不到前一日的步數（那天可能沒帶手機/手錶），「前一日的活動」未參與",
        Language.EN to
            "The previous day's steps could not be read (the phone/watch may not have been" +
            "carried that day), so \"Previous day's activity\" did not participate",
        Language.KO to
            "전날 걸음 수를 읽을 수 없어(그날 휴대폰/시계를 소지하지 않았을 수 있습니다) '전날의 활동'이 참여하지 않았습니다",
        ),
        "s_7ebfb967" to mapOf(
        Language.ZH_HANS to
            "本次未判定",
        Language.ZH_HANT to
            "本次未判定",
        Language.EN to
            "Not determined this time",
        Language.KO to
            "이번에는 판정되지 않았습니다",
        ),
        "s_7ed62678" to mapOf(
        Language.ZH_HANS to
            "这个数据由三星健康监测器（另一个 App）产生，需要在那里开启「心律不齐通知」并",
        Language.ZH_HANT to
            "這個資料由三星健康監測器（另一個 App）產生，需要在那裡開啟「心律不齊通知」並",
        Language.EN to
            "This data is produced by Samsung Health Monitor (a separate app); you must enable" +
            "\"Irregular Rhythm Notifications\" there and ",
        Language.KO to
            "이 데이터는 Samsung Health Monitor(별도 앱)에서 생성되며, 그곳에서 '불규칙 심장 박동 알림'을 켜고 ",
        ),
        "s_7f3df8c2" to mapOf(
        Language.ZH_HANS to
            "基础代谢率",
        Language.ZH_HANT to
            "基礎代謝率",
        Language.EN to
            "Basal Metabolic Rate",
        Language.KO to
            "기초 대사율",
        ),
        "s_7f4ea8ac" to mapOf(
        Language.ZH_HANS to
            "多次测量超时(-6)",
        Language.ZH_HANT to
            "多次測量逾時(-6)",
        Language.EN to
            "Repeated measurement timeouts (-6)",
        Language.KO to
            "여러 번 측정 시간 초과(-6)",
        ),
        "s_81fe396e" to mapOf(
        Language.ZH_HANS to
            "未检出心跳波形，请确认手指按住表冠并保持不动",
        Language.ZH_HANT to
            "未檢出心跳波形，請確認手指按住錶冠並保持不動",
        Language.EN to
            "No heartbeat waveform detected; make sure your finger presses the crown and stays" +
            "still",
        Language.KO to
            "심박 파형이 감지되지 않았습니다. 손가락으로 크라운을 누른 채 움직이지 마세요",
        ),
        "s_82cd2d7c" to mapOf(
        Language.ZH_HANS to
            "步数与时段覆盖，都是不随个人而变的人群参考线。",
        Language.ZH_HANT to
            "步數與時段涵蓋，都是不隨個人而變的人群參考線。",
        Language.EN to
            "and step counts and slot coverage are all population reference lines that do not" +
            "vary from person to person.",
        Language.KO to
            "걸음 수와 구간 커버리지는 모두 개인에 따라 변하지 않는 인구 집단 기준선입니다.",
        ),
        "s_84817695" to mapOf(
        Language.ZH_HANS to
            "中指压在 2 点钟键、无名指压在 4 点钟键，两根手指全程都要压住。",
        Language.ZH_HANT to
            "中指壓在 2 點鐘鍵、無名指壓在 4 點鐘鍵，兩根手指全程都要壓住。",
        Language.EN to
            "Press your middle finger on the 2 o'clock button and your ring finger on the 4" +
            "o'clock button; keep both pressed the whole time.",
        Language.KO to
            "중지는 2시 버튼, 약지는 4시 버튼에 대고 두 손가락 모두 처음부터 끝까지 누르고 계세요.",
        ),
        "s_853419c2" to mapOf(
        Language.ZH_HANS to
            "只记了开始日、没记结束日的记录不参与经期平均长度的统计（不拿默认值顶替），",
        Language.ZH_HANT to
            "只記了開始日、沒記結束日的記錄不參與經期平均長度的統計（不拿預設值頂替），",
        Language.EN to
            "Records with only a start date and no end date do not enter the average" +
            "period-length statistic (no default value is substituted), ",
        Language.KO to
            "시작일만 있고 종료일이 없는 기록은 월경 평균 길이 통계에 포함되지 않습니다(기본값으로 대체하지 않습니다). ",
        ),
        "s_85a16834" to mapOf(
        Language.ZH_HANS to
            "7 小时的睡眠下限、95%/88% 的血氧界线、±0.3℃ 的皮温波动、24:00 这条线、",
        Language.ZH_HANT to
            "7 小時的睡眠下限、95%/88% 的血氧界線、±0.3℃ 的皮溫波動、24:00 這條線、",
        Language.EN to
            "the 7-hour lower bound for sleep, the 95%/88% blood-oxygen thresholds, the ±0.3°C" +
            "skin-temperature swing, the 24:00 line, ",
        Language.KO to
            "수면 하한 7시간, 95%/88% 혈중 산소 경계, ±0.3°C 피부 온도 변동, 24:00 기준선, ",
        ),
        "s_85f4f51c" to mapOf(
        Language.ZH_HANS to
            "腕式光学血氧在这个水平上与「没戴好」无法区分，留着会被当成极重度事件。",
        Language.ZH_HANT to
            "腕式光學血氧在這個水平上與「沒戴好」無法區分，留著會被當成極重度事件。",
        Language.EN to
            "At this level, wrist-based optical blood oxygen cannot be distinguished from \"not" +
            "worn properly\", and keeping it would treat it as an extremely severe event.",
        Language.KO to
            "이 수준의 손목형 광학 혈중 산소는 '제대로 착용하지 않음'과 구분할 수 없어, 남겨 두면 극중증 이벤트로 처리됩니다.",
        ),
        "s_866396c9" to mapOf(
        Language.ZH_HANS to
            "而且要那个 App 把记录写进三星健康。所以本机没有数据是正常的 —— ",
        Language.ZH_HANT to
            "而且要那個 App 把紀錄寫進三星健康。所以本機沒有資料是正常的 —— ",
        Language.EN to
            "and that app must write the records into Samsung Health. So it is normal for this" +
            "device to have no data — ",
        Language.KO to
            "그 앱이 기록을 Samsung Health에 써 넣어야 합니다. 따라서 이 기기에 데이터가 없는 것은 정상입니다 — ",
        ),
        "s_86b2f535" to mapOf(
        Language.ZH_HANS to
            "心率变异性",
        Language.ZH_HANT to
            "心率變異性",
        Language.EN to
            "Heart Rate Variability",
        Language.KO to
            "심박수 변이도",
        ),
        "s_87472bd7" to mapOf(
        Language.ZH_HANS to
            "通常是刚进出冷热环境、或表带没有贴合皮肤",
        Language.ZH_HANT to
            "通常是剛進出冷熱環境、或錶帶沒有貼合皮膚",
        Language.EN to
            "This is usually because you just moved between hot and cold environments, or the" +
            "band is not in contact with the skin",
        Language.KO to
            "보통 냉온 환경을 막 드나들었거나 밴드가 피부에 밀착되지 않았기 때문입니다",
        ),
        "s_877710eb" to mapOf(
        Language.ZH_HANS to
            "更早窗口的心率无效",
        Language.ZH_HANT to
            "更早視窗的心率無效",
        Language.EN to
            "The heart rate of the earlier window is invalid",
        Language.KO to
            "이전 구간의 심박수가 유효하지 않습니다",
        ),
        "s_87d2f224" to mapOf(
        Language.ZH_HANS to
            "前者比的是绝对值上晚不晚。",
        Language.ZH_HANT to
            "前者比的是絕對值上晚不晚。",
        Language.EN to
            "whereas the former compares how late it is in absolute terms.",
        Language.KO to
            "전자는 절대적인 시각으로 얼마나 늦었는지를 비교합니다.",
        ),
        "s_88992735" to mapOf(
        Language.ZH_HANS to
            "窦性心律",
        Language.ZH_HANT to
            "竇性心律",
        Language.EN to
            "Sinus rhythm",
        Language.KO to
            "동성 리듬",
        ),
        "s_88e58e1d" to mapOf(
        Language.ZH_HANS to
            "相隔太近的两组数差的只是昼夜相位，不是恢复状态",
        Language.ZH_HANT to
            "相隔太近的兩組數差的只是晝夜相位，不是恢復狀態",
        Language.EN to
            "Two sets of numbers too close together differ only in circadian phase, not in" +
            "recovery state",
        Language.KO to
            "너무 가까운 두 그룹의 수치는 일주기 위상만 다를 뿐 회복 상태가 다릅니다",
        ),
        "s_89452404" to mapOf(
        Language.ZH_HANS to
            "手表的 PPG 测不出血糖，这个数据只能来自血糖仪或连续血糖仪（CGM），",
        Language.ZH_HANT to
            "手錶的 PPG 測不出血糖，這個資料只能來自血糖機或連續血糖機（CGM），",
        Language.EN to
            "A watch's PPG cannot measure blood glucose; this data can only come from a blood" +
            "glucose meter or a continuous glucose monitor (CGM), ",
        Language.KO to
            "시계의 PPG로는 혈당을 측정할 수 없습니다. 이 데이터는 혈당계 또는 연속혈당측정기(CGM)에서만 나올 수 있으며, ",
        ),
        "s_8989f03b" to mapOf(
        Language.ZH_HANS to
            "与平时基本一致",
        Language.ZH_HANT to
            "與平時基本一致",
        Language.EN to
            "About the same as usual",
        Language.KO to
            "평소와 거의 같음",
        ),
        "s_8a322511" to mapOf(
        Language.ZH_HANS to
            "⚠ 注意：这些条件不能让核心体温推算变得可用 —— 那一项需要室温，",
        Language.ZH_HANT to
            "⚠ 注意：這些條件不能讓核心體溫推算變得可用 —— 那一項需要室溫，",
        Language.EN to
            "⚠ Note: these conditions cannot make core-temperature estimation usable — that" +
            "item needs room temperature, ",
        Language.KO to
            "⚠ 주의: 이 조건들이 심부 체온 추정을 쓸 수 있게 해 주지는 않습니다 — 그 항목에는 실내 온도가 필요하며 ",
        ),
        "s_8aaf0652" to mapOf(
        Language.ZH_HANS to
            "24:00 前入睡满分（早睡不扣）；前一日的活动达到你设的步数目标得满分；",
        Language.ZH_HANT to
            "24:00 前入睡滿分（早睡不扣）；前一日的活動達到你設的步數目標得滿分；",
        Language.EN to
            "falling asleep before 24:00 scores full marks (going to bed early is not" +
            "penalized); the previous day's activity reaching your step goal scores full marks;",
        Language.KO to
            "24:00 이전에 잠들면 만점(일찍 자는 것은 감점 없음); 전날의 활동이 설정한 걸음 목표에 도달하면 만점; ",
        ),
        "s_8ab7e639" to mapOf(
        Language.ZH_HANS to
            "蓝牙: 未启动",
        Language.ZH_HANT to
            "藍牙：未啟動",
        Language.EN to
            "Bluetooth: not started",
        Language.KO to
            "블루투스: 시작되지 않음",
        ),
        "s_8af5e886" to mapOf(
        Language.ZH_HANS to
            "上午加餐",
        Language.ZH_HANT to
            "上午加餐",
        Language.EN to
            "Morning snack",
        Language.KO to
            "오전 간식",
        ),
        "s_8b183621" to mapOf(
        Language.ZH_HANS to
            "下午加餐",
        Language.ZH_HANT to
            "下午加餐",
        Language.EN to
            "Afternoon snack",
        Language.KO to
            "오후 간식",
        ),
        "s_8b6b05b4" to mapOf(
        Language.ZH_HANS to
            "有效数据不足 5 秒，请重新测量",
        Language.ZH_HANT to
            "有效資料不足 5 秒，請重新測量",
        Language.EN to
            "Less than 5 seconds of valid data; please measure again",
        Language.KO to
            "유효 데이터가 5초 미만입니다. 다시 측정하세요",
        ),
        "s_8bc7f497" to mapOf(
        Language.ZH_HANS to
            "比平时低",
        Language.ZH_HANT to
            "比平時低",
        Language.EN to
            "Lower than usual",
        Language.KO to
            "평소보다 낮음",
        ),
        "s_8bc84021" to mapOf(
        Language.ZH_HANS to
            "比平时高",
        Language.ZH_HANT to
            "比平時高",
        Language.EN to
            "Higher than usual",
        Language.KO to
            "평소보다 높음",
        ),
        "s_8c17b40a" to mapOf(
        Language.ZH_HANS to
            "不足 1 分钟",
        Language.ZH_HANT to
            "不足 1 分鐘",
        Language.EN to
            "Less than 1 min",
        Language.KO to
            "1분 미만",
        ),
        "s_8c988480" to mapOf(
        Language.ZH_HANS to
            "基线不成立的那个数没有意义 —— 宁可先不给",
        Language.ZH_HANT to
            "基線不成立的那個數沒有意義 —— 寧可先不給",
        Language.EN to
            "and a number whose baseline does not hold is meaningless — better to withhold it" +
            "for now",
        Language.KO to
            "기준선이 성립하지 않는 수치는 의미가 없습니다 — 차라리 당분간 제공하지 않는 편이 낫습니다",
        ),
        "s_8cb906db" to mapOf(
        Language.ZH_HANS to
            "走动中的逐拍间期反映步频而不是自主神经张力，这次数据不能用来评分；",
        Language.ZH_HANT to
            "走動中的逐拍間期反映步頻而不是自律神經張力，這次資料不能用來評分；",
        Language.EN to
            "Beat-to-beat intervals while walking reflect step cadence rather than autonomic" +
            "tone, so this data cannot be used for scoring; ",
        Language.KO to
            "걷는 중의 박동 간격은 자율신경 긴장도가 아니라 걸음 빈도를 반영하므로 이 데이터는 점수에 사용할 수 없습니다. ",
        ),
        "s_8d9018d3" to mapOf(
        Language.ZH_HANS to
            "生物组织对高频电流的通路比对低频多（低频只能走细胞外液），所以阻抗必然随频率升高而下降，",
        Language.ZH_HANT to
            "生物組織對高頻電流的通路比對低頻多（低頻只能走細胞外液），所以阻抗必然隨頻率升高而下降，",
        Language.EN to
            "Biological tissue offers more pathways for high-frequency current than for" +
            "low-frequency current (low frequency can only travel through extracellular fluid)," +
            "so impedance must fall as frequency rises, ",
        Language.KO to
            "생체 조직은 저주파 전류보다 고주파 전류에 더 많은 통로를 제공하므로(저주파는 세포외액만 통과할 수 있음) 주파수가 높아지면 임피던스가 반드시" +
            "낮아집니다. ",
        ),
        "s_8e2841fb" to mapOf(
        Language.ZH_HANS to
            "数据来自三星健康睡眠期间每分钟的血氧记录。",
        Language.ZH_HANT to
            "資料來自三星健康睡眠期間每分鐘的血氧記錄。",
        Language.EN to
            "The data comes from Samsung Health's per-minute blood-oxygen records during sleep.",
        Language.KO to
            "데이터는 Samsung Health의 수면 중 분 단위 혈중 산소 기록에서 옵니다.",
        ),
        "s_8e6f0199" to mapOf(
        Language.ZH_HANS to
            "三个分量的权重（HRV 60% / 静息心率 25% / 皮温−环境温 15%）是按「哪个量更直接、更不易被",
        Language.ZH_HANT to
            "三個分量的權重（HRV 60% / 靜息心率 25% / 皮溫−環境溫 15%）是按「哪個量更直接、更不易被",
        Language.EN to
            "The weights of the three components (HRV 60% / resting heart rate 25% / skin temp" +
            "− ambient temp 15%) are assigned on the basis of \"which quantity is more direct" +
            "and less easily ",
        Language.KO to
            "세 성분의 가중치(HRV 60% / 안정 심박수 25% / 피부 온도 − 환경 온도 15%)는 '어떤 양이 더 직접적이고 ",
        ),
        "s_8fd626da" to mapOf(
        Language.ZH_HANS to
            "活动持续性",
        Language.ZH_HANT to
            "活動持續性",
        Language.EN to
            "Activity spread",
        Language.KO to
            "활동 지속성",
        ),
        "s_904df289" to mapOf(
        Language.ZH_HANS to
            "所以这个温差没有物理含义 —— 核心体温推算在本机佩戴状态下给不出可信值。",
        Language.ZH_HANT to
            "所以這個溫差沒有物理含義 —— 核心體溫推算在本機佩戴狀態下給不出可信值。",
        Language.EN to
            "so this temperature difference has no physical meaning — core-temperature" +
            "estimation cannot give a trustworthy value while worn on this device.",
        Language.KO to
            "따라서 이 온도차는 물리적 의미가 없습니다 — 이 기기를 착용한 상태에서는 심부 체온 추정이 신뢰할 만한 값을 낼 수 없습니다.",
        ),
        "s_910466d8" to mapOf(
        Language.ZH_HANS to
            "模型需要的是室温，本机给不出，所以核心体温推算在佩戴状态下是",
        Language.ZH_HANT to
            "模型需要的是室溫，本機給不出，所以核心體溫推算在佩戴狀態下是",
        Language.EN to
            "The model needs room temperature, which this device cannot provide, so" +
            "core-temperature estimation is, when worn, ",
        Language.KO to
            "모델에 필요한 것은 실내 온도인데 이 기기에서는 제공할 수 없으므로, 착용 상태에서 심부 체온 추정은 ",
        ),
        "s_910cc97c" to mapOf(
        Language.ZH_HANS to
            "检测到移动，请保持静止…",
        Language.ZH_HANT to
            "偵測到移動，請保持靜止…",
        Language.EN to
            "Movement detected; stay still…",
        Language.KO to
            "움직임이 감지되었습니다. 정지해 주세요…",
        ),
        "s_9284bf3f" to mapOf(
        Language.ZH_HANS to
            "标准（5 分钟以上）",
        Language.ZH_HANT to
            "標準（5 分鐘以上）",
        Language.EN to
            "Standard (5 minutes or more)",
        Language.KO to
            "표준(5분 이상)",
        ),
        "s_92addbee" to mapOf(
        Language.ZH_HANS to
            "中度活动",
        Language.ZH_HANT to
            "中度活動",
        Language.EN to
            "Moderate activity",
        Language.KO to
            "중간 강도 활동",
        ),
        "s_9322a64e" to mapOf(
        Language.ZH_HANS to
            "④ 洗澡、从室外进室内之后等 30 分钟，让体表温度回稳。",
        Language.ZH_HANT to
            "④ 洗澡、從室外進室內之後等 30 分鐘，讓體表溫度回穩。",
        Language.EN to
            "④ after a shower or coming indoors from outside, wait 30 minutes for surface" +
            "temperature to stabilize.",
        Language.KO to
            "④ 샤워 후나 야외에서 실내로 들어온 뒤에는 30분을 기다려 체표 온도가 안정되게 하세요.",
        ),
        "s_93331838" to mapOf(
        Language.ZH_HANS to
            "一个看着正常、但与体成分页对不上的数。所以体成分仍读取三星算好的结果，",
        Language.ZH_HANT to
            "一個看著正常、但與體成分頁對不上的數。所以體成分仍讀取三星算好的結果，",
        Language.EN to
            "a number that looks normal but does not match the body-composition page. So body" +
            "composition still reads Samsung's computed result, ",
        Language.KO to
            "정상처럼 보이지만 체성분 페이지와 맞지 않는 값이 나옵니다. 따라서 체성분은 여전히 삼성이 계산한 결과를 읽어오며 ",
        ),
        "s_933c0c84" to mapOf(
        Language.ZH_HANS to
            "连接服务失败：SDK 调用异常",
        Language.ZH_HANT to
            "連線服務失敗：SDK 調用異常",
        Language.EN to
            "Failed to connect the service: SDK call error",
        Language.KO to
            "서비스 연결 실패: SDK 호출 오류",
        ),
        "s_934ed295" to mapOf(
        Language.ZH_HANS to
            "这次没能连上公告服务器，也可能是网络不可用。\\n",
        Language.ZH_HANT to
            "這次沒能連上公告伺服器，也可能是網路不可用。\\n",
        Language.EN to
            "Could not connect to the announcement server this time; the network may also be" +
            "unavailable.\\n",
        Language.KO to
            "이번에는 공지 서버에 연결하지 못했습니다. 네트워크를 사용할 수 없을 수도 있습니다.\\n",
        ),
        "s_9428b2f3" to mapOf(
        Language.ZH_HANS to
            "本软件的血压数值是基于校准样本的统计预估，不是一次测量，也不能用于诊断高血压、调整用药或任何医疗决定，请以袖套血压计的读数为准。",
        Language.ZH_HANT to
            "本軟體的血壓數值是基於校準樣本的統計預估，不是一次測量，也不能用於診斷高血壓、調整用藥或任何醫療決定，請以袖套血壓計的讀數為準。",
        Language.EN to
            "The blood pressure values in this app are statistical estimates based on your" +
            "calibration samples. They are not measurements, and must not be used to diagnose" +
            "hypertension, to adjust medication, or for any medical decision. Use a cuff blood" +
            "pressure monitor for the actual reading.",
        Language.KO to
            "이 앱의 혈압 수치는 교정 샘플을 이용한 통계적 예측치이며 측정값이 아닙니다. 고혈압 진단, 약물 조절, 어떠한 의료적 결정에도 사용할 수 없으며," +
            "실제 값은 상박 혈압계로 측정하십시오.",
        ),
        "s_949f46f3" to mapOf(
        Language.ZH_HANS to
            "校准样本不足时本软件不会给出血压数值，这是刻意设计的，不是不够完善。",
        Language.ZH_HANT to
            "校準樣本不足時本軟體不會給出血壓數值，這是刻意設計的，不是不夠完善。",
        Language.EN to
            "When there are not enough calibration samples, this app deliberately gives no" +
            "blood pressure value. This is by design, not a missing feature.",
        Language.KO to
            "교정 샘플이 부족하면 이 앱은 혈압 수치를 제시하지 않습니다. 미완성 기능이 아니라 의도된 설계입니다",
        ),
        "s_94cb17ba" to mapOf(
        Language.ZH_HANS to
            "还受体位、睡眠、咖啡因影响。所以这里只给数值与信度分级，",
        Language.ZH_HANT to
            "還受體位、睡眠、咖啡因影響。所以這裡只給數值與信度分級，",
        Language.EN to
            "and it is also affected by posture, sleep, and caffeine. So only the value and a" +
            "confidence tier are given here, ",
        Language.KO to
            "자세, 수면, 카페인의 영향도 받습니다. 따라서 여기서는 값과 신뢰도 등급만 제공하고 ",
        ),
        "s_94f3b1f4" to mapOf(
        Language.ZH_HANS to
            "它和上面的「氧减指数」不是同一个东西：氧减指数是我们自己从夜间血氧序列算的",
        Language.ZH_HANT to
            "它和上面的「氧減指數」不是同一個東西：氧減指數是我們自己從夜間血氧序列算的",
        Language.EN to
            "It is not the same thing as the \"Oxygen Desaturation Index\" above: the ODI is" +
            "computed by us from the overnight blood-oxygen series ",
        Language.KO to
            "위의 '산소탈포화지수'와는 다른 것입니다. ODI는 저희가 야간 혈중 산소 시계열로 직접 계산한 값이며 ",
        ),
        "s_94fa0205" to mapOf(
        Language.ZH_HANS to
            "记录还太少，暂时只显示绝对值。连续测几次后这里会给出与你自己的对比",
        Language.ZH_HANT to
            "記錄還太少，暫時只顯示絕對值。連續測幾次後這裡會給出與你自己的對比",
        Language.EN to
            "There are too few records, so only absolute values are shown for now. After" +
            "several consecutive measurements a comparison with yourself will appear here ",
        Language.KO to
            "기록이 너무 적어 현재는 절대값만 표시합니다. 몇 번 연속 측정하면 본인과의 비교가 여기에 표시됩니다 ",
        ),
        "s_95ccdd38" to mapOf(
        Language.ZH_HANS to
            "推算方式：T核心 = T皮温 + α ×（T皮温 − T室温）。这是稳态传热的解（体内到皮肤的热流 ",
        Language.ZH_HANT to
            "推算方式：T核心 = T皮溫 + α ×（T皮溫 − T室溫）。這是穩態傳熱的解（體內到皮膚的熱流 ",
        Language.EN to
            "Estimation method: T_core = T_skin + α × (T_skin − T_room). This is the solution" +
            "for steady-state heat transfer (heat flow from inside the body to the skin ",
        Language.KO to
            "추정 방식: T심부 = T피부 + α × (T피부 − T실내). 이는 정상 상태 열전달의 해입니다(체내에서 피부로의 열류 ",
        ),
        "s_982e4a33" to mapOf(
        Language.ZH_HANS to
            "它们大概率是静息（测心电需要按住表冠不动），但不保证",
        Language.ZH_HANT to
            "它們大概率是靜息（測心電需要按住錶冠不動），但不保證",
        Language.EN to
            "They are most likely resting (measuring ECG requires holding the crown still), but" +
            "this is not guaranteed",
        Language.KO to
            "대부분 안정 상태일 것입니다(심전도 측정은 크라운을 누르고 움직이지 않아야 합니다). 다만 보장되지는 않습니다",
        ),
        "s_98ad3db3" to mapOf(
        Language.ZH_HANS to
            "公告内容里没有 announcements 数组（顶层要有 { }）",
        Language.ZH_HANT to
            "公告內容裡沒有 announcements 陣列（頂層要有 { }）",
        Language.EN to
            "The announcement content has no announcements array (the top level must have { })",
        Language.KO to
            "공지 내용에 announcements 배열이 없습니다(최상위에 { } 필요)",
        ),
        "s_98e5c4fc" to mapOf(
        Language.ZH_HANS to
            "走动中的逐拍间期反映的是步频而不是自主神经张力，这次数据不能当 HRV 用；",
        Language.ZH_HANT to
            "走動中的逐拍間期反映的是步頻而不是自律神經張力，這次資料不能當 HRV 用；",
        Language.EN to
            "Beat-to-beat intervals while walking reflect step cadence rather than autonomic" +
            "tone, so this data cannot be used as HRV; ",
        Language.KO to
            "걷는 중의 박동 간격은 자율신경 긴장도가 아니라 걸음 빈도를 반영하므로 이 데이터는 HRV로 쓸 수 없습니다. ",
        ),
        "s_991f45ca" to mapOf(
        Language.ZH_HANS to
            "这通常意味着电极接触不良或测量中动了，本次结果不可信",
        Language.ZH_HANT to
            "這通常意味著電極接觸不良或測量中動了，本次結果不可信",
        Language.EN to
            "this usually means poor electrode contact or movement during the measurement, so" +
            "this result is unreliable",
        Language.KO to
            "이는 보통 전극 접촉 불량이나 측정 중 움직임을 뜻하므로 이번 결과는 신뢰할 수 없습니다",
        ),
        "s_99230581" to mapOf(
        Language.ZH_HANS to
            "下了一个它没下过的结论。三星那份又本来就在 HC 里，回填等于重复。",
        Language.ZH_HANT to
            "下了一個它沒下過的結論。三星那份又本來就在 HC 裡，回填等於重複。",
        Language.EN to
            "reach a conclusion on its behalf that it never reached. And Samsung's copy is" +
            "already in HC, so backfilling would duplicate it.",
        Language.KO to
            "그것이 내린 적 없는 결론을 대신 내리는 것입니다. 게다가 삼성의 사본은 이미 HC에 있어 되쓰기는 중복입니다.",
        ),
        "s_992d0ea3" to mapOf(
        Language.ZH_HANS to
            "加速度采样 —— 我们的加速度只在打开 App 期间才采，拿来算「今天动了多少」会系统性低估。",
        Language.ZH_HANT to
            "加速度取樣 —— 我們的加速度只在開啟 App 期間才採，拿來算「今天動了多少」會系統性低估。",
        Language.EN to
            "accelerometer sampling — our accelerometer only samples while the app is open, so" +
            "using it to compute \"how much I moved today\" would systematically underestimate.",
        Language.KO to
            "가속도 샘플링이 아닙니다 — 저희 가속도는 앱이 열려 있는 동안에만 수집하므로 '오늘 얼마나 움직였는가'를 계산하는 데 쓰면 체계적으로 과소평가됩니다.",
        ),
        "s_9a19fb04" to mapOf(
        Language.ZH_HANS to
            "这个通道测的不是室温，而是随手表自身温度变化的一个量 —— ",
        Language.ZH_HANT to
            "這個通道測的不是室溫，而是隨手錶自身溫度變化的一個量 —— ",
        Language.EN to
            "This channel does not measure room temperature but a quantity that changes with" +
            "the watch's own temperature — ",
        Language.KO to
            "이 채널은 실내 온도가 아니라 시계 자체 온도에 따라 변하는 양을 측정합니다 — ",
        ),
        "s_9a26637f" to mapOf(
        Language.ZH_HANS to
            "风筝冲浪",
        Language.ZH_HANT to
            "風箏衝浪",
        Language.EN to
            "Kitesurfing",
        Language.KO to
            "카이트서핑",
        ),
        "s_9a9c8c51" to mapOf(
        Language.ZH_HANS to
            "从 Health Connect 读取的经期记录只用于本机统计，本应用不往 Health Connect 写入经期数据",
        Language.ZH_HANT to
            "從 Health Connect 讀取的經期記錄只用於本機統計，本應用不往 Health Connect 寫入經期資料",
        Language.EN to
            "Period records read from Health Connect are used only for on-device statistics;" +
            "this app does not write period data to Health Connect",
        Language.KO to
            "Health Connect에서 읽은 월경 기록은 기기 내 통계에만 사용되며, 이 앱은 Health Connect에 월경 데이터를 쓰지 않습니다",
        ),
        "s_9aaa0474" to mapOf(
        Language.ZH_HANS to
            "黄体期 14 天是人群平均，个体在 10~16 天之间，所以这个窗口只是粗略估计",
        Language.ZH_HANT to
            "黃體期 14 天是人群平均，個體在 10~16 天之間，所以這個視窗只是粗略估計",
        Language.EN to
            "The 14-day luteal phase is a population average; individuals range from 10 to 16" +
            "days, so this window is only a rough estimate",
        Language.KO to
            "황체기 14일은 인구 평균이며 개인차는 10~16일이므로 이 구간은 개략적인 추정일 뿐입니다",
        ),
        "s_9ade3097" to mapOf(
        Language.ZH_HANS to
            "请检查历史记录，或再完整记一次",
        Language.ZH_HANT to
            "請檢查歷史記錄，或再完整記一次",
        Language.EN to
            "please check the history, or log one more complete record",
        Language.KO to
            "기록을 확인하거나 한 번 더 온전히 기록해 주세요",
        ),
        "s_9b050f0e" to mapOf(
        Language.ZH_HANS to
            "间隔过长多为漏记（实际是两次经期），过短多为同一段经期被记了两次。",
        Language.ZH_HANT to
            "間隔過長多為漏記（實際是兩次經期），過短多為同一段經期被記了兩次。",
        Language.EN to
            "An overly long interval usually means a missed entry (actually two periods); an" +
            "overly short one usually means the same period logged twice.",
        Language.KO to
            "간격이 너무 길면 대개 누락(실제로는 두 번의 월경), 너무 짧으면 같은 월경을 두 번 기록한 경우입니다.",
        ),
        "s_9bcba0a0" to mapOf(
        Language.ZH_HANS to
            "这一晚没有血氧序列数据",
        Language.ZH_HANT to
            "這一晚沒有血氧序列資料",
        Language.EN to
            "There is no blood-oxygen series data for this night",
        Language.KO to
            "이 날 밤에는 혈중 산소 시계열 데이터가 없습니다",
        ),
        "s_9bda4e04" to mapOf(
        Language.ZH_HANS to
            "这么短的一段算出来的指数没有意义",
        Language.ZH_HANT to
            "這麼短的一段算出來的指數沒有意義",
        Language.EN to
            "An index computed from such a short segment is not meaningful",
        Language.KO to
            "이렇게 짧은 구간에서 계산한 지수는 의미가 없습니다",
        ),
        "s_9c2b2915" to mapOf(
        Language.ZH_HANS to
            "这一路没有采到脉搏波数据。",
        Language.ZH_HANT to
            "這一路沒有採到脈搏波資料。",
        Language.EN to
            "No pulse-wave data was captured on this signal.",
        Language.KO to
            "이 갈래에서는 맥파 데이터가 수집되지 않았습니다.",
        ),
        "s_9c79d94a" to mapOf(
        Language.ZH_HANS to
            "收缩压（高的那个）必须大于舒张压（低的那个）—— ",
        Language.ZH_HANT to
            "收縮壓（高的那個）必須大於舒張壓（低的那個）—— ",
        Language.EN to
            "Systolic pressure (the higher one) must be greater than diastolic pressure (the" +
            "lower one) — ",
        Language.KO to
            "수축기압(높은 쪽)은 이완기압(낮은 쪽)보다 커야 합니다 — ",
        ),
        "s_9d3b0aad" to mapOf(
        Language.ZH_HANS to
            "先确认睡觉时手表戴在手上，并且三星健康里有这一天的睡眠记录",
        Language.ZH_HANT to
            "先確認睡覺時手錶戴在手上，並且三星健康裡有這一天的睡眠記錄",
        Language.EN to
            "first make sure the watch was on your wrist while sleeping and that Samsung Health" +
            "has a sleep record for that day",
        Language.KO to
            "먼저 잠잘 때 시계를 손목에 착용했는지, Samsung Health에 그날의 수면 기록이 있는지 확인해 주세요",
        ),
        "s_9d9e79e5" to mapOf(
        Language.ZH_HANS to
            "③ 手腕干燥，没有水或酒精（蒸发会让皮温偏低）；",
        Language.ZH_HANT to
            "③ 手腕乾燥，沒有水或酒精（蒸發會讓皮溫偏低）；",
        Language.EN to
            "③ keep the wrist dry, with no water or alcohol (evaporation lowers skin" +
            "temperature); ",
        Language.KO to
            "③ 손목은 물기나 알코올이 없는 건조한 상태로 유지하세요(증발이 피부 온도를 낮춥니다). ",
        ),
        "s_9db1c090" to mapOf(
        Language.ZH_HANS to
            "同一个人日间变异约 5~8%，所以平均心率的一个「显著偏离单位」取 6%；",
        Language.ZH_HANT to
            "同一個人日間變異約 5~8%，所以平均心率的一個「顯著偏離單位」取 6%；",
        Language.EN to
            "day-to-day variation within one person is about 5–8%, so one \"significant" +
            "deviation unit\" for average heart rate is taken as 6%; ",
        Language.KO to
            "동일인의 날짜 간 변동은 약 5~8%이므로 평균 심박수의 '유의한 편차 단위' 하나는 6%로 잡았습니다; ",
        ),
        "s_9df4ec16" to mapOf(
        Language.ZH_HANS to
            "PERMISSION_ERROR：传感器权限被拒绝",
        Language.ZH_HANT to
            "PERMISSION_ERROR：感測器權限被拒絕",
        Language.EN to
            "PERMISSION_ERROR: sensor permission denied",
        Language.KO to
            "PERMISSION_ERROR: 센서 권한이 거부되었습니다",
        ),
        "s_9e978bdb" to mapOf(
        Language.ZH_HANS to
            "采样率未知，无法把采样点数换算成时间。",
        Language.ZH_HANT to
            "取樣率未知，無法把取樣點數換算成時間。",
        Language.EN to
            "The sampling rate is unknown, so the number of samples cannot be converted into" +
            "time.",
        Language.KO to
            "샘플링 레이트를 알 수 없어 샘플 수를 시간으로 환산할 수 없습니다.",
        ),
        "s_9ea373bd" to mapOf(
        Language.ZH_HANS to
            "花样滑冰",
        Language.ZH_HANT to
            "花式滑冰",
        Language.EN to
            "Figure skating",
        Language.KO to
            "피겨 스케이트",
        ),
        "s_9eba88e8" to mapOf(
        Language.ZH_HANS to
            "睡眠场景改由「氧减指数 ODI」承担（见睡眠页）。",
        Language.ZH_HANT to
            "睡眠場景改由「氧減指數 ODI」承擔（見睡眠頁）。",
        Language.EN to
            "and the sleep scenario is handled instead by the \"Oxygen Desaturation Index" +
            "(ODI)\" (see the sleep page).",
        Language.KO to
            "수면 상황은 '산소탈포화지수(ODI)'가 담당합니다(수면 페이지 참고).",
        ),
        "s_a023d6a6" to mapOf(
        Language.ZH_HANS to
            "这一组是三星健康自己统计的活动量（步数/活动卡路里/活动时长/距离/爬楼），",
        Language.ZH_HANT to
            "這一組是三星健康自己統計的活動量（步數/活動卡路里/活動時長/距離/爬樓），",
        Language.EN to
            "This group is the activity Samsung Health tallies itself (steps / active calories" +
            "/ active time / distance / floors), ",
        Language.KO to
            "이 그룹은 Samsung Health가 자체 집계한 활동량입니다(걸음 수 / 활동 칼로리 / 활동 시간 / 거리 / 층수). ",
        ),
        "s_a112e43d" to mapOf(
        Language.ZH_HANS to
            "睡眠规律性",
        Language.ZH_HANT to
            "睡眠規律性",
        Language.EN to
            "Sleep regularity",
        Language.KO to
            "수면 규칙성",
        ),
        "s_a127948f" to mapOf(
        Language.ZH_HANS to
            "氧减事件：血氧相对此前 5 分钟的中位数下降 ≥3%，再回升到距基线 1% 以内算一次。",
        Language.ZH_HANT to
            "氧減事件：血氧相對此前 5 分鐘的中位數下降 ≥3%，再回升到距基線 1% 以內算一次。",
        Language.EN to
            "Desaturation event: blood oxygen drops by ≥3% relative to the median of the" +
            "preceding 5 minutes and then recovers to within 1% of baseline, counted as one" +
            "event.",
        Language.KO to
            "산소 저하 이벤트: 혈중 산소가 직전 5분의 중앙값 대비 ≥3% 떨어진 뒤 기준선의 1% 이내로 회복되면 1회로 셉니다.",
        ),
        "s_a15e2ff9" to mapOf(
        Language.ZH_HANS to
            "简体中文",
        Language.ZH_HANT to
            "簡體中文",
        Language.EN to
            "Simplified Chinese",
        Language.KO to
            "중국어 간체",
        ),
        "s_a182ad9e" to mapOf(
        Language.ZH_HANS to
            "本机不支持多频阻抗测量（能力列表里没有 MF_BIA_ON_DEMAND）",
        Language.ZH_HANT to
            "本機不支援多頻阻抗測量（能力列表裡沒有 MF_BIA_ON_DEMAND）",
        Language.EN to
            "Multi-frequency impedance measurement is not supported on this device" +
            "(MF_BIA_ON_DEMAND absent from the capability list)",
        Language.KO to
            "이 기기에서는 다중 주파수 임피던스 측정을 지원하지 않습니다(기능 목록에 MF_BIA_ON_DEMAND 없음)",
        ),
        "s_a1a3c44e" to mapOf(
        Language.ZH_HANS to
            "水肺潜水",
        Language.ZH_HANT to
            "水肺潛水",
        Language.EN to
            "Scuba diving",
        Language.KO to
            "스쿠버 다이빙",
        ),
        "s_a1c02dd1" to mapOf(
        Language.ZH_HANS to
            "十个分量的权重（睡眠时长 18% / 最低血氧 12% / 睡眠心率 12% / 前一日的活动 10% /",
        Language.ZH_HANT to
            "十個分量的權重（睡眠時長 18% / 最低血氧 12% / 睡眠心率 12% / 前一日的活動 10% /",
        Language.EN to
            "The weights of the ten components (sleep duration 18% / lowest blood oxygen 12% /" +
            "sleeping heart rate 12% / previous day's activity 10% / ",
        Language.KO to
            "열 개 성분의 가중치(수면 시간 18% / 최저 혈중 산소 12% / 수면 심박수 12% / 전날의 활동 10% / ",
        ),
        "s_a2a7026f" to mapOf(
        Language.ZH_HANS to
            "请用指腹贴紧表冠电极、保持手臂放松后重新测量",
        Language.ZH_HANT to
            "請用指腹貼緊錶冠電極、保持手臂放鬆後重新測量",
        Language.EN to
            "Press your fingertip firmly on the crown electrode, relax your arm, and measure" +
            "again",
        Language.KO to
            "손가락 지문을 크라운 전극에 밀착하고 팔의 힘을 뺀 뒤 다시 측정하세요",
        ),
        "s_a41fbf35" to mapOf(
        Language.ZH_HANS to
            "算不出来",
        Language.ZH_HANT to
            "算不出來",
        Language.EN to
            "Cannot compute",
        Language.KO to
            "계산할 수 없음",
        ),
        "s_a5625475" to mapOf(
        Language.ZH_HANS to
            "参照窗口的数值无效，无法比较",
        Language.ZH_HANT to
            "參照視窗的數值無效，無法比較",
        Language.EN to
            "The values in the reference window are invalid, so no comparison is possible",
        Language.KO to
            "참조 구간의 값이 유효하지 않아 비교할 수 없습니다",
        ),
        "s_a5ce4fba" to mapOf(
        Language.ZH_HANS to
            "公告顶层不是一个 JSON 对象",
        Language.ZH_HANT to
            "公告頂層不是一個 JSON 物件",
        Language.EN to
            "The announcement's top level is not a JSON object",
        Language.KO to
            "공지의 최상위가 JSON 객체가 아닙니다",
        ),
        "s_a5ed15ac" to mapOf(
        Language.ZH_HANS to
            "心率波动的那一项放宽到 25%（标准差本身更不稳）。",
        Language.ZH_HANT to
            "心率波動的那一項放寬到 25%（標準差本身更不穩）。",
        Language.EN to
            "for the heart-rate variability item it is relaxed to 25% (the standard deviation" +
            "itself is less stable).",
        Language.KO to
            "심박 변동 항목은 25%로 완화했습니다(표준편차 자체가 더 불안정하기 때문입니다).",
        ),
        "s_a5f911f6" to mapOf(
        Language.ZH_HANS to
            "手指皮肤太干",
        Language.ZH_HANT to
            "手指皮膚太乾",
        Language.EN to
            "Finger skin too dry",
        Language.KO to
            "손가락 피부가 너무 건조함",
        ),
        "s_a6019732" to mapOf(
        Language.ZH_HANS to
            "只给「检测到 / 未检测到 / 未判定」三态、没有程度也没有次数。",
        Language.ZH_HANT to
            "只給「偵測到 / 未偵測到 / 未判定」三態、沒有程度也沒有次數。",
        Language.EN to
            "which only gives the three states \"detected / not detected / not determined\"," +
            "with no severity and no count.",
        Language.KO to
            "'감지됨 / 감지되지 않음 / 미판정' 세 가지 상태만 제공하며 정도나 횟수는 없습니다.",
        ),
        "s_a62c0fbd" to mapOf(
        Language.ZH_HANS to
            "开始日不能晚于今天",
        Language.ZH_HANT to
            "開始日不能晚於今天",
        Language.EN to
            "The start date cannot be later than today",
        Language.KO to
            "시작일은 오늘보다 늦을 수 없습니다",
        ),
        "s_a632f926" to mapOf(
        Language.ZH_HANS to
            "高于常见静息范围",
        Language.ZH_HANT to
            "高於常見靜息範圍",
        Language.EN to
            "Above the usual resting range",
        Language.KO to
            "일반적인 안정 범위보다 높음",
        ),
        "s_a71c8b43" to mapOf(
        Language.ZH_HANS to
            "来自手机与手表的计步，不是本 App 采的 —— 我们只是把它读过来显示。",
        Language.ZH_HANT to
            "來自手機與手錶的計步，不是本 App 採的 —— 我們只是把它讀過來顯示。",
        Language.EN to
            "It comes from step counting on the phone and watch, not from this app — we only" +
            "read it and display it.",
        Language.KO to
            "휴대폰과 시계의 걸음 측정에서 나온 값이며 이 앱이 수집한 것이 아닙니다 — 저희는 읽어서 표시할 뿐입니다.",
        ),
        "s_a73756d7" to mapOf(
        Language.ZH_HANS to
            "水陆两项",
        Language.ZH_HANT to
            "水陸兩項",
        Language.EN to
            "Aquathlon",
        Language.KO to
            "아쿠아슬론",
        ),
        "s_a7c347bd" to mapOf(
        Language.ZH_HANS to
            "被跳过的记录仍会显示在列表里，方便核对",
        Language.ZH_HANT to
            "被跳過的記錄仍會顯示在列表裡，方便核對",
        Language.EN to
            "Skipped records are still shown in the list for easy checking",
        Language.KO to
            "건너뛴 기록도 목록에 그대로 표시되어 확인하기 쉽습니다",
        ),
        "s_a7ee00a0" to mapOf(
        Language.ZH_HANS to
            "没有可回填的源数据。",
        Language.ZH_HANT to
            "沒有可回填的來源資料。",
        Language.EN to
            "so there is no source data to backfill.",
        Language.KO to
            "따라서 되쓸 소스 데이터가 없습니다.",
        ),
        "s_a8256090" to mapOf(
        Language.ZH_HANS to
            "手指太干导不通电。稍微湿润一下手指，或换另一只手再试。",
        Language.ZH_HANT to
            "手指太乾導不通電。稍微濕潤一下手指，或換另一隻手再試。",
        Language.EN to
            "Dry fingers do not conduct. Moisten your fingers slightly, or try the other hand.",
        Language.KO to
            "손가락이 너무 건조하면 전기가 통하지 않습니다. 손가락을 약간 적시거나 다른 손으로 다시 시도하세요.",
        ),
        "s_a8818b13" to mapOf(
        Language.ZH_HANS to
            "并以这两项包络出区间宽度。",
        Language.ZH_HANT to
            "並以這兩項包絡出區間寬度。",
        Language.EN to
            "and these two are used to envelope the interval width.",
        Language.KO to
            "이 두 값으로 구간 폭을 포괄합니다.",
        ),
        "s_a962e6c5" to mapOf(
        Language.ZH_HANS to
            "本 App 不会替你编一个默认目标（那会变成「我们觉得你应该走一万步」）。",
        Language.ZH_HANT to
            "本 App 不會替你編一個預設目標（那會變成「我們覺得你應該走一萬步」）。",
        Language.EN to
            "this app will not invent a default goal for you (that would amount to telling you" +
            "what you \"should\" do).",
        Language.KO to
            "이 앱은 기본 목표를 임의로 만들지 않습니다(그것은 '우리가 당신은 만 보를 걸어야 한다고 생각한다'는 뜻이 되기 때문입니다).",
        ),
        "s_a98a53c0" to mapOf(
        Language.ZH_HANS to
            "这次记录不到 1 分钟，只能看相邻心跳的变化（RMSSD / pNN50）；",
        Language.ZH_HANT to
            "這次記錄不到 1 分鐘，只能看相鄰心跳的變化（RMSSD / pNN50）；",
        Language.EN to
            "This record is under 1 minute, so only the beat-to-beat variation can be looked at" +
            "(RMSSD / pNN50); ",
        Language.KO to
            "이 기록은 1분이 되지 않아 인접 박동의 변화(RMSSD / pNN50)만 볼 수 있습니다. ",
        ),
        "s_a99055e4" to mapOf(
        Language.ZH_HANS to
            "所以这个温差没有物理含义 —— 核心体温推算在本机属于结构性给不出",
        Language.ZH_HANT to
            "所以這個溫差沒有物理含義 —— 核心體溫推算在本機屬於結構性給不出",
        Language.EN to
            "so this temperature difference has no physical meaning — core-temperature" +
            "estimation is structurally impossible on this device ",
        Language.KO to
            "이 온도차는 물리적 의미가 없습니다 — 심부 체온 추정은 이 기기에서 구조적으로 불가능하며 ",
        ),
        "s_a9ed9a24" to mapOf(
        Language.ZH_HANS to
            "仰卧起坐",
        Language.ZH_HANT to
            "仰臥起坐",
        Language.EN to
            "Sit-ups",
        Language.KO to
            "윗몸일으키기",
        ),
        "s_aa7c0e8d" to mapOf(
        Language.ZH_HANS to
            "沙滩排球",
        Language.ZH_HANT to
            "沙灘排球",
        Language.EN to
            "Beach volleyball",
        Language.KO to
            "비치발리볼",
        ),
        "s_ac64abd5" to mapOf(
        Language.ZH_HANS to
            "输入只能用手表点检的绝对皮温（watch_temp）。三星健康那一项可能是相对个人基线的偏差，",
        Language.ZH_HANT to
            "輸入只能用手錶點檢的絕對皮溫（watch_temp）。三星健康那一項可能是相對個人基線的偏差，",
        Language.EN to
            "The only input usable is the absolute skin temperature from the watch's spot check" +
            "(watch_temp). Samsung Health's item may be a deviation relative to a personal" +
            "baseline, ",
        Language.KO to
            "입력은 시계 점검의 절대 피부 온도(watch_temp)만 사용할 수 있습니다. Samsung Health의 항목은 개인 기준선 대비 편차일 수 있어 ",
        ),
        "s_aca650a6" to mapOf(
        Language.ZH_HANS to
            "升→公斤要乘体水密度（≈0.99），那是一个近似换算 —— ",
        Language.ZH_HANT to
            "升→公斤要乘體水密度（≈0.99），那是一個近似換算 —— ",
        Language.EN to
            "Converting litres to kilograms requires multiplying by the density of body water" +
            "(≈0.99), which is an approximation — ",
        Language.KO to
            "리터에서 킬로그램으로 바꾸려면 체수분 밀도(≈0.99)를 곱해야 하는데 이는 근사 환산입니다 — ",
        ),
        "s_ad27eb8f" to mapOf(
        Language.ZH_HANS to
            "也不像绝对温度（三星那一侧口径未公开），量纲判不出来，该分量未参与",
        Language.ZH_HANT to
            "也不像絕對溫度（三星那一側口徑未公開），量綱判不出來，該分量未參與",
        Language.EN to
            "and it does not look like an absolute temperature either (Samsung's definition is" +
            "not published), so the dimension cannot be determined and this component did not" +
            "participate",
        Language.KO to
            "절대 온도로도 보이지 않아(삼성 측 기준이 공개되지 않음) 차원을 판단할 수 없으므로 이 성분은 참여하지 않았습니다",
        ),
        "s_ad32029e" to mapOf(
        Language.ZH_HANS to
            "这些在三星健康里本来就是三星的（或用户自己记的）。",
        Language.ZH_HANT to
            "這些在三星健康裡本來就是三星的（或使用者自己記的）。",
        Language.EN to
            "These are Samsung's own in Samsung Health to begin with (or recorded by the user).",
        Language.KO to
            "이것들은 애초에 Samsung Health에서 삼성의 것이거나(또는 사용자가 직접 기록한 것)입니다.",
        ),
        "s_ad4226c2" to mapOf(
        Language.ZH_HANS to
            "请尝试将手表移至手腕上方的位置并系紧表带。",
        Language.ZH_HANT to
            "請嘗試將手錶移至手腕上方的位置並繫緊錶帶。",
        Language.EN to
            "Try moving the watch higher up your wrist and tightening the strap.",
        Language.KO to
            "워치를 손목 위쪽으로 옮기고 스트랩을 조여 보세요.",
        ),
        "s_ad55fadf" to mapOf(
        Language.ZH_HANS to
            "十个分量各有刻度，全部可倒推：睡眠时长 7 小时 = 50 分、9 小时 = 100 分；",
        Language.ZH_HANT to
            "十個分量各有刻度，全部可倒推：睡眠時長 7 小時 = 50 分、9 小時 = 100 分；",
        Language.EN to
            "Each of the ten components has its own scale, all of which can be worked out" +
            "backward: sleep duration 7 hours = 50 points, 9 hours = 100 points; ",
        Language.KO to
            "열 개 성분은 각각 척도를 가지며 모두 역산할 수 있습니다: 수면 시간 7시간 = 50점, 9시간 = 100점; ",
        ),
        "s_ad7685cb" to mapOf(
        Language.ZH_HANS to
            "它由手表点检一并给出，拿不到时不给估算）",
        Language.ZH_HANT to
            "它由手錶點檢一併給出，拿不到時不給估算）",
        Language.EN to
            "it is provided along with the watch's spot check, and no estimate is given when it" +
            "is unavailable)",
        Language.KO to
            "이는 시계 점검 시 함께 제공되며, 얻을 수 없으면 추정값을 제공하지 않습니다)",
        ),
        "s_ae1ee2c1" to mapOf(
        Language.ZH_HANS to
            "这个结果通常意味着用户资料不对。去手机端「体成分」页核一下身高/体重/性别/年龄，改对后重测。",
        Language.ZH_HANT to
            "這個結果通常意味著使用者資料不對。去手機端「體成分」頁核一下身高/體重/性別/年齡，改對後重測。",
        Language.EN to
            "This result usually means the profile data is wrong. Check your" +
            "height/weight/sex/age on the phone's \"Body Composition\" page, correct them, and" +
            "measure again.",
        Language.KO to
            "이 결과는 보통 사용자 정보가 잘못되었음을 뜻합니다. 휴대폰의 '체성분' 페이지에서 키/체중/성별/나이를 확인해 바로잡은 뒤 다시 측정하세요.",
        ),
        "s_aedbc92c" to mapOf(
        Language.ZH_HANS to
            "活动状态未判定",
        Language.ZH_HANT to
            "活動狀態未判定",
        Language.EN to
            "Activity state undetermined",
        Language.KO to
            "활동 상태 미판정",
        ),
        "s_afef74b5" to mapOf(
        Language.ZH_HANS to
            "恰好把结论说反",
        Language.ZH_HANT to
            "恰好把結論說反",
        Language.EN to
            "which would get the conclusion exactly backward",
        Language.KO to
            "이는 결론을 정반대로 뒤집는 것입니다",
        ),
        "s_b02d6af5" to mapOf(
        Language.ZH_HANS to
            "波形基线",
        Language.ZH_HANT to
            "波形基線",
        Language.EN to
            "Waveform baseline",
        Language.KO to
            "파형 기준선",
        ),
        "s_b0587a54" to mapOf(
        Language.ZH_HANS to
            "「前一日的活动」以你在三星健康里设的步数目标为满分线；",
        Language.ZH_HANT to
            "「前一日的活動」以你在三星健康裡設的步數目標為滿分線；",
        Language.EN to
            "\"Previous day's activity\" uses the step goal you set in Samsung Health as the" +
            "full-marks line; ",
        Language.KO to
            "'전날의 활동'은 Samsung Health에서 설정한 걸음 목표를 만점 기준으로 삼습니다; ",
        ),
        "s_b0ef0e1d" to mapOf(
        Language.ZH_HANS to
            "只覆盖 App 前台运行的那段时间。要「今天一共动了多少」，看三星这一组才对。",
        Language.ZH_HANT to
            "只涵蓋 App 前景執行的那段時間。要「今天一共動了多少」，看三星這一組才對。",
        Language.EN to
            "and only covers the time the app was running in the foreground. For \"how much did" +
            "I move today in total\", Samsung's group here is the right one.",
        Language.KO to
            "앱이 포그라운드에서 실행된 시간만 포함합니다. '오늘 총 얼마나 움직였는가'는 삼성의 이 그룹을 보셔야 맞습니다.",
        ),
        "s_b172e221" to mapOf(
        Language.ZH_HANS to
            "而本机拿不到（实测它那个通道随手表自身温度变化，佩戴时被手腕与机身加热）。",
        Language.ZH_HANT to
            "而本機拿不到（實測它那個通道隨手錶自身溫度變化，佩戴時被手腕與機身加熱）。",
        Language.EN to
            "and this device cannot obtain it (measured: that channel follows the watch's own" +
            "temperature, heated by the wrist and device body when worn).",
        Language.KO to
            "이 기기에서는 얻을 수 없습니다(실측: 그 채널은 시계 자체 온도에 따라 변하며, 착용 시 손목과 기기 본체에 의해 가열됩니다).",
        ),
        "s_b199bd1f" to mapOf(
        Language.ZH_HANS to
            "现在只能给这一次压力分；连着测几天后这里会补上",
        Language.ZH_HANT to
            "現在只能給這一次壓力分；連著測幾天後這裡會補上",
        Language.EN to
            "For now only this single stress score can be given; it will be filled in after" +
            "measuring for a few consecutive days",
        Language.KO to
            "지금은 이번 스트레스 점수만 제공할 수 있으며, 며칠 연속 측정하면 이 부분이 채워집니다",
        ),
        "s_b1d1e208" to mapOf(
        Language.ZH_HANS to
            "数据不足",
        Language.ZH_HANT to
            "資料不足",
        Language.EN to
            "Insufficient data",
        Language.KO to
            "데이터 부족",
        ),
        "s_b23f39ed" to mapOf(
        Language.ZH_HANS to
            "两根手指都离开了电极键",
        Language.ZH_HANT to
            "兩根手指都離開了電極鍵",
        Language.EN to
            "Both fingers left the electrode buttons",
        Language.KO to
            "두 손가락이 모두 전극 버튼에서 떨어졌습니다",
        ),
        "s_b27cbba3" to mapOf(
        Language.ZH_HANS to
            "抬手臂时让腋下张开，手和手臂都不要碰到身体，也别让两只手互相碰到。",
        Language.ZH_HANT to
            "抬手臂時讓腋下張開，手和手臂都不要碰到身體，也別讓兩隻手互相碰到。",
        Language.EN to
            "When raising your arm, keep your armpit open; do not let your hand or arm touch" +
            "your body, and do not let the two hands touch each other.",
        Language.KO to
            "팔을 들 때 겨드랑이를 벌리고, 손과 팔이 몸에 닿지 않게 하며, 두 손이 서로 닿지 않게 하세요.",
        ),
        "s_b3c4090e" to mapOf(
        Language.ZH_HANS to
            "十个分量用了两种刻度，不是随手分的。",
        Language.ZH_HANT to
            "十個分量用了兩種刻度，不是隨手分的。",
        Language.EN to
            "The ten components use two kinds of scale; this was not an arbitrary choice.",
        Language.KO to
            "열 개 성분은 두 가지 척도를 사용하며, 임의로 정한 것이 아닙니다.",
        ),
        "s_b49c9c6b" to mapOf(
        Language.ZH_HANS to
            "目前只有 1 次经期记录，还看不出周期长度。再记 1~2 次之后就能给出预测 ——",
        Language.ZH_HANT to
            "目前只有 1 次經期記錄，還看不出週期長度。再記 1~2 次之後就能給出預測 ——",
        Language.EN to
            "There is currently only 1 period record, so the cycle length cannot be derived." +
            "After 1–2 more records a prediction can be given — ",
        Language.KO to
            "현재 월경 기록이 1회뿐이라 주기 길이를 알 수 없습니다. 1~2회 더 기록하면 예측을 제공할 수 있습니다 — ",
        ),
        "s_b515b90c" to mapOf(
        Language.ZH_HANS to
            "但换算不成 mmHg。本体只给毫秒。",
        Language.ZH_HANT to
            "但換算不成 mmHg。本體只給毫秒。",
        Language.EN to
            "but cannot be converted into mmHg. This feature only gives milliseconds.",
        Language.KO to
            "mmHg로 환산할 수는 없습니다. 이 기능은 밀리초만 제공합니다.",
        ),
        "s_b5292207" to mapOf(
        Language.ZH_HANS to
            "低于平时",
        Language.ZH_HANT to
            "低於平時",
        Language.EN to
            "Below usual",
        Language.KO to
            "평소보다 낮음",
        ),
        "s_b59e7dca" to mapOf(
        Language.ZH_HANS to
            "请按姿势引导放好手指…",
        Language.ZH_HANT to
            "請按姿勢引導放好手指…",
        Language.EN to
            "Place your fingers as shown in the posture guide…",
        Language.KO to
            "자세 안내에 따라 손가락을 올려주세요…",
        ),
        "s_b5cecb8f" to mapOf(
        Language.ZH_HANS to
            "还没有记录过经期。这个数据没有传感器能测 —— 需要在手机端手动记一次",
        Language.ZH_HANT to
            "還沒有記錄過經期。這個資料沒有感測器能測 —— 需要在手機端手動記一次",
        Language.EN to
            "No period has been recorded yet. No sensor can measure this — you need to log one" +
            "manually on the phone ",
        Language.KO to
            "아직 월경을 기록한 적이 없습니다. 이 데이터는 어떤 센서로도 측정할 수 없습니다 — 휴대폰에서 직접 한 번 기록해야 합니다 ",
        ),
        "s_b5ddfdd1" to mapOf(
        Language.ZH_HANS to
            "平均睡眠时间",
        Language.ZH_HANT to
            "平均睡眠時間",
        Language.EN to
            "Average sleep duration",
        Language.KO to
            "평균 수면 시간",
        ),
        "s_b5e6d11c" to mapOf(
        Language.ZH_HANS to
            "泳池游泳",
        Language.ZH_HANT to
            "泳池游泳",
        Language.EN to
            "Pool swimming",
        Language.KO to
            "수영장 수영",
        ),
        "s_b6bea6ee" to mapOf(
        Language.ZH_HANS to
            "本次没有拿到逐拍间期",
        Language.ZH_HANT to
            "本次沒有拿到逐拍間期",
        Language.EN to
            "No beat-to-beat intervals obtained this time",
        Language.KO to
            "이번에는 박동 간격을 얻지 못했습니다",
        ),
        "s_b6d907e1" to mapOf(
        Language.ZH_HANS to
            "方向要注意：这里心率低于基线、波动小于基线都表示恢复更好、分更高，",
        Language.ZH_HANT to
            "方向要注意：這裡心率低於基線、波動小於基線都表示恢復更好、分更高，",
        Language.EN to
            "Note the direction: here a heart rate below baseline or variability below baseline" +
            "both mean better recovery and a higher score, ",
        Language.KO to
            "방향에 주의하세요. 여기서는 심박수가 기준선보다 낮거나 변동이 기준선보다 작으면 회복이 더 좋고 점수가 더 높습니다. ",
        ),
        "s_b8680c60" to mapOf(
        Language.ZH_HANS to
            "睡得规不规律）与昨天白天的活动量。",
        Language.ZH_HANT to
            "睡得規不規律）與昨天白天的活動量。",
        Language.EN to
            "how regular your sleep was) and yesterday's daytime activity.",
        Language.KO to
            "수면이 규칙적이었는지)와 어제 낮의 활동량입니다.",
        ),
        "s_b86ffef2" to mapOf(
        Language.ZH_HANS to
            "皮温分量未参与",
        Language.ZH_HANT to
            "皮溫分量未參與",
        Language.EN to
            "the skin-temperature component did not participate",
        Language.KO to
            "피부 온도 성분은 참여하지 않았습니다",
        ),
        "s_b983c14a" to mapOf(
        Language.ZH_HANS to
            " · 疲劳 数据不足",
        Language.ZH_HANT to
            " · 疲勞 資料不足",
        Language.EN to
            " · Fatigue: not enough data",
        Language.KO to
            " · 피로: 데이터 부족",
        ),
        "s_b993837a" to mapOf(
        Language.ZH_HANS to
            "%.1f 公里",
        Language.ZH_HANT to
            "%.1f 公里",
        Language.EN to
            "%.1f km",
        Language.KO to
            "%.1f km",
        ),
        "s_bc4c3531" to mapOf(
        Language.ZH_HANS to
            "而这一条是三星后台用持续心率做的节律筛查 —— 两者算法与输入都不同，",
        Language.ZH_HANT to
            "而這一條是三星後台用持續心率做的節律篩檢 —— 兩者演算法與輸入都不同，",
        Language.EN to
            "while this entry is a rhythm screening that Samsung performs in the background" +
            "using continuous heart rate — the two differ in both algorithm and input, ",
        Language.KO to
            "이 항목은 삼성이 지속 심박수를 이용해 백그라운드에서 수행한 리듬 선별입니다 — 둘은 알고리즘과 입력이 모두 다르므로 ",
        ),
        "s_bd36935e" to mapOf(
        Language.ZH_HANS to
            "%d年%d月%d日",
        Language.ZH_HANT to
            "%d年%d月%d日",
        Language.EN to
            "%04d-%02d-%02d",
        Language.KO to
            "%d년 %d월 %d일",
        ),
        "s_bd746bd2" to mapOf(
        Language.ZH_HANS to
            "心跳快慢的两种调节谁占上风，一般不用纠结具体数值",
        Language.ZH_HANT to
            "心跳快慢的兩種調節誰佔上風，一般不用糾結具體數值",
        Language.EN to
            "Which of the two ways of regulating heart rate dominates; the specific number" +
            "usually does not matter much",
        Language.KO to
            "심박수를 조절하는 두 가지 방식 중 어느 쪽이 우세한지로, 보통 구체적인 수치에 얽매일 필요는 없습니다",
        ),
        "s_bf222df1" to mapOf(
        Language.ZH_HANS to
            "呼吸会以三种方式改变脉搏波：幅度起伏、心搏间隔起伏（呼吸性心律不齐）、基线起伏。",
        Language.ZH_HANT to
            "呼吸會以三種方式改變脈搏波：幅度起伏、心搏間隔起伏（呼吸性心律不整）、基線起伏。",
        Language.EN to
            "Respiration changes the pulse wave in three ways: amplitude fluctuation," +
            "beat-interval fluctuation (respiratory sinus arrhythmia), and baseline" +
            "fluctuation.",
        Language.KO to
            "호흡은 세 가지 방식으로 맥파를 바꿉니다: 진폭 변동, 박동 간격 변동(호흡성 동성 부정맥), 기준선 변동.",
        ),
        "s_bfb7987f" to mapOf(
        Language.ZH_HANS to
            "（本模块只处理静息下的平稳节律）",
        Language.ZH_HANT to
            "（本模組只處理靜息下的平穩節律）",
        Language.EN to
            " (this module only handles steady rhythms at rest)",
        Language.KO to
            "만큼 변했기 때문입니다(이 모듈은 안정 상태의 평탄한 리듬만 처리합니다)",
        ),
        "s_c0fe32cf" to mapOf(
        Language.ZH_HANS to
            "这一晚没有入睡时刻记录，「睡眠时间一致性」「睡眠规律性」「睡眠定时」三项都未参与",
        Language.ZH_HANT to
            "這一晚沒有入睡時刻記錄，「睡眠時間一致性」「睡眠規律性」「睡眠定時」三項都未參與",
        Language.EN to
            "There is no sleep-onset time for this night, so \"Sleep-time consistency\"," +
            "\"Sleep regularity\", and \"Sleep timing\" all did not participate",
        Language.KO to
            "이 날 밤에는 잠든 시각 기록이 없어 '수면 시간 일관성', '수면 규칙성', '수면 시각' 세 항목 모두 참여하지 않았습니다",
        ),
        "s_c18d7cc3" to mapOf(
        Language.ZH_HANS to
            "信号噪声过大，请保持静止重新测量",
        Language.ZH_HANT to
            "訊號雜訊過大，請保持靜止重新測量",
        Language.EN to
            "Signal noise is too high; stay still and measure again",
        Language.KO to
            "신호 노이즈가 너무 큽니다. 움직이지 말고 다시 측정하세요",
        ),
        "s_c2111bfd" to mapOf(
        Language.ZH_HANS to
            "请等同一口径的记录攒够再看",
        Language.ZH_HANT to
            "請等同一口徑的記錄攢夠再看",
        Language.EN to
            "please wait until enough records of the same definition have accumulated",
        Language.KO to
            "같은 기준의 기록이 충분히 쌓일 때까지 기다려 주세요",
        ),
        "s_c30afee6" to mapOf(
        Language.ZH_HANS to
            "两边都还没有数据",
        Language.ZH_HANT to
            "兩邊都還沒有資料",
        Language.EN to
            "Neither side has data yet",
        Language.KO to
            "양쪽 모두 아직 데이터가 없습니다",
        ),
        "s_c335d2b6" to mapOf(
        Language.ZH_HANS to
            "平板支撑",
        Language.ZH_HANT to
            "平板支撐",
        Language.EN to
            "Plank",
        Language.KO to
            "플랭크",
        ),
        "s_c34c0523" to mapOf(
        Language.ZH_HANS to
            "无法判定",
        Language.ZH_HANT to
            "無法判定",
        Language.EN to
            "Cannot determine",
        Language.KO to
            "판정 불가",
        ),
        "s_c455a02e" to mapOf(
        Language.ZH_HANS to
            "常见范围",
        Language.ZH_HANT to
            "常見範圍",
        Language.EN to
            "Usual range",
        Language.KO to
            "일반적인 범위",
        ),
        "s_c4b98638" to mapOf(
        Language.ZH_HANS to
            "本机自己测的「心电」是它自己采的 30 秒波形（有节律分类），",
        Language.ZH_HANT to
            "本機自己測的「心電」是它自己採的 30 秒波形（有節律分類），",
        Language.EN to
            "The \"ECG\" measured on this device is a 30-second waveform it records itself" +
            "(with a rhythm classification), ",
        Language.KO to
            "이 기기에서 직접 측정한 '심전도'는 30초 파형을 자체적으로 수집한 것(리듬 분류 포함)이며, ",
        ),
        "s_c4caa988" to mapOf(
        Language.ZH_HANS to
            "还没有可用的心电 / HRV 记录；评分的输入是逐拍间期，先在手表上测一次心电",
        Language.ZH_HANT to
            "還沒有可用的心電 / HRV 記錄；評分的輸入是逐拍間期，先在手錶上測一次心電",
        Language.EN to
            "No ECG / HRV records are available yet. The score is computed from beat-to-beat" +
            "intervals, so take an ECG measurement on your watch first.",
        Language.KO to
            "아직 사용할 수 있는 심전도 / HRV 기록이 없습니다. 점수는 박동 간 간격을 입력으로 계산되므로, 먼저 워치에서 심전도를 한 번 측정하세요.",
        ),
        "s_c591f1b9" to mapOf(
        Language.ZH_HANS to
            "这段光信号里检不出可辨的脉搏，无法提取呼吸调制",
        Language.ZH_HANT to
            "這段光訊號裡檢不出可辨的脈搏，無法提取呼吸調製",
        Language.EN to
            "No discernible pulse can be detected in this light signal, so respiratory" +
            "modulation cannot be extracted",
        Language.KO to
            "이 광신호에서 식별할 수 있는 맥박이 검출되지 않아 호흡 변조를 추출할 수 없습니다",
        ),
        "s_c5ffb718" to mapOf(
        Language.ZH_HANS to
            "公告顶层写成了数组，需要是一个对象（最外层要有 { }）",
        Language.ZH_HANT to
            "公告頂層寫成了陣列，需要是一個物件（最外層要有 { }）",
        Language.EN to
            "The announcement's top level is an array; it needs to be an object (the outermost" +
            "level must have { })",
        Language.KO to
            "공지의 최상위가 배열로 작성되었습니다. 객체여야 합니다(가장 바깥에 { } 필요)",
        ),
        "s_c6e8c7a7" to mapOf(
        Language.ZH_HANS to
            "这次心电的节律不齐。HRV 关心的变异是窦性心律下由呼吸与自主神经引起的变异；",
        Language.ZH_HANT to
            "這次心電的節律不齊。HRV 關心的變異是竇性心律下由呼吸與自律神經引起的變異；",
        Language.EN to
            "This ECG shows an irregular rhythm. The variability HRV cares about is that caused" +
            "by respiration and the autonomic nervous system under sinus rhythm; ",
        Language.KO to
            "이번 심전도는 리듬이 불규칙합니다. HRV가 관심을 두는 변동은 정상 동율동에서 호흡과 자율신경으로 인한 변동입니다. ",
        ),
        "s_c70ed011" to mapOf(
        Language.ZH_HANS to
            "已注册，等待传感器（请戴好手表）…",
        Language.ZH_HANT to
            "已註冊，等待感測器（請戴好手錶）…",
        Language.EN to
            "Registered, waiting for the sensor (put the watch on)…",
        Language.KO to
            "등록됨, 센서 대기 중(워치를 착용하세요)…",
        ),
        "s_c759e8f9" to mapOf(
        Language.ZH_HANS to
            "近期 vs 更早 · 静息心率",
        Language.ZH_HANT to
            "近期 vs 更早 · 靜息心率",
        Language.EN to
            "Recent vs earlier · Resting heart rate",
        Language.KO to
            "최근 vs 이전 · 안정 심박수",
        ),
        "s_c7bb85ed" to mapOf(
        Language.ZH_HANS to
            "明显偏高",
        Language.ZH_HANT to
            "明顯偏高",
        Language.EN to
            "Clearly high",
        Language.KO to
            "뚜렷하게 높음",
        ),
        "s_c869cb06" to mapOf(
        Language.ZH_HANS to
            "请坐下静息 2~3 分钟后再测",
        Language.ZH_HANT to
            "請坐下靜息 2~3 分鐘後再測",
        Language.EN to
            "please sit and rest for 2–3 minutes before measuring again",
        Language.KO to
            "2~3분간 앉아서 안정한 뒤 다시 측정해 주세요",
        ),
        "s_c88c88e4" to mapOf(
        Language.ZH_HANS to
            "睡眠期间皮肤温度未参与：三星给的是绝对温度，",
        Language.ZH_HANT to
            "睡眠期間皮膚溫度未參與：三星給的是絕對溫度，",
        Language.EN to
            "Skin temperature during sleep did not participate: Samsung provides an absolute" +
            "temperature, ",
        Language.KO to
            "수면 중 피부 온도는 참여하지 않았습니다: 삼성이 제공하는 값은 절대 온도이며 ",
        ),
        "s_c8f8fff9" to mapOf(
        Language.ZH_HANS to
            "注意区别：本机「活动」模块里那条自研的活动记录是手表传感器在自己采的，",
        Language.ZH_HANT to
            "注意區別：本機「活動」模組裡那條自研的活動紀錄是手錶感測器在自己採的，",
        Language.EN to
            "Note the difference: the in-house activity record in this device's \"Activity\"" +
            "section is collected by the watch's own sensors, ",
        Language.KO to
            "차이점에 유의하세요. 이 기기의 '활동' 모듈에 있는 자체 개발 활동 기록은 시계 센서가 직접 수집한 것으로, ",
        ),
        "s_c9d15a8e" to mapOf(
        Language.ZH_HANS to
            "请保持手指按住表冠、手腕不动，重新测量",
        Language.ZH_HANT to
            "請保持手指按住錶冠、手腕不動，重新測量",
        Language.EN to
            "please keep your finger on the crown and your wrist still, and measure again",
        Language.KO to
            "손가락을 크라운에 대고 손목을 움직이지 않은 채 다시 측정해 주세요",
        ),
        "s_ca8789cf" to mapOf(
        Language.ZH_HANS to
            "HRV 有昼夜节律，全在同一个时段测出来的数只代表那个时段，不代表你的平时",
        Language.ZH_HANT to
            "HRV 有晝夜節律，全在同一個時段測出來的數只代表那個時段，不代表你的平時",
        Language.EN to
            "HRV has a circadian rhythm; numbers all measured in the same time window only" +
            "represent that window, not your usual state",
        Language.KO to
            "HRV에는 일주기 리듬이 있어 같은 시간대에 측정한 수치는 그 시간대만 나타낼 뿐 평소 상태를 나타내지 않습니다",
        ),
        "s_ca9bb45b" to mapOf(
        Language.ZH_HANS to
            "测量中动了或接触在变。整个过程中保持不动、手指压力稳定，再重测。",
        Language.ZH_HANT to
            "測量中動了或接觸在變。整個過程中保持不動、手指壓力穩定，再重測。",
        Language.EN to
            "You moved or the contact changed during the measurement. Stay still with steady" +
            "finger pressure throughout, then measure again.",
        Language.KO to
            "측정 중 움직였거나 접촉이 변했습니다. 전 과정 동안 움직이지 말고 손가락 압력을 일정하게 유지한 뒤 다시 측정하세요.",
        ),
        "s_cb754364" to mapOf(
        Language.ZH_HANS to
            "测量完成",
        Language.ZH_HANT to
            "測量完成",
        Language.EN to
            "Measurement complete",
        Language.KO to
            "측정 완료",
        ),
        "s_cc189cd0" to mapOf(
        Language.ZH_HANS to
            "请确认手指完全覆盖手表侧键、且测量过程中保持不动",
        Language.ZH_HANT to
            "請確認手指完全覆蓋手錶側鍵、且測量過程中保持不動",
        Language.EN to
            "Make sure your fingers fully cover the watch's side buttons and stay still during" +
            "the measurement",
        Language.KO to
            "손가락이 시계 측면 버튼을 완전히 덮고 있는지, 측정 중 움직이지 않는지 확인하세요",
        ),
        "s_cc66f413" to mapOf(
        Language.ZH_HANS to
            "活动那两项依赖三星健康的步数：没授权、或那天没带手机手表，它们会缺席，",
        Language.ZH_HANT to
            "活動那兩項依賴三星健康的步數：沒授權、或那天沒帶手機手錶，它們會缺席，",
        Language.EN to
            "The two activity items depend on Samsung Health's steps: if permission was not" +
            "granted, or you did not carry the phone/watch that day, they will be absent, ",
        Language.KO to
            "활동 두 항목은 Samsung Health의 걸음 수에 의존합니다: 권한을 허용하지 않았거나 그날 휴대폰/시계를 소지하지 않았다면 이들은 빠지게" +
            "됩니다. ",
        ),
        "s_ccf663a0" to mapOf(
        Language.ZH_HANS to
            "本机没有加速度传感器",
        Language.ZH_HANT to
            "本機沒有加速度感測器",
        Language.EN to
            "This device has no accelerometer",
        Language.KO to
            "이 기기에는 가속도 센서가 없습니다",
        ),
        "s_cdc4a484" to mapOf(
        Language.ZH_HANS to
            "已存到本机，但没有写进",
        Language.ZH_HANT to
            "已存到本機，但沒有寫進",
        Language.EN to
            "Saved on this device, but not written to ",
        Language.KO to
            "이 기기에 저장되었지만 다음에 쓰이지 않았습니다: ",
        ),
        "s_cf202fbb" to mapOf(
        Language.ZH_HANS to
            "与参考心率一致",
        Language.ZH_HANT to
            "與參考心率一致",
        Language.EN to
            "Matches the reference heart rate",
        Language.KO to
            "참조 심박수와 일치",
        ),
        "s_cf6b907d" to mapOf(
        Language.ZH_HANS to
            "正在注册…",
        Language.ZH_HANT to
            "正在註冊…",
        Language.EN to
            "Registering…",
        Language.KO to
            "등록 중…",
        ),
        "s_cfa65f41" to mapOf(
        Language.ZH_HANS to
            "正在测量…",
        Language.ZH_HANT to
            "正在測量…",
        Language.EN to
            "Measuring…",
        Language.KO to
            "측정 중…",
        ),
        "s_d102be21" to mapOf(
        Language.ZH_HANS to
            "睡眠心率变化",
        Language.ZH_HANT to
            "睡眠心率變化",
        Language.EN to
            "Sleeping heart rate variability",
        Language.KO to
            "수면 심박 변동",
        ),
        "s_d15f6a55" to mapOf(
        Language.ZH_HANS to
            "比较两个窗口中位数的漂移。它回答的是「最近这段时间比之前差了多少」，",
        Language.ZH_HANT to
            "比較兩個視窗中位數的漂移。它回答的是「最近這段時間比之前差了多少」，",
        Language.EN to
            "and compares the drift of the two windows' medians. It answers \"how much worse" +
            "has it been recently\", ",
        Language.KO to
            "두 구간 중앙값의 이동을 비교합니다. 이는 '최근이 이전보다 얼마나 나빠졌는가'를 답하며 ",
        ),
        "s_d183ac86" to mapOf(
        Language.ZH_HANS to
            "这一晚读到了平均心率但没算出心率波动（序列太短），「睡眠心率变化」未参与",
        Language.ZH_HANT to
            "這一晚讀到了平均心率但沒算出心率波動（序列太短），「睡眠心率變化」未參與",
        Language.EN to
            "An average heart rate was read for this night but heart-rate variability could not" +
            "be computed (the series was too short), so \"Sleeping heart rate variability\" did" +
            "not participate",
        Language.KO to
            "이 날 밤 평균 심박수는 읽었으나 심박 변동을 계산하지 못했습니다(시계열이 너무 짧음). '수면 심박 변동'은 참여하지 않았습니다",
        ),
        "s_d24ab246" to mapOf(
        Language.ZH_HANS to
            "「前一日的活动」未参与：三星健康里没设过步数目标。",
        Language.ZH_HANT to
            "「前一日的活動」未參與：三星健康裡沒設過步數目標。",
        Language.EN to
            "\"Previous day's activity\" did not participate: no step goal has been set in" +
            "Samsung Health.",
        Language.KO to
            "'전날의 활동'은 참여하지 않았습니다: Samsung Health에 걸음 목표를 설정한 적이 없습니다.",
        ),
        "s_d28db85f" to mapOf(
        Language.ZH_HANS to
            "引体向上",
        Language.ZH_HANT to
            "引體向上",
        Language.EN to
            "Pull-ups",
        Language.KO to
            "풀업",
        ),
        "s_d3083c11" to mapOf(
        Language.ZH_HANS to
            "近期窗口的 RMSSD 无效",
        Language.ZH_HANT to
            "近期視窗的 RMSSD 無效",
        Language.EN to
            "The RMSSD of the recent window is invalid",
        Language.KO to
            "최근 구간의 RMSSD가 유효하지 않습니다",
        ),
        "s_d3210a14" to mapOf(
        Language.ZH_HANS to
            "把表带系紧，让手表背面紧贴手腕，再重测。",
        Language.ZH_HANT to
            "把錶帶繫緊，讓手錶背面緊貼手腕，再重測。",
        Language.EN to
            "Tighten the band so the back of the watch is snug against your wrist, then measure" +
            "again.",
        Language.KO to
            "밴드를 조여 시계 뒷면을 손목에 밀착시킨 뒤 다시 측정하세요.",
        ),
        "s_d350fbc7" to mapOf(
        Language.ZH_HANS to
            "⚠ 这一项需要的是室温，而本机没有可用的室温读数（见「为什么给不出」）。",
        Language.ZH_HANT to
            "⚠ 這一項需要的是室溫，而本機沒有可用的室溫讀數（見「為什麼給不出」）。",
        Language.EN to
            "⚠ This item needs room temperature, and this device has no usable room-temperature" +
            "reading (see \"Why it cannot be given\").",
        Language.KO to
            "⚠ 이 항목에는 실내 온도가 필요한데 이 기기에는 사용할 수 있는 실내 온도 판독값이 없습니다('왜 제공할 수 없는가' 참고).",
        ),
        "s_d41d10be" to mapOf(
        Language.ZH_HANS to
            "而且我们只有三星那一趟序列，所以不如时长重。",
        Language.ZH_HANT to
            "而且我們只有三星那一趟序列，所以不如時長重。",
        Language.EN to
            "and we only have Samsung's single series, so it weighs less than duration.",
        Language.KO to
            "저희에게는 삼성의 한 시계열만 있어 시간보다 가볍게 두었습니다.",
        ),
        "s_d4297276" to mapOf(
        Language.ZH_HANS to
            "HF 频带下限 0.15Hz 的一个周期 6.7 秒，估功率至少要 8~10 个周期；",
        Language.ZH_HANT to
            "HF 頻帶下限 0.15Hz 的一個週期 6.7 秒，估功率至少要 8~10 個週期；",
        Language.EN to
            "one cycle at the HF band's lower bound of 0.15 Hz takes 6.7 seconds, and" +
            "estimating power needs at least 8–10 cycles; ",
        Language.KO to
            "HF 대역 하한 0.15Hz의 한 주기는 6.7초이며, 파워를 추정하려면 최소 8~10주기가 필요합니다. ",
        ),
        "s_d4636095" to mapOf(
        Language.ZH_HANS to
            "高于平时",
        Language.ZH_HANT to
            "高於平時",
        Language.EN to
            "Above usual",
        Language.KO to
            "평소보다 높음",
        ),
        "s_d48cfbe7" to mapOf(
        Language.ZH_HANS to
            "高位下拉",
        Language.ZH_HANT to
            "高位下拉",
        Language.EN to
            "Lat pulldowns",
        Language.KO to
            "랫풀다운",
        ),
        "s_d4956122" to mapOf(
        Language.ZH_HANS to
            "本 App 的分还没算出来（右侧是三星的分）",
        Language.ZH_HANT to
            "本 App 的分還沒算出來（右側是三星的分）",
        Language.EN to
            "This app's score has not been computed yet (the right side is Samsung's score)",
        Language.KO to
            "이 앱의 점수는 아직 계산되지 않았습니다(오른쪽은 삼성의 점수)",
        ),
        "s_d4cbd33d" to mapOf(
        Language.ZH_HANS to
            "并排的两个分不是同一把尺子：左边是本 App 自己算的，右边是三星健康的「能量评分」。",
        Language.ZH_HANT to
            "並排的兩個分不是同一把尺子：左邊是本 App 自己算的，右邊是三星健康的「能量評分」。",
        Language.EN to
            "The two scores side by side are not the same ruler: the left is computed by this" +
            "app, the right is Samsung Health's \"Energy Score\".",
        Language.KO to
            "나란히 놓인 두 점수는 같은 자가 아닙니다: 왼쪽은 이 앱이 계산한 것이고 오른쪽은 Samsung Health의 '에너지 점수'입니다.",
        ),
        "s_d53ec54f" to mapOf(
        Language.ZH_HANS to
            "至少 3 晚才算成立。睡眠期心率会随睡前饮酒、房间温度、生病、昼夜节律变化，",
        Language.ZH_HANT to
            "至少 3 晚才算成立。睡眠期心率會隨睡前飲酒、房間溫度、生病、晝夜節律變化，",
        Language.EN to
            "and needs at least 3 nights to hold. Sleeping heart rate varies with alcohol" +
            "before bed, room temperature, illness, and circadian rhythm, ",
        Language.KO to
            "최소 3일 밤은 되어야 성립합니다. 수면 심박수는 취침 전 음주, 실내 온도, 질병, 일주기 리듬에 따라 변하므로 ",
        ),
        "s_d63379c6" to mapOf(
        Language.ZH_HANS to
            "略有波动",
        Language.ZH_HANT to
            "略有波動",
        Language.EN to
            "Slightly variable",
        Language.KO to
            "약간 변동",
        ),
        "s_d63e0580" to mapOf(
        Language.ZH_HANS to
            "另一种可能是记录期间心率发生了 ",
        Language.ZH_HANT to
            "另一種可能是記錄期間心率發生了 ",
        Language.EN to
            "Another possibility is that the heart rate changed during the recording by ",
        Language.KO to
            "또 다른 가능성은 기록 중 심박수가 ",
        ),
        "s_d6d8d272" to mapOf(
        Language.ZH_HANS to
            "皮肤温度偏差 0.3℃ 以内满分、1.5℃ 归零；",
        Language.ZH_HANT to
            "皮膚溫度偏差 0.3℃ 以內滿分、1.5℃ 歸零；",
        Language.EN to
            "skin temperature deviation within 0.3°C scores full marks, 1.5°C scores zero; ",
        Language.KO to
            "피부 온도 편차가 0.3°C 이내면 만점, 1.5°C면 0점; ",
        ),
        "s_d738ab54" to mapOf(
        Language.ZH_HANS to
            "暂无数据",
        Language.ZH_HANT to
            "暫無資料",
        Language.EN to
            "No data yet",
        Language.KO to
            "데이터 없음",
        ),
        "s_d762d6ad" to mapOf(
        Language.ZH_HANS to
            "比的是你自己：把最近一晚与之前几晚的中位数相减，不给绝对温度。\\n",
        Language.ZH_HANT to
            "比的是你自己：把最近一晚與之前幾晚的中位數相減，不給絕對溫度。\\n",
        Language.EN to
            "It compares against yourself: the most recent night is subtracted from the median" +
            "of the preceding nights, and no absolute temperature is given.\\n",
        Language.KO to
            "본인과 비교합니다: 가장 최근 밤에서 이전 며칠 밤의 중앙값을 빼며, 절대 온도는 제공하지 않습니다.\\n",
        ),
        "s_d80ed79e" to mapOf(
        Language.ZH_HANS to
            "高山滑雪",
        Language.ZH_HANT to
            "高山滑雪",
        Language.EN to
            "Alpine skiing",
        Language.KO to
            "알파인 스키",
        ),
        "s_d81c2912" to mapOf(
        Language.ZH_HANS to
            "本机不支持心率采集器，拿不到逐拍间期",
        Language.ZH_HANT to
            "本機不支援心率採集器，拿不到逐拍間期",
        Language.EN to
            "This device does not support the heart rate collector, so beat-to-beat intervals" +
            "are unavailable",
        Language.KO to
            "이 기기는 심박 수집기를 지원하지 않아 박동 간격을 가져올 수 없습니다",
        ),
        "s_da047b4f" to mapOf(
        Language.ZH_HANS to
            "目标值来自你在三星健康里设置的每日目标。没设过就是空的 —— ",
        Language.ZH_HANT to
            "目標值來自你在三星健康裡設定的每日目標。沒設過就是空的 —— ",
        Language.EN to
            "Goal values come from the daily goals you set in Samsung Health. If you never set" +
            "one it is empty — ",
        Language.KO to
            "목표값은 Samsung Health에서 설정한 일일 목표에서 옵니다. 설정한 적이 없으면 비어 있습니다 — ",
        ),
        "s_daabb0a5" to mapOf(
        Language.ZH_HANS to
            "越野铁人三项",
        Language.ZH_HANT to
            "越野鐵人三項",
        Language.EN to
            "Cross triathlon",
        Language.KO to
            "크로스 트라이애슬론",
        ),
        "s_daabbe76" to mapOf(
        Language.ZH_HANS to
            "越野铁人两项",
        Language.ZH_HANT to
            "越野鐵人兩項",
        Language.EN to
            "Cross duathlon",
        Language.KO to
            "크로스 듀애슬론",
        ),
        "s_db4c78de" to mapOf(
        Language.ZH_HANS to
            "用它算「今天的累积活动量」会系统性低估 —— 没采到的时间会被当成没动。",
        Language.ZH_HANT to
            "用它算「今天的累積活動量」會系統性低估 —— 沒採到的時間會被當成沒動。",
        Language.EN to
            "using it to compute \"today's cumulative activity\" would systematically" +
            "underestimate — time not sampled is treated as not moving.",
        Language.KO to
            "이를 이용해 '오늘의 누적 활동량'을 계산하면 체계적으로 과소평가됩니다 — 수집되지 않은 시간은 움직이지 않은 것으로 간주됩니다.",
        ),
        "s_db4c8e39" to mapOf(
        Language.ZH_HANS to
            "跳过（已超出总时长预算）",
        Language.ZH_HANT to
            "跳過（已超出總時長預算）",
        Language.EN to
            "Skipped (total time budget exceeded)",
        Language.KO to
            "건너뜀 (총 소요 시간 예산 초과)",
        ),
        "s_dd47f6a4" to mapOf(
        Language.ZH_HANS to
            "预测把「最近一次经期开始日 + 平均周期长度」当成下次经期，平均周期长度取历史的中位数",
        Language.ZH_HANT to
            "預測把「最近一次經期開始日 + 平均週期長度」當成下次經期，平均週期長度取歷史的中位數",
        Language.EN to
            "The prediction treats \"most recent period start date + average cycle length\" as" +
            "the next period, with the average cycle length taken as the median of the history ",
        Language.KO to
            "예측은 '가장 최근 월경 시작일 + 평균 주기 길이'를 다음 월경으로 봅니다. 평균 주기 길이는 기록의 중앙값을 사용합니다 ",
        ),
        "s_dd4aafb9" to mapOf(
        Language.ZH_HANS to
            "相邻两次心跳的间隔有多少变化 —— 变化大说明心跳更灵活",
        Language.ZH_HANT to
            "相鄰兩次心跳的間隔有多少變化 —— 變化大說明心跳更靈活",
        Language.EN to
            "How much the interval between adjacent heartbeats varies — a larger variation" +
            "means the heart is more flexible",
        Language.KO to
            "인접한 두 심장 박동 사이 간격이 얼마나 변하는지 — 변동이 크면 심장이 더 유연함을 뜻합니다",
        ),
        "s_dd672ac0" to mapOf(
        Language.ZH_HANS to
            "手腕电极未贴合",
        Language.ZH_HANT to
            "手腕電極未貼合",
        Language.EN to
            "Wrist electrodes not in contact",
        Language.KO to
            "손목 전극 밀착 안 됨",
        ),
        "s_de6a0d02" to mapOf(
        Language.ZH_HANS to
            "晚间加餐",
        Language.ZH_HANT to
            "晚間加餐",
        Language.EN to
            "Evening snack",
        Language.KO to
            "저녁 간식",
        ),
        "s_ded293c7" to mapOf(
        Language.ZH_HANS to
            "这几路信号里都没有落在呼吸频段的周期成分 —— ",
        Language.ZH_HANT to
            "這幾路訊號裡都沒有落在呼吸頻段的週期成分 —— ",
        Language.EN to
            "None of these signals has a periodic component in the respiratory band — ",
        Language.KO to
            "이 여러 신호 중 호흡 대역에 해당하는 주기 성분이 없습니다 — ",
        ),
        "s_df2bb2de" to mapOf(
        Language.ZH_HANS to
            "这次没能确认测量时是否静息（刚打开 App 不足一个采样窗口），",
        Language.ZH_HANT to
            "這次沒能確認測量時是否靜息（剛開啟 App 不足一個取樣視窗），",
        Language.EN to
            "It could not be confirmed whether you were at rest during this measurement (the" +
            "app had just opened, less than one sampling window), ",
        Language.KO to
            "이번 측정 시 안정 상태였는지 확인하지 못했습니다(앱을 막 열어 하나의 샘플링 구간에 못 미침). ",
        ),
        "s_df5eebe4" to mapOf(
        Language.ZH_HANS to
            "睡眠时长是最有解释力的单一输入，所以最重；最低血氧受单次读数影响较大、",
        Language.ZH_HANT to
            "睡眠時長是最有解釋力的單一輸入，所以最重；最低血氧受單次讀數影響較大、",
        Language.EN to
            "Sleep duration is the single most explanatory input, so it weighs the most; lowest" +
            "blood oxygen is fairly sensitive to a single reading ",
        Language.KO to
            "수면 시간은 설명력이 가장 큰 단일 입력이라 가장 무겁고, 최저 혈중 산소는 단일 판독값의 영향을 비교적 크게 받으며 ",
        ),
        "s_e02a8d93" to mapOf(
        Language.ZH_HANS to
            "「规律」按周期长度的最大减最小判定（不超过 7 天为规律、14 天以上为不规律），",
        Language.ZH_HANT to
            "「規律」按週期長度的最大減最小判定（不超過 7 天為規律、14 天以上為不規律），",
        Language.EN to
            "\"Regular\" is judged by the maximum minus minimum cycle length (no more than 7" +
            "days is regular, 14 days or more is irregular), ",
        Language.KO to
            "'규칙적'은 주기 길이의 최댓값에서 최솟값을 뺀 값으로 판정합니다(7일 이내면 규칙적, 14일 이상이면 불규칙). ",
        ),
        "s_e0a58f23" to mapOf(
        Language.ZH_HANS to
            "本 App 没有独立的静息心率记录（界面上的静息范围是从心率序列现算的），",
        Language.ZH_HANT to
            "本 App 沒有獨立的靜息心率記錄（介面上的靜息範圍是從心率序列現算的），",
        Language.EN to
            "This app has no separate resting heart rate record (the resting range on the" +
            "screen is computed on the fly from the heart-rate series), ",
        Language.KO to
            "이 앱에는 별도의 안정 심박수 기록이 없습니다(화면의 안정 범위는 심박수 시계열에서 즉석 계산합니다). ",
        ),
        "s_e1a34dbd" to mapOf(
        Language.ZH_HANS to
            "2 点钟方向的手指没放上",
        Language.ZH_HANT to
            "2 點鐘方向的手指沒放上",
        Language.EN to
            "The finger at the 2 o'clock position is not placed",
        Language.KO to
            "2시 방향 손가락이 올려지지 않았습니다",
        ),
        "s_e35ce677" to mapOf(
        Language.ZH_HANS to
            "佩戴时被手腕与机身加热（实测长期就是 31~36℃），摘下来也降得很慢。",
        Language.ZH_HANT to
            "佩戴時被手腕與機身加熱（實測長期就是 31~36℃），摘下來也降得很慢。",
        Language.EN to
            "when worn it is heated by the wrist and the device body (measured at a steady" +
            "31–36°C), and it cools very slowly when taken off.",
        Language.KO to
            "착용 시 손목과 기기 본체에 의해 가열되며(실측상 장기간 31~36°C) 벗어도 천천히 식습니다.",
        ),
        "s_e3a0222a" to mapOf(
        Language.ZH_HANS to
            "（睡眠规律性是个例外：它本身就是「跨晚的离散度」，所以刻度是绝对的。）",
        Language.ZH_HANT to
            "（睡眠規律性是例外：它本身就是「跨晚的離散度」，所以刻度是絕對的。）",
        Language.EN to
            "(Sleep regularity is an exception: it is itself a \"night-to-night spread\", so" +
            "its scale is absolute.)",
        Language.KO to
            "(수면 규칙성은 예외입니다. 그 자체가 '밤마다의 산포'이므로 척도는 절대적입니다.)",
        ),
        "s_e3b7b94f" to mapOf(
        Language.ZH_HANS to
            "所以没有可回填的源数据；落库再回填等于把派生量固化成历史值。",
        Language.ZH_HANT to
            "所以沒有可回填的來源資料；落庫再回填等於把派生量固化成歷史值。",
        Language.EN to
            "so there is no source data to backfill; storing it and then backfilling would fix" +
            "a derived quantity as a historical value.",
        Language.KO to
            "따라서 되쓸 소스 데이터가 없으며, 저장한 뒤 되쓰면 파생량이 역사적 값으로 고정됩니다.",
        ),
        "s_e4d2df73" to mapOf(
        Language.ZH_HANS to
            "量纲不同，代入会得到一个看着像正常体温的错值。",
        Language.ZH_HANT to
            "量綱不同，代入會得到一個看著像正常體溫的錯值。",
        Language.EN to
            "whose dimension differs; substituting it would yield a wrong value that looks like" +
            "a normal body temperature.",
        Language.KO to
            "차원이 달라 대입하면 정상 체온처럼 보이는 잘못된 값이 나옵니다.",
        ),
        "s_e4ded133" to mapOf(
        Language.ZH_HANS to
            "本 App 自己算的分，0~100，越高表示这一夜的恢复 + 前一天的活动越好。",
        Language.ZH_HANT to
            "本 App 自己算的分，0~100，越高表示這一夜的恢復 + 前一天的活動越好。",
        Language.EN to
            "This app's own score, 0–100; higher means better recovery overnight plus better" +
            "activity the previous day.",
        Language.KO to
            "이 앱이 직접 계산한 점수로 0~100이며, 높을수록 그날 밤의 회복과 전날의 활동이 좋았음을 뜻합니다.",
        ),
        "s_e4fbd45e" to mapOf(
        Language.ZH_HANS to
            "本次没有可用的皮温点检（需要带环境温度的点检，且距本次不超过 ",
        Language.ZH_HANT to
            "本次沒有可用的皮溫點檢（需要帶環境溫度的點檢，且距本次不超過 ",
        Language.EN to
            "There is no usable skin-temperature spot check for this measurement (it requires a" +
            "spot check with ambient temperature, no more than ",
        Language.KO to
            "이번에 사용할 수 있는 피부 온도 점검이 없습니다(환경 온도가 포함된 점검이 필요하며, 이번 측정과 ",
        ),
        "s_e61a6cab" to mapOf(
        Language.ZH_HANS to
            "与平时持平",
        Language.ZH_HANT to
            "與平時持平",
        Language.EN to
            "Level with usual",
        Language.KO to
            "평소와 같은 수준",
        ),
        "s_e624a4f0" to mapOf(
        Language.ZH_HANS to
            "与平时相当",
        Language.ZH_HANT to
            "與平時相當",
        Language.EN to
            "About the same as usual",
        Language.KO to
            "평소와 비슷함",
        ),
        "s_e696069a" to mapOf(
        Language.ZH_HANS to
            "检测到不规则节律的迹象",
        Language.ZH_HANT to
            "偵測到不規則節律的跡象",
        Language.EN to
            "Signs of an irregular rhythm detected",
        Language.KO to
            "불규칙한 리듬의 징후 감지됨",
        ),
        "s_e69fba24" to mapOf(
        Language.ZH_HANS to
            "但那个数字不代表 HRV。请以心电的节律结论为准",
        Language.ZH_HANT to
            "但那個數字不代表 HRV。請以心電的節律結論為準",
        Language.EN to
            "but that number does not represent HRV. Go by the ECG's rhythm conclusion",
        Language.KO to
            "하지만 그 숫자는 HRV를 나타내지 않습니다. 심전도의 리듬 결론을 기준으로 하세요",
        ),
        "s_e79f0a9c" to mapOf(
        Language.ZH_HANS to
            "我们从三星读出来再写回 HC，就是重复计数 + 来源冒充 —— ",
        Language.ZH_HANT to
            "我們從三星讀出來再寫回 HC，就是重複計數 + 來源冒充 —— ",
        Language.EN to
            "If we read them from Samsung and write them back to HC, that is double counting" +
            "plus source impersonation — ",
        Language.KO to
            "저희가 삼성에서 읽어 HC에 다시 쓰면 중복 집계에 소스 사칭이 됩니다 — ",
        ),
        "s_e7dd54ad" to mapOf(
        Language.ZH_HANS to
            "超短时（不足 1 分钟）",
        Language.ZH_HANT to
            "超短時（不足 1 分鐘）",
        Language.EN to
            "Ultra-short (under 1 minute)",
        Language.KO to
            "초단기(1분 미만)",
        ),
        "s_e896af1e" to mapOf(
        Language.ZH_HANS to
            "授权同步给三星健康才读得到；该功能分国家/地区开放。",
        Language.ZH_HANT to
            "授權同步給三星健康才讀得到；該功能分國家/地區開放。",
        Language.EN to
            "grant permission to sync it to Samsung Health before we can read it; this feature" +
            "is available by country/region.",
        Language.KO to
            "Samsung Health로의 동기화를 허용해야 읽을 수 있습니다. 이 기능은 국가/지역별로 제공됩니다.",
        ),
        "s_e8aeffb7" to mapOf(
        Language.ZH_HANS to
            "水中健美操",
        Language.ZH_HANT to
            "水中健美操",
        Language.EN to
            "Water aerobics",
        Language.KO to
            "아쿠아로빅",
        ),
        "s_e947c4bb" to mapOf(
        Language.ZH_HANS to
            "逐拍间期（心电）",
        Language.ZH_HANT to
            "逐拍間期（心電）",
        Language.EN to
            "Beat-to-beat interval (ECG)",
        Language.KO to
            "박동 간 간격(심전도)",
        ),
        "s_ea2a394b" to mapOf(
        Language.ZH_HANS to
            "（RMSSD 的一个单位 = 相对变化 30%，心率 8%，皮温−环境温 1.0℃）。",
        Language.ZH_HANT to
            "（RMSSD 的一個單位 = 相對變化 30%，心率 8%，皮溫−環境溫 1.0℃）。",
        Language.EN to
            "(one unit of RMSSD = a 30% relative change, heart rate 8%, skin temp − ambient" +
            "temp 1.0°C).",
        Language.KO to
            "(RMSSD 한 단위 = 상대 변화 30%, 심박수 8%, 피부 온도 − 환경 온도 1.0°C).",
        ),
        "s_eabedb17" to mapOf(
        Language.ZH_HANS to
            "只用指尖压住两个电极键，别碰到表壳金属边框。",
        Language.ZH_HANT to
            "只用指尖壓住兩個電極鍵，別碰到錶殼金屬邊框。",
        Language.EN to
            "Press only the two electrode buttons with your fingertips, and do not touch the" +
            "watch case's metal bezel.",
        Language.KO to
            "손끝으로 두 전극 버튼만 누르고 시계 케이스의 금속 베젤에는 닿지 마세요.",
        ),
        "s_ece324b6" to mapOf(
        Language.ZH_HANS to
            "等待手表连接…",
        Language.ZH_HANT to
            "等待手錶連線…",
        Language.EN to
            "Waiting for the watch to connect...",
        Language.KO to
            "워치 연결 대기 중...",
        ),
        "s_ecf14dfb" to mapOf(
        Language.ZH_HANS to
            "02:30 之后归零。早睡不扣分 —— 22:00 睡和 23:30 睡一样是满分，",
        Language.ZH_HANT to
            "02:30 之後歸零。早睡不扣分 —— 22:00 睡和 23:30 睡一樣是滿分，",
        Language.EN to
            "and it drops to zero after 02:30. Going to bed early is not penalized — sleeping" +
            "at 22:00 and at 23:30 both score full marks, ",
        Language.KO to
            "02:30 이후에는 0점이 됩니다. 일찍 자는 것은 감점 없음 — 22:00에 자든 23:30에 자든 모두 만점입니다. ",
        ),
        "s_ee4e46ec" to mapOf(
        Language.ZH_HANS to
            "此时总分按其余分量的权重归一，不会因为缺项被拉低。",
        Language.ZH_HANT to
            "此時總分按其餘分量的權重歸一，不會因為缺項被拉低。",
        Language.EN to
            "in that case the total is normalized by the weights of the remaining components" +
            "and will not be dragged down by the missing items.",
        Language.KO to
            "이때 총점은 나머지 성분의 가중치로 정규화되며, 빠진 항목 때문에 낮아지지 않습니다.",
        ),
        "s_eef6b041" to mapOf(
        Language.ZH_HANS to
            "信号质量低，请保持静止…",
        Language.ZH_HANT to
            "訊號品質低，請保持靜止…",
        Language.EN to
            "Low signal quality; stay still…",
        Language.KO to
            "신호 품질이 낮습니다. 정지해 주세요…",
        ),
        "s_ef6e6608" to mapOf(
        Language.ZH_HANS to
            "心搏间隔",
        Language.ZH_HANT to
            "心搏間隔",
        Language.EN to
            "Beat interval",
        Language.KO to
            "박동 간격",
        ),
        "s_efa90637" to mapOf(
        Language.ZH_HANS to
            "本次没有可用的 HRV 数值（RMSSD 或平均心率无效），无法评分。",
        Language.ZH_HANT to
            "本次沒有可用的 HRV 數值（RMSSD 或平均心率無效），無法評分。",
        Language.EN to
            "There is no usable HRV value for this measurement (RMSSD or average heart rate is" +
            "invalid), so it cannot be scored.",
        Language.KO to
            "이번 측정에는 사용할 수 있는 HRV 값이 없어(RMSSD 또는 평균 심박수가 유효하지 않음) 점수를 낼 수 없습니다.",
        ),
        "s_effc0521" to mapOf(
        Language.ZH_HANS to
            "孤证不足以采信）—— 换个更安静的时段重测更可靠",
        Language.ZH_HANT to
            "孤證不足以採信）—— 換個更安靜的時段重測更可靠",
        Language.EN to
            "a single line of evidence is not enough to rely on) — measuring again in a quieter" +
            "period is more reliable",
        Language.KO to
            "단일 근거만으로는 신뢰하기 어렵습니다) — 더 조용한 시간대에 다시 측정하는 편이 더 신뢰할 만합니다",
        ),
        "s_f026e45f" to mapOf(
        Language.ZH_HANS to
            "循环训练",
        Language.ZH_HANT to
            "循環訓練",
        Language.EN to
            "Circuit training",
        Language.KO to
            "서킷 트레이닝",
        ),
        "s_f047b24e" to mapOf(
        Language.ZH_HANS to
            "注册加速度监听失败",
        Language.ZH_HANT to
            "註冊加速度監聽失敗",
        Language.EN to
            "Failed to register the accelerometer listener",
        Language.KO to
            "가속도 리스너 등록 실패",
        ),
        "s_f0ae92be" to mapOf(
        Language.ZH_HANS to
            "睡眠时长、最低血氧、皮肤温度偏差、睡眠定时、活动相关的那三项用绝对刻度 ——",
        Language.ZH_HANT to
            "睡眠時長、最低血氧、皮膚溫度偏差、睡眠定時、活動相關的那三項用絕對刻度 ——",
        Language.EN to
            "Sleep duration, lowest blood oxygen, skin temperature deviation, sleep timing, and" +
            "the three activity-related items use absolute scales — ",
        Language.KO to
            "수면 시간, 최저 혈중 산소, 피부 온도 편차, 수면 시각, 활동 관련 세 항목은 절대 척도를 사용합니다 — ",
        ),
        "s_f0fa9406" to mapOf(
        Language.ZH_HANS to
            "水上自行车",
        Language.ZH_HANT to
            "水上自行車",
        Language.EN to
            "Aqua cycling",
        Language.KO to
            "아쿠아 사이클링",
        ),
        "s_f1123087" to mapOf(
        Language.ZH_HANS to
            "频域：只给 HF（呼吸性窦性心律不齐）。LF 频带下限 0.04Hz 的一个完整周期就要 25 秒，",
        Language.ZH_HANT to
            "頻域：只給 HF（呼吸性竇性心律不整）。LF 頻帶下限 0.04Hz 的一個完整週期就要 25 秒，",
        Language.EN to
            "Frequency domain: only HF (respiratory sinus arrhythmia). One full cycle at the LF" +
            "band's lower bound of 0.04 Hz takes 25 seconds, ",
        Language.KO to
            "주파수 영역: HF(호흡성 동성 부정맥)만 제공합니다. LF 대역 하한 0.04Hz의 한 주기가 25초이므로 ",
        ),
        "s_f1af72a9" to mapOf(
        Language.ZH_HANS to
            "本次测量是轻度活动：这组数字反映的是「边走边测」，不要与静息记录直接比较",
        Language.ZH_HANT to
            "本次測量是輕度活動：這組數字反映的是「邊走邊測」，不要與靜息記錄直接比較",
        Language.EN to
            "This measurement was light activity: these numbers reflect \"measured while" +
            "walking\", so do not compare them directly with resting records",
        Language.KO to
            "이번 측정은 가벼운 활동이었습니다: 이 수치들은 '걸으면서 측정'한 것을 반영하므로 안정 시 기록과 직접 비교하지 마세요",
        ),
        "s_f26a5fb8" to mapOf(
        Language.ZH_HANS to
            "未知原因",
        Language.ZH_HANT to
            "未知原因",
        Language.EN to
            "Unknown reason",
        Language.KO to
            "알 수 없는 원인",
        ),
        "s_f2898c1c" to mapOf(
        Language.ZH_HANS to
            "未知通道",
        Language.ZH_HANT to
            "未知通道",
        Language.EN to
            "Unknown channel",
        Language.KO to
            "알 수 없는 채널",
        ),
        "s_f2ee522f" to mapOf(
        Language.ZH_HANS to
            "节律不齐但数据量不足，请重新测量",
        Language.ZH_HANT to
            "節律不齊但資料量不足，請重新測量",
        Language.EN to
            "Irregular rhythm but insufficient data; please measure again",
        Language.KO to
            "리듬이 불규칙하지만 데이터가 부족합니다. 다시 측정하세요",
        ),
        "s_f3576e8b" to mapOf(
        Language.ZH_HANS to
            "心率偏低",
        Language.ZH_HANT to
            "心率偏低",
        Language.EN to
            "Heart rate low",
        Language.KO to
            "심박수 낮음",
        ),
        "s_f357ba15" to mapOf(
        Language.ZH_HANS to
            "心率偏高",
        Language.ZH_HANT to
            "心率偏高",
        Language.EN to
            "Heart rate high",
        Language.KO to
            "심박수 높음",
        ),
        "s_f550b283" to mapOf(
        Language.ZH_HANS to
            "未检测到迹象",
        Language.ZH_HANT to
            "未偵測到跡象",
        Language.EN to
            "No signs detected",
        Language.KO to
            "징후 감지되지 않음",
        ),
        "s_f55a91ff" to mapOf(
        Language.ZH_HANS to
            "两个限定是必须的：「夜间」—— 只有睡觉时全身血管扩张、皮温又高又稳，",
        Language.ZH_HANT to
            "兩個限定是必須的：「夜間」—— 只有睡覺時全身血管擴張、皮溫又高又穩，",
        Language.EN to
            "Both qualifications are necessary: \"nighttime\" — only during sleep do the body's" +
            "vessels dilate and skin temperature become high and stable, ",
        Language.KO to
            "두 가지 한정은 필수입니다: '야간' — 잠잘 때만 전신 혈관이 확장되어 피부 온도가 높고 안정적이며 ",
        ),
        "s_f60c0fc9" to mapOf(
        Language.ZH_HANS to
            "我们不会替你编一个目标，没设过这一项就不参与，其余分量按权重归一。",
        Language.ZH_HANT to
            "我們不會替你編一個目標，沒設過這一項就不參與，其餘分量按權重歸一。",
        Language.EN to
            "we will not invent a goal for you; if you have never set one this item does not" +
            "participate and the remaining components are normalized by weight.",
        Language.KO to
            "저희가 목표를 임의로 만들지 않습니다. 설정한 적이 없으면 이 항목은 참여하지 않고 나머지 성분을 가중치로 정규화합니다.",
        ),
        "s_f692633c" to mapOf(
        Language.ZH_HANS to
            "写进去会污染对方的核心体温序列。",
        Language.ZH_HANT to
            "寫進去會污染對方的核心體溫序列。",
        Language.EN to
            "Writing it in would contaminate the other side's core body temperature series.",
        Language.KO to
            "써 넣으면 상대의 심부 체온 시계열을 오염시킵니다.",
        ),
        "s_f6ac3f6f" to mapOf(
        Language.ZH_HANS to
            "这里填的是你自己用血压计测到的读数，会按你填的原样保存。",
        Language.ZH_HANT to
            "這裡填的是你自己用血壓計測到的讀數，會按你填的原樣保存。",
        Language.EN to
            "What you enter here is the reading you measured with your own blood pressure" +
            "monitor, and it is saved exactly as entered.",
        Language.KO to
            "여기에 입력하는 것은 본인이 혈압계로 측정한 값이며, 입력한 그대로 저장됩니다.",
        ),
        "s_f6b76756" to mapOf(
        Language.ZH_HANS to
            "近期 vs 更早 · RMSSD",
        Language.ZH_HANT to
            "近期 vs 更早 · RMSSD",
        Language.EN to
            "Recent vs earlier · RMSSD",
        Language.KO to
            "최근 vs 이전 · RMSSD",
        ),
        "s_f779d324" to mapOf(
        Language.ZH_HANS to
            "躯干 0.5067、手 0.7665 —— 该表未单列腕部，故取两者中点 0.6366 作中心值，",
        Language.ZH_HANT to
            "軀幹 0.5067、手 0.7665 —— 該表未單列腕部，故取兩者中點 0.6366 作中心值，",
        Language.EN to
            "torso 0.5067, hand 0.7665 — the table does not list the wrist separately, so the" +
            "midpoint 0.6366 of the two is taken as the central value, ",
        Language.KO to
            "몸통 0.5067, 손 0.7665 — 이 표에는 손목이 따로 없어 둘의 중간값 0.6366을 중심값으로 삼고 ",
        ),
        "s_f7b279ee" to mapOf(
        Language.ZH_HANS to
            "SDK_POLICY_ERROR：请在手表上开启 Health Sensor Service 开发者模式",
        Language.ZH_HANT to
            "SDK_POLICY_ERROR：請在手錶上開啟 Health Sensor Service 開發者模式",
        Language.EN to
            "SDK_POLICY_ERROR: enable Health Sensor Service developer mode on the watch",
        Language.KO to
            "SDK_POLICY_ERROR: 워치에서 Health Sensor Service 개발자 모드를 켜세요",
        ),
        "s_f7e7aca0" to mapOf(
        Language.ZH_HANS to
            "未检测到也不代表没有 —— 有疑虑请就医。",
        Language.ZH_HANT to
            "未偵測到也不代表沒有 —— 有疑慮請就醫。",
        Language.EN to
            "not detecting it does not mean it is absent — if you have concerns, see a doctor.",
        Language.KO to
            "감지되지 않았다고 해서 없는 것은 아닙니다 — 우려되면 병원에 가 보시기 바랍니다.",
        ),
        "s_f84ce9c2" to mapOf(
        Language.ZH_HANS to
            "PERMISSION_ERROR：缺少 READ_ADDITIONAL_HEALTH_DATA 权限",
        Language.ZH_HANT to
            "PERMISSION_ERROR：缺少 READ_ADDITIONAL_HEALTH_DATA 權限",
        Language.EN to
            "PERMISSION_ERROR: READ_ADDITIONAL_HEALTH_DATA permission missing",
        Language.KO to
            "PERMISSION_ERROR: READ_ADDITIONAL_HEALTH_DATA 권한 없음",
        ),
        "s_f85da64a" to mapOf(
        Language.ZH_HANS to
            "若你在三星健康里看得到血糖，请等一次同步后回来。",
        Language.ZH_HANT to
            "若你在三星健康裡看得到血糖，請等一次同步後回來。",
        Language.EN to
            "if you can see blood glucose in Samsung Health, come back after one sync.",
        Language.KO to
            "Samsung Health에서 혈당이 보인다면 한 번 동기화한 뒤 다시 확인해 주세요.",
        ),
        "s_f86b7836" to mapOf(
        Language.ZH_HANS to
            "阻抗超出可测范围",
        Language.ZH_HANT to
            "阻抗超出可測範圍",
        Language.EN to
            "Impedance outside the measurable range",
        Language.KO to
            "임피던스가 측정 가능 범위를 벗어남",
        ),
        "s_fb10b6bb" to mapOf(
        Language.ZH_HANS to
            "前一日的活动",
        Language.ZH_HANT to
            "前一日的活動",
        Language.EN to
            "Previous day's activity",
        Language.KO to
            "전날의 활동",
        ),
        "s_fb432e41" to mapOf(
        Language.ZH_HANS to
            "一段准备时间（PEP），而后一段在手表上看不见、还会随情绪、咖啡因、姿势变化",
        Language.ZH_HANT to
            "一段準備時間（PEP），而後一段在手錶上看不見、還會隨情緒、咖啡因、姿勢變化",
        Language.EN to
            "preparation interval before the heart begins to contract (PEP), whereas the latter" +
            "is invisible on a watch and varies with emotion, caffeine, and posture ",
        Language.KO to
            "준비 시간(PEP)을 더한 것입니다. 후자는 시계에서 볼 수 없고 감정, 카페인, 자세에 따라 ",
        ),
        "s_fcf7ba99" to mapOf(
        Language.ZH_HANS to
            "短时（1~5 分钟）",
        Language.ZH_HANT to
            "短時（1~5 分鐘）",
        Language.EN to
            "Short-term (1–5 minutes)",
        Language.KO to
            "단기(1~5분)",
        ),
        "s_fd0804d6" to mapOf(
        Language.ZH_HANS to
            "初步预测（历史较少）",
        Language.ZH_HANT to
            "初步預測（歷史較少）",
        Language.EN to
            "Preliminary prediction (limited history)",
        Language.KO to
            "예비 예측(기록 적음)",
        ),
        "s_fd93232e" to mapOf(
        Language.ZH_HANS to
            "这不是发烧判断。 腕部皮肤温度不等于体温，它受室温、被褥、袖口、表带松紧影响，",
        Language.ZH_HANT to
            "這不是發燒判斷。 腕部皮膚溫度不等於體溫，它受室溫、被褥、袖口、錶帶鬆緊影響，",
        Language.EN to
            "This is not a fever test. Wrist skin temperature is not body temperature; it is" +
            "affected by room temperature, bedding, sleeves, and band tightness, ",
        Language.KO to
            "이것은 발열 판단이 아닙니다. 손목 피부 온도는 체온이 아니며 실내 온도, 이불, 소매, 밴드 조임의 영향을 받습니다. ",
        ),
        "s_fd9b38a1" to mapOf(
        Language.ZH_HANS to
            "室温与被褥也相对固定；「相对自己」—— 腕部皮温的绝对值在人与人之间差得很远，",
        Language.ZH_HANT to
            "室溫與被褥也相對固定；「相對自己」—— 腕部皮溫的絕對值在人與人之間差得很遠，",
        Language.EN to
            "and room temperature and bedding are relatively fixed; \"relative to yourself\" —" +
            "the absolute value of wrist skin temperature varies greatly from person to person,",
        Language.KO to
            "실내 온도와 이불도 비교적 일정합니다; '본인 기준' — 손목 피부 온도의 절대값은 사람마다 크게 차이 나므로 ",
        ),
        "s_fdddefe4" to mapOf(
        Language.ZH_HANS to
            "与临床口径一致；不用变异系数，否则「稳定 40 天」会被算成比「21~28 天波动」更规律，",
        Language.ZH_HANT to
            "與臨床口徑一致；不用變異係數，否則「穩定 40 天」會被算成比「21~28 天波動」更規律，",
        Language.EN to
            "consistent with the clinical definition; the coefficient of variation is not used," +
            "otherwise \"a steady 40 days\" would count as more regular than \"fluctuating" +
            "between 21 and 28 days\", ",
        Language.KO to
            "임상 기준과 일치합니다. 변동계수는 사용하지 않습니다. 그렇지 않으면 '안정적인 40일'이 '21~28일 변동'보다 더 규칙적인 것으로 계산되기" +
            "때문입니다. ",
        ),
        "s_ffa0de79" to mapOf(
        Language.ZH_HANS to
            "两个分数不相等是正常的，也不该拿来互相印证谁对：即使输入看起来相似，",
        Language.ZH_HANT to
            "兩個分數不相等是正常的，也不該拿來互相印證誰對：即使輸入看起來相似，",
        Language.EN to
            "It is normal for the two scores not to be equal, and they should not be used to" +
            "corroborate which one is right: even if the inputs look similar, ",
        Language.KO to
            "두 점수가 같지 않은 것은 정상이며, 어느 쪽이 맞는지 교차 검증에 사용해서는 안 됩니다: 입력이 비슷해 보여도 ",
        ),
        "s_ffd110bc" to mapOf(
        Language.ZH_HANS to
            "呼吸率这个数据类型本身也没有开放。所以本页只在我们自己采到原始波形时给数，",
        Language.ZH_HANT to
            "呼吸率這個資料類型本身也沒有開放。所以本頁只在我們自己採到原始波形時給數，",
        Language.EN to
            "the respiratory-rate data type itself is not exposed either. So this page only" +
            "gives a value when we capture the raw waveform ourselves, ",
        Language.KO to
            "호흡수 데이터 유형 자체도 개방되지 않았습니다. 따라서 이 페이지는 저희가 원시 파형을 직접 수집했을 때만 값을 제공하며 ",
        ),
    )

    /**
     * 全部**有插值**文案：值是**带 `%N$s` 的模板**，占位符序号查
     * [placeholder_map]（**以 zhHans 为权威**，各语言语序可不同）。
     *
     * ⚠ 取用一律走 [fmt]，不要自己 `String.format(Text.of(...))` ——
     * 那样会绕过「实参个数校验」，参数不齐时是**运行期异常**。
     */
    private val TPL: Map<String, Map<Language, String>> = mapOf(
        "s_024cb9fc" to mapOf(
        Language.ZH_HANS to
            "表体温度读数 %1\$s℃ 不低于皮温 %2\$s℃：",
        Language.ZH_HANT to
            "錶體溫度讀數 %1\$s℃ 不低於皮溫 %2\$s℃：",
        Language.EN to
            "The body temperature reading %1\$s°C is not lower than the skin temperature" +
            "%2\$s°C: ",
        Language.KO to
            "시계 본체 온도 판독값 %1\$s°C가 피부 온도 %2\$s°C보다 낮지 않습니다: ",
        ),
        "s_02b69cea" to mapOf(
        Language.ZH_HANS to
            "%1\$s 小时 —— ",
        Language.ZH_HANT to
            "%1\$s 小時 —— ",
        Language.EN to
            "%1\$s hours — ",
        Language.KO to
            "%1\$s시간을 포괄해야 합니다 — ",
        ),
        "s_034c12b9" to mapOf(
        Language.ZH_HANS to
            "数据解析异常: %1\$s: %2\$s",
        Language.ZH_HANT to
            "資料解析異常：%1\$s: %2\$s",
        Language.EN to
            "Data parsing error: %1\$s: %2\$s",
        Language.KO to
            "데이터 파싱 오류: %1\$s: %2\$s",
        ),
        "s_05d7346b" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次取不到搜索窗（落在脉搏波有数据的块之外，或相邻两拍挨得太近）",
        Language.ZH_HANT to
            "%1\$s 次取不到搜尋窗（落在脈搏波有資料的塊之外，或相鄰兩拍挨得太近）",
        Language.EN to
            "%1\$s times no search window could be taken (outside the blocks where pulse-wave" +
            "data exists, or two adjacent beats too close together)",
        Language.KO to
            "%1\$s회 검색 창을 얻지 못했습니다(맥파 데이터가 있는 블록 밖에 있거나 인접한 두 박동이 너무 가까움)",
        ),
        "s_0628cf70" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次搜索窗里的有值点太少（这一路只有 ",
        Language.ZH_HANT to
            "%1\$s 次搜尋窗裡的有值點太少（這一路只有 ",
        Language.EN to
            "%1\$s search windows had too few valid points (this signal has only ",
        Language.KO to
            "%1\$s회 검색 창의 유효점이 너무 적었습니다(이 갈래는 ",
        ),
        "s_0772dd89" to mapOf(
        Language.ZH_HANS to
            "心电波形过弱（幅度 %1\$s mV），",
        Language.ZH_HANT to
            "心電波形過弱（幅度 %1\$s mV），",
        Language.EN to
            "ECG waveform too weak (amplitude %1\$s mV), ",
        Language.KO to
            "심전도 파형이 너무 약합니다(진폭 %1\$s mV), ",
        ),
        "s_078573c4" to mapOf(
        Language.ZH_HANS to
            "这段光信号只检出 %1\$s 个心搏，不足以构成呼吸调制序列（至少需要 4 个）",
        Language.ZH_HANT to
            "這段光訊號只檢出 %1\$s 個心搏，不足以構成呼吸調製序列（至少需要 4 個）",
        Language.EN to
            "Only %1\$s heartbeats were detected in this light signal, not enough to form a" +
            "respiratory-modulation series (at least 4 are needed)",
        Language.KO to
            "이 광신호에서 검출된 심장 박동이 %1\$s개뿐이라 호흡 변조 시계열을 구성하기에 부족합니다(최소 4개 필요)",
        ),
        "s_08deb072" to mapOf(
        Language.ZH_HANS to
            "只拿到 %1\$s 个频点的有效阻抗（至少需要 %2\$s 个）——",
        Language.ZH_HANT to
            "只拿到 %1\$s 個頻點的有效阻抗（至少需要 %2\$s 個）——",
        Language.EN to
            "Only %1\$s frequencies yielded valid impedance (at least %2\$s are needed) — ",
        Language.KO to
            "유효한 임피던스를 얻은 주파수가 %1\$s개뿐입니다(최소 %2\$s개 필요) — ",
        ),
        "s_0993f376" to mapOf(
        Language.ZH_HANS to
            "已剔除 %1\$s 个不可信读数（低于 %2\$s%% 或高于 %3\$s%%）——",
        Language.ZH_HANT to
            "已剔除 %1\$s 個不可信讀數（低於 %2\$s%% 或高於 %3\$s%%）——",
        Language.EN to
            "Removed %1\$s unreliable readings (below %2\$s%% or above %3\$s%%) — ",
        Language.KO to
            "신뢰할 수 없는 판독값 %1\$s개를 제외했습니다(%2\$s%% 미만 또는 %3\$s%% 초과) — ",
        ),
        "s_0998483c" to mapOf(
        Language.ZH_HANS to
            "采样率 %1\$s Hz 太低，分辨不出呼吸调制",
        Language.ZH_HANT to
            "取樣率 %1\$s Hz 太低，分辨不出呼吸調製",
        Language.EN to
            "The sampling rate of %1\$s Hz is too low to resolve respiratory modulation",
        Language.KO to
            "샘플링 레이트 %1\$s Hz가 너무 낮아 호흡 변조를 구분할 수 없습니다",
        ),
        "s_0a1457be" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s /min",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_0b2c570b" to mapOf(
        Language.ZH_HANS to
            "设备不支持体成分测量（能力：%1\$s）",
        Language.ZH_HANT to
            "裝置不支援體成分測量（能力：%1\$s）",
        Language.EN to
            "The device does not support body composition measurement (capabilities: %1\$s)",
        Language.KO to
            "이 기기는 체성분 측정을 지원하지 않습니다(기능: %1\$s)",
        ),
        "s_0c67abd7" to mapOf(
        Language.ZH_HANS to
            "RMSSD 算出 %1\$s 毫秒，超过 %2\$s 毫秒的物理上限 —— ",
        Language.ZH_HANT to
            "RMSSD 算出 %1\$s 毫秒，超過 %2\$s 毫秒的物理上限 —— ",
        Language.EN to
            "The computed RMSSD is %1\$s ms, exceeding the physical upper limit of %2\$s ms — ",
        Language.KO to
            "계산된 RMSSD는 %1\$s밀리초로 물리적 상한 %2\$s밀리초를 넘습니다 — ",
        ),
        "s_0dd201af" to mapOf(
        Language.ZH_HANS to
            "（%1\$s 天前），之后再没有新的睡眠记录 —— ",
        Language.ZH_HANT to
            "（%1\$s 天前），之後再也沒有新的睡眠記錄 —— ",
        Language.EN to
            "(%1\$s days ago), and there have been no new sleep records since — ",
        Language.KO to
            "(%1\$s일 전)이며 이후 새로운 수면 기록이 없습니다 — ",
        ),
        "s_0f5978a1" to mapOf(
        Language.ZH_HANS to
            "这次只有 %1\$s 个分量参与",
        Language.ZH_HANT to
            "這次只有 %1\$s 個分量參與",
        Language.EN to
            "only %1\$s components participated this time",
        Language.KO to
            "이번에는 %1\$s개 성분만 참여했습니다",
        ),
        "s_1097ca05" to mapOf(
        Language.ZH_HANS to
            "丢弃 %1\$s 拍）超过上限 %2\$s%%。",
        Language.ZH_HANT to
            "丟棄 %1\$s 拍）超過上限 %2\$s%%。",
        Language.EN to
            "discarded %1\$s beats) exceeds the upper limit of %2\$s%%.",
        Language.KO to
            "폐기 %1\$s박동)이 상한 %2\$s%%를 넘었습니다.",
        ),
        "s_10cf7950" to mapOf(
        Language.ZH_HANS to
            "连接失败(%1\$s): %2\$s",
        Language.ZH_HANT to
            "連線失敗(%1\$s): %2\$s",
        Language.EN to
            "Connection failed (%1\$s): %2\$s",
        Language.KO to
            "연결 실패(%1\$s): %2\$s",
        ),
        "s_10cfc4bb" to mapOf(
        Language.ZH_HANS to
            "这一晚（%1\$s）的十个输入一个都没有：",
        Language.ZH_HANT to
            "這一晚（%1\$s）的十個輸入一個都沒有：",
        Language.EN to
            "For this night (%1\$s) not a single one of the ten inputs is available: ",
        Language.KO to
            "이 날 밤(%1\$s)의 열 개 입력 중 하나도 없습니다: ",
        ),
        "s_122824e7" to mapOf(
        Language.ZH_HANS to
            "±%1\$s 次/分",
        Language.ZH_HANT to
            "±%1\$s 次/分",
        Language.EN to
            "±%1\$s bpm",
        Language.KO to
            "±%1\$s 회/분",
        ),
        "s_12d7163e" to mapOf(
        Language.ZH_HANS to
            "已采集 %1\$s 个逐拍间期",
        Language.ZH_HANT to
            "已採集 %1\$s 個逐拍間期",
        Language.EN to
            "Collected %1\$s beat-to-beat intervals",
        Language.KO to
            "박동 간격 %1\$s개 수집됨",
        ),
        "s_12dbc408" to mapOf(
        Language.ZH_HANS to
            "%1\$s 小时），皮温分量未参与，其余分量按权重归一",
        Language.ZH_HANT to
            "%1\$s 小時），皮溫分量未參與，其餘分量按權重歸一",
        Language.EN to
            "%1\$s hours), so the skin-temperature component did not participate and the" +
            "remaining components are normalized by weight",
        Language.KO to
            "%1\$s시간 이내). 피부 온도 성분은 참여하지 않았고 나머지 성분을 가중치로 정규화했습니다",
        ),
        "s_12ee6c7e" to mapOf(
        Language.ZH_HANS to
            "%1\$s · %2\$s 分",
        Language.ZH_HANT to
            "%1\$s · %2\$s 分",
        Language.EN to
            "%1\$s · %2\$s pts",
        Language.KO to
            "%1\$s · %2\$s점",
        ),
        "s_142e4e32" to mapOf(
        Language.ZH_HANS to
            "（需要至少 %1\$s 晚有睡眠期心率的记录，当前 %2\$s 晚）",
        Language.ZH_HANT to
            "（需要至少 %1\$s 晚有睡眠期心率的記錄，目前 %2\$s 晚）",
        Language.EN to
            "(at least %1\$s nights with a sleeping heart rate are needed; currently %2\$s" +
            "nights)",
        Language.KO to
            "(수면 심박수 기록이 있는 날이 최소 %1\$s일 필요하며, 현재 %2\$s일입니다)",
        ),
        "s_164bcab3" to mapOf(
        Language.ZH_HANS to
            "这一晚的睡眠时长不是有效值（记录为 %1\$s 毫秒），该分量未参与",
        Language.ZH_HANT to
            "這一晚的睡眠時長不是有效值（記錄為 %1\$s 毫秒），該分量未參與",
        Language.EN to
            "This night's sleep duration is not a valid value (recorded as %1\$s ms), so this" +
            "component did not participate",
        Language.KO to
            "이 날 밤의 수면 시간은 유효한 값이 아니어서(기록: %1\$s 밀리초) 이 성분은 참여하지 않았습니다",
        ),
        "s_16ad28e9" to mapOf(
        Language.ZH_HANS to
            "压力 %1\$s %2\$s",
        Language.ZH_HANT to
            "壓力 %1\$s %2\$s",
        Language.EN to
            "Stress %1\$s %2\$s",
        Language.KO to
            "스트레스 %1\$s %2\$s",
        ),
        "s_16d12029" to mapOf(
        Language.ZH_HANS to
            "%1\$s · 易孕期",
        Language.ZH_HANT to
            "%1\$s · 易孕期",
        Language.EN to
            "%1\$s · fertile window",
        Language.KO to
            "%1\$s · 가임기",
        ),
        "s_19a64c8e" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次算出的时延不在 %2\$s~%3\$s ms 内（波形形态不对）",
        Language.ZH_HANT to
            "%1\$s 次算出的時延不在 %2\$s~%3\$s ms 內（波形形態不對）",
        Language.EN to
            "%1\$s computed delays fell outside %2\$s–%3\$s ms (wrong waveform shape)",
        Language.KO to
            "%1\$s회 계산된 지연이 %2\$s~%3\$s ms 범위를 벗어났습니다(파형 형태가 잘못됨)",
        ),
        "s_19ce3b05" to mapOf(
        Language.ZH_HANS to
            "心率 %1\$s 次/分，超出可判定范围（40~180 次/分）",
        Language.ZH_HANT to
            "心率 %1\$s 次/分，超出可判定範圍（40~180 次/分）",
        Language.EN to
            "Heart rate %1\$s bpm is outside the assessable range (40–180 bpm)",
        Language.KO to
            "심박수 %1\$s 회/분은 판정 가능 범위(40~180 회/분)를 벗어났습니다",
        ),
        "s_1a407d1a" to mapOf(
        Language.ZH_HANS to
            "比平时长 %1\$s ms（传导更慢）",
        Language.ZH_HANT to
            "比平時長 %1\$s ms（傳導更慢）",
        Language.EN to
            "%1\$s ms longer than usual (slower conduction)",
        Language.KO to
            "평소보다 %1\$s ms 김(전도가 더 느림)",
        ),
        "s_1bf06354" to mapOf(
        Language.ZH_HANS to
            "%1\$s%% 的包带脉搏波）",
        Language.ZH_HANT to
            "%1\$s%% 的包帶脈搏波）",
        Language.EN to
            "%1\$s%% pulse-wave coverage)",
        Language.KO to
            "%1\$s%%의 맥파 포함)",
        ),
        "s_1c5cb884" to mapOf(
        Language.ZH_HANS to
            "比平时略长 %1\$s ms",
        Language.ZH_HANT to
            "比平時略長 %1\$s ms",
        Language.EN to
            "Slightly longer than usual by %1\$s ms",
        Language.KO to
            "평소보다 약간 김 %1\$s ms",
        ),
        "s_21ac845b" to mapOf(
        Language.ZH_HANS to
            "有值占比 %1\$s%%",
        Language.ZH_HANT to
            "有值佔比 %1\$s%%",
        Language.EN to
            "valid ratio %1\$s%%",
        Language.KO to
            "유효 비율 %1\$s%%",
        ),
        "s_22250a66" to mapOf(
        Language.ZH_HANS to
            "两路心率对不上（脉搏 %1\$s、心电 %2\$s，差 %3\$s%%）——",
        Language.ZH_HANT to
            "兩路心率對不上（脈搏 %1\$s、心電 %2\$s，差 %3\$s%%）——",
        Language.EN to
            "The two heart rates do not match (pulse %1\$s, ECG %2\$s, differing by %3\$s%%) — ",
        Language.KO to
            "두 심박수가 맞지 않습니다(맥박 %1\$s, 심전도 %2\$s, 차이 %3\$s%%) — ",
        ),
        "s_27769dd8" to mapOf(
        Language.ZH_HANS to
            "睡眠时段 %1\$s · %2\$s / 监测 ",
        Language.ZH_HANT to
            "睡眠時段 %1\$s · %2\$s / 監測 ",
        Language.EN to
            "Sleep period %1\$s · %2\$s / monitoring ",
        Language.KO to
            "수면 시간대 %1\$s · %2\$s / 모니터링 ",
        ),
        "s_29600ac3" to mapOf(
        Language.ZH_HANS to
            "信号质量不足（相干性 %1\$s、",
        Language.ZH_HANT to
            "訊號品質不足（相干性 %1\$s、",
        Language.EN to
            "Signal quality too low (coherence %1\$s, ",
        Language.KO to
            "신호 품질 부족(일관성 %1\$s, ",
        ),
        "s_2ac2539f" to mapOf(
        Language.ZH_HANS to
            "本次光信号数据不足（%1\$s 点 / %2\$s 毫秒），已丢弃",
        Language.ZH_HANT to
            "本次光訊號資料不足（%1\$s 點 / %2\$s 毫秒），已丟棄",
        Language.EN to
            "Insufficient optical signal data this time (%1\$s points / %2\$s ms); discarded",
        Language.KO to
            "이번 광신호 데이터가 부족하여(%1\$s개 지점 / %2\$sms) 폐기했습니다",
        ),
        "s_2ad6b743" to mapOf(
        Language.ZH_HANS to
            "第 %1\$s 天 · 距下次 %2\$s 天",
        Language.ZH_HANT to
            "第 %1\$s 天 · 距下次 %2\$s 天",
        Language.EN to
            "Day %1\$s · %2\$s days to next",
        Language.KO to
            "%1\$s일째 · 다음까지 %2\$s일",
        ),
        "s_2ccfbf11" to mapOf(
        Language.ZH_HANS to
            "；越界 %1\$s 拍的时延 ",
        Language.ZH_HANT to
            "；越界 %1\$s 拍的時延 ",
        Language.EN to
            "; out-of-range delays for %1\$s beats ",
        Language.KO to
            "; 범위를 벗어난 %1\$s박동의 지연 ",
        ),
        "s_2d31f588" to mapOf(
        Language.ZH_HANS to
            "两边相差 %1\$s 分，属于同一量级",
        Language.ZH_HANT to
            "兩邊相差 %1\$s 分，屬於同一量級",
        Language.EN to
            "The two differ by %1\$s points, which is the same order of magnitude",
        Language.KO to
            "양쪽 차이는 %1\$s점으로 같은 수준입니다",
        ),
        "s_2d6769a9" to mapOf(
        Language.ZH_HANS to
            "推算结果 %1\$s℃ 高于可信区间上限 %2\$s℃：",
        Language.ZH_HANT to
            "推算結果 %1\$s℃ 高於可信區間上限 %2\$s℃：",
        Language.EN to
            "the estimated result %1\$s°C is above the upper bound of the plausible range" +
            "%2\$s°C: ",
        Language.KO to
            "추정 결과 %1\$s°C가 신뢰 구간 상한 %2\$s°C보다 높습니다: ",
        ),
        "s_2dd87159" to mapOf(
        Language.ZH_HANS to
            "传感器报错：%1\$s",
        Language.ZH_HANT to
            "感測器報錯：%1\$s",
        Language.EN to
            "Sensor error: %1\$s",
        Language.KO to
            "센서 오류: %1\$s",
        ),
        "s_2e1b6077" to mapOf(
        Language.ZH_HANS to
            "公告格式不认识（%1\$s）。",
        Language.ZH_HANT to
            "公告格式不認識（%1\$s）。",
        Language.EN to
            "Unrecognized announcement format (%1\$s).",
        Language.KO to
            "인식할 수 없는 공지 형식입니다(%1\$s).",
        ),
        "s_2eb5c82c" to mapOf(
        Language.ZH_HANS to
            "%1\$s ±%2\$s 天",
        Language.ZH_HANT to
            "%1\$s ±%2\$s 天",
        Language.EN to
            "%1\$s ±%2\$s days",
        Language.KO to
            "%1\$s ±%2\$s일",
        ),
        "s_30499470" to mapOf(
        Language.ZH_HANS to
            "Tracker 错误: %1\$s",
        Language.ZH_HANT to
            "Tracker 錯誤：%1\$s",
        Language.EN to
            "Tracker error: %1\$s",
        Language.KO to
            "Tracker 오류: %1\$s",
        ),
        "s_31585db9" to mapOf(
        Language.ZH_HANS to
            "压力 %1\$s · %2\$s",
        Language.ZH_HANT to
            "壓力 %1\$s · %2\$s",
        Language.EN to
            "Stress %1\$s · %2\$s",
        Language.KO to
            "스트레스 %1\$s · %2\$s",
        ),
        "s_32ae46fc" to mapOf(
        Language.ZH_HANS to
            "算的是 %1\$s 那一晚，不是今天",
        Language.ZH_HANT to
            "算的是 %1\$s 那一晚，不是今天",
        Language.EN to
            "This is computed for the night of %1\$s, not tonight",
        Language.KO to
            "%1\$s 밤을 기준으로 계산한 것이며, 오늘 밤이 아닙니다",
        ),
        "s_3351fb99" to mapOf(
        Language.ZH_HANS to
            "（至少需要 %1\$s 小时）—— ",
        Language.ZH_HANT to
            "（至少需要 %1\$s 小時）—— ",
        Language.EN to
            "(at least %1\$s hours are needed) — ",
        Language.KO to
            "(최소 %1\$s시간 필요) — ",
        ),
        "s_353d9c43" to mapOf(
        Language.ZH_HANS to
            "但最长的一段也放不下一个 %1\$s ms 的搜索窗。",
        Language.ZH_HANT to
            "但最長的一段也放不下一個 %1\$s ms 的搜尋窗。",
        Language.EN to
            "but even the longest segment cannot fit a %1\$s ms search window.",
        Language.KO to
            "그러나 가장 긴 구간조차 %1\$s ms 검색 창을 담을 수 없습니다.",
        ),
        "s_367069d0" to mapOf(
        Language.ZH_HANS to
            "公告格式升级了（v%1\$s），当前 App 还看不懂，请更新 App",
        Language.ZH_HANT to
            "公告格式升級了（v%1\$s），目前 App 還看不懂，請更新 App",
        Language.EN to
            "The announcement format was upgraded (v%1\$s) and this app version cannot read it;" +
            "please update the app",
        Language.KO to
            "공지 형식이 업그레이드되었습니다(v%1\$s). 현재 앱에서는 읽을 수 없으니 앱을 업데이트해 주세요",
        ),
        "s_36db3b3d" to mapOf(
        Language.ZH_HANS to
            "比参考心率高 %1\$s",
        Language.ZH_HANT to
            "比參考心率高 %1\$s",
        Language.EN to
            "%1\$s above the reference heart rate",
        Language.KO to
            "참조 심박수보다 %1\$s 높음",
        ),
        "s_386e068b" to mapOf(
        Language.ZH_HANS to
            "皮温 %1\$s℃ 与表体温度 %2\$s℃ 只差 %3\$s℃，",
        Language.ZH_HANT to
            "皮溫 %1\$s℃ 與錶體溫度 %2\$s℃ 只差 %3\$s℃，",
        Language.EN to
            "skin temperature %1\$s°C and body temperature %2\$s°C differ by only %3\$s°C, ",
        Language.KO to
            "피부 온도 %1\$s°C와 시계 본체 온도 %2\$s°C는 %3\$s°C밖에 차이 나지 않습니다. ",
        ),
        "s_38cba5ec" to mapOf(
        Language.ZH_HANS to
            "（当前 %1\$s 晚）",
        Language.ZH_HANT to
            "（目前 %1\$s 晚）",
        Language.EN to
            "(currently %1\$s nights)",
        Language.KO to
            "(현재 %1\$s일 밤)",
        ),
        "s_3d155d9d" to mapOf(
        Language.ZH_HANS to
            "这 %1\$s 个血氧读数全部不可信（低于 %2\$s%% 或高于 %3\$s%%）",
        Language.ZH_HANT to
            "這 %1\$s 個血氧讀數全部不可信（低於 %2\$s%% 或高於 %3\$s%%）",
        Language.EN to
            "All %1\$s blood-oxygen readings are unreliable (below %2\$s%% or above %3\$s%%)",
        Language.KO to
            "이 %1\$s개의 혈중 산소 판독값은 모두 신뢰할 수 없습니다(%2\$s%% 미만 또는 %3\$s%% 초과)",
        ),
        "s_3dbb2363" to mapOf(
        Language.ZH_HANS to
            "这段光信号的完整度只有 %1\$s%%",
        Language.ZH_HANT to
            "這段光訊號的完整度只有 %1\$s%%",
        Language.EN to
            "The completeness of this light signal is only %1\$s%%",
        Language.KO to
            "이 광신호의 완전도는 %1\$s%%에 불과합니다",
        ),
        "s_3e6bc4e9" to mapOf(
        Language.ZH_HANS to
            "目标 %1\$s 步",
        Language.ZH_HANT to
            "目標 %1\$s 步",
        Language.EN to
            "goal %1\$s steps",
        Language.KO to
            "목표 %1\$s걸음",
        ),
        "s_3ebccb9e" to mapOf(
        Language.ZH_HANS to
            "这段记录只有 %1\$s 秒，至少需要 ",
        Language.ZH_HANT to
            "這段記錄只有 %1\$s 秒，至少需要 ",
        Language.EN to
            "This record is only %1\$s seconds long, and at least ",
        Language.KO to
            "이 기록은 %1\$s초뿐이며 최소 ",
        ),
        "s_3fd7469e" to mapOf(
        Language.ZH_HANS to
            "算出的平均周期长度是 %1\$s 天，超出 %2\$s~%3\$s 天的合理范围。",
        Language.ZH_HANT to
            "算出的平均週期長度是 %1\$s 天，超出 %2\$s~%3\$s 天的合理範圍。",
        Language.EN to
            "The computed average cycle length is %1\$s days, outside the reasonable range of" +
            "%2\$s–%3\$s days.",
        Language.KO to
            "계산된 평균 주기 길이는 %1\$s일로, 합리적 범위인 %2\$s~%3\$s일을 벗어납니다.",
        ),
        "s_405fab2a" to mapOf(
        Language.ZH_HANS to
            "本 App 的分比三星低 %1\$s 分",
        Language.ZH_HANT to
            "本 App 的分比三星低 %1\$s 分",
        Language.EN to
            "This app's score is %1\$s points lower than Samsung's",
        Language.KO to
            "이 앱의 점수가 삼성보다 %1\$s점 낮습니다",
        ),
        "s_421f28b5" to mapOf(
        Language.ZH_HANS to
            " 秒）、有值占比 %1\$s%%",
        Language.ZH_HANT to
            " 秒）、有值佔比 %1\$s%%",
        Language.EN to
            " s), valid ratio %1\$s%%",
        Language.KO to
            "초), 유효 비율 %1\$s%%",
        ),
        "s_431a8f0f" to mapOf(
        Language.ZH_HANS to
            "%1\$s 米",
        Language.ZH_HANT to
            "%1\$s 公尺",
        Language.EN to
            "%1\$s m",
        Language.KO to
            "%1\$s m",
        ),
        "s_447443f6" to mapOf(
        Language.ZH_HANS to
            "置信%1\$s · 依据 %2\$s · 覆盖 %3\$s 次呼吸",
        Language.ZH_HANT to
            "置信%1\$s · 依據 %2\$s · 涵蓋 %3\$s 次呼吸",
        Language.EN to
            "Confidence %1\$s · based on %2\$s · covering %3\$s breaths",
        Language.KO to
            "신뢰도 %1\$s · 근거 %2\$s · %3\$s회 호흡 포함",
        ),
        "s_4577f457" to mapOf(
        Language.ZH_HANS to
            "%1\$s月%2\$s日",
        Language.ZH_HANT to
            "%1\$s月%2\$s日",
        Language.EN to
            "%1\$s/%2\$s",
        Language.KO to
            "%1\$s월 %2\$s일",
        ),
        "s_45f04e6b" to mapOf(
        Language.ZH_HANS to
            "「活动持续性」未参与：只取到 %1\$s 个时段（应为 %2\$s 段）",
        Language.ZH_HANT to
            "「活動持續性」未參與：只取到 %1\$s 個時段（應為 %2\$s 段）",
        Language.EN to
            "\"Activity spread\" did not participate: only %1\$s slots were obtained (should be" +
            "%2\$s)",
        Language.KO to
            "'활동 지속성'은 참여하지 않았습니다: %1\$s개 구간만 확보되었습니다(%2\$s개여야 함)",
        ),
        "s_4685eacb" to mapOf(
        Language.ZH_HANS to
            "ECG%1\$s SPO2%2\$s 体温%3\$s ",
        Language.ZH_HANT to
            "ECG%1\$s SPO2%2\$s 體溫%3\$s ",
        Language.EN to
            "ECG%1\$s SPO2%2\$s temp%3\$s ",
        Language.KO to
            "ECG%1\$s SPO2%2\$s 체온%3\$s ",
        ),
        "s_47dfe3a0" to mapOf(
        Language.ZH_HANS to
            "重试注册失败: %1\$s",
        Language.ZH_HANT to
            "重試註冊失敗：%1\$s",
        Language.EN to
            "Retry registration failed: %1\$s",
        Language.KO to
            "재시도 등록 실패: %1\$s",
        ),
        "s_486f9243" to mapOf(
        Language.ZH_HANS to
            "这段记录里识别的干扰偏多（%1\$s%%），",
        Language.ZH_HANT to
            "這段記錄裡識別的干擾偏多（%1\$s%%），",
        Language.EN to
            "Too much interference was identified in this record (%1\$s%%), ",
        Language.KO to
            "이 기록에서 식별된 간섭이 너무 많습니다(%1\$s%%). ",
        ),
        "s_48c84e5c" to mapOf(
        Language.ZH_HANS to
            "逐拍之间自己就不一致（离散度 ±%1\$s ms，上限 %2\$s ms）——",
        Language.ZH_HANT to
            "逐拍之間自己就不一致（離散度 ±%1\$s ms，上限 %2\$s ms）——",
        Language.EN to
            "The beats are themselves inconsistent (spread ±%1\$s ms, upper limit %2\$s ms) — ",
        Language.KO to
            "박동 간 자체가 일관되지 않습니다(산포 ±%1\$s ms, 상한 %2\$s ms) — ",
        ),
        "s_4907d3af" to mapOf(
        Language.ZH_HANS to
            "手表已连接：%1\$s",
        Language.ZH_HANT to
            "手錶已連線：%1\$s",
        Language.EN to
            "Watch connected: %1\$s",
        Language.KO to
            "워치 연결됨: %1\$s",
        ),
        "s_494665c5" to mapOf(
        Language.ZH_HANS to
            "伪迹比例 %1\$s%%（修正 %2\$s 拍、",
        Language.ZH_HANT to
            "偽跡比例 %1\$s%%（修正 %2\$s 拍、",
        Language.EN to
            "Artifact rate %1\$s%% (corrected %2\$s beats, ",
        Language.KO to
            "아티팩트 비율 %1\$s%%(보정 %2\$s박동, ",
        ),
        "s_4cd89620" to mapOf(
        Language.ZH_HANS to
            "比参考心率低 %1\$s",
        Language.ZH_HANT to
            "比參考心率低 %1\$s",
        Language.EN to
            "%1\$s below the reference heart rate",
        Language.KO to
            "참조 심박수보다 %1\$s 낮음",
        ),
        "s_4cf063d4" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s bpm",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_4f7ccb9f" to mapOf(
        Language.ZH_HANS to
            "已记录 %1\$s 次经期，但相邻两次的间隔都不在 ",
        Language.ZH_HANT to
            "已記錄 %1\$s 次經期，但相鄰兩次的間隔都不在 ",
        Language.EN to
            "%1\$s periods have been recorded, but none of the intervals between adjacent ones" +
            "falls within ",
        Language.KO to
            "월경을 %1\$s회 기록했지만 인접한 두 번의 간격이 모두 ",
        ),
        "s_4faccfa0" to mapOf(
        Language.ZH_HANS to
            "睡眠心率基线只有 %1\$s 晚，中位数还不够稳，",
        Language.ZH_HANT to
            "睡眠心率基線只有 %1\$s 晚，中位數還不夠穩，",
        Language.EN to
            "The sleeping heart-rate baseline is only %1\$s nights, so the median is not yet" +
            "stable enough, ",
        Language.KO to
            "수면 심박수 기준선이 %1\$s일 밤뿐이라 중앙값이 아직 충분히 안정적이지 않습니다. ",
        ),
        "s_5112833e" to mapOf(
        Language.ZH_HANS to
            "现在只有 %1\$s 晚",
        Language.ZH_HANT to
            "現在只有 %1\$s 晚",
        Language.EN to
            "currently there are only %1\$s nights",
        Language.KO to
            "현재는 %1\$s일 밤뿐입니다",
        ),
        "s_5313a347" to mapOf(
        Language.ZH_HANS to
            "现在填的是 %1\$s/%2\$s，像是填反了",
        Language.ZH_HANT to
            "現在填的是 %1\$s/%2\$s，像是填反了",
        Language.EN to
            "You entered %1\$s/%2\$s, which looks reversed",
        Language.KO to
            "현재 %1\$s/%2\$s로 입력되어 있어 뒤바뀐 것 같습니다",
        ),
        "s_54b70b5d" to mapOf(
        Language.ZH_HANS to
            "静息记录（当前 %1\$s / %2\$s 条）。",
        Language.ZH_HANT to
            "靜息記錄（目前 %1\$s / %2\$s 條）。",
        Language.EN to
            "resting records (currently %1\$s / %2\$s).",
        Language.KO to
            "안정 시 기록이 필요합니다(현재 %1\$s / %2\$s개).",
        ),
        "s_55365a28" to mapOf(
        Language.ZH_HANS to
            "相邻差异超 20%% 占比 %1\$s%%），",
        Language.ZH_HANT to
            "相鄰差異超 20%% 佔比 %1\$s%%），",
        Language.EN to
            "share of adjacent differences over 20%% is %1\$s%%),",
        Language.KO to
            "인접 차이 20%% 초과 비율 %1\$s%%),",
        ),
        "s_55d12c60" to mapOf(
        Language.ZH_HANS to
            "%1\$s℃，更像传感器没贴合而不是真实的皮温变化，该分量未参与",
        Language.ZH_HANT to
            "%1\$s℃，更像感測器沒貼合而不是真實的皮溫變化，該分量未參與",
        Language.EN to
            "%1\$s°C, which looks more like the sensor not being in contact than a real" +
            "skin-temperature change, so this component did not participate",
        Language.KO to
            "%1\$s°C 차이 나며, 이는 실제 피부 온도 변화보다 센서가 밀착되지 않은 것으로 보여 이 성분은 참여하지 않았습니다",
        ),
        "s_56ab5d19" to mapOf(
        Language.ZH_HANS to
            "（中位 %1\$s）",
        Language.ZH_HANT to
            "（中位 %1\$s）",
        Language.EN to
            "(median %1\$s)",
        Language.KO to
            "(중앙값 %1\$s)",
        ),
        "s_5876d5d1" to mapOf(
        Language.ZH_HANS to
            "%1\$s %2\$s 分",
        Language.ZH_HANT to
            "%1\$s %2\$s 分",
        Language.EN to
            "%1\$s %2\$s pts",
        Language.KO to
            "%1\$s %2\$s점",
        ),
        "s_5907b6be" to mapOf(
        Language.ZH_HANS to
            "获取血氧 Tracker 失败: %1\$s",
        Language.ZH_HANT to
            "取得血氧 Tracker 失敗：%1\$s",
        Language.EN to
            "Failed to get the blood oxygen tracker: %1\$s",
        Language.KO to
            "혈중 산소 트래커 가져오기 실패: %1\$s",
        ),
        "s_590b6b5b" to mapOf(
        Language.ZH_HANS to
            "%1\$s 小时",
        Language.ZH_HANT to
            "%1\$s 小時",
        Language.EN to
            "%1\$s h",
        Language.KO to
            "%1\$s시간",
        ),
        "s_592f3cd0" to mapOf(
        Language.ZH_HANS to
            "SDK 报告测量未成功（状态码 %1\$s）—— 请重新测量，手指要完全覆盖侧键并保持不动",
        Language.ZH_HANT to
            "SDK 回報測量未成功（狀態碼 %1\$s）—— 請重新測量，手指要完全覆蓋側鍵並保持不動",
        Language.EN to
            "The SDK reports the measurement was unsuccessful (status code %1\$s) — please" +
            "measure again, fully covering the side buttons with your fingers and staying still",
        Language.KO to
            "SDK가 측정 실패를 보고했습니다(상태 코드 %1\$s) — 손가락으로 측면 버튼을 완전히 덮고 움직이지 않은 채 다시 측정해 주세요",
        ),
        "s_5950ff31" to mapOf(
        Language.ZH_HANS to
            "同步完成，待同步 HR%1\$s BP%2\$s ",
        Language.ZH_HANT to
            "同步完成，待同步 HR%1\$s BP%2\$s ",
        Language.EN to
            "Sync complete, pending HR%1\$s BP%2\$s ",
        Language.KO to
            "동기화 완료, 대기 중 HR%1\$s BP%2\$s ",
        ),
        "s_59607bc4" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次脉搏波起伏太小（表戴松了、手臂被压住、或手腕血流本就弱）",
        Language.ZH_HANT to
            "%1\$s 次脈搏波起伏太小（錶戴鬆了、手臂被壓住、或手腕血流本就弱）",
        Language.EN to
            "%1\$s beats had too small a pulse-wave fluctuation (band too loose, arm pressed," +
            "or wrist blood flow naturally weak)",
        Language.KO to
            "%1\$s회는 맥파 변동이 너무 작았습니다(시계가 헐거움, 팔이 눌림, 또는 손목 혈류가 원래 약함)",
        ),
        "s_59efdef3" to mapOf(
        Language.ZH_HANS to
            "睡眠规律性未参与：要看最近不少于 %1\$s 晚的入睡时刻，",
        Language.ZH_HANT to
            "睡眠規律性未參與：要看最近不少於 %1\$s 晚的入睡時刻，",
        Language.EN to
            "Sleep regularity did not participate: it needs the sleep-onset times of at least" +
            "the last %1\$s nights, ",
        Language.KO to
            "수면 규칙성은 참여하지 않았습니다: 최근 최소 %1\$s일 밤의 잠든 시각이 필요하며 ",
        ),
        "s_5af5d5d1" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s bpm",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_5c520706" to mapOf(
        Language.ZH_HANS to
            "当前只有 %1\$s 晚",
        Language.ZH_HANT to
            "目前只有 %1\$s 晚",
        Language.EN to
            "and currently there are only %1\$s nights",
        Language.KO to
            "현재는 %1\$s일 밤뿐입니다",
        ),
        "s_5cda2491" to mapOf(
        Language.ZH_HANS to
            "测量返回成功状态但没有任何阻抗值（已收到 %1\$s 个数据点）",
        Language.ZH_HANT to
            "測量返回成功狀態但沒有任何阻抗值（已收到 %1\$s 個資料點）",
        Language.EN to
            "Measurement returned success but no impedance values (received %1\$s data points)",
        Language.KO to
            "측정이 성공 상태를 반환했지만 임피던스 값이 없습니다(데이터 포인트 %1\$s개 수신)",
        ),
        "s_5d9cb6b0" to mapOf(
        Language.ZH_HANS to
            "推算值必然贴着皮温（要落进区间至少需要 %1\$s℃ 的温差）。",
        Language.ZH_HANT to
            "推算值必然貼著皮溫（要落進區間至少需要 %1\$s℃ 的溫差）。",
        Language.EN to
            "The estimate necessarily hugs the skin temperature (falling within the range" +
            "requires a difference of at least %1\$s°C).",
        Language.KO to
            "추정값은 필연적으로 피부 온도에 붙습니다(구간 안에 들려면 최소 %1\$s°C의 온도차가 필요합니다).",
        ),
        "s_6066f410" to mapOf(
        Language.ZH_HANS to
            "该分量的波动会偏大（攒到 %1\$s 晚以后会明显收敛）",
        Language.ZH_HANT to
            "該分量的波動會偏大（攢到 %1\$s 晚以後會明顯收斂）",
        Language.EN to
            "and this component will fluctuate more (it will settle noticeably once %1\$s" +
            "nights have accumulated)",
        Language.KO to
            "이 성분의 변동이 커질 수 있습니다(%1\$s일 밤이 쌓이면 눈에 띄게 안정됩니다)",
        ),
        "s_60c88a9b" to mapOf(
        Language.ZH_HANS to
            "这一晚的皮肤温度 %1\$s℃ 离你自己平时（%2\$s℃）差了 ",
        Language.ZH_HANT to
            "這一晚的皮膚溫度 %1\$s℃ 離你自己平時（%2\$s℃）差了 ",
        Language.EN to
            "This night's skin temperature %1\$s°C differs from your own usual (%2\$s°C) by ",
        Language.KO to
            "이 날 밤의 피부 온도 %1\$s°C는 본인의 평소(%2\$s°C)와 ",
        ),
        "s_6208c22f" to mapOf(
        Language.ZH_HANS to
            "%1\$s%% 以上的大幅变化",
        Language.ZH_HANT to
            "%1\$s%% 以上的大幅變化",
        Language.EN to
            "large changes of %1\$s%% or more",
        Language.KO to
            "%1\$s%% 이상의 큰 변화",
        ),
        "s_62814298" to mapOf(
        Language.ZH_HANS to
            "锐度 %1\$s、",
        Language.ZH_HANT to
            "銳度 %1\$s、",
        Language.EN to
            "sharpness %1\$s, ",
        Language.KO to
            "선예도 %1\$s, ",
        ),
        "s_65550c3f" to mapOf(
        Language.ZH_HANS to
            "皮温与环境温差 %1\$s℃ 超出模型适用范围（>%2\$s℃）；",
        Language.ZH_HANT to
            "皮溫與環境溫差 %1\$s℃ 超出模型適用範圍（>%2\$s℃）；",
        Language.EN to
            "the skin-to-environment temperature difference %1\$s°C exceeds the model's" +
            "applicable range (>%2\$s°C); ",
        Language.KO to
            "피부와 환경의 온도차 %1\$s°C가 모델 적용 범위(>%2\$s°C)를 벗어났습니다. ",
        ),
        "s_65744483" to mapOf(
        Language.ZH_HANS to
            "心电只检出 %1\$s 次心搏，至少要 %2\$s 次才算得出脉搏到达时间。",
        Language.ZH_HANT to
            "心電只檢出 %1\$s 次心搏，至少要 %2\$s 次才算得出脈搏到達時間。",
        Language.EN to
            "The ECG detected only %1\$s heartbeats; at least %2\$s are needed to compute the" +
            "pulse arrival time.",
        Language.KO to
            "심전도에서 검출된 심장 박동이 %1\$s회뿐입니다. 맥박 도달 시간을 계산하려면 최소 %2\$s회가 필요합니다.",
        ),
        "s_65cd2d9e" to mapOf(
        Language.ZH_HANS to
            "只有 %1\$s 个有效血氧读数（至少需要 %2\$s 个）—— ",
        Language.ZH_HANT to
            "只有 %1\$s 個有效血氧讀數（至少需要 %2\$s 個）—— ",
        Language.EN to
            "There are only %1\$s valid blood-oxygen readings (at least %2\$s are needed) — ",
        Language.KO to
            "유효한 혈중 산소 판독값이 %1\$s개뿐입니다(최소 %2\$s개 필요) — ",
        ),
        "s_66456820" to mapOf(
        Language.ZH_HANS to
            "%1\$s/%2\$s 个时段有活动",
        Language.ZH_HANT to
            "%1\$s/%2\$s 個時段有活動",
        Language.EN to
            "%1\$s/%2\$s slots with activity",
        Language.KO to
            "%1\$s/%2\$s 구간에 활동 있음",
        ),
        "s_679e78f3" to mapOf(
        Language.ZH_HANS to
            "本次配到的皮温点检是 %1\$s 秒前（约 %2\$s 小时）的那次，",
        Language.ZH_HANT to
            "本次配到的皮溫點檢是 %1\$s 秒前（約 %2\$s 小時）的那次，",
        Language.EN to
            "The skin-temperature spot check matched for this measurement was the one %1\$s" +
            "seconds ago (about %2\$s hours), ",
        Language.KO to
            "이번에 연결된 피부 온도 점검은 %1\$s초 전(약 %2\$s시간)의 것으로, ",
        ),
        "s_67d9d424" to mapOf(
        Language.ZH_HANS to
            "（至少需要 %1\$s%%）—— 缺样会让时间换算整体偏移",
        Language.ZH_HANT to
            "（至少需要 %1\$s%%）—— 缺樣會讓時間換算整體偏移",
        Language.EN to
            "(at least %1\$s%% is needed) — missing samples shift the whole time conversion",
        Language.KO to
            "(최소 %1\$s%% 필요) — 샘플 누락은 시간 환산 전체를 어긋나게 만듭니다",
        ),
        "s_68435e9e" to mapOf(
        Language.ZH_HANS to
            "只有 %1\$s 个有效心搏（至少需要 %2\$s 个）—— ",
        Language.ZH_HANT to
            "只有 %1\$s 個有效心搏（至少需要 %2\$s 個）—— ",
        Language.EN to
            "There are only %1\$s valid heartbeats (at least %2\$s are needed) — ",
        Language.KO to
            "유효한 심장 박동이 %1\$s개뿐입니다(최소 %2\$s개 필요) — ",
        ),
        "s_68ea5192" to mapOf(
        Language.ZH_HANS to
            "近期 %1\$s 条 vs 更早 %2\$s 条（中位时刻相隔 %3\$s 天）",
        Language.ZH_HANT to
            "近期 %1\$s 條 vs 更早 %2\$s 條（中位時刻相隔 %3\$s 天）",
        Language.EN to
            "Recent %1\$s vs earlier %2\$s (median times %3\$s days apart)",
        Language.KO to
            "최근 %1\$s개 vs 이전 %2\$s개(중앙 시각 %3\$s일 차이)",
        ),
        "s_6a08d742" to mapOf(
        Language.ZH_HANS to
            "%1\$s 小时前",
        Language.ZH_HANT to
            "%1\$s 小時前",
        Language.EN to
            "%1\$s h ago",
        Language.KO to
            "%1\$s시간 전",
        ),
        "s_6a28c484" to mapOf(
        Language.ZH_HANS to
            "这一晚的记录日期（%1\$s）在今天之后，",
        Language.ZH_HANT to
            "這一晚的記錄日期（%1\$s）在今天之後，",
        Language.EN to
            "This night's record date (%1\$s) is after today, ",
        Language.KO to
            "이 날 밤의 기록 날짜(%1\$s)가 오늘 이후입니다. ",
        ),
        "s_6a480f63" to mapOf(
        Language.ZH_HANS to
            "比平时略短 %1\$s ms",
        Language.ZH_HANT to
            "比平時略短 %1\$s ms",
        Language.EN to
            "Slightly shorter than usual by %1\$s ms",
        Language.KO to
            "평소보다 약간 짧음 %1\$s ms",
        ),
        "s_6bfb51a6" to mapOf(
        Language.ZH_HANS to
            "%1\$s（只有一路支持）",
        Language.ZH_HANT to
            "%1\$s（只有一路支援）",
        Language.EN to
            "%1\$s (supported by only one signal)",
        Language.KO to
            "%1\$s(한 갈래만 지지)",
        ),
        "s_6c4e3a3d" to mapOf(
        Language.ZH_HANS to
            "目标 %1\$s",
        Language.ZH_HANT to
            "目標 %1\$s",
        Language.EN to
            "Goal %1\$s",
        Language.KO to
            "목표 %1\$s",
        ),
        "s_6cab37a7" to mapOf(
        Language.ZH_HANS to
            "只有 %1\$s 个周期长度可用，预测偏差可能较大。",
        Language.ZH_HANT to
            "只有 %1\$s 個週期長度可用，預測偏差可能較大。",
        Language.EN to
            "Only %1\$s cycle lengths are available, so the prediction may deviate considerably.",
        Language.KO to
            "사용할 수 있는 주기 길이가 %1\$s개뿐이라 예측 편차가 클 수 있습니다.",
        ),
        "s_6da66927" to mapOf(
        Language.ZH_HANS to
            "%1\$s 天前",
        Language.ZH_HANT to
            "%1\$s 天前",
        Language.EN to
            "%1\$s days ago",
        Language.KO to
            "%1\$s일 전",
        ),
        "s_6ef1c1ee" to mapOf(
        Language.ZH_HANS to
            "传感器重连中…（%1\$s）",
        Language.ZH_HANT to
            "感測器重連中…（%1\$s）",
        Language.EN to
            "Reconnecting the sensor… (%1\$s)",
        Language.KO to
            "센서 재연결 중… (%1\$s)",
        ),
        "s_6ef294cc" to mapOf(
        Language.ZH_HANS to
            "±%1\$s 次/分",
        Language.ZH_HANT to
            "±%1\$s 次/分",
        Language.EN to
            "±%1\$s bpm",
        Language.KO to
            "±%1\$s 회/분",
        ),
        "s_6f3d6338" to mapOf(
        Language.ZH_HANS to
            "算于 %1\$s",
        Language.ZH_HANT to
            "算於 %1\$s",
        Language.EN to
            "Computed %1\$s ago",
        Language.KO to
            "%1\$s 전 계산",
        ),
        "s_71bb0e68" to mapOf(
        Language.ZH_HANS to
            "%1\$s数据不足",
        Language.ZH_HANT to
            "%1\$s資料不足",
        Language.EN to
            "%1\$s insufficient data",
        Language.KO to
            "%1\$s 데이터 부족",
        ),
        "s_72d535b9" to mapOf(
        Language.ZH_HANS to
            "测量中（状态码 %1\$s）—— 手指要完全覆盖侧键并保持不动",
        Language.ZH_HANT to
            "測量中（狀態碼 %1\$s）—— 手指要完全覆蓋側鍵並保持不動",
        Language.EN to
            "Measuring (status code %1\$s) — your finger must fully cover the side key and stay" +
            "still",
        Language.KO to
            "측정 중(상태 코드 %1\$s) — 손가락으로 측면 키를 완전히 덮고 움직이지 마세요",
        ),
        "s_74a50900" to mapOf(
        Language.ZH_HANS to
            "%1\$s · 疲劳 %2\$s %3\$s",
        Language.ZH_HANT to
            "%1\$s · 疲勞 %2\$s %3\$s",
        Language.EN to
            "%1\$s · Fatigue %2\$s %3\$s",
        Language.KO to
            "%1\$s · 피로 %2\$s %3\$s",
        ),
        "s_74d2f008" to mapOf(
        Language.ZH_HANS to
            "睡眠时段 %1\$s",
        Language.ZH_HANT to
            "睡眠時段 %1\$s",
        Language.EN to
            "Sleep period %1\$s",
        Language.KO to
            "수면 시간대 %1\$s",
        ),
        "s_7558cf39" to mapOf(
        Language.ZH_HANS to
            "脉搏 %1\$s 超出常见的 %2\$s~%3\$s 次/分",
        Language.ZH_HANT to
            "脈搏 %1\$s 超出常見的 %2\$s~%3\$s 次/分",
        Language.EN to
            "Pulse %1\$s is outside the usual %2\$s–%3\$s /min",
        Language.KO to
            "맥박 %1\$s는 일반적인 %2\$s~%3\$s회/분 범위를 벗어납니다",
        ),
        "s_758f2d9a" to mapOf(
        Language.ZH_HANS to
            "（它只看更早的夜，攒够 %1\$s 晚才有结果）",
        Language.ZH_HANT to
            "（它只看更早的夜，攢夠 %1\$s 晚才有結果）",
        Language.EN to
            "(it only looks at earlier nights, and a result appears once %1\$s nights have" +
            "accumulated)",
        Language.KO to
            "(더 이른 밤만 보며, %1\$s일 밤이 쌓여야 결과가 나옵니다)",
        ),
        "s_75fe88f4" to mapOf(
        Language.ZH_HANS to
            "注册失败: %1\$s",
        Language.ZH_HANT to
            "註冊失敗：%1\$s",
        Language.EN to
            "Registration failed: %1\$s",
        Language.KO to
            "등록 실패: %1\$s",
        ),
        "s_76782b5c" to mapOf(
        Language.ZH_HANS to
            "%1\$s%2\$s 个单位",
        Language.ZH_HANT to
            "%1\$s%2\$s 個單位",
        Language.EN to
            "%1\$s%2\$s units",
        Language.KO to
            "%1\$s%2\$s 단위",
        ),
        "s_7ac010b0" to mapOf(
        Language.ZH_HANS to
            "还差 %1\$s 晚（需要 %2\$s 晚：1 晚待评估 + %3\$s 晚基线），",
        Language.ZH_HANT to
            "還差 %1\$s 晚（需要 %2\$s 晚：1 晚待評估 + %3\$s 晚基線），",
        Language.EN to
            "%1\$s more nights are needed (%2\$s nights required: 1 night to evaluate + %3\$s" +
            "baseline nights), ",
        Language.KO to
            "%1\$s일 밤이 더 필요합니다(%2\$s일 밤 필요: 평가할 1일 밤 + 기준선 %3\$s일 밤). ",
        ),
        "s_7b0246e9" to mapOf(
        Language.ZH_HANS to
            "设备未开放心电测量（支持类型：%1\$s）",
        Language.ZH_HANT to
            "裝置未開放心電測量（支援類型：%1\$s）",
        Language.EN to
            "The device does not expose ECG measurement (supported types: %1\$s)",
        Language.KO to
            "이 기기는 심전도 측정을 제공하지 않습니다(지원 유형: %1\$s)",
        ),
        "s_7c50fd86" to mapOf(
        Language.ZH_HANS to
            "节律不齐（RR 相对变异 %1\$s%%，",
        Language.ZH_HANT to
            "節律不齊（RR 相對變異 %1\$s%%，",
        Language.EN to
            "Irregular rhythm (RR relative variability %1\$s%%,",
        Language.KO to
            "리듬 불규칙(RR 상대 변이 %1\$s%%,",
        ),
        "s_7d3bc632" to mapOf(
        Language.ZH_HANS to
            "这一晚记录到的最低血氧 %1\$s%% 不在可信区间",
        Language.ZH_HANT to
            "這一晚記錄到的最低血氧 %1\$s%% 不在可信區間",
        Language.EN to
            "This night's recorded lowest blood oxygen %1\$s%% is outside the plausible range ",
        Language.KO to
            "이 날 밤 기록된 최저 혈중 산소 %1\$s%%는 신뢰 구간을 벗어납니다 ",
        ),
        "s_817dd14b" to mapOf(
        Language.ZH_HANS to
            "疲劳看的是趋势，需要至少 %1\$s 条「近期」和 %1\$s 条「更早」的",
        Language.ZH_HANT to
            "疲勞看的是趨勢，需要至少 %1\$s 條「近期」和 %1\$s 條「更早」的",
        Language.EN to
            "Fatigue looks at a trend, requiring at least %1\$s \"recent\" and %1\$s \"earlier\" ",
        Language.KO to
            "피로는 추세를 보므로 최소 %1\$s개의 '최근'과 %1\$s개의 '이전' ",
        ),
        "s_8375c02b" to mapOf(
        Language.ZH_HANS to
            "基于 %1\$s 个周期长度，各次之间相差 %2\$s 天。",
        Language.ZH_HANT to
            "基於 %1\$s 個週期長度，各次之間相差 %2\$s 天。",
        Language.EN to
            "Based on %1\$s cycle lengths, with a spread of %2\$s days between them.",
        Language.KO to
            "주기 길이 %1\$s개를 기준으로 하며, 주기 간 차이는 %2\$s일입니다.",
        ),
        "s_843d374b" to mapOf(
        Language.ZH_HANS to
            "峰值因子 %1\$s、检出 %2\$s 拍），",
        Language.ZH_HANT to
            "峰值因子 %1\$s、檢出 %2\$s 拍），",
        Language.EN to
            "crest factor %1\$s, %2\$s beats detected), ",
        Language.KO to
            "첨두 인자 %1\$s, %2\$s박 검출), ",
        ),
        "s_847dcbdc" to mapOf(
        Language.ZH_HANS to
            "设备不支持连续 PPG（能力：%1\$s）",
        Language.ZH_HANT to
            "裝置不支援連續 PPG（能力：%1\$s）",
        Language.EN to
            "Device does not support continuous PPG (capabilities: %1\$s)",
        Language.KO to
            "기기가 연속 PPG를 지원하지 않습니다(기능: %1\$s)",
        ),
        "s_84c4ef03" to mapOf(
        Language.ZH_HANS to
            "注册监听失败: %1\$s",
        Language.ZH_HANT to
            "註冊監聽失敗：%1\$s",
        Language.EN to
            "Failed to register the listener: %1\$s",
        Language.KO to
            "리스너 등록 실패: %1\$s",
        ),
        "s_86150d1f" to mapOf(
        Language.ZH_HANS to
            "%1\$s 小时",
        Language.ZH_HANT to
            "%1\$s 小時",
        Language.EN to
            "%1\$s h",
        Language.KO to
            "%1\$s시간",
        ),
        "s_861e538c" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次",
        Language.ZH_HANT to
            "%1\$s 次",
        Language.EN to
            "%1\$s events",
        Language.KO to
            "%1\$s회",
        ),
        "s_861e53d0" to mapOf(
        Language.ZH_HANS to
            "%1\$s 步",
        Language.ZH_HANT to
            "%1\$s 步",
        Language.EN to
            "%1\$s steps",
        Language.KO to
            "%1\$s 걸음",
        ),
        "s_86bb9d34" to mapOf(
        Language.ZH_HANS to
            "%1\$s 分钟",
        Language.ZH_HANT to
            "%1\$s 分鐘",
        Language.EN to
            "%1\$s min",
        Language.KO to
            "%1\$s분",
        ),
        "s_888609af" to mapOf(
        Language.ZH_HANS to
            "%1\$s分",
        Language.ZH_HANT to
            "%1\$s分",
        Language.EN to
            "%1\$smin",
        Language.KO to
            "%1\$s분",
        ),
        "s_8887c3b1" to mapOf(
        Language.ZH_HANS to
            "%1\$s秒",
        Language.ZH_HANT to
            "%1\$s秒",
        Language.EN to
            "%1\$ss",
        Language.KO to
            "%1\$s초",
        ),
        "s_8b2589ef" to mapOf(
        Language.ZH_HANS to
            "最近一晚的数值判不出口径（%1\$s），不敢拿去相减",
        Language.ZH_HANT to
            "最近一晚的數值判不出口徑（%1\$s），不敢拿去相減",
        Language.EN to
            "the definition of the most recent night's value cannot be determined (%1\$s), so" +
            "it is not safe to subtract",
        Language.KO to
            "가장 최근 밤의 값은 기준을 판단할 수 없어(%1\$s) 빼는 데 사용할 수 없습니다",
        ),
        "s_8beb368e" to mapOf(
        Language.ZH_HANS to
            "%1\$s 秒不够",
        Language.ZH_HANT to
            "%1\$s 秒不夠",
        Language.EN to
            "%1\$s seconds is not enough",
        Language.KO to
            "%1\$s초로는 부족합니다",
        ),
        "s_8ee93988" to mapOf(
        Language.ZH_HANS to
            "%1\$s那一晚",
        Language.ZH_HANT to
            "%1\$s那一晚",
        Language.EN to
            "the night of %1\$s",
        Language.KO to
            "%1\$s 밤",
        ),
        "s_8ff59bb6" to mapOf(
        Language.ZH_HANS to
            "%1\$s · %2\$s · 监测 %3\$s",
        Language.ZH_HANT to
            "%1\$s · %2\$s · 監測 %3\$s",
        Language.EN to
            "%1\$s · %2\$s · monitored %3\$s",
        Language.KO to
            "%1\$s · %2\$s · 모니터링 %3\$s",
        ),
        "s_91426bc6" to mapOf(
        Language.ZH_HANS to
            "最长块 %1\$s ms、",
        Language.ZH_HANT to
            "最長塊 %1\$s ms、",
        Language.EN to
            "longest block %1\$s ms, ",
        Language.KO to
            "가장 긴 블록 %1\$s ms, ",
        ),
        "s_92933427" to mapOf(
        Language.ZH_HANS to
            "第 %1\$s 天",
        Language.ZH_HANT to
            "第 %1\$s 天",
        Language.EN to
            "Day %1\$s",
        Language.KO to
            "%1\$s일차",
        ),
        "s_94c0fd95" to mapOf(
        Language.ZH_HANS to
            "%1\$s / 目标 %2\$s（%3\$s%%）",
        Language.ZH_HANT to
            "%1\$s / 目標 %2\$s（%3\$s%%）",
        Language.EN to
            "%1\$s / goal %2\$s (%3\$s%%)",
        Language.KO to
            "%1\$s / 목표 %2\$s (%3\$s%%)",
        ),
        "s_970e14a6" to mapOf(
        Language.ZH_HANS to
            "三星 %1\$s 分",
        Language.ZH_HANT to
            "三星 %1\$s 分",
        Language.EN to
            "Samsung %1\$s pts",
        Language.KO to
            "삼성 %1\$s점",
        ),
        "s_9782bb29" to mapOf(
        Language.ZH_HANS to
            "要转成「相对你自己的偏差」得先攒够 %1\$s 晚",
        Language.ZH_HANT to
            "要轉成「相對你自己的偏差」得先攢夠 %1\$s 晚",
        Language.EN to
            "converting it into a \"deviation relative to yourself\" requires at least %1\$s" +
            "nights",
        Language.KO to
            "'본인 대비 편차'로 전환하려면 최소 %1\$s일 밤이 필요합니다",
        ),
        "s_9ac84de3" to mapOf(
        Language.ZH_HANS to
            "获取心电 Tracker 失败: %1\$s",
        Language.ZH_HANT to
            "取得心電 Tracker 失敗：%1\$s",
        Language.EN to
            "Failed to get the ECG tracker: %1\$s",
        Language.KO to
            "심전도 Tracker 가져오기 실패: %1\$s",
        ),
        "s_9ea6ba57" to mapOf(
        Language.ZH_HANS to
            "%1\$s 秒 —— 呼吸一次要 2.5~5 秒，太短的片段只能看到噪声",
        Language.ZH_HANT to
            "%1\$s 秒 —— 呼吸一次要 2.5~5 秒，太短的片段只能看到雜訊",
        Language.EN to
            "%1\$s seconds is needed — one breath takes 2.5–5 seconds, so too short a segment" +
            "shows only noise",
        Language.KO to
            "%1\$s초가 필요합니다 — 한 번 호흡에 2.5~5초가 걸리므로 너무 짧은 구간에서는 잡음만 보입니다",
        ),
        "s_a0321705" to mapOf(
        Language.ZH_HANS to
            "皮温点检不足 %1\$s 次，算不出你自己的皮温基线，",
        Language.ZH_HANT to
            "皮溫點檢不足 %1\$s 次，算不出你自己的皮溫基線，",
        Language.EN to
            "There are fewer than %1\$s skin-temperature spot checks, so your own" +
            "skin-temperature baseline cannot be computed, ",
        Language.KO to
            "피부 온도 점검이 %1\$s회 미만이라 본인의 피부 온도 기준선을 계산할 수 없습니다. ",
        ),
        "s_a07347b2" to mapOf(
        Language.ZH_HANS to
            "第 %1\$s 天 · 已超预计 %2\$s 天",
        Language.ZH_HANT to
            "第 %1\$s 天 · 已超預計 %2\$s 天",
        Language.EN to
            "Day %1\$s · %2\$s days past expected",
        Language.KO to
            "%1\$s일째 · 예정보다 %2\$s일 지남",
        ),
        "s_a16f134c" to mapOf(
        Language.ZH_HANS to
            "这不是今天的分数：算的是 %1\$s 那一晚",
        Language.ZH_HANT to
            "這不是今天的分數：算的是 %1\$s 那一晚",
        Language.EN to
            "This is not today's score: it is computed for the night of %1\$s",
        Language.KO to
            "이것은 오늘의 점수가 아닙니다: %1\$s 밤에 대해 계산한 것입니다",
        ),
        "s_a1ed4110" to mapOf(
        Language.ZH_HANS to
            "测量时人在活动（活动档位「%1\$s」）。",
        Language.ZH_HANT to
            "測量時人在活動（活動檔位「%1\$s」）。",
        Language.EN to
            "You were active during the measurement (activity level \"%1\$s\").",
        Language.KO to
            "측정 시 활동 중이었습니다(활동 단계 '%1\$s').",
        ),
        "s_a4741644" to mapOf(
        Language.ZH_HANS to
            "已稳定(%1\$s 次采样)",
        Language.ZH_HANT to
            "已穩定(%1\$s 次取樣)",
        Language.EN to
            "Stable (%1\$s samples)",
        Language.KO to
            "안정됨(%1\$s회 샘플링)",
        ),
        "s_a691b493" to mapOf(
        Language.ZH_HANS to
            "经期长度 %1\$s 天超过 %2\$s 天上限。如果确实是两次经期，请分开记录",
        Language.ZH_HANT to
            "經期長度 %1\$s 天超過 %2\$s 天上限。如果確實是兩次經期，請分開記錄",
        Language.EN to
            "The period length of %1\$s days exceeds the %2\$s-day upper limit. If these are" +
            "indeed two periods, please log them separately",
        Language.KO to
            "월경 길이 %1\$s일이 상한 %2\$s일을 넘습니다. 실제로 두 번의 월경이라면 따로 기록해 주세요",
        ),
        "s_a6eefdff" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s bpm",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_a892c77b" to mapOf(
        Language.ZH_HANS to
            "；成功 %1\$s 拍",
        Language.ZH_HANT to
            "；成功 %1\$s 拍",
        Language.EN to
            "; succeeded for %1\$s beats",
        Language.KO to
            "; 성공 %1\$s박동",
        ),
        "s_a972db7c" to mapOf(
        Language.ZH_HANS to
            "响应超过上限（%1\$s 字节）",
        Language.ZH_HANT to
            "回應超過上限（%1\$s 位元組）",
        Language.EN to
            "Response exceeds the limit (%1\$s bytes)",
        Language.KO to
            "응답이 상한을 초과했습니다 (%1\$s 바이트)",
        ),
        "s_aba77fb7" to mapOf(
        Language.ZH_HANS to
            "本 App 的分比三星高 %1\$s 分",
        Language.ZH_HANT to
            "本 App 的分比三星高 %1\$s 分",
        Language.EN to
            "This app's score is %1\$s points higher than Samsung's",
        Language.KO to
            "이 앱의 점수가 삼성보다 %1\$s점 높습니다",
        ),
        "s_ad6219cf" to mapOf(
        Language.ZH_HANS to
            "%1\$s 晚入睡时刻",
        Language.ZH_HANT to
            "%1\$s 晚入睡時刻",
        Language.EN to
            "%1\$s nights of sleep-onset time",
        Language.KO to
            "잠든 시각 %1\$s일 밤",
        ),
        "s_ae118c8b" to mapOf(
        Language.ZH_HANS to
            "舒张压通常在 %1\$s~%2\$s 之间，请确认没有输错",
        Language.ZH_HANT to
            "舒張壓通常在 %1\$s~%2\$s 之間，請確認沒有輸錯",
        Language.EN to
            "and diastolic pressure is usually between %1\$s and %2\$s; please check for typos",
        Language.KO to
            "이완기압은 보통 %1\$s~%2\$s 사이입니다. 잘못 입력하지 않았는지 확인해 주세요",
        ),
        "s_b0e1efbe" to mapOf(
        Language.ZH_HANS to
            "目前只有 %1\$s 晚历史，几项相对基线的分量还没法参与 —— ",
        Language.ZH_HANT to
            "目前只有 %1\$s 晚歷史，幾項相對基線的分量還沒法參與 —— ",
        Language.EN to
            "There are currently only %1\$s nights of history, so several baseline-relative" +
            "components cannot participate yet — ",
        Language.KO to
            "현재 %1\$s일 밤의 기록만 있어 몇몇 기준선 상대 성분은 아직 참여할 수 없습니다 — ",
        ),
        "s_b1a7ba0a" to mapOf(
        Language.ZH_HANS to
            "睡眠心率变化未参与：这项的基线还差 %1\$s 晚",
        Language.ZH_HANT to
            "睡眠心率變化未參與：這項的基線還差 %1\$s 晚",
        Language.EN to
            "Sleeping heart rate variability did not participate: this item's baseline is still" +
            "short by %1\$s nights",
        Language.KO to
            "수면 심박 변동은 참여하지 않았습니다: 이 항목의 기준선이 아직 %1\$s일 밤 부족합니다",
        ),
        "s_b33cf0af" to mapOf(
        Language.ZH_HANS to
            "传感器读取异常(STATUS %1\$s)，等待下一次…",
        Language.ZH_HANT to
            "感測器讀取異常(STATUS %1\$s)，等待下一次…",
        Language.EN to
            "Sensor read error (STATUS %1\$s), waiting for the next…",
        Language.KO to
            "센서 읽기 오류(STATUS %1\$s), 다음을 기다리는 중…",
        ),
        "s_b458407c" to mapOf(
        Language.ZH_HANS to
            "重试失败: %1\$s",
        Language.ZH_HANT to
            "重試失敗：%1\$s",
        Language.EN to
            "Retry failed: %1\$s",
        Language.KO to
            "재시도 실패: %1\$s",
        ),
        "s_b4d0624e" to mapOf(
        Language.ZH_HANS to
            "%1\$s分%2\$s秒",
        Language.ZH_HANT to
            "%1\$s分%2\$s秒",
        Language.EN to
            "%1\$smin %2\$ss",
        Language.KO to
            "%1\$s분 %2\$s초",
        ),
        "s_b664210f" to mapOf(
        Language.ZH_HANS to
            "算出的呼吸率 %1\$s 次/分 超出可能范围（%2\$s~%3\$s），判为无效",
        Language.ZH_HANT to
            "算出的呼吸率 %1\$s 次/分 超出可能範圍（%2\$s~%3\$s），判為無效",
        Language.EN to
            "the computed respiratory rate %1\$s /min is outside the possible range" +
            "(%2\$s–%3\$s) and is judged invalid",
        Language.KO to
            "계산된 호흡수 %1\$s회/분은 가능한 범위(%2\$s~%3\$s)를 벗어나 무효로 판정되었습니다",
        ),
        "s_b6bdaabb" to mapOf(
        Language.ZH_HANS to
            "测量返回成功状态但没有数值（已收到 %1\$s 个数据点）",
        Language.ZH_HANT to
            "測量返回成功狀態但沒有數值（已收到 %1\$s 個資料點）",
        Language.EN to
            "The measurement returned success but no value (received %1\$s data points)",
        Language.KO to
            "측정이 성공 상태를 반환했지만 값이 없습니다(%1\$s개 데이터 포인트 수신)",
        ),
        "s_b8e28369" to mapOf(
        Language.ZH_HANS to
            "这一路脉搏波没有连续可用的时段：%1\$s 个有值点，",
        Language.ZH_HANT to
            "這一路脈搏波沒有連續可用的時段：%1\$s 個有值點，",
        Language.EN to
            "This pulse-wave signal has no continuously usable segment: %1\$s valid points, ",
        Language.KO to
            "이 맥파 갈래에는 연속적으로 쓸 수 있는 구간이 없습니다: 유효점 %1\$s개, ",
        ),
        "s_bcb1ad4a" to mapOf(
        Language.ZH_HANS to
            "算于 %1\$s，可能已过时（基线会随新记录变化），最新值看手机端",
        Language.ZH_HANT to
            "算於 %1\$s，可能已過時（基準會隨新記錄變化），最新值看手機端",
        Language.EN to
            "Computed %1\$s ago; may be outdated (the baseline changes with new records). See" +
            "the phone for the latest value",
        Language.KO to
            "%1\$s 전 계산, 오래된 값일 수 있음(기준선은 새 기록에 따라 변함), 최신 값은 휴대폰에서 확인",
        ),
        "s_bda8a54f" to mapOf(
        Language.ZH_HANS to
            "体成分%1\$s 活动%2\$s ",
        Language.ZH_HANT to
            "體成分%1\$s 活動%2\$s ",
        Language.EN to
            "body comp.%1\$s activity%2\$s ",
        Language.KO to
            "체성분%1\$s 활동%2\$s ",
        ),
        "s_bdcc99a8" to mapOf(
        Language.ZH_HANS to
            "节律整齐，心率 %1\$s 次/分（100~180 次/分）",
        Language.ZH_HANT to
            "節律整齊，心率 %1\$s 次/分（100~180 次/分）",
        Language.EN to
            "Regular rhythm, heart rate %1\$s bpm (100–180 bpm)",
        Language.KO to
            "리듬 규칙적, 심박수 %1\$s 회/분(100~180 회/분)",
        ),
        "s_bdf3849f" to mapOf(
        Language.ZH_HANS to
            "只有一路信号检出了呼吸周期，而且置信不足（相关 %1\$s，",
        Language.ZH_HANT to
            "只有一路訊號檢出了呼吸週期，而且置信不足（相關 %1\$s，",
        Language.EN to
            "Only one signal detected a respiratory cycle, and its confidence is insufficient" +
            "(correlation %1\$s, ",
        Language.KO to
            "한 갈래 신호만 호흡 주기를 검출했고 신뢰도도 부족합니다(상관 %1\$s, ",
        ),
        "s_be05dd60" to mapOf(
        Language.ZH_HANS to
            "心电数据异常: %1\$s: %2\$s",
        Language.ZH_HANT to
            "心電資料異常：%1\$s: %2\$s",
        Language.EN to
            "ECG data error: %1\$s: %2\$s",
        Language.KO to
            "심전도 데이터 오류: %1\$s: %2\$s",
        ),
        "s_be9365e8" to mapOf(
        Language.ZH_HANS to
            "光信号落库回调异常: %1\$s",
        Language.ZH_HANT to
            "光訊號落庫回呼異常：%1\$s",
        Language.EN to
            "Optical signal DB write callback error: %1\$s",
        Language.KO to
            "광신호 DB 저장 콜백 오류: %1\$s",
        ),
        "s_bf0f539a" to mapOf(
        Language.ZH_HANS to
            "节律整齐，心率 %1\$s 次/分，未检出房颤特征",
        Language.ZH_HANT to
            "節律整齊，心率 %1\$s 次/分，未檢出房顫特徵",
        Language.EN to
            "Regular rhythm, heart rate %1\$s bpm, no atrial fibrillation features detected",
        Language.KO to
            "리듬 규칙적, 심박수 %1\$s 회/분, 심방세동 특징 미검출",
        ),
        "s_bf3b4434" to mapOf(
        Language.ZH_HANS to
            "（%1\$s），分数基本就是它们的换算结果，",
        Language.ZH_HANT to
            "（%1\$s），分數基本就是它們的換算結果，",
        Language.EN to
            "(%1\$s), the score is essentially their converted result, ",
        Language.KO to
            "(%1\$s), 점수는 사실상 그것들의 환산 결과이며 ",
        ),
        "s_bfafc867" to mapOf(
        Language.ZH_HANS to
            "（%1\$s~%2\$s%%），已剔除、该分量未参与",
        Language.ZH_HANT to
            "（%1\$s~%2\$s%%），已剔除、該分量未參與",
        Language.EN to
            "(%1\$s–%2\$s%%), so it was discarded and this component did not participate",
        Language.KO to
            "(%1\$s~%2\$s%%), 제외되었으며 이 성분은 참여하지 않았습니다",
        ),
        "s_c18d2520" to mapOf(
        Language.ZH_HANS to
            "只有 %1\$s 次心搏能定出脉搏波足（至少要 %2\$s 次）；",
        Language.ZH_HANT to
            "只有 %1\$s 次心搏能定出脈搏波足（至少要 %2\$s 次）；",
        Language.EN to
            "Only %1\$s heartbeats could have their pulse-wave foot determined (at least %2\$s" +
            "are needed); ",
        Language.KO to
            "맥파 발을 정할 수 있는 심장 박동이 %1\$s회뿐입니다(최소 %2\$s회 필요). ",
        ),
        "s_c282191f" to mapOf(
        Language.ZH_HANS to
            "%1\$s 分钟",
        Language.ZH_HANT to
            "%1\$s 分鐘",
        Language.EN to
            "%1\$s min",
        Language.KO to
            "%1\$s분",
        ),
        "s_c323366d" to mapOf(
        Language.ZH_HANS to
            "有效跨度只有 %1\$s 秒，不足 ",
        Language.ZH_HANT to
            "有效跨度只有 %1\$s 秒，不足 ",
        Language.EN to
            "The valid span is only %1\$s seconds, less than ",
        Language.KO to
            "유효 구간이 %1\$s초뿐으로 ",
        ),
        "s_c4f53144" to mapOf(
        Language.ZH_HANS to
            "获取多频阻抗 Tracker 失败: %1\$s: %2\$s",
        Language.ZH_HANT to
            "取得多頻阻抗 Tracker 失敗：%1\$s: %2\$s",
        Language.EN to
            "Failed to get the multi-frequency impedance tracker: %1\$s: %2\$s",
        Language.KO to
            "다중 주파수 임피던스 트래커 가져오기 실패: %1\$s: %2\$s",
        ),
        "s_c5ef9b14" to mapOf(
        Language.ZH_HANS to
            "%1\$s 小时 %2\$s 分",
        Language.ZH_HANT to
            "%1\$s 小時 %2\$s 分",
        Language.EN to
            "%1\$s h %2\$s min",
        Language.KO to
            "%1\$s시간 %2\$s분",
        ),
        "s_c6cc9e85" to mapOf(
        Language.ZH_HANS to
            "%1\$s 小时",
        Language.ZH_HANT to
            "%1\$s 小時",
        Language.EN to
            "%1\$s h",
        Language.KO to
            "%1\$s시간",
        ),
        "s_c72ec742" to mapOf(
        Language.ZH_HANS to
            "近 %1\$s 晚相差 %2\$s",
        Language.ZH_HANT to
            "近 %1\$s 晚相差 %2\$s",
        Language.EN to
            "a spread of %2\$s across the last %1\$s nights",
        Language.KO to
            "최근 %1\$s일 밤 차이 %2\$s",
        ),
        "s_c81c235e" to mapOf(
        Language.ZH_HANS to
            "实际监测到的血氧时长只有 %1\$s",
        Language.ZH_HANT to
            "實際監測到的血氧時長只有 %1\$s",
        Language.EN to
            "The actual monitored blood-oxygen duration is only %1\$s",
        Language.KO to
            "실제 모니터링된 혈중 산소 시간은 %1\$s뿐입니다",
        ),
        "s_c9325c18" to mapOf(
        Language.ZH_HANS to
            "%1\$s 千卡",
        Language.ZH_HANT to
            "%1\$s 千卡",
        Language.EN to
            "%1\$s kcal",
        Language.KO to
            "%1\$s kcal",
        ),
        "s_c9660eea" to mapOf(
        Language.ZH_HANS to
            "注册监听失败: %1\$s",
        Language.ZH_HANT to
            "註冊監聽失敗：%1\$s",
        Language.EN to
            "Failed to register the listener: %1\$s",
        Language.KO to
            "리스너 등록 실패: %1\$s",
        ),
        "s_c97204d2" to mapOf(
        Language.ZH_HANS to
            "估计功率至少需要 %1\$s 秒，本次 ",
        Language.ZH_HANT to
            "估計功率至少需要 %1\$s 秒，本次 ",
        Language.EN to
            "Estimating power requires at least %1\$s seconds, and this ",
        Language.KO to
            "파워 추정에는 최소 %1\$s초가 필요하지만 이번은 ",
        ),
        "s_cac415ca" to mapOf(
        Language.ZH_HANS to
            "记录日期异常（%1\$s）",
        Language.ZH_HANT to
            "記錄日期異常（%1\$s）",
        Language.EN to
            "Abnormal record date (%1\$s)",
        Language.KO to
            "기록 날짜가 비정상입니다 (%1\$s)",
        ),
        "s_cba0d69c" to mapOf(
        Language.ZH_HANS to
            "这 %1\$s 条记录集中在 %2\$s 小时内，需要覆盖至少 ",
        Language.ZH_HANT to
            "這 %1\$s 條記錄集中在 %2\$s 小時內，需要涵蓋至少 ",
        Language.EN to
            "these %1\$s records are concentrated within %2\$s hours, and need to span at least ",
        Language.KO to
            "이 %1\$s개 기록이 %2\$s시간 안에 몰려 있어 최소 ",
        ),
        "s_cce00d68" to mapOf(
        Language.ZH_HANS to
            "（至少需要 %1\$s 次）—— 再测长一点更可靠",
        Language.ZH_HANT to
            "（至少需要 %1\$s 次）—— 再測長一點更可靠",
        Language.EN to
            "(at least %1\$s are needed) — measuring longer is more reliable",
        Language.KO to
            "(최소 %1\$s회 필요) — 더 오래 측정하면 더 신뢰할 만합니다",
        ),
        "s_d012fdee" to mapOf(
        Language.ZH_HANS to
            "%1\$s %2\$s 步",
        Language.ZH_HANT to
            "%1\$s %2\$s 步",
        Language.EN to
            "%1\$s %2\$s steps",
        Language.KO to
            "%1\$s %2\$s 걸음",
        ),
        "s_d05d6449" to mapOf(
        Language.ZH_HANS to
            "另外 %1\$s 次的原因是：",
        Language.ZH_HANT to
            "另外 %1\$s 次的原因是：",
        Language.EN to
            "the reasons for the other %1\$s are: ",
        Language.KO to
            "나머지 %1\$s회의 원인은: ",
        ),
        "s_d1628c8f" to mapOf(
        Language.ZH_HANS to
            "响应过大（%1\$s 字节）",
        Language.ZH_HANT to
            "回應過大（%1\$s 位元組）",
        Language.EN to
            "Response too large (%1\$s bytes)",
        Language.KO to
            "응답이 너무 큽니다 (%1\$s 바이트)",
        ),
        "s_d1aa82e3" to mapOf(
        Language.ZH_HANS to
            "只有 %1\$s 个分量参与（明细见手机端）",
        Language.ZH_HANT to
            "只有 %1\$s 個分量參與（明細見手機端）",
        Language.EN to
            "Only %1\$s components took part (see the phone for details)",
        Language.KO to
            "구성 요소 %1\$s개만 반영되었습니다 (자세한 내용은 스마트폰에서 확인하세요)",
        ),
        "s_d1fb6d13" to mapOf(
        Language.ZH_HANS to
            "睡眠心率未参与：你自己的基线还差 %1\$s 晚",
        Language.ZH_HANT to
            "睡眠心率未參與：你自己的基線還差 %1\$s 晚",
        Language.EN to
            "Sleeping heart rate did not participate: your own baseline is still short by %1\$s" +
            "nights",
        Language.KO to
            "수면 심박수는 참여하지 않았습니다: 본인 기준선이 아직 %1\$s일 밤 부족합니다",
        ),
        "s_d209dafc" to mapOf(
        Language.ZH_HANS to
            "%1\$s · 疲劳 待累积",
        Language.ZH_HANT to
            "%1\$s · 疲勞 待累積",
        Language.EN to
            "%1\$s · Fatigue accumulating",
        Language.KO to
            "%1\$s · 피로 누적 중",
        ),
        "s_d216a0c6" to mapOf(
        Language.ZH_HANS to
            "%1\$s 天 —— ",
        Language.ZH_HANT to
            "%1\$s 天 —— ",
        Language.EN to
            "%1\$s days are needed — ",
        Language.KO to
            "%1\$s일이 필요합니다 — ",
        ),
        "s_d4c2b227" to mapOf(
        Language.ZH_HANS to
            "%1\$s 层",
        Language.ZH_HANT to
            "%1\$s 層",
        Language.EN to
            "%1\$s floors",
        Language.KO to
            "%1\$s층",
        ),
        "s_d5d781ee" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s bpm",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_d7255444" to mapOf(
        Language.ZH_HANS to
            "周期长度在 %1\$s~%2\$s 天之外的记录不参与统计：",
        Language.ZH_HANT to
            "週期長度在 %1\$s~%2\$s 天之外的記錄不參與統計：",
        Language.EN to
            "Records whose cycle length falls outside %1\$s–%2\$s days do not enter the" +
            "statistics: ",
        Language.KO to
            "주기 길이가 %1\$s~%2\$s일을 벗어나는 기록은 통계에 포함되지 않습니다: ",
        ),
        "s_d74093ee" to mapOf(
        Language.ZH_HANS to
            "注册光信号监听失败: %1\$s",
        Language.ZH_HANT to
            "註冊光訊號監聽失敗：%1\$s",
        Language.EN to
            "Failed to register the optical signal listener: %1\$s",
        Language.KO to
            "광신호 리스너 등록 실패: %1\$s",
        ),
        "s_d75ee59b" to mapOf(
        Language.ZH_HANS to
            "皮温 %1\$s℃ 与表体温度 %2\$s℃ 相差 %3\$s℃。",
        Language.ZH_HANT to
            "皮溫 %1\$s℃ 與錶體溫度 %2\$s℃ 相差 %3\$s℃。",
        Language.EN to
            "skin temperature %1\$s°C differs from body temperature %2\$s°C by %3\$s°C.",
        Language.KO to
            "피부 온도 %1\$s°C와 시계 본체 온도 %2\$s°C는 %3\$s°C 차이 납니다.",
        ),
        "s_d7a73d8b" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s bpm",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_d8f57b8d" to mapOf(
        Language.ZH_HANS to
            " · 疲劳 %1\$s · %2\$s",
        Language.ZH_HANT to
            " · 疲勞 %1\$s · %2\$s",
        Language.EN to
            " · Fatigue %1\$s · %2\$s",
        Language.KO to
            " · 피로 %1\$s · %2\$s",
        ),
        "s_d9208031" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s bpm",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_d96d556d" to mapOf(
        Language.ZH_HANS to
            "本次跨度不足 %1\$s 秒，不出频域指标。",
        Language.ZH_HANT to
            "本次跨度不足 %1\$s 秒，不出頻域指標。",
        Language.EN to
            "The span for this measurement is under %1\$s seconds, so no frequency-domain index" +
            "is given.",
        Language.KO to
            "이번 구간이 %1\$s초에 못 미쳐 주파수 영역 지표를 제공하지 않습니다.",
        ),
        "s_da1e9d3c" to mapOf(
        Language.ZH_HANS to
            "记录满 3 个周期后会稳定一些（当前各次之间的差异是 %1\$s 天）",
        Language.ZH_HANT to
            "記錄滿 3 個週期後會穩定一些（目前各次之間的差異是 %1\$s 天）",
        Language.EN to
            "It stabilizes somewhat after 3 full cycles are recorded (the current spread" +
            "between cycles is %1\$s days)",
        Language.KO to
            "3주기를 모두 기록하면 다소 안정됩니다(현재 주기 간 차이는 %1\$s일)",
        ),
        "s_db0bbd48" to mapOf(
        Language.ZH_HANS to
            "这次有 %1\$s/10 个分量参与，其余见上面的逐条说明",
        Language.ZH_HANT to
            "這次有 %1\$s/10 個分量參與，其餘見上面的逐條說明",
        Language.EN to
            "%1\$s/10 components participated this time; see the item-by-item notes above for" +
            "the rest",
        Language.KO to
            "이번에는 %1\$s/10개 성분이 참여했습니다. 나머지는 위의 항목별 설명을 참고하세요",
        ),
        "s_db3f21be" to mapOf(
        Language.ZH_HANS to
            "光信号%1\$s HRV%2\$s",
        Language.ZH_HANT to
            "光訊號%1\$s HRV%2\$s",
        Language.EN to
            "optical signal%1\$s HRV%2\$s",
        Language.KO to
            "광신호%1\$s HRV%2\$s",
        ),
        "s_db925254" to mapOf(
        Language.ZH_HANS to
            "获取体成分 Tracker 失败: %1\$s: %2\$s",
        Language.ZH_HANT to
            "取得體成分 Tracker 失敗：%1\$s: %2\$s",
        Language.EN to
            "Failed to get the body composition tracker: %1\$s: %2\$s",
        Language.KO to
            "체성분 Tracker 가져오기 실패: %1\$s: %2\$s",
        ),
        "s_db9aed4f" to mapOf(
        Language.ZH_HANS to
            "获取体温 Tracker 失败: %1\$s",
        Language.ZH_HANT to
            "取得體溫 Tracker 失敗：%1\$s",
        Language.EN to
            "Failed to get the temperature tracker: %1\$s",
        Language.KO to
            "체온 트래커 가져오기 실패: %1\$s",
        ),
        "s_dbf0f917" to mapOf(
        Language.ZH_HANS to
            "%1\$s小时",
        Language.ZH_HANT to
            "%1\$s小時",
        Language.EN to
            "%1\$sh",
        Language.KO to
            "%1\$s시간",
        ),
        "s_dc74c5c9" to mapOf(
        Language.ZH_HANS to
            "这一晚的皮肤温度读数是 %1\$s℃ ，既不像「相对个人基线的偏差」",
        Language.ZH_HANT to
            "這一晚的皮膚溫度讀數是 %1\$s℃ ，既不像「相對個人基線的偏差」",
        Language.EN to
            "This night's skin-temperature reading is %1\$s°C, which looks neither like a" +
            "\"deviation from a personal baseline\" ",
        Language.KO to
            "이 날 밤의 피부 온도 판독값은 %1\$s°C로, '개인 기준선 대비 편차'로도 ",
        ),
        "s_dc97892c" to mapOf(
        Language.ZH_HANS to
            "%1\$s分钟",
        Language.ZH_HANT to
            "%1\$s分鐘",
        Language.EN to
            "%1\$smin",
        Language.KO to
            "%1\$s분",
        ),
        "s_dd7052b7" to mapOf(
        Language.ZH_HANS to
            "可用块 %1\$s 段（最长 ",
        Language.ZH_HANT to
            "可用塊 %1\$s 段（最長 ",
        Language.EN to
            "usable blocks: %1\$s (longest ",
        Language.KO to
            "사용 가능한 블록 %1\$s개(가장 긴 ",
        ),
        "s_df267302" to mapOf(
        Language.ZH_HANS to
            "%1\$s %2\$s 千卡",
        Language.ZH_HANT to
            "%1\$s %2\$s 千卡",
        Language.EN to
            "%1\$s %2\$s kcal",
        Language.KO to
            "%1\$s %2\$s kcal",
        ),
        "s_e1953c06" to mapOf(
        Language.ZH_HANS to
            "获取 PPG Tracker 失败: %1\$s",
        Language.ZH_HANT to
            "取得 PPG Tracker 失敗：%1\$s",
        Language.EN to
            "Failed to get the PPG tracker: %1\$s",
        Language.KO to
            "PPG 트래커 가져오기 실패: %1\$s",
        ),
        "s_e26dff6c" to mapOf(
        Language.ZH_HANS to
            "与已有记录「%1\$s」相距不到 %2\$s 天，",
        Language.ZH_HANT to
            "與已有記錄「%1\$s」相距不到 %2\$s 天，",
        Language.EN to
            "It is less than %2\$s days from the existing record \"%1\$s\", ",
        Language.KO to
            "기존 기록 '%1\$s'과 %2\$s일 미만 차이이므로 ",
        ),
        "s_e29fc615" to mapOf(
        Language.ZH_HANS to
            "两个窗口的中位时刻只相隔 %1\$s 天，需要至少 ",
        Language.ZH_HANT to
            "兩個視窗的中位時刻只相隔 %1\$s 天，需要至少 ",
        Language.EN to
            "The median times of the two windows are only %1\$s days apart, and at least ",
        Language.KO to
            "두 구간의 중앙 시각이 %1\$s일밖에 차이 나지 않습니다. 최소 ",
        ),
        "s_e4b7e37c" to mapOf(
        Language.ZH_HANS to
            "推算结果 %1\$s℃ 低于可信区间下限 %2\$s℃：",
        Language.ZH_HANT to
            "推算結果 %1\$s℃ 低於可信區間下限 %2\$s℃：",
        Language.EN to
            "the estimated result %1\$s°C is below the lower bound of the plausible range" +
            "%2\$s°C: ",
        Language.KO to
            "추정 결과 %1\$s°C가 신뢰 구간 하한 %2\$s°C보다 낮습니다: ",
        ),
        "s_e523a885" to mapOf(
        Language.ZH_HANS to
            "收缩压通常在 %1\$s~%2\$s、",
        Language.ZH_HANT to
            "收縮壓通常在 %1\$s~%2\$s、",
        Language.EN to
            "Systolic pressure is usually %1\$s–%2\$s, ",
        Language.KO to
            "수축기압은 보통 %1\$s~%2\$s, ",
        ),
        "s_e687a298" to mapOf(
        Language.ZH_HANS to
            "%1\$s 分",
        Language.ZH_HANT to
            "%1\$s 分",
        Language.EN to
            "%1\$s pts",
        Language.KO to
            "%1\$s점",
        ),
        "s_e68f7923" to mapOf(
        Language.ZH_HANS to
            "测量时人在活动（活动档位「%1\$s」）。",
        Language.ZH_HANT to
            "測量時人在活動（活動檔位「%1\$s」）。",
        Language.EN to
            "You were active during the measurement (activity level \"%1\$s\").",
        Language.KO to
            "측정 시 활동 중이었습니다(활동 단계 '%1\$s').",
        ),
        "s_e70c48f1" to mapOf(
        Language.ZH_HANS to
            "只有 %1\$s 条可比记录（需要至少 %2\$s 条：数值有效、且当时没在明显活动）",
        Language.ZH_HANT to
            "只有 %1\$s 條可比記錄（需要至少 %2\$s 條：數值有效、且當時沒在明顯活動）",
        Language.EN to
            "there are only %1\$s comparable records (at least %2\$s are needed: valid values," +
            "and not clearly active at the time)",
        Language.KO to
            "비교 가능한 기록이 %1\$s개뿐입니다(최소 %2\$s개 필요: 값이 유효하고 당시 뚜렷한 활동이 없어야 함)",
        ),
        "s_e7cd15b2" to mapOf(
        Language.ZH_HANS to
            "比平时短 %1\$s ms（传导更快）",
        Language.ZH_HANT to
            "比平時短 %1\$s ms（傳導更快）",
        Language.EN to
            "%1\$s ms shorter than usual (faster conduction)",
        Language.KO to
            "평소보다 %1\$s ms 짧음(전도가 더 빠름)",
        ),
        "s_e7ecf9be" to mapOf(
        Language.ZH_HANS to
            "基线里有 %1\$s 条记录的活动状态未判定，",
        Language.ZH_HANT to
            "基線裡有 %1\$s 條記錄的活動狀態未判定，",
        Language.EN to
            "%1\$s records in the baseline have an undetermined activity state, ",
        Language.KO to
            "기준선에 활동 상태가 판정되지 않은 기록이 %1\$s개 있어 ",
        ),
        "s_e85ca052" to mapOf(
        Language.ZH_HANS to
            "注册失败: %1\$s: %2\$s",
        Language.ZH_HANT to
            "註冊失敗：%1\$s: %2\$s",
        Language.EN to
            "Registration failed: %1\$s: %2\$s",
        Language.KO to
            "등록 실패: %1\$s: %2\$s",
        ),
        "s_e9acf174" to mapOf(
        Language.ZH_HANS to
            "%1\$s 次/分",
        Language.ZH_HANT to
            "%1\$s 次/分",
        Language.EN to
            "%1\$s bpm",
        Language.KO to
            "%1\$s 회/분",
        ),
        "s_e9be9c18" to mapOf(
        Language.ZH_HANS to
            "%1\$s 秒，不足以刻画逐拍变异",
        Language.ZH_HANT to
            "%1\$s 秒，不足以刻畫逐拍變異",
        Language.EN to
            "%1\$s seconds, which is not enough to characterize beat-to-beat variability",
        Language.KO to
            "초에 못 미쳐 %1\$s초는 박동 간 변동을 나타내기에 부족합니다",
        ),
        "s_ea86aeb8" to mapOf(
        Language.ZH_HANS to
            "%1\$s 步",
        Language.ZH_HANT to
            "%1\$s 步",
        Language.EN to
            "%1\$s steps",
        Language.KO to
            "%1\$s걸음",
        ),
        "s_eb871cd6" to mapOf(
        Language.ZH_HANS to
            "资料不合法：%1\$s（身高 %2\$scm 体重 %3\$skg）",
        Language.ZH_HANT to
            "資料不合法：%1\$s（身高 %2\$scm 體重 %3\$skg）",
        Language.EN to
            "Invalid profile: %1\$s (height %2\$scm weight %3\$skg)",
        Language.KO to
            "프로필이 유효하지 않습니다: %1\$s(키 %2\$scm 몸무게 %3\$skg)",
        ),
        "s_eb9bac04" to mapOf(
        Language.ZH_HANS to
            "%1\$s小时%2\$s分",
        Language.ZH_HANT to
            "%1\$s小時%2\$s分",
        Language.EN to
            "%1\$s h %2\$s min",
        Language.KO to
            "%1\$s시간 %2\$s분",
        ),
        "s_ec82e730" to mapOf(
        Language.ZH_HANS to
            "%1\$s 分钟前",
        Language.ZH_HANT to
            "%1\$s 分鐘前",
        Language.EN to
            "%1\$s min ago",
        Language.KO to
            "%1\$s분 전",
        ),
        "s_ee98fed6" to mapOf(
        Language.ZH_HANS to
            "设备能力：%1\$s",
        Language.ZH_HANT to
            "裝置能力：%1\$s",
        Language.EN to
            "Device capabilities: %1\$s",
        Language.KO to
            "기기 기능: %1\$s",
        ),
        "s_ef129119" to mapOf(
        Language.ZH_HANS to
            "获取心率 Tracker 失败: %1\$s",
        Language.ZH_HANT to
            "取得心率 Tracker 失敗：%1\$s",
        Language.EN to
            "Failed to get the heart rate tracker: %1\$s",
        Language.KO to
            "심박수 Tracker 가져오기 실패: %1\$s",
        ),
        "s_ef841427" to mapOf(
        Language.ZH_HANS to
            "读数超出合理范围(%1\$s)，继续等待…",
        Language.ZH_HANT to
            "讀數超出合理範圍(%1\$s)，繼續等待…",
        Language.EN to
            "Reading outside the plausible range (%1\$s); still waiting…",
        Language.KO to
            "측정값이 합리적 범위를 벗어남(%1\$s), 계속 대기 중…",
        ),
        "s_f083cd00" to mapOf(
        Language.ZH_HANS to
            "节律整齐，心率 %1\$s 次/分（40~50 次/分）；",
        Language.ZH_HANT to
            "節律整齊，心率 %1\$s 次/分（40~50 次/分）；",
        Language.EN to
            "Regular rhythm, heart rate %1\$s bpm (40–50 bpm);",
        Language.KO to
            "리듬 규칙적, 심박수 %1\$s 회/분(40~50 회/분);",
        ),
        "s_f0b7f01b" to mapOf(
        Language.ZH_HANS to
            "这段记录只有 %1\$s 秒，按算出的频率只覆盖 %2\$s 次呼吸",
        Language.ZH_HANT to
            "這段記錄只有 %1\$s 秒，按算出的頻率只涵蓋 %2\$s 次呼吸",
        Language.EN to
            "This record is only %1\$s seconds long, which at the computed frequency covers" +
            "only %2\$s breaths",
        Language.KO to
            "이 기록은 %1\$s초뿐이라 계산된 주파수 기준으로 %2\$s회 호흡만 포함합니다",
        ),
        "s_f169a62d" to mapOf(
        Language.ZH_HANS to
            "重试中(%1\$s/2)…",
        Language.ZH_HANT to
            "重試中(%1\$s/2)…",
        Language.EN to
            "Retrying (%1\$s/2)…",
        Language.KO to
            "재시도 중(%1\$s/2)…",
        ),
        "s_f4ed055b" to mapOf(
        Language.ZH_HANS to
            "有效心搏只有 %1\$s 次（需要至少 %2\$s 次）。",
        Language.ZH_HANT to
            "有效心搏只有 %1\$s 次（需要至少 %2\$s 次）。",
        Language.EN to
            "There are only %1\$s valid heartbeats (at least %2\$s are needed).",
        Language.KO to
            "유효한 심장 박동이 %1\$s회뿐입니다(최소 %2\$s회 필요).",
        ),
        "s_f716d88f" to mapOf(
        Language.ZH_HANS to
            "落库回调异常: %1\$s",
        Language.ZH_HANT to
            "落庫回呼異常：%1\$s",
        Language.EN to
            "Database callback error: %1\$s",
        Language.KO to
            "데이터베이스 콜백 오류: %1\$s",
        ),
        "s_f79a6375" to mapOf(
        Language.ZH_HANS to
            "设备不支持血氧测量（能力：%1\$s）",
        Language.ZH_HANT to
            "裝置不支援血氧測量（能力：%1\$s）",
        Language.EN to
            "Device does not support blood oxygen measurement (capabilities: %1\$s)",
        Language.KO to
            "기기가 혈중 산소 측정을 지원하지 않습니다(기능: %1\$s)",
        ),
        "s_fcce8df3" to mapOf(
        Language.ZH_HANS to
            "%1\$s~%2\$s 天范围内（多为漏记或重复录入）。",
        Language.ZH_HANT to
            "%1\$s~%2\$s 天範圍內（多為漏記或重複錄入）。",
        Language.EN to
            "%1\$s–%2\$s days (usually due to missed or duplicate entries).",
        Language.KO to
            "%1\$s~%2\$s일 범위에 들어가지 않습니다(대개 누락이나 중복 입력).",
        ),
        "s_fd4301cb" to mapOf(
        Language.ZH_HANS to
            "你自己 %1\$s℃",
        Language.ZH_HANT to
            "你自己 %1\$s℃",
        Language.EN to
            "your own %1\$s°C",
        Language.KO to
            "본인 %1\$s°C",
        ),
    )

    /**
     * 取某条**无插值**文案。
     *
     * @param l 省略时用[当前语言][lang]（core 内部绝大多数调用点都该省略；
     *   显式传参只用于「必须固定某种语言」的场合，如台架逐语言核验）。
     * @throws IllegalStateException key 未登记 —— 宁可当场炸，也不要静默显示 key。
     */
    fun of(key: String, l: Language = lang): String =
        TABLE[key]?.get(l) ?: error("Text: 未登记的 key=$key")

    /** 取某条文案；key 不存在返回 null（**用于运行期才拿到的 key**）。 */
    fun ofOrNull(key: String, l: Language = lang): String? = TABLE[key]?.get(l)

    /**
     * 取某条**有插值**文案并填参数。
     *
     * ⚠ 实参顺序**按 zhHans 里变量首次出现的顺序**（不是按译文语序）——
     * 各语言语序可以不同，序号由模板里的 `%N$s` 决定，所以译文怎么排都对位。
     *
     * ```kotlin
     * // 原文："脉搏 $pulse 超出常见的 $MIN_PULSE~$MAX_PULSE 次/分"
     * Text.fmt("s_ae118c8b", pulse, MIN_PULSE, MAX_PULSE)
     * ```
     *
     * ⚠ 用 [java.util.Locale.ROOT]：避免某些区域把数字换成非拉丁数字
     * （项目里所有 `String.format` 都显式给 Locale，同一条纪律）。
     */
    fun fmt(key: String, vararg args: Any?): String {
        val t = TPL[key]?.get(lang) ?: error("Text: 未登记的模板 key=$key")
        return String.format(java.util.Locale.ROOT, t, *args)
    }

    /** 取模板原文（**台架用**：核对占位符是否与译文的语序自洽）。 */
    fun tpl(key: String, l: Language = lang): String? = TPL[key]?.get(l)

    /** 是否登记过这个 key（两表都查；台架用）。 */
    fun has(key: String): Boolean = TABLE.containsKey(key) || TPL.containsKey(key)

    /** 全部已登记的无插值 key（台架用）。 */
    val KEYS: Set<String> get() = TABLE.keys

    /** 全部已登记的模板 key（台架用）。 */
    val TPL_KEYS: Set<String> get() = TPL.keys

    /** 无插值条目数（台架用）。 */
    val SIZE: Int get() = TABLE.size

    /** 模板条目数（台架用）。 */
    val TPL_SIZE: Int get() = TPL.size
}
