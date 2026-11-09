package com.gemini.healthbridge.core

/**
 * **医疗免责与合规文案**的四语言表（模块 #15，2026-09-29）。
 *
 * ## ⚠⚠ 为什么单独一个文件、而且译文要逐条校对
 *
 * 这些文案全部涉及**医疗免责**（"不是诊断" / "不能用于排除疾病" / "请就医" /
 * "不能用于避孕"）。翻译出错不是排版问题，是**合规风险**：
 * 一句"不能诊断"若被译成语气更弱的表述，等于把筛查结果说成了结论。
 *
 * 所以本文件的三条纪律：
 * ① **中文原文逐字照抄**改造前各 `*.kt` 里的常量（`Energy.DISCLAIMER` 等），一个字不改；
 * ② 译文**宁可直白、不许弱化**：`不能诊断任何疾病` 类句式一律译成明确的否定
 *    （en: `cannot diagnose any disease`），绝不用 may/might/could 这类软化词；
 * ③ 「请就医 / 咨询医生 / 不能用于避孕 / 不能排除」这些**导向性句子必须完整保留** ——
 *    它们才是免责声明真正起作用的部分，删掉等于免责声明只剩前半句。
 *
 * ## 用法
 *
 * 调用方（app / wear）把当前语言传进来：
 * ```
 * val lang = LanguageStore.current(context)
 * Text(Disclaimer.energy(lang))
 * ```
 * ⚠ 原常量（`Energy.DISCLAIMER` 等）**保留不动**：它们是中文原文的单一真源，
 * 也被现有台架（`energycheck` / `stresscheck` …）钉着。这里的 `zhHans` 必须与它们逐字相同，
 * 由离线校验台架断言（见该台架「原文一致性」一节）。
 */
object Disclaimer {

    // ══════════════════ 能量评分（#16）══════════════════

    /** 对应 `Energy.DISCLAIMER`。 */
    private val ENERGY = mapOf(
        Language.ZH_HANS to
            "这是由睡眠时长、睡眠期最低血氧、睡眠心率与心率变化、夜间皮肤温度、" +
                "入睡时刻的规律性与定时、以及前一天的活动量折算出的参考分数，" +
                "不是医学测量，也不能诊断任何疾病。" +
                "它不含主观感受（困不困只有你自己知道）。" +
                "如果你连续多日睡够、这里的数也正常却仍然疲惫，请就医而不是看这个分数。",
        Language.ZH_HANT to
            "這是由睡眠時長、睡眠期最低血氧、睡眠心率與心率變化、夜間皮膚溫度、" +
                "入睡時刻的規律性與定時、以及前一天的活動量折算出的參考分數，" +
                "不是醫學測量，也不能診斷任何疾病。" +
                "它不含主觀感受（睏不睏只有你自己知道）。" +
                "如果你連續多日睡夠、這裡的數也正常卻仍然疲憊，請就醫而不是看這個分數。",
        Language.EN to
            "This is a reference score derived from sleep duration, the lowest blood oxygen " +
                "during sleep, sleeping heart rate and its variability, nighttime skin temperature, " +
                "the regularity and timing of when you fall asleep, and your activity on the " +
                "previous day. It is not a medical measurement and cannot diagnose any disease. " +
                "It does not include how you subjectively feel (only you know whether you are tired). " +
                "If you have slept enough for several days in a row and this number looks normal " +
                "yet you still feel exhausted, see a doctor rather than relying on this score.",
        Language.KO to
            "이 점수는 수면 시간, 수면 중 최저 혈중 산소, 수면 심박수와 그 변동, 야간 피부 온도, " +
                "잠드는 시각의 규칙성과 시간대, 그리고 전날의 활동량을 바탕으로 산출한 참고 점수이며, " +
                "의학적 측정이 아니고 어떤 질병도 진단할 수 없습니다. " +
                "주관적인 느낌은 포함되지 않습니다(피곤한지는 본인만 알 수 있습니다). " +
                "며칠째 충분히 잤는데도 이 수치가 정상인데 계속 피곤하다면, " +
                "이 점수를 보지 말고 병원에 가 보시기 바랍니다.",
    )

    fun energy(l: Language) = ENERGY.getValue(l)

    // ══════════════════ 压力 / 疲劳（#12）══════════════════

