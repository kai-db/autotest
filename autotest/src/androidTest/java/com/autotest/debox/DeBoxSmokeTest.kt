package com.autotest.debox

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autotest.base.BaseUiTest
import com.autotest.dsl.scenario
import com.autotest.util.clickResId
import com.autotest.util.waitForResId
import com.autotest.util.waitForText
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DeBox 冒烟测试（白盒固化版）。
 *
 * 形态：独立 test APK（`com.autotest.test`）通过 UiAutomator **跨进程操作**设备上
 * 已装的 DeBox 正式包 `com.tm.security.wallet`——复用其登录态 + 测试环境，**不覆盖、不重装**。
 * 选择器用 resource-id（来自 2026-06-05 黑盒探索），比纯坐标稳、对分辨率不敏感。
 *
 * 对应 docs/testing/runs/2026-06-05-smoke 的 TC-S-001/002/003，
 * 是从黑盒探索固化而来的可回归用例（M2 固化桥）。
 *
 * 硬约束：仅 terminate→launch + 只读导航，绝不清数据/登出/切环境/发消息。
 */
@RunWith(AndroidJUnit4::class)
class DeBoxSmokeTest : BaseUiTest() {

    private val pkg = "com.tm.security.wallet"
    private fun id(name: String) = "$pkg:id/$name"

    @Test
    fun tcS001_coldStart_landsOnMessageTab() {
        scenario("TC-S-001 冷启动", reportCollector, interceptors) {
            step("启动 DeBox") { launchAppAndDismissDialogs(pkg) }
            step("验证停在消息页") {
                assertTrue("消息页标题(tvTitle)未出现", device.waitForResId(id("tvTitle"), 8000))
                assertTrue("顶部分类「全部」未出现", device.waitForText("全部", 3000))
            }
            step("验证底部 4 Tab 齐全") {
                assertTrue("消息 Tab 缺失", device.waitForResId(id("tvDao"), 3000))
                assertTrue("朋友 Tab 缺失", device.waitForResId(id("tvFriend"), 3000))
                assertTrue("发现 Tab 缺失", device.waitForResId(id("tvBrowse"), 3000))
                assertTrue("我的 Tab 缺失", device.waitForResId(id("tvMine"), 3000))
            }
        }.run()
    }

    @Test
    fun tcS002_bottomTabNavigation() {
        scenario("TC-S-002 底部 Tab 导航", reportCollector, interceptors) {
            step("启动") { launchAppAndDismissDialogs(pkg) }
            step("→ 朋友") {
                device.clickResId(id("tvFriend"))
                assertTrue("朋友页未出现", device.waitForText("新增关注", 5000) || device.waitForText("粉丝", 2000))
            }
            step("→ 发现") {
                device.clickResId(id("tvBrowse"))
                assertTrue("发现页未出现", device.waitForText("行情", 6000) || device.waitForText("DApp", 2000))
            }
            step("→ 我的") {
                device.clickResId(id("tvMine"))
                assertTrue("我的页未出现", device.waitForText("总资产", 5000) || device.waitForText("转账", 2000))
            }
            step("→ 回消息") {
                device.clickResId(id("tvDao"))
                assertTrue("未回到消息页", device.waitForResId(id("tvTitle"), 5000))
            }
        }.run()
    }

    @Test
    fun tcS003_loginState_mineTabShowsUserInfo() {
        scenario("TC-S-003 登录态校验", reportCollector, interceptors) {
            step("启动") { launchAppAndDismissDialogs(pkg) }
            step("进「我的」") { device.clickResId(id("tvMine")) }
            step("验证已登录（总资产/转账可见）") {
                assertTrue(
                    "登录态特征未出现（疑似未登录或被登出）",
                    device.waitForText("总资产", 5000) || device.waitForText("转账", 2000)
                )
            }
        }.run()
    }
}
