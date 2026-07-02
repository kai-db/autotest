package com.autotest.safety

/**
 * 可审计的点击目标描述：危险操作守卫（[DangerousOpsGuard]）比对与留证的统一输入。
 * 字段语义对齐 [com.autotest.selector.ElementSnapshot]；纯 JVM 类型，便于单测。
 */
data class ClickTarget(
    val text: String = "",
    val resourceId: String = "",
    val contentDesc: String = "",
    val className: String = "",
    val packageName: String = "",
    /** 形如 "[l,t][r,b]"，可为空 */
    val bounds: String = ""
) {

    /**
     * 不可审计目标：text / resourceId / contentDesc 全空，无任何语义可与危险操作清单比对。
     * 守卫对这类目标 fail-closed（默认阻断）——钱包场景宁误杀不误放。
     */
    val isUnverifiable: Boolean
        get() = text.isEmpty() && resourceId.isEmpty() && contentDesc.isEmpty()

    /** 人读的简短描述，用于守卫事件与异常信息 */
    fun describe(): String = listOfNotNull(
        text.ifEmpty { null }?.let { "text=$it" },
        resourceId.ifEmpty { null }?.let { "res=$it" },
        contentDesc.ifEmpty { null }?.let { "desc=$it" },
        className.ifEmpty { null }?.let { "class=${it.substringAfterLast('.')}" },
        packageName.ifEmpty { null }?.let { "pkg=$it" },
        bounds.ifEmpty { null }?.let { "bounds=$it" }
    ).joinToString(", ").ifEmpty { "(空目标)" }
}