    /** 对应 `Stress.DISCLAIMER`。 */
    private val STRESS = mapOf(
        Language.ZH_HANS to
            "这是由 HRV、静息心率与皮温梯度折算出的参考分数，不是医学测量，也不能诊断压力或疲劳相关疾病。" +
                "它的分辨率只到「你这次比你平时怎么样」，受睡眠、咖啡因、体位、情绪、测量时刻影响很大。" +
                "如长期感到疲惫或不适，请就医而不是看这个分数。",
        Language.ZH_HANT to
            "這是由 HRV、靜息心率與皮溫梯度折算出的參考分數，不是醫學測量，也不能診斷壓力或疲勞相關疾病。" +
                "它的解析度只到「你這次比你平時怎麼樣」，受睡眠、咖啡因、體位、情緒、測量時刻影響很大。" +
                "如長期感到疲憊或不適，請就醫而不是看這個分數。",
        Language.EN to
            "This is a reference score derived from HRV, resting heart rate, and the skin " +
                "temperature gradient. It is not a medical measurement and cannot diagnose any " +
                "stress- or fatigue-related disease. Its resolution only goes as far as " +
                "\"how you compare with your own usual state\"; it is strongly affected by sleep, " +
                "caffeine, posture, mood, and the time of measurement. If you feel exhausted or " +
                "unwell for a long period, see a doctor rather than relying on this score.",
        Language.KO to
            "이 점수는 HRV, 안정 시 심박수, 피부 온도 구배를 바탕으로 산출한 참고 점수이며, " +
                "의학적 측정이 아니고 스트레스나 피로 관련 질병을 진단할 수 없습니다. " +
                "해상도는 \"이번이 평소와 비교해 어떤지\" 수준이며, 수면·카페인·자세·감정·측정 시각의 " +
                "영향을 크게 받습니다. 장기간 피곤하거나 몸이 좋지 않다면 " +
                "이 점수를 보지 말고 병원에 가 보시기 바랍니다.",
    )

    fun stress(l: Language) = STRESS.getValue(l)

    // ══════════════════ HRV（#9）══════════════════

    /** 对应 `Hrv.DISCLAIMER`。 */
    private val HRV = mapOf(
        Language.ZH_HANS to
            "HRV 反映的是自主神经对心率的调节强弱，不是诊断指标。佩戴状态、体位、情绪都会影响它，" +
                "数值异常不等于疾病；持续不适请就医。",
        Language.ZH_HANT to
            "HRV 反映的是自律神經對心率的調節強弱，不是診斷指標。配戴狀態、體位、情緒都會影響它，" +
                "數值異常不等於疾病；持續不適請就醫。",
        Language.EN to
            "HRV reflects how strongly the autonomic nervous system modulates heart rate; " +
                "it is not a diagnostic measure. Wearing state, posture, and mood all affect it, " +
                "and an abnormal value does not mean disease. If you feel unwell persistently, " +
                "see a doctor.",
        Language.KO to
            "HRV는 자율신경이 심박수를 조절하는 강도를 나타내며 진단 지표가 아닙니다. " +
                "착용 상태, 자세, 감정의 영향을 받고 수치가 이상해도 질병을 의미하지 않습니다. " +
                "지속적으로 몸이 불편하면 병원에 가 보시기 바랍니다.",
    )

    fun hrv(l: Language) = HRV.getValue(l)

    // ══════════════════ 皮肤温度（#8）══════════════════

    /** 对应 `Temperature.DISCLAIMER`。 */
    private val SKIN_TEMP = mapOf(
        Language.ZH_HANS to
            "腕部皮肤温度不等于体温：通常比腋下体温低 1~3 ℃，且受环境温度、佩戴松紧、手臂姿势影响。" +
                "本功能仅用于观察自身趋势，不能用于判断发热或诊断任何疾病。",
        Language.ZH_HANT to
            "腕部皮膚溫度不等於體溫：通常比腋下體溫低 1~3 ℃，且受環境溫度、配戴鬆緊、手臂姿勢影響。" +
                "本功能僅用於觀察自身趨勢，不能用於判斷發燒或診斷任何疾病。",
        Language.EN to
            "Wrist skin temperature is not body temperature: it is usually 1-3 °C lower than " +
                "underarm temperature and is affected by ambient temperature, strap tightness, " +
                "and arm posture. This feature is only for observing your own trends and cannot be " +
                "used to judge fever or diagnose any disease.",
        Language.KO to
            "손목 피부 온도는 체온이 아닙니다. 일반적으로 겨드랑이 체온보다 1~3 ℃ 낮고 " +
                "주변 온도, 착용 밴드의 조임, 팔 자세에 영향을 받습니다. " +
                "이 기능은 자신의 추세를 관찰하는 용도일 뿐이며, " +
                "발열을 판단하거나 어떤 질병도 진단하는 데 사용할 수 없습니다.",
    )

