package com.autotest.debox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autotest.base.BaseUiTest
import com.autotest.bridge.CacheMode
import com.autotest.bridge.CacheReplay
import com.autotest.bridge.CaseCacheStore
import com.autotest.bridge.UiReplayExecutor
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 固化桥回放验证（docs/09 §2 实战）：
 * AI 黑盒探索（mobile-mcp）产出的 docs/testing/cache 下的 .cache.json 经 assets 打进 test APK，
 * 本测试用 READ_ONLY 模式加载并确定性回放——全程零 LLM 调用。
 *
 * 元素定位走 BaseUiTest.locator 三级自愈链（确定性 → 指纹 → AI 兜底未注入），
 * 定位成功自动回写指纹库，自愈事件（如有）进报告。
 *
 * 硬约束同 DeBoxSmokeTest：仅 terminate→launch + 只读导航。
 */
@RunWith(AndroidJUnit4::class)
class DeBoxCacheReplayTest : BaseUiTest() {

    /** 把 assets 里的用例缓存物化到测试 App 私有目录，供 CaseCacheStore 读取 */
    private fun materializeCacheDir(): File {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val dir = File(ctx.filesDir, "case-cache").apply { mkdirs() }
        ctx.assets.list("").orEmpty()
            .filter { it.endsWith(".cache.json") }
            .forEach { name ->
                ctx.assets.open(name).use { input ->
                    File(dir, name).outputStream().use { input.copyTo(it) }
                }
            }
        return dir
    }

    private fun replay(caseId: String) {
        val store = CaseCacheStore(materializeCacheDir(), CacheMode.READ_ONLY, logger)
        val case = requireNotNull(store.load(caseId)) {
            "用例缓存未命中: $caseId（检查 docs/testing/cache/ 是否有该文件并重新打包）"
        }
        logger.i("CacheReplay", "回放 ${case.caseId}（${case.steps.size} 步，source=${case.source}）")
        CacheReplay.toScenario(
            case = case,
            executor = UiReplayExecutor(device, locator, logger),
            collector = reportCollector,
            interceptors = interceptors
        ).run()
    }

    @Test
    fun replay_TC_S_001_coldStart() = replay("TC-S-001")

    @Test
    fun replay_TC_S_002_tabNavigation() = replay("TC-S-002")

    @Test
    fun replay_TC_S_003_loginState() = replay("TC-S-003")
}
