package com.autotest.selector

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 解析 uiautomator dump 的 window hierarchy XML 为元素快照列表。
 * 用 DOM 解析（层级 dump 体积小，JVM 与 Android 都可用，便于单测）。
 */
object HierarchyParser {

    fun parse(xml: String): List<ElementSnapshot> {
        if (xml.isBlank()) return emptyList()
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val nodes = doc.getElementsByTagName("node")
        return (0 until nodes.length).map { i ->
            val attrs = nodes.item(i).attributes
            fun attr(name: String): String = attrs.getNamedItem(name)?.nodeValue ?: ""
            ElementSnapshot(
                text = attr("text"),
                resourceId = attr("resource-id"),
                contentDesc = attr("content-desc"),
                className = attr("class"),
                packageName = attr("package"),
                bounds = attr("bounds")
            )
        }
    }
}
