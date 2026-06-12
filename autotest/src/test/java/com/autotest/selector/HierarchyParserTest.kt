package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HierarchyParserTest {

    @Test
    fun `解析 uiautomator dump XML 的节点属性`() {
        val xml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.app" content-desc="" bounds="[0,0][1080,2400]">
                <node index="1" text="登录" resource-id="com.app:id/btn_login" class="android.widget.Button" package="com.app" content-desc="login" bounds="[100,200][500,300]" />
              </node>
            </hierarchy>
        """.trimIndent()

        val elements = HierarchyParser.parse(xml)
        assertEquals(2, elements.size)

        val btn = elements[1]
        assertEquals("登录", btn.text)
        assertEquals("com.app:id/btn_login", btn.resourceId)
        assertEquals("login", btn.contentDesc)
        assertEquals("android.widget.Button", btn.className)
        assertEquals("com.app", btn.packageName)
        assertEquals("[100,200][500,300]", btn.bounds)
    }

    @Test
    fun `空白 XML 返回空列表`() {
        assertTrue(HierarchyParser.parse("").isEmpty())
        assertTrue(HierarchyParser.parse("   ").isEmpty())
    }

    @Test
    fun `缺失属性的节点用空串填充`() {
        val xml = """<hierarchy><node class="android.view.View" /></hierarchy>"""
        val e = HierarchyParser.parse(xml).single()
        assertEquals("", e.text)
        assertEquals("", e.resourceId)
        assertEquals("android.view.View", e.className)
    }

    @Test
    fun `bounds 解析与中心点计算`() {
        val e = ElementSnapshot(bounds = "[100,200][500,300]")
        assertEquals(300 to 250, e.center())
        assertEquals(null, ElementSnapshot(bounds = "非法").center())
        assertEquals(null, ElementSnapshot().center())
    }
}
