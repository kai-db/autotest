package com.autotest.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TestArtifactsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun createFile(name: String, lastModified: Long) =
        tmp.newFile(name).apply { setLastModified(lastModified) }

    @Test
    fun `不超过保留数时不删除任何文件`() {
        val f1 = createFile("a.png", 1000)
        val f2 = createFile("b.png", 2000)

        TestArtifacts.cleanup(tmp.root, keepRecent = 5)

        assertTrue(f1.exists())
        assertTrue(f2.exists())
    }

    @Test
    fun `超过保留数时删除最旧的文件`() {
        val old1 = createFile("old1.png", 1000)
        val old2 = createFile("old2.png", 2000)
        val new1 = createFile("new1.png", 3000)
        val new2 = createFile("new2.png", 4000)

        TestArtifacts.cleanup(tmp.root, keepRecent = 2)

        assertFalse(old1.exists())
        assertFalse(old2.exists())
        assertTrue(new1.exists())
        assertTrue(new2.exists())
    }

    @Test
    fun `递归清理子目录中的旧文件`() {
        val sub = tmp.newFolder("sub")
        val oldInSub = sub.resolve("old.png").apply { writeText("x"); setLastModified(1000) }
        val newInRoot = createFile("new.png", 9000)

        TestArtifacts.cleanup(tmp.root, keepRecent = 1)

        assertFalse(oldInSub.exists())
        assertTrue(newInRoot.exists())
    }

    @Test
    fun `目录不存在时不抛异常`() {
        TestArtifacts.cleanup(tmp.root.resolve("not-exist"))
    }

    @Test
    fun `空目录不抛异常`() {
        TestArtifacts.cleanup(tmp.root)
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    @Test
    fun `B3 protectedNames 的文件永不被删（指纹库跨 run 护住）`() {
        val fp = createFile("fingerprints.json", 1) // 最旧
        repeat(5) { createFile("shot_$it.png", 1000L + it) } // 5 张新截图
        // 只保留最近 2 个，但 fingerprints.json 受保护不参与竞争
        TestArtifacts.cleanup(tmp.root, keepRecent = 2, protectedNames = setOf("fingerprints.json"))
        assertTrue("指纹库应被护住", fp.exists())
    }
}
