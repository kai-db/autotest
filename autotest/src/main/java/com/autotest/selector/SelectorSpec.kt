package com.autotest.selector

/** 属性匹配方式。REGEX 为全串匹配（与 UiAutomator By.text(Pattern) 语义一致） */
enum class MatchMode { EXACT, CONTAINS, STARTS_WITH, REGEX }

/** 参与匹配的元素属性 */
enum class AttrType { TEXT, RES_ID, DESC, CLASS_NAME }

/**
 * 复合选择器：用 与/或/非 组合多个属性条件，UI 文案微调时仍可稳定定位。
 *
 * 两种消费方式：
 * - 设备查找：经 [toDnf] 展开后转 UiAutomator BySelector（见 SelfHealingLocator），逐个析取项尝试
 * - 纯谓词：[matches] 对 [ElementSnapshot] 判定，用于指纹自愈候选过滤与 JVM 单测
 *
 * [key] 是规范化键，作为指纹库与用例缓存的索引——同一选择器写法必须产生同一键。
 */
sealed class SelectorSpec {

    data class Leaf(val attr: AttrType, val mode: MatchMode, val value: String) : SelectorSpec()

    /** 非：仅允许包裹叶子条件（避免 De Morgan 展开复杂度，够用且语义清晰） */
    data class Not(val leaf: Leaf) : SelectorSpec()

    data class And(val children: List<SelectorSpec>) : SelectorSpec() {
        init {
            require(children.size >= 2) { "And 至少需要 2 个子条件" }
        }
    }

    data class Or(val children: List<SelectorSpec>) : SelectorSpec() {
        init {
            require(children.size >= 2) { "Or 至少需要 2 个子条件" }
        }
    }

    /** 对元素快照做纯谓词匹配 */
    fun matches(e: ElementSnapshot): Boolean = when (this) {
        is Leaf -> matchLeaf(e)
        is Not -> !leaf.matchLeaf(e)
        is And -> children.all { it.matches(e) }
        is Or -> children.any { it.matches(e) }
    }

    private fun Leaf.matchLeaf(e: ElementSnapshot): Boolean {
        val actual = when (attr) {
            AttrType.TEXT -> e.text
            AttrType.RES_ID -> e.resourceId
            AttrType.DESC -> e.contentDesc
            AttrType.CLASS_NAME -> e.className
        }
        return when (mode) {
            MatchMode.EXACT -> actual == value
            MatchMode.CONTAINS -> actual.contains(value)
            MatchMode.STARTS_WITH -> actual.startsWith(value)
            MatchMode.REGEX -> Regex(value).matches(actual)
        }
    }

    /**
     * 规范化键：指纹库与用例缓存的索引。
     *
     * value 用**长度前缀编码**（`<len>:<value>`）而非裸拼——value 含 `,`/`(`/`)` 时裸拼会让不同写法
     * 产生同键、指纹/缓存索引互相污染（C4）；长度前缀让「同一写法必产生同键、不同写法必产生不同键」。
     */
    fun key(): String = when (this) {
        is Leaf -> "${attr.name.lowercase()}:${mode.name.lowercase()}:${value.length}:$value"
        is Not -> "not(${leaf.key()})"
        is And -> "and(${children.joinToString(",") { it.key() }})"
        is Or -> "or(${children.joinToString(",") { it.key() }})"
    }

    /**
     * 展开为析取范式：外层 List 是「或」的各备选项，内层 List 是该备选项的「与」条件
     * （元素只会是 Leaf 或 Not）。设备查找时按备选项顺序逐个尝试。
     */
    fun toDnf(): List<List<SelectorSpec>> = when (this) {
        is Leaf, is Not -> listOf(listOf(this))
        is Or -> children.flatMap { it.toDnf() }
        is And -> children
            .map { it.toDnf() }
            .reduce { acc, next -> acc.flatMap { a -> next.map { b -> a + b } } }
    }

    infix fun and(other: SelectorSpec): SelectorSpec = And(listOf(this, other))

    infix fun or(other: SelectorSpec): SelectorSpec = Or(listOf(this, other))
}

// ---- 顶层 DSL ----

fun byText(value: String) = SelectorSpec.Leaf(AttrType.TEXT, MatchMode.EXACT, value)
fun byTextContains(value: String) = SelectorSpec.Leaf(AttrType.TEXT, MatchMode.CONTAINS, value)
fun byTextStartsWith(value: String) = SelectorSpec.Leaf(AttrType.TEXT, MatchMode.STARTS_WITH, value)
fun byTextRegex(pattern: String) = SelectorSpec.Leaf(AttrType.TEXT, MatchMode.REGEX, pattern)

fun byResId(value: String) = SelectorSpec.Leaf(AttrType.RES_ID, MatchMode.EXACT, value)
fun byResIdContains(value: String) = SelectorSpec.Leaf(AttrType.RES_ID, MatchMode.CONTAINS, value)

fun byDesc(value: String) = SelectorSpec.Leaf(AttrType.DESC, MatchMode.EXACT, value)
fun byDescContains(value: String) = SelectorSpec.Leaf(AttrType.DESC, MatchMode.CONTAINS, value)

fun byClass(value: String) = SelectorSpec.Leaf(AttrType.CLASS_NAME, MatchMode.EXACT, value)

fun not(leaf: SelectorSpec.Leaf) = SelectorSpec.Not(leaf)

/**
 * 从元素快照推导选择器：按 res-id → content-desc → text 的稳定性排序，
 * 多属性可用时组合成 Or（任一属性微调后其余属性仍可命中）。
 * 全部属性为空（如纯图形元素）返回 null。
 */
fun ElementSnapshot.toSelectorSpec(): SelectorSpec? {
    val alternatives = listOfNotNull(
        resourceId.ifEmpty { null }?.let { byResId(it) },
        contentDesc.ifEmpty { null }?.let { byDesc(it) },
        text.ifEmpty { null }?.let { byText(it) }
    )
    return when {
        alternatives.isEmpty() -> null
        alternatives.size == 1 -> alternatives.single()
        else -> SelectorSpec.Or(alternatives)
    }
}
