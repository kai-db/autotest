package com.autotest.safety

/** 守卫事件类型：危险命中与不可审计目标严格区分，放行与阻断都留痕 */
enum class GuardEventType {
    /** 词表命中且未显式放行 → 已阻断（抛 [DangerousOperationException]） */
    DANGEROUS_BLOCKED,

    /** 词表命中但调用方显式 allowDangerous=true → 放行留痕 */
    DANGEROUS_ALLOWED,

    /** 目标 text/res-id/desc 全空（无法比对清单）且未显式放行 → 已阻断（fail-closed） */
    UNVERIFIABLE_BLOCKED,

    /** 不可审计目标但调用方显式 allowUnverifiable=true → 放行留痕 */
    UNVERIFIABLE_ALLOWED,

    /** safety.enabled=false 关闭守卫（调试/非钱包接入的逃生口）——必须留痕，不允许静默关闭 */
    GUARD_DISABLED
}

/**
 * 守卫事件：危险操作比对的显式记账（对齐 LocatorEvent 的「禁止静默」原则）。
 * 全部事件进报告独立 section，供人审。
 */
data class GuardEvent(
    val type: GuardEventType,
    /** 点击目标描述（ClickTarget.describe()） */
    val target: String,
    /** 命中的危险词（DANGEROUS_* 时有值） */
    val matchedWord: String? = null,
    /** 命中时刻截图路径（阻断时尽力采集） */
    val screenshotPath: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