    fun skinTemp(l: Language) = SKIN_TEMP.getValue(l)

    /** 对应 `Temperature.CORE_DISCLAIMER`。 */
    private val CORE_TEMP = mapOf(
        Language.ZH_HANS to
            "这是由皮温与另一个温度推算出的估计值，不是测出来的体温：误差通常在 ±0.5℃ 以上。" +
                "⚠ 本机这个推算目前给不出结果：它需要室温，而手表给出的那个「表体温度」不是室温" +
                "（佩戴时被手腕与机身加热，实测长期在 31~36℃）。" +
                "只可用于观察自身趋势，不能用于判断发热或诊断任何疾病。",
        Language.ZH_HANT to
            "這是由皮溫與另一個溫度推算出的估計值，不是測出來的體溫：誤差通常在 ±0.5℃ 以上。" +
                "⚠ 本機這個推算目前給不出結果：它需要室溫，而手錶給出的那個「錶體溫度」不是室溫" +
                "（配戴時被手腕與機身加熱，實測長期在 31~36℃）。" +
                "只可用於觀察自身趨勢，不能用於判斷發燒或診斷任何疾病。",
        Language.EN to
            "This is an estimate derived from skin temperature and another temperature, not a " +
                "measured body temperature: the error is usually ±0.5 °C or more. " +
                "⚠ On this device the estimate currently cannot produce a result: it needs room " +
                "temperature, and the \"case temperature\" reported by the watch is not room " +
                "temperature (it is heated by the wrist and the device body while worn, measured " +
                "at 31-36 °C over long periods). It may only be used to observe your own trends " +
                "and cannot be used to judge fever or diagnose any disease.",
        Language.KO to
            "이것은 피부 온도와 다른 온도로부터 산출한 추정값이며 실제로 측정한 체온이 아닙니다. " +
                "오차는 보통 ±0.5℃ 이상입니다. ⚠ 이 기기에서는 현재 이 추정값을 낼 수 없습니다. " +
                "실내 온도가 필요한데, 워치가 알려주는 \"본체 온도\"는 실내 온도가 아닙니다 " +
                "(착용 시 손목과 기기 본체에 의해 가열되어 실측 시 장기간 31~36℃). " +
                "자신의 추세를 관찰하는 데에만 사용할 수 있고, " +
                "발열을 판단하거나 어떤 질병도 진단하는 데 사용할 수 없습니다.",
    )

    fun coreTemp(l: Language) = CORE_TEMP.getValue(l)

    /** 对应 `Temperature.NIGHT_DEVIATION_ADVICE`。 */
    private val NIGHT_TEMP_ADVICE = mapOf(
        Language.ZH_HANS to
            "这个数只提示「这一晚和平时不太一样」。若连续几晚偏得较多、或本身有不舒服，" +
                "请用体温计确认。",
        Language.ZH_HANT to
            "這個數只提示「這一晚和平時不太一樣」。若連續幾晚偏得較多、或本身有不舒服，" +
                "請用體溫計確認。",        Language.EN to
            "This number only indicates that \"this night is somewhat different from your usual\". " +
                "If it deviates a lot for several nights in a row, or you feel unwell, " +
                "please confirm with a thermometer.",
        Language.KO to
            "이 수치는 \"오늘 밤이 평소와 조금 다르다\"는 것만 알려줍니다. " +
                "며칠 연속 많이 벗어나거나 몸이 좋지 않다면 체온계로 확인하시기 바랍니다.",
    )

    fun nightTempAdvice(l: Language) = NIGHT_TEMP_ADVICE.getValue(l)

    // ══════════════════ 多频阻抗 / 体成分（#14）══════════════════

