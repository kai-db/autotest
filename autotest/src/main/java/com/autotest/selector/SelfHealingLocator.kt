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
    private val logger: TestLogger? = null,
    /** 危险操作守卫（铁律#7）：null = 无守卫（独立使用/守卫关闭） */
    private val guard: com.autotest.safety.DangerousOpsGuard? = null
) {

    private val _events = mutableListOf<LocatorEvent>()

    /** 自愈/AI 兜底命中事件（确定性命中不记，避免噪音） */
    val events: List<LocatorEvent> get() = _events.toList()

    /** 三级降级查找，返回受守卫保护的元素句柄；失败抛 AssertionError（带各级诊断信息） */
    fun element(spec: SelectorSpec, timeoutMs: Long = 5000): GuardedElement =
        GuardedElement(findRaw(spec, timeoutMs) ?: throw locateFailure(spec), guard)

    /** 同 [element]，但失败返回 null 不抛（用于可选元素） */
    fun tryElement(spec: SelectorSpec, timeoutMs: Long = 5000): GuardedElement? =
        findRaw(spec, timeoutMs)?.let { GuardedElement(it, guard) }

    /**
     * 三级降级查找；全部失败抛 AssertionError。
     * @return 裸 UiObject2——其上的 click() **绕过危险操作守卫**，点击请改用 [click] / [element]
     */
    @Deprecated(
        "裸 UiObject2 上的点击绕过危险操作守卫（铁律#7）；点击用 click()/element()，只读用 element()",
        ReplaceWith("element(spec, timeoutMs)")
    )
    fun find(spec: SelectorSpec, timeoutMs: Long = 5000): UiObject2 {
        return findRaw(spec, timeoutMs) ?: throw locateFailure(spec)
    }

    /** 同 [find]，失败返回 null；同样绕过守卫，点击请改用 [tryElement] */
    @Deprecated(
        "裸 UiObject2 上的点击绕过危险操作守卫（铁律#7）；改用 tryElement()",
        ReplaceWith("tryElement(spec, timeoutMs)")
    )
    fun tryFind(spec: SelectorSpec, timeoutMs: Long = 5000): UiObject2? = findRaw(spec, timeoutMs)

    private fun locateFailure(spec: SelectorSpec) = AssertionError(
        "定位失败: ${spec.key()}（确定性/指纹自愈/AI 兜底 三级均未命中，" +
            "aiFallback=${if (aiFallback != null) "已注入" else "未注入"}）"
    )

    private fun findRaw(spec: SelectorSpec, timeoutMs: Long = 5000): UiObject2? {
        val key = spec.key()

        // L1 确定性定位
        deterministicFind(spec, timeoutMs)?.let { obj ->
            store.record(key, obj.toSnapshot())
            return obj
        }

        val hierarchyXml = dumpHierarchy()
        val allCandidates = HierarchyParser.parse(hierarchyXml)

        // L2 指纹自愈
        store.find(key)?.let { fp ->
            // C1：provisional 指纹过期则不作 heal 基线（避免陈旧误愈长期驻留）
            if (fp.provisional && isExpired(fp, nowMs())) {
                logger?.d("Locator", "provisional 指纹已过期，不作自愈基线: $key")
            } else {
                // C2：只在被测 App 自己的元素里自愈（指纹 packageName 非空时过滤系统 UI/输入法/悬浮窗）
                val candidates = filterByPackage(allCandidates, fp.snapshot.packageName)
                engine.heal(fp.snapshot, candidates)?.let { result ->
                    // C1：provisional 基线的最终置信度封顶，即便 exact match 也不给满分、始终留人审
                    val confidence = if (fp.provisional) minOf(result.confidence, PROVISIONAL_CEILING) else result.confidence
                    report(LocatorEvent(key, LocatorLevel.HEALED, result.candidate.describe(), confidence))
                    findBySnapshot(key, result.candidate)?.let { obj ->
                        // C1：自愈命中写 provisional（可能误愈），不转正、不锁定满分
                        store.recordProvisional(key, result.candidate)
                        return obj
                    }
                }
            }
        }

        // L3 AI 兜底
        aiFallback?.locate(key, hierarchyXml)?.let { snap ->
            report(LocatorEvent(key, LocatorLevel.AI_FALLBACK, snap.describe()))
            findBySnapshot(key, snap)?.let { obj ->
                // C1：AI 命中同样写 provisional
                store.recordProvisional(key, snap)
                return obj
            }
        }

        return null
    }

    /** C2：按指纹 packageName 过滤候选（非空时只留同包元素，排除系统 UI）；空 packageName 不过滤（兼容旧指纹） */
    private fun filterByPackage(candidates: List<ElementSnapshot>, pkg: String): List<ElementSnapshot> =
        if (pkg.isEmpty()) candidates else candidates.filter { it.packageName == pkg }

    private fun isExpired(fp: com.autotest.selector.ElementFingerprint, now: Long): Boolean =
        fp.lastSeenMs > 0 && now - fp.lastSeenMs > PROVISIONAL_TTL_MS

    private fun nowMs(): Long = System.currentTimeMillis()

    /** 查找并点击（过危险操作守卫） */
    fun click(
        spec: SelectorSpec,
        timeoutMs: Long = 5000,
        allowDangerous: Boolean = false,
        allowUnverifiable: Boolean = false
    ) {
        element(spec, timeoutMs).click(allowDangerous, allowUnverifiable)
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
            // C3：BySelector 同属性二次 set 会抛 IllegalStateException（如 byText and byTextContains）。
            // 每属性只取首个 Leaf（主 Leaf）驱动设备查找；其余条件**不丢**——完整 conjunction
            // （含同属性其余 Leaf 与 Not）由下方 findObjects 后置过滤兜底，不满足则受控返回 null。
            val by = primaryLeaves(positives).fold(null as BySelector?) { acc, leaf -> applyLeaf(acc, leaf) } ?: continue

            device.wait(Until.findObject(by), perAltTimeout) ?: continue
            // findObjects 重新取全量命中，用完整 conjunction（含 Not）过滤
            val match = device.findObjects(by).firstOrNull { obj ->
                conjunction.all { it.matches(obj.toSnapshot()) }
            }
            if (match != null) return match
        }
        return null
    }

    /**
     * 用快照的最强属性回查 UiObject2（自愈/AI 命中后转回可操作对象）。
     * C5 多命中真消歧：bounds 精确 → 候选唯一 → 全属性收窄 → 仍无法唯一确定则 **fail-closed 返回 null + 上报**，
     * 绝不默认点第一个（钱包场景宁失败不点错列表项）。
     */
    private fun findBySnapshot(key: String, snap: ElementSnapshot): UiObject2? {
        val by = when {
            snap.resourceId.isNotEmpty() -> By.res(snap.resourceId)
            snap.contentDesc.isNotEmpty() -> By.desc(snap.contentDesc)
            snap.text.isNotEmpty() -> By.text(snap.text)
            else -> return null
        }
        val matches = device.findObjects(by) ?: return null
        val idx = disambiguate(matches.map { it.toSnapshot() }, snap)
        if (idx < 0) {
            report(LocatorEvent(key, LocatorLevel.AMBIGUOUS,
                "快照回查多命中 ${matches.size} 且无法唯一消歧，fail-closed 不点击（待人审）"))
            return null
        }
        return matches[idx]
    }

    companion object {
        /**
         * C3 纯决策：每属性只保留首个 Leaf 作为驱动 BySelector 的主 Leaf（同属性二次 set 会抛
         * IllegalStateException）。被略过的同属性 Leaf 不会丢失——调用方用完整 conjunction 后置过滤。
         * 抽为纯函数便于 JVM 单测。
         */
        internal fun primaryLeaves(positives: List<SelectorSpec.Leaf>): List<SelectorSpec.Leaf> =
            positives.distinctBy { it.attr }

        /** provisional 基线自愈置信度上限：即便 exact match 也不给满分，始终留人审区间（C1） */
        const val PROVISIONAL_CEILING = 0.85
        /** provisional 指纹存活期（毫秒），过期不作自愈基线（C1），默认 24h */
        const val PROVISIONAL_TTL_MS = 24L * 60 * 60 * 1000

        /**
         * 纯决策：从 [candidates] 里按目标 [snap] 消歧，返回唯一命中的下标；无法唯一确定返回 -1（fail-closed）。
         * 顺序：bounds 精确唯一 → 候选唯一 → res-id∧text∧desc 全属性收窄到唯一 → 否则 -1。
         * 抽为纯函数便于 JVM 单测（不依赖 UiObject2）。
         */
        internal fun disambiguate(candidates: List<ElementSnapshot>, snap: ElementSnapshot): Int {
            if (candidates.isEmpty()) return -1
            if (candidates.size == 1) return 0
            // 1) bounds 精确匹配且唯一
            val boundsHits = candidates.indices.filter { candidates[it].bounds == snap.bounds && snap.bounds.isNotEmpty() }
            if (boundsHits.size == 1) return boundsHits[0]
            // 2) 全属性收窄（非空属性全部相等）到唯一
            val attrHits = candidates.indices.filter { i ->
                val c = candidates[i]
                (snap.resourceId.isEmpty() || c.resourceId == snap.resourceId) &&
                    (snap.text.isEmpty() || c.text == snap.text) &&
                    (snap.contentDesc.isEmpty() || c.contentDesc == snap.contentDesc) &&
                    (snap.bounds.isEmpty() || c.bounds == snap.bounds)
            }
            if (attrHits.size == 1) return attrHits[0]
            // 3) 仍无法唯一确定 → fail-closed
            return -1
        }
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
            MatchMode.REGEX -> leaf.pattern!!.let { base?.text(it) ?: By.text(it) } // 构造期已编译（Q4）
        }
        AttrType.RES_ID -> when (leaf.mode) {
            MatchMode.EXACT -> base?.res(leaf.value) ?: By.res(leaf.value)
            else -> leaf.toPattern().let { base?.res(it) ?: By.res(it) }
        }
        AttrType.DESC -> when (leaf.mode) {
            MatchMode.EXACT -> base?.desc(leaf.value) ?: By.desc(leaf.value)
            MatchMode.CONTAINS -> base?.descContains(leaf.value) ?: By.descContains(leaf.value)
            MatchMode.STARTS_WITH -> base?.descStartsWith(leaf.value) ?: By.descStartsWith(leaf.value)
            MatchMode.REGEX -> leaf.pattern!!.let { base?.desc(it) ?: By.desc(it) } // 构造期已编译（Q4）
        }
        AttrType.CLASS_NAME -> when (leaf.mode) {
            MatchMode.EXACT -> base?.clazz(leaf.value) ?: By.clazz(leaf.value)
            else -> leaf.toPattern().let { base?.clazz(it) ?: By.clazz(it) }
        }
    }

    /** CONTAINS/STARTS_WITH 在无原生 By 支持的属性上转正则实现；REGEX 复用构造期编译结果（Q4） */
    private fun SelectorSpec.Leaf.toPattern(): Pattern = when (mode) {
        MatchMode.CONTAINS -> Pattern.compile(".*" + Pattern.quote(value) + ".*")
        MatchMode.STARTS_WITH -> Pattern.compile(Pattern.quote(value) + ".*")
        MatchMode.REGEX -> pattern!!
        MatchMode.EXACT -> Pattern.compile(Pattern.quote(value))
    }
}
