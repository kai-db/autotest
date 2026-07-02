package com.autotest.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** B2：原子写——成功替换；rename 失败 fail-closed 保留旧文件不覆盖。 */
class AtomicFileWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `写入并原子替换成功`() {
        val f = tmp.root.resolve("data.json")
        assertTrue(AtomicFileWriter.writeText(f, "v1"))
        assertEquals("v1", f.readText())
        assertTrue(AtomicFileWriter.writeText(f, "v2"))
        assertEquals("v2", f.readText())
    }

    @Test
    fun `不留临时文件`() {
        val f = tmp.root.resolve("data.json")
        AtomicFileWriter.writeText(f, "x")
        val leftover = tmp.root.listFiles()!!.filter { it.name.contains(".tmp.") }
        assertTrue("不应残留 .tmp 文件", leftover.isEmpty())
    }

    @Test
    fun `目标是已存在目录时 rename 失败，旧数据不被破坏`() {
        // 让目标路径是一个目录，renameTo 失败 → fail-closed
        val target = tmp.root.resolve("collide")
        target.mkdirs()
        // 目录内放一个文件代表"旧资产"
        val inside = target.resolve("keep.txt").apply { writeText("old") }

        val ok = AtomicFileWriter.writeText(target, "new-content")
        assertFalse("rename 到已存在目录应失败", ok)
        // 旧目录内容不被覆盖
        assertTrue(inside.exists())
        assertEquals("old", inside.readText())
    }
}