    /** 对应 `MfBia.DISCLAIMER`。 */
    private val MFBIA = mapOf(
        Language.ZH_HANS to
            "阻抗谱是自研分析结果，没有与任何临床方法（DXA、生物阻抗分析仪）做过比对，" +
                "不能用于诊断。它最可靠的用法是看你自己的长期趋势，而不是与别人比较。",
        Language.ZH_HANT to
            "阻抗譜是自研分析結果，沒有與任何臨床方法（DXA、生物阻抗分析儀）做過比對，" +
                "不能用於診斷。它最可靠的用法是看你自己的長期趨勢，而不是與別人比較。",
        Language.EN to
            "The impedance spectrum is our own analysis and has not been compared against any " +
                "clinical method (DXA, bioimpedance analyzers); it cannot be used for diagnosis. " +
                "Its most reliable use is to look at your own long-term trend, not to compare " +
                "with others.",
        Language.KO to
            "임피던스 스펙트럼은 자체 개발한 분석 결과이며, 어떤 임상 방법(DXA, 생체 임피던스 " +
                "분석기)과도 비교 검증을 거치지 않았고 진단에 사용할 수 없습니다. " +
                "가장 신뢰할 수 있는 사용법은 다른 사람과 비교하는 것이 아니라 " +
                "자신의 장기 추세를 보는 것입니다.",
    )

    fun mfBia(l: Language) = MFBIA.getValue(l)

    /** 对应 `MfBia.PHASE_SIGN_NOTE`。 */
    private val PHASE_SIGN = mapOf(
        Language.ZH_HANS to
            "相位值原样显示：三星没有公开相位角的符号约定（报告的是超前还是滞后、以哪个频率为基准），" +
                "所以本页不对相位的符号做任何解释，也不给出「偏高/偏低」的判断。",
        Language.ZH_HANT to
            "相位值原樣顯示：三星沒有公開相位角的符號約定（報告的是超前還是滯後、以哪個頻率為基準），" +
                "所以本頁不對相位的符號做任何解釋，也不給出「偏高/偏低」的判斷。",
        Language.EN to
            "Phase values are shown as-is: Samsung has not published the sign convention for the " +
                "phase angle (whether it reports lead or lag, and at which frequency it is " +
                "referenced), so this page does not interpret the sign in any way and does not " +
                "give any \"high/low\" judgment.",
        Language.KO to
            "위상 값은 그대로 표시합니다. 삼성은 위상각의 부호 규약(리드인지 래그인지, 어느 주파수를 " +
                "기준으로 하는지)을 공개하지 않았으므로, 이 페이지는 부호에 대한 어떤 해석도 하지 않고 " +
                "\"높음/낮음\" 판단도 제시하지 않습니다.",
    )

    fun phaseSign(l: Language) = PHASE_SIGN.getValue(l)

    // ══════════════════ 氧减指数 ODI（#11）══════════════════

    /** 对应 `Odi.DISCLAIMER`。 */
    private val ODI = mapOf(
        Language.ZH_HANS to
            "ODI 是筛查参考，不是诊断。分钟级腕式血氧与多导睡眠监测（PSG）不可互换：" +
                "它既会漏掉很短的事件，也可能把翻身、表带松动的伪迹算成事件。" +
                "本结果不能用于诊断或排除睡眠呼吸暂停，如怀疑请就医。",
        Language.ZH_HANT to
            "ODI 是篩查參考，不是診斷。分鐘級腕式血氧與多導睡眠監測（PSG）不可互換：" +
                "它既會漏掉很短的事件，也可能把翻身、錶帶鬆動的偽跡算成事件。" +
                "本結果不能用於診斷或排除睡眠呼吸中止，如懷疑請就醫。",
        Language.EN to
            "ODI is a screening reference, not a diagnosis. Minute-level wrist oximetry and " +
                "polysomnography (PSG) are not interchangeable: it will miss very short events and " +
                "may also count artifacts such as turning over or a loose strap as events. This " +
                "result cannot be used to diagnose or rule out sleep apnea; if you suspect it, " +
                "see a doctor.",
        Language.KO to
            "ODI는 선별 참고치이며 진단이 아닙니다. 분 단위 손목 산소포화도와 수면다원검사(PSG)는 " +
                "서로 대체할 수 없습니다: 아주 짧은 이벤트를 놓칠 수 있고, 뒤척임이나 밴드가 느슨한 " +
                "아티팩트를 이벤트로 셀 수도 있습니다. 이 결과는 수면 무호흡을 진단하거나 배제하는 " +
                "데 사용할 수 없으며, 의심되면 병원에 가 보시기 바랍니다.",
    )

