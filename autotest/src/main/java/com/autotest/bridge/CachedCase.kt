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
    val timeoutMs: Long = 5000,
    /** CLICK/LONG_CLICK 命中危险操作词表时的显式放行（用例明确要求执行才置 true，缺省拦截） */
    val allowDangerous: Boolean = false,
    /** 目标快照无 text/res-id/desc 时的显式放行（守卫 fail-closed，缺省拦截） */
    val allowUnverifiable: Boolean = false
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
        require(caseId.matches(CASE_ID_REGEX)) { "caseId 仅允许字母数字._-（将作为文件名）: $caseId" }
    }

    companion object {
        val CASE_ID_REGEX = Regex("""^[A-Za-z0-9._-]+$""")
    }
}

/**
 * 反序列化后校验（B1）：Gson Unsafe 实例化绕过 `init{}`，手编 JSON 可让 caseId 逃逸文件名安全约束、
 * 或给非空字段/steps 注入 null → NPE 炸在远端（CacheReplay.forEach / when(action)）。
 * 这里显式递归校验，只放结构完整数据进内存。
 * @return 结构完整返回 null；否则返回第一处问题描述
 */
@Suppress("SENSELESS_COMPARISON")
fun CachedCase.validationError(): String? {
    if (caseId == null || !caseId.matches(CachedCase.CASE_ID_REGEX)) return "caseId 非法或为空: $caseId"
    if (steps == null) return "steps 为 null"
    if (steps.isEmpty()) return "steps 为空（空场景会假 PASS）"
    steps.forEachIndexed { i, step ->
        step.validationError()?.let { return "step[$i]: $it" }
    }
    return null
}

@Suppress("SENSELESS_COMPARISON")
fun CachedStep.validationError(): String? {
    if (name == null) return "name 为 null"
    if (action == null) return "action 为 null"
    target?.let { if (!it.isStructurallyValid()) return "target 快照字段缺失" }
    when (action) {
        CachedActionType.INPUT_TEXT, CachedActionType.WAIT_TEXT ->
            if (payload.isNullOrEmpty()) return "$action 缺少 payload"
        CachedActionType.CLICK, CachedActionType.LONG_CLICK, CachedActionType.ASSERT_VISIBLE ->
            if (target == null) return "$action 缺少 target"
        else -> {}
    }
    return null
}
