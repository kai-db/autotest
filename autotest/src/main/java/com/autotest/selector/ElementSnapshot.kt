package com.autotest.selector

/**
 * 屏幕元素快照：定位成功时记录的元素属性指纹，也是层级 dump 解析后的统一表示。
 * 字段对齐 uiautomator dump XML 的节点属性，缺失属性用空串（Gson 反序列化友好）。
 */
data class ElementSnapshot(
    val text: String = "",
    val resourceId: String = "",
    val contentDesc: String = "",
    val className: String = "",
    val packageName: String = "",
    /** 形如 "[l,t][r,b]"，与 uiautomator dump 格式一致 */
    val bounds: String = ""
) {
    /** 中心点坐标；bounds 缺失或非法时返回 null */
    fun center(): Pair<Int, Int>? {
        val b = parseBounds(bounds) ?: return null
        return (b[0] + b[2]) / 2 to (b[1] + b[3]) / 2
    }

    /** 人读的简短描述，用于自愈事件上报 */
    fun describe(): String = listOfNotNull(
        resourceId.ifEmpty { null }?.let { "res=$it" },
        text.ifEmpty { null }?.let { "text=$it" },
        contentDesc.ifEmpty { null }?.let { "desc=$it" },
        className.ifEmpty { null }?.let { "class=${it.substringAfterLast('.')}" },
        bounds.ifEmpty { null }?.let { "bounds=$it" }
    ).joinToString(", ").ifEmpty { "(空元素)" }

    companion object {
        private val BOUNDS_REGEX = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

        /** 解析 "[l,t][r,b]" 为 [l, t, r, b]；非法格式返回 null */
        fun parseBounds(s: String): IntArray? {
            val m = BOUNDS_REGEX.find(s) ?: return null
            return IntArray(4) { m.groupValues[it + 1].toInt() }
        }
    }
}