    fun odi(l: Language) = ODI.getValue(l)

    // ══════════════════ 呼吸率（#11）══════════════════

    /** 对应 `Resp.DISCLAIMER`。 */
    private val RESP = mapOf(
        Language.ZH_HANS to
            "这是自研估算值，没有与任何临床金标准（肺功能、胸带、多导睡眠监测）做过比对，" +
                "不能用于诊断或排除任何疾病。常见静息范围 12~20 次/分只是参考区间，不是本算法的结论。",
        Language.ZH_HANT to
            "這是自研估算值，沒有與任何臨床金標準（肺功能、胸帶、多導睡眠監測）做過比對，" +
                "不能用於診斷或排除任何疾病。常見靜息範圍 12~20 次/分只是參考區間，不是本演算法的結論。",
        Language.EN to
            "This is our own estimate and has not been compared against any clinical gold standard " +
                "(pulmonary function testing, chest belt, polysomnography); it cannot be used to " +
                "diagnose or rule out any disease. The common resting range of 12-20 breaths/min " +
                "is only a reference interval, not a conclusion of this algorithm.",
        Language.KO to
            "이것은 자체 개발한 추정값이며, 어떤 임상 표준(폐기능 검사, 흉부 벨트, 수면다원검사)과도 " +
                "비교 검증을 거치지 않았고 어떤 질병도 진단하거나 배제하는 데 사용할 수 없습니다. " +
                "일반적인 안정 시 범위 12~20회/분은 참고 구간일 뿐이며, " +
                "이 알고리즘의 결론이 아닙니다.",
    )

    fun resp(l: Language) = RESP.getValue(l)

    // ══════════════════ 睡眠呼吸暂停筛查 ══════════════════

    /** 对应 `Apnea.NOT_A_DIAGNOSIS`。 */
    private val APNEA = mapOf(
        Language.ZH_HANS to
            "这是筛查信号，不是诊断。它只提示存在这类迹象的可能性，" +
                "未检测到也不代表没有 —— 有疑虑请就医。",
        Language.ZH_HANT to
            "這是篩查訊號，不是診斷。它只提示存在這類跡象的可能性，" +
                "未檢測到也不代表沒有 —— 有疑慮請就醫。",
        Language.EN to
            "This is a screening signal, not a diagnosis. It only indicates the possibility that " +
                "such signs are present; not detecting them does not mean they are absent. " +
                "If you have concerns, see a doctor.",
        Language.KO to
            "이것은 선별 신호이며 진단이 아닙니다. 이러한 징후가 있을 가능성만 알려주며, " +
                "감지되지 않았다고 해서 없는 것은 아닙니다. 걱정되면 병원에 가 보시기 바랍니다.",
    )

    fun apnea(l: Language) = APNEA.getValue(l)

    // ══════════════════ 经期预测（#13）══════════════════

    /** 对应 `Cycle.DISCLAIMER`。 */
    private val CYCLE = mapOf(
        Language.ZH_HANS to
            "⚠ 本预测基于历史周期的统计外推，不是医学判断，不能用于避孕，也不能作为备孕依据。" +
                "周期会受压力、睡眠、疾病、跨时区旅行、体重变化与药物影响；" +
                "若周期长期短于 21 天、长于 35 天，或突然明显改变，请咨询医生",
        Language.ZH_HANT to
            "⚠ 本預測基於歷史週期的統計外推，不是醫學判斷，不能用於避孕，也不能作為備孕依據。" +
                "週期會受壓力、睡眠、疾病、跨時區旅行、體重變化與藥物影響；" +
                "若週期長期短於 21 天、長於 35 天，或突然明顯改變，請諮詢醫生",
        Language.EN to
            "⚠ This prediction is a statistical extrapolation from past cycles, not a medical " +
                "judgment. It must not be used for contraception or as a basis for trying to " +
                "conceive. Cycles are affected by stress, sleep, illness, travel across time " +
                "zones, weight change, and medication. If your cycle is persistently shorter than " +
                "21 days or longer than 35 days, or changes suddenly and markedly, consult a doctor.",
        Language.KO to
            "⚠ 이 예측은 과거 주기의 통계적 외삽이며 의학적 판단이 아닙니다. 피임에 사용할 수 없고 " +
                "임신을 준비하는 근거로도 사용할 수 없습니다. 주기는 스트레스, 수면, 질병, " +
                "시차 이동, 체중 변화, 약물의 영향을 받습니다. 주기가 장기간 21일 미만이거나 " +
                "35일을 넘거나 갑자기 뚜렷하게 변하면 의사와 상담하시기 바랍니다.",
    )

