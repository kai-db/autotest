package com.autotest.bridge

import com.autotest.selector.ElementSnapshot

/**
 * 缓存步骤的动作类型。
 * 覆盖 AI 黑盒探索（mobile-mcp）产出的常见操作，回放时由 ReplayExecutor 映射到设备操作。
 */
enum class CachedActionType {
    /** 启动 App（payload 为空时用 case 的 appPackage） */
    LAUNCH_APP,

    /** 强停 App */
    TERMINATE_APP,

    /** 点击 target 元素 */
    CLICK,

    /** 长按 target 元素 */
    LONG_CLICK,

    /** 对 target 元素输入文本（payload 为输入内容） */
    INPUT_TEXT,

    /** 向上滑动（翻下一页） */
    SWIPE_UP,

    /** 向下滑动（翻上一页） */
    SWIPE_DOWN,

    /** 按返回键 */
    PRESS_BACK,

    /** 等待包含 payload 文本的元素出现（超时抛错） */
    WAIT_TEXT,

    /** 断言 target 元素可见（找不到抛错） */
    ASSERT_VISIBLE,

    /** 固定等待 payload 毫秒（尽量少用，优先 WAIT_TEXT） */
    SLEEP
}

/**
 * 缓存的单步操作：AI 首跑探索时落盘，回归时确定性回放。
 * target 记录探索时元素的完整快照——回放时按 res-id → desc → text 优先级构建选择器，
 * 配合 SelfHealingLocator 的三级降级，元素微调后缓存仍可命中。
 */
data class CachedStep(
    val name: String,
    val action: CachedActionType,
    val target: ElementSnapshot? = null,
    val payload: String? = null,
    val timeoutMs: Long = 5000
)

/**
 * 缓存用例（固化桥的中间产物，借鉴 midscene.js 缓存回放机制）：
 * AI 黑盒探索跑通一条用例后产出本结构（JSON 落盘、可进版本库），
 * 回归阶段直接按缓存驱动 UiAutomator 回放，全程零 LLM 调用。
 * 稳定后可进一步固化为 Kotlin Scenario 白盒用例。
 */
data class CachedCase(
    /** 用例标识，对应 TEST_CASES.md 的编号（如 TC-S-001），也是缓存文件名 */
    val caseId: String,
    val description: String = "",
    val appPackage: String = "",
    /** 产出来源（如 ai-exploration / manual），便于审计 */
    val source: String = "ai-exploration",
    val createdAtMs: Long = 0,
    val steps: List<CachedStep>
) {
    init {
        require(caseId.isNotBlank()) { "caseId 不能为空" }
        require(caseId.matches(Regex("""^[A-Za-z0-9._-]+$"""))) { "caseId 仅允许字母数字._-（将作为文件名）: $caseId" }
    }
}
