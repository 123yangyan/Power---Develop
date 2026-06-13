package com.timedrecorder.core.model

/**
 * 录音场景：用户在准备页「对号入座」，降低开录前的决策成本。
 *
 * @param id 场景唯一标识，写入切片文件名前缀供云端区分
 * @param displayName 宫格主标题
 * @param durationHint 时长上限说明（消除录到一半断开的焦虑）
 * @param sliceHint 切片策略说明
 * @param action 选中后触发的行为类型
 * @param maxDurationMs 单次最长录音毫秒数，null 表示不限制
 * @param sliceDurationMinutes 覆盖默认切片间隔（分钟），null 表示使用用户设置
 * @param filePrefix 切片文件名前缀（manual_xxx）
 */
enum class RecordingScenario(
    val id: String,
    val displayName: String,
    val durationHint: String,
    val sliceHint: String,
    val action: ScenarioAction,
    val maxDurationMs: Long? = null,
    val sliceDurationMinutes: Int? = null,
    val filePrefix: String = "manual",
) {
    /** 即时速记：突然有想法，马上录 */
    QUICK_NOTE(
        id = "quick_note",
        displayName = "即时速记",
        durationHint = "单次最长 2 小时",
        sliceHint = "整段录音，结束后一次上传",
        action = ScenarioAction.START_MANUAL_RECORDING,
        maxDurationMs = 2 * 60 * 60_000L,
    ),

    /** 会议记录：多人、较长 */
    MEETING(
        id = "meeting",
        displayName = "会议记录",
        durationHint = "建议 ≤ 3 小时",
        sliceHint = "整段录音，结束后一次上传",
        action = ScenarioAction.START_MANUAL_RECORDING,
        maxDurationMs = 3 * 60 * 60_000L,
        sliceDurationMinutes = 5,
        filePrefix = "manual_meeting",
    ),

    /** 课堂/讲座：长时间固定环境，引导配置定时任务 */
    LECTURE(
        id = "lecture",
        displayName = "课堂讲座",
        durationHint = "按任务时段执行",
        sliceHint = "定时自动开始/结束",
        action = ScenarioAction.NAVIGATE_TO_SCHEDULE,
    ),

    /** 定时值守：本 App 差异化能力，无人值守自动录 */
    TIMED_GUARD(
        id = "timed_guard",
        displayName = "定时值守",
        durationHint = "按已配置时段",
        sliceHint = "后台自动切片上传",
        action = ScenarioAction.NAVIGATE_TO_SCHEDULE,
    ),
    ;

    /** 默认录音会话标题 */
    val defaultSessionTitle: String
        get() = when (this) {
            QUICK_NOTE -> "即时速记"
            MEETING -> "会议记录"
            LECTURE -> "课堂讲座"
            TIMED_GUARD -> "定时值守"
        }

    companion object {
        fun fromId(id: String?): RecordingScenario? =
            entries.firstOrNull { it.id == id }
    }
}

/** 场景选中后的跳转/开录行为 */
enum class ScenarioAction {
    /** 直接开始手动录音 */
    START_MANUAL_RECORDING,

    /** 跳转到录音任务列表页配置定时任务 */
    NAVIGATE_TO_SCHEDULE,
}