    fun cycle(l: Language) = CYCLE.getValue(l)

    // ══════════════════ 血压（#14 / 手动录入）══════════════════

    /** 对应 `Bp.MANUAL_ENTRY_NOTE`。 */
    private val BP_MANUAL = mapOf(
        Language.ZH_HANS to
            "这里填的是你自己用血压计测到的读数，会按你填的原样保存。" +
                "本 App 不做血压判定，也不诊断任何疾病。",
        Language.ZH_HANT to
            "這裡填的是你自己用血壓計測到的讀數，會按你填的原樣儲存。" +
                "本 App 不做血壓判定，也不診斷任何疾病。",
        Language.EN to
            "What you enter here is the reading you measured yourself with a blood pressure " +
                "monitor, and it is saved exactly as you entered it. This app does not judge blood " +
                "pressure and does not diagnose any disease.",
        Language.KO to
            "여기에 입력하는 값은 직접 혈압계로 측정한 수치이며, 입력한 그대로 저장됩니다. " +
                "이 앱은 혈압을 판정하지 않고 어떤 질병도 진단하지 않습니다.",
    )

    fun bpManual(l: Language) = BP_MANUAL.getValue(l)

    // ══════════════════ 血压预估（#14 方案 C）══════════════════

    /**
     * 血压**预估**的第一条免责。⚠ 这是整套功能里最要紧的一句：
     * 用户给的红线是「文案只能写**血压预估参考**，不能写测量血压、不能宣称可诊断高血压」。
     * 那条红线在算法层已经兑现（区间半宽下限 15 mmHg、不给分级），**这里兑现的是措辞层**。
     */
    // ⚠⚠⚠ **每一条都必须显式传 `Language`**：`Text.of(key)` 省略语言时读的是
    //   **当前**语言，而这个 `mapOf` 只在**类初始化时算一次** ——
    //   四条会全部塌成初始化那一刻的那一种语言（langcheck 第 [8] 节当场抓到：
    //   英文/韩文条目里全是汉字）。这个坑与「类级 val 只算一次」同源，但更隐蔽：
    //   它不报错，只是让四种语言变成一种。
    private val BP_ESTIMATE = mapOf(
        Language.ZH_HANS to Text.of("s_9428b2f3", Language.ZH_HANS),
        Language.ZH_HANT to Text.of("s_9428b2f3", Language.ZH_HANT),
        Language.EN to Text.of("s_9428b2f3", Language.EN),
        Language.KO to Text.of("s_9428b2f3", Language.KO),
    )

    /**
     * 「样本不足就不给数值」这句要**主动说给用户听** ——
     * 否则他会以为功能坏了、或者以为 App 在藏数据。
     */
    private val BP_ESTIMATE_NOT_ENOUGH = mapOf(
        Language.ZH_HANS to Text.of("s_949f46f3", Language.ZH_HANS),
        Language.ZH_HANT to Text.of("s_949f46f3", Language.ZH_HANT),
        Language.EN to Text.of("s_949f46f3", Language.EN),
        Language.KO to Text.of("s_949f46f3", Language.KO),
    )

    fun bpEstimate(l: Language) = BP_ESTIMATE.getValue(l)

    fun bpEstimateNotEnough(l: Language) = BP_ESTIMATE_NOT_ENOUGH.getValue(l)

    // ══════════════════ 三星健康：心律不齐筛查 ══════════════════

