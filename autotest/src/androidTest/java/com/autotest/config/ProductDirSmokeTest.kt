package com.autotest.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * P0-9 坐实：产物目录用 targetContext（被测 App 进程有权写），不落公共目录 /sdcard/Pictures。
 * 框架自测场景下 targetContext = 框架自身 test 包，路径应为
 * /sdcard/Android/data/<targetPkg>/files/autotest 一类且可写。
 */
@RunWith(AndroidJUnit4::class)
class ProductDirSmokeTest {

    @Test
    fun screenshotDir_usesTargetContext_notPublicDir_andWritable() {
        val dir = TestConfig.screenshotDir
        val targetPkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName

        // 路径应属于 target 包的私有外部目录，而非公共目录
        assertTrue("产物目录应包含 targetContext 包名，实际: $dir", dir.contains(targetPkg))
        assertFalse("产物目录不应落公共目录 /sdcard/Pictures（scoped storage 必死），实际: $dir",
            dir.contains("/sdcard/Pictures") || dir.contains("/Pictures/"))

        // 实际可写：create + write + delete 一个探针文件
        val probe = File(dir, "product_dir_probe_${android.os.SystemClock.uptimeMillis()}.txt")
        probe.parentFile?.mkdirs()
        try {
            probe.writeText("ok")
            assertTrue("产物目录探针文件应写入成功: ${probe.absolutePath}", probe.exists() && probe.readText() == "ok")
        } finally {
            probe.delete()
        }
    }
}
