package com.autotest.selector

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.autotest.log.TestLogger
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/**
 * AI 兜底定位接口：自愈也失败时的最后一级。
 * 框架只定义接口不实现 LLM 调用——AI 在环时（Claude Code + mobile-mcp）由外部注入实现，
 * CI 回归时不注入，保证执行期零 LLM 依赖（agentic authoring + deterministic execution）。
 */
interface AiLocatorFallback {
    /**
     * 根据选择器语义和当前层级 XML 定位元素。
     * @return 命中的元素快照；定位不了返回 null
     */
    fun locate(selectorKey: String, hierarchyXml: String): ElementSnapshot?
}

/**
 * 三级降级定位器（borrowed from Healenium / Appium AI 插件生态）：
 *
 * 1. 确定性选择器（BySelector）——正常路径，成功时回写指纹库
 * 2. 指纹自愈（HealingEngine）——纯算法零成本，命中必须显式上报
 * 3. AI 兜底（AiLocatorFallback）——可选注入，命中必须显式上报
 *
 * 所有非确定性命中记入 [events]，供报告呈现与人审回填——禁止静默自愈。
 */
class SelfHealingLocator(
    private val device: UiDevice,
    private val store: FingerprintStore,
    private val engine: HealingEngine = HealingEngine(),
    private val aiFallback: AiLocatorFallback? = null,
    private val logger: TestLogger? = null
) {

    private val _events = mutableListOf<LocatorEvent>()

    /** 自愈/AI 兜底命中事件（确定性命中不记，避免噪音） */
    val events: List<LocatorEvent> get() = _events.toList()

    /** 三级降级查找；全部失败抛 AssertionError（带各级诊断信息） */
    fun find(spec: SelectorSpec, timeoutMs: Long = 5000): UiObject2 {
        return tryFind(spec, timeoutMs)
            ?: throw AssertionError(
                "定位失败: ${spec.key()}（确定性/指纹自愈/AI 兜底 三级均未命中，" +
                    "aiFallback=${if (aiFallback != null) "已注入" else "未注入"}）"
            )
    }

    /** 同 [find]，但失败返回 null 不抛（用于可选元素） */
    fun tryFind(spec: SelectorSpec, timeoutMs: Long = 5000): UiObject2? {
        val key = spec.key()

        // L1 确定性定位
        deterministicFind(spec, timeoutMs)?.let { obj ->
            store.record(key, obj.toSnapshot())
            return obj
        }

        val hierarchyXml = dumpHierarchy()
        val candidates = HierarchyParser.parse(hierarchyXml)

        // L2 指纹自愈
        store.find(key)?.let { fp ->
            engine.heal(fp.snapshot, candidates)?.let { result ->
                report(LocatorEvent(key, LocatorLevel.HEALED, result.candidate.describe(), result.confidence))
                findBySnapshot(result.candidate)?.let { obj ->
                    store.record(key, result.candidate)
                    return obj
                }
            }
        }

        // L3 AI 兜底
        aiFallback?.locate(key, hierarchyXml)?.let { snap ->
            report(LocatorEvent(key, LocatorLevel.AI_FALLBACK, snap.describe()))
            findBySnapshot(snap)?.let { obj ->
                store.record(key, snap)
                return obj
            }
        }

        return null
    }

    /** 查找并点击 */
    fun click(spec: SelectorSpec, timeoutMs: Long = 5000) {
        find(spec, timeoutMs).click()
    }

    private fun report(event: LocatorEvent) {
        _events.add(event)
        logger?.w(
            "Locator",
            "非确定性定位命中 [${event.level}] ${event.selectorKey} -> ${event.resolved}" +
                (event.confidence?.let { " (置信度 %.2f)".format(it) } ?: "") +
                "，可能存在 UI 回归，请人工确认后回填选择器"
        )
    }

    /** 按 DNF 备选项顺序做确定性查找；Not 条件经快照谓词后置过滤 */
    private fun deterministicFind(spec: SelectorSpec, timeoutMs: Long): UiObject2? {
        val alternatives = spec.toDnf()
        val perAltTimeout = (timeoutMs / alternatives.size).coerceAtLeast(500)
        for (conjunction in alternatives) {
            val positives = conjunction.filterIsInstance<SelectorSpec.Leaf>()
            if (positives.isEmpty()) continue // 纯 Not 条件无法驱动设备查找，跳过该备选项
            val by = positives.fold(null as BySelector?) { acc, leaf -> applyLeaf(acc, leaf) } ?: continue

            device.wait(Until.findObject(by), perAltTimeout) ?: continue
            // findObjects 重新取全量命中，用完整 conjunction（含 Not）过滤
            val match = device.findObjects(by).firstOrNull { obj ->
                conjunction.all { it.matches(obj.toSnapshot()) }
            }
            if (match != null) return match
        }
        return null
    }

    /** 用快照的最强属性回查 UiObject2（自愈/AI 命中后转回可操作对象） */
    private fun findBySnapshot(snap: ElementSnapshot): UiObject2? {
        val by = when {
            snap.resourceId.isNotEmpty() -> By.res(snap.resourceId)
            snap.contentDesc.isNotEmpty() -> By.desc(snap.contentDesc)
            snap.text.isNotEmpty() -> By.text(snap.text)
            else -> return null
        }
        val matches = device.findObjects(by) ?: return null
        // 多命中时用 bounds 消歧
        return matches.firstOrNull { it.toSnapshot().bounds == snap.bounds } ?: matches.firstOrNull()
    }

    private fun dumpHierarchy(): String = try {
        val out = ByteArrayOutputStream()
        device.dumpWindowHierarchy(out)
        out.toString("UTF-8")
    } catch (e: Throwable) {
        logger?.w("Locator", "层级 dump 失败，自愈降级不可用: ${e.message}")
        ""
    }

    private fun UiObject2.toSnapshot(): ElementSnapshot = ElementSnapshot(
        text = text ?: "",
        resourceId = resourceName ?: "",
        contentDesc = contentDescription ?: "",
        className = className ?: "",
        packageName = applicationPackage ?: "",
        bounds = visibleBounds.let { "[${it.left},${it.top}][${it.right},${it.bottom}]" }
    )

    private fun applyLeaf(base: BySelector?, leaf: SelectorSpec.Leaf): BySelector = when (leaf.attr) {
        AttrType.TEXT -> when (leaf.mode) {
            MatchMode.EXACT -> base?.text(leaf.value) ?: By.text(leaf.value)
            MatchMode.CONTAINS -> base?.textContains(leaf.value) ?: By.textContains(leaf.value)
            MatchMode.STARTS_WITH -> base?.textStartsWith(leaf.value) ?: By.textStartsWith(leaf.value)
            MatchMode.REGEX -> Pattern.compile(leaf.value).let { base?.text(it) ?: By.text(it) }
        }
        AttrType.RES_ID -> when (leaf.mode) {
            MatchMode.EXACT -> base?.res(leaf.value) ?: By.res(leaf.value)
            else -> leaf.toPattern().let { base?.res(it) ?: By.res(it) }
        }
        AttrType.DESC -> when (leaf.mode) {
            MatchMode.EXACT -> base?.desc(leaf.value) ?: By.desc(leaf.value)
            MatchMode.CONTAINS -> base?.descContains(leaf.value) ?: By.descContains(leaf.value)
            MatchMode.STARTS_WITH -> base?.descStartsWith(leaf.value) ?: By.descStartsWith(leaf.value)
            MatchMode.REGEX -> Pattern.compile(leaf.value).let { base?.desc(it) ?: By.desc(it) }
        }
        AttrType.CLASS_NAME -> when (leaf.mode) {
            MatchMode.EXACT -> base?.clazz(leaf.value) ?: By.clazz(leaf.value)
            else -> leaf.toPattern().let { base?.clazz(it) ?: By.clazz(it) }
        }
    }

    /** CONTAINS/STARTS_WITH 在无原生 By 支持的属性上转正则实现 */
    private fun SelectorSpec.Leaf.toPattern(): Pattern = when (mode) {
        MatchMode.CONTAINS -> Pattern.compile(".*" + Pattern.quote(value) + ".*")
        MatchMode.STARTS_WITH -> Pattern.compile(Pattern.quote(value) + ".*")
        MatchMode.REGEX -> Pattern.compile(value)
        MatchMode.EXACT -> Pattern.compile(Pattern.quote(value))
    }
}