    /** 对应 `SamsungExtras.NotADiagnosis.ADVICE`。 */
    private val IRREGULAR_ADVICE = mapOf(
        Language.ZH_HANS to
            "如果看到这条，建议用手表做一次心电测量（打开三星健康监测器，手指按住上键 30 秒）。" +
                "它只是一条筛查提示，不是诊断。",
        Language.ZH_HANT to
            "如果看到這條，建議用手錶做一次心電測量（打開三星健康監測器，手指按住上鍵 30 秒）。" +
                "它只是一條篩查提示，不是診斷。",
        Language.EN to
            "If you see this, we suggest taking an ECG with your watch (open Samsung Health " +
                "Monitor and hold your finger on the top button for 30 seconds). It is only a " +
                "screening prompt, not a diagnosis.",
        Language.KO to
            "이 항목이 보이면 워치로 심전도를 한 번 측정해 보시기 바랍니다 (삼성 헬스 모니터를 열고 " +
                "상단 버튼에 손가락을 30초간 대고 있으면 됩니다). " +
                "이것은 선별 안내일 뿐이며 진단이 아닙니다.",
    )

    fun irregularAdvice(l: Language) = IRREGULAR_ADVICE.getValue(l)

    /** 对应 `SamsungExtras.NotADiagnosis.EMPTY_IS_NOT_NEGATIVE`。 */
    private val IRREGULAR_EMPTY = mapOf(
        Language.ZH_HANS to
            "⚠ 「这里没有记录」不等于「心脏没问题」。 这个功能要你在三星健康监测器里" +
                "开启才会记录；没开启、或者手表当时没戴，都不会产生记录。" +
                "而且它只报「检测到迹象」的那几次 —— 没有「未检测到」这个取值。",
        Language.ZH_HANT to
            "⚠ 「這裡沒有記錄」不等於「心臟沒問題」。 這個功能要你在三星健康監測器裡" +
                "開啟才會記錄；沒開啟、或者手錶當時沒戴，都不會產生記錄。" +
                "而且它只報「檢測到跡象」的那幾次 —— 沒有「未檢測到」這個取值。",
        Language.EN to
            "⚠ \"There are no records here\" does not mean \"your heart is fine\". This feature " +
                "only records if you enable it in Samsung Health Monitor; if it is not enabled, " +
                "or the watch was not being worn at the time, no record is produced. It also only " +
                "reports the occasions when signs were detected -- there is no \"not detected\" value.",
        Language.KO to
            "⚠ \"여기에 기록이 없다\"는 \"심장에 문제가 없다\"는 뜻이 아닙니다. 이 기능은 삼성 헬스 " +
                "모니터에서 켜 두어야 기록됩니다. 켜 두지 않았거나 그때 워치를 착용하지 않았다면 " +
                "기록이 생기지 않습니다. 또한 \"징후가 감지된\" 경우만 보고하며, " +
                "\"감지되지 않음\"이라는 값은 없습니다.",
    )

    fun irregularEmptyIsNotNegative(l: Language) = IRREGULAR_EMPTY.getValue(l)

    /** 对应 `SamsungExtras.NotADiagnosis.NOT_A_DIAGNOSIS`。 */
    private val IRREGULAR_DISCLAIMER = mapOf(
        Language.ZH_HANS to
            "本项仅如实展示三星健康里的记录，不做任何诊断，也不能用来排除心脏问题。" +
                "有关症状请就医。",
        Language.ZH_HANT to
            "本項僅如實展示三星健康裡的記錄，不做任何診斷，也不能用來排除心臟問題。" +
                "有關症狀請就醫。",
        Language.EN to
            "This item only faithfully displays the records in Samsung Health. It makes no " +
                "diagnosis and cannot be used to rule out heart problems. If you have symptoms, " +
                "see a doctor.",
        Language.KO to
            "이 항목은 삼성 헬스의 기록을 그대로 보여줄 뿐이며, 어떤 진단도 하지 않고 " +
                "심장 문제를 배제하는 데에도 사용할 수 없습니다. 증상이 있으면 병원에 가 보시기 바랍니다.",
    )

    fun irregularDisclaimer(l: Language) = IRREGULAR_DISCLAIMER.getValue(l)

    // ══════════════════ 三星健康：血糖 ══════════════════

    /** 对应 `SamsungExtras.Glucose.UNIT_NOTE`。 */
    private val GLUCOSE_UNIT = mapOf(
        Language.ZH_HANS to "单位是 mmol/L（与三星健康一致）。",
        Language.ZH_HANT to "單位是 mmol/L（與三星健康一致）。",
        Language.EN to "The unit is mmol/L (consistent with Samsung Health).",
        Language.KO to "단위는 mmol/L입니다 (삼성 헬스와 동일).",
    )

