package com.autotest.selector

/** 自愈命中结果 */
data class HealingResult(
    val candidate: ElementSnapshot,
    /** 0.0-1.0，加权属性相似度 */
    val confidence: Double
)

/**
 * 指纹自愈引擎（借鉴 Healenium）：确定性定位失败时，用历史成功定位的元素指纹
 * 与当前屏幕全部候选元素做加权属性相似度比对，给出带置信度的最佳候选。
 *
 * 纯算法、零成本（不调 LLM），是三级定位降级链的第二级。
 * 注意：自愈命中必须显式上报（LocatorEvent），静默自愈会掩盖真实 UI 回归。
 */
class HealingEngine(
    /** 自愈命中阈值：最佳候选低于该置信度则放弃自愈（宁可降级到下一级，不乱点） */
    private val threshold: Double = 0.75
) {

    /** 从候选中找与指纹最相似的元素；无候选或最佳分低于阈值返回 null */
    fun heal(fingerprint: ElementSnapshot, candidates: List<ElementSnapshot>): HealingResult? {
        val best = candidates
            .map { HealingResult(it, score(fingerprint, it)) }
            .maxByOrNull { it.confidence } ?: return null
        return if (best.confidence >= threshold) best else null
    }

    /**
     * 加权属性相似度。resource-id 最稳定权重最高，text 次之；
     * 指纹中为空的属性不参与计分（权重重新归一化）。
     */
    fun score(fingerprint: ElementSnapshot, candidate: ElementSnapshot): Double {
        val terms = listOfNotNull(
            term(0.40, fingerprint.resourceId, candidate.resourceId),
            term(0.30, fingerprint.text, candidate.text),
            term(0.20, fingerprint.contentDesc, candidate.contentDesc),
            term(0.10, fingerprint.className, candidate.className)
        )
        if (terms.isEmpty()) return 0.0
        val totalWeight = terms.sumOf { it.first }
        return terms.sumOf { it.first * it.second } / totalWeight
    }

    /** 指纹属性为空时该项不计分（返回 null） */
    private fun term(weight: Double, expected: String, actual: String): Pair<Double, Double>? {
        if (expected.isEmpty()) return null
        return weight to similarity(expected, actual)
    }

    companion object {
        /**
         * 字符串相似度：相等 1.0；非空包含关系 0.8；否则归一化编辑距离相似度再打 6 折
         * （避免"长得有点像"的字符串拿到过高分数误导自愈）。
         */
        internal fun similarity(a: String, b: String): Double = when {
            a == b -> 1.0
            a.isEmpty() || b.isEmpty() -> 0.0
            a.contains(b) || b.contains(a) -> 0.8
            else -> 0.6 * (1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length))
        }

        private fun levenshtein(a: String, b: String): Int {
            val prev = IntArray(b.length + 1) { it }
            val curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                curr.copyInto(prev)
            }
            return prev[b.length]
        }
    }
}
