package com.autotest.selector

/** 定位降级链命中的层级 */
enum class LocatorLevel {
    /** 第一级：确定性选择器直接命中（正常路径，不上报事件） */
    DETERMINISTIC,

    /** 第二级：确定性失败，指纹自愈命中（必须上报人审——可能是真实 UI 回归） */
    HEALED,

    /** 第三级：自愈也失败，AI 兜底定位命中（必须上报） */
    AI_FALLBACK,

    /** 快照回查多命中且无法唯一消歧（fail-closed 不点击，上报人审，C5） */
    AMBIGUOUS
}

/**
 * 定位事件：自愈/AI 兜底命中的显式记账。
 * 设计原则（Healenium 教训）：静默自愈会掩盖真实 UI 回归，
 * 所有非确定性命中都要进报告供人审，确认后回填选择器。
 */
data class LocatorEvent(
    val selectorKey: String,
    val level: LocatorLevel,
    /** 实际命中元素的描述（ElementSnapshot.describe()） */
    val resolved: String,
    /** 自愈置信度（HEALED 时有值） */
    val confidence: Double? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