    fun glucoseUnit(l: Language) = GLUCOSE_UNIT.getValue(l)

    /** 对应 `SamsungExtras.Glucose.NO_JUDGEMENT`。 */
    private val GLUCOSE_NO_JUDGEMENT = mapOf(
        Language.ZH_HANS to
            "本页只如实显示数值、不做高低判断：血糖怎么解读取决于测量情境" +
                "（空腹还是餐后多久、有没有用药），本 App 拿不到这些上下文，凭一个数下结论是越界。",
        Language.ZH_HANT to
            "本頁只如實顯示數值、不做高低判斷：血糖怎麼解讀取決於測量情境" +
                "（空腹還是餐後多久、有沒有用藥），本 App 拿不到這些情境，憑一個數下結論是越界。",
        Language.EN to
            "This page only displays the value faithfully and makes no high/low judgment: how to " +
                "interpret blood glucose depends on the measurement context (fasting or how long " +
                "after a meal, whether medication was taken). This app does not have that context, " +
                "and drawing a conclusion from a single number would be overstepping.",
        Language.KO to
            "이 페이지는 수치를 그대로 보여줄 뿐 높낮이를 판단하지 않습니다. 혈당 해석은 측정 상황" +
                "(공복인지, 식후 얼마나 지났는지, 약을 복용했는지)에 따라 달라지는데, " +
                "이 앱은 그러한 맥락을 알 수 없으므로 숫자 하나로 결론을 내리는 것은 월권입니다.",
    )

    fun glucoseNoJudgement(l: Language) = GLUCOSE_NO_JUDGEMENT.getValue(l)

    // ══════════════════ 饮食（营养 / 水分）══════════════════

    /** 对应 `DietNotes.NOT_A_PLAN`。 */
    private val DIET_NOT_A_PLAN = mapOf(
        Language.ZH_HANS to
            "这里显示的是三星健康里的目标值（你在三星健康里设过才有），" +
                "本 App 不做任何营养建议，也不评价你吃得对不对。",
        Language.ZH_HANT to
            "這裡顯示的是三星健康裡的目標值（你在三星健康裡設過才有），" +
                "本 App 不做任何營養建議，也不評價你吃得對不對。",
        Language.EN to
            "What is shown here is the target value from Samsung Health (it appears only if you " +
                "have set one there). This app gives no nutritional advice and does not judge " +
                "whether you eat well or not.",
        Language.KO to
            "여기에 표시되는 것은 삼성 헬스의 목표값입니다(삼성 헬스에서 설정한 경우에만 표시됩니다). " +
                "이 앱은 어떤 영양 조언도 하지 않으며 식사가 적절한지 평가하지도 않습니다.",
    )

    fun dietNotAPlan(l: Language) = DIET_NOT_A_PLAN.getValue(l)

    // ══════════════════ 台架用：全部条目 ══════════════════

    /**
     * 全部免责条目的「名字 → 四语言文案」，**只给台架用**。
     *
     * 离线校验台架据此断言：四语言齐全、无空串、无残留中文占位
     * （英/韩条目里不该出现 CJK 字符 —— 这能抓住"忘了翻译直接复制中文"的低级错误）。
     */
    val ALL: Map<String, Map<Language, String>> = linkedMapOf(
        "energy" to ENERGY,
        "stress" to STRESS,
        "hrv" to HRV,
        "skinTemp" to SKIN_TEMP,
        "coreTemp" to CORE_TEMP,
        "nightTempAdvice" to NIGHT_TEMP_ADVICE,
        "mfBia" to MFBIA,
        "phaseSign" to PHASE_SIGN,
        "odi" to ODI,
        "resp" to RESP,
        "apnea" to APNEA,
        "cycle" to CYCLE,
        "bpManual" to BP_MANUAL,
        "irregularAdvice" to IRREGULAR_ADVICE,
        "irregularEmpty" to IRREGULAR_EMPTY,
        "irregularDisclaimer" to IRREGULAR_DISCLAIMER,
        "glucoseUnit" to GLUCOSE_UNIT,
        "glucoseNoJudgement" to GLUCOSE_NO_JUDGEMENT,
        "dietNotAPlan" to DIET_NOT_A_PLAN,
        "bpEstimate" to BP_ESTIMATE,
        "bpEstimateNotEnough" to BP_ESTIMATE_NOT_ENOUGH,
    )
}
