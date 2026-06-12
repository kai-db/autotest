package com.autotest.bridge

import androidx.test.uiautomator.UiDevice
import com.autotest.log.TestLogger
import com.autotest.selector.SelfHealingLocator
import com.autotest.selector.toSelectorSpec
import com.autotest.util.scrollDown
import com.autotest.util.scrollUp
import com.autotest.util.waitForText

/**
 * UiAutomator 回放执行器：把缓存步骤映射到真实设备操作。
 * 元素定位走 [SelfHealingLocator] 三级降级链——缓存里的元素微调后仍可命中，且降级显式记账。
 */
class UiReplayExecutor(
    private val device: UiDevice,
    private val locator: SelfHealingLocator,
    private val logger: TestLogger? = null
) : ReplayExecutor {

    override fun execute(case: CachedCase, step: CachedStep) {
        when (step.action) {
            CachedActionType.LAUNCH_APP -> launchApp(step.payload ?: case.appPackage)
            CachedActionType.TERMINATE_APP -> terminateApp(step.payload ?: case.appPackage)
            CachedActionType.CLICK -> findTarget(step).click()
            CachedActionType.LONG_CLICK -> findTarget(step).longClick()
            CachedActionType.INPUT_TEXT -> findTarget(step).text = requireNotNull(step.payload) {
                "INPUT_TEXT 步骤缺少 payload: ${step.name}"
            }
            CachedActionType.SWIPE_UP -> device.scrollUp()
            CachedActionType.SWIPE_DOWN -> device.scrollDown()
            CachedActionType.PRESS_BACK -> device.pressBack()
            CachedActionType.WAIT_TEXT -> {
                val text = requireNotNull(step.payload) { "WAIT_TEXT 步骤缺少 payload: ${step.name}" }
                check(device.waitForText(text, step.timeoutMs)) {
                    "等待文本超时(${step.timeoutMs}ms): \"$text\""
                }
            }
            CachedActionType.ASSERT_VISIBLE -> findTarget(step)
            CachedActionType.SLEEP -> Thread.sleep(step.payload?.toLongOrNull() ?: 1000)
        }
    }

    private fun findTarget(step: CachedStep) = run {
        val target = requireNotNull(step.target) { "${step.action} 步骤缺少 target: ${step.name}" }
        val spec = requireNotNull(target.toSelectorSpec()) {
            "target 无可用定位属性（res-id/desc/text 均为空）: ${step.name}"
        }
        locator.find(spec, step.timeoutMs)
    }

    private fun launchApp(packageName: String) {
        requireSafePackage(packageName)
        device.executeShellCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        logger?.i("Replay", "启动 App: $packageName")
    }

    private fun terminateApp(packageName: String) {
        requireSafePackage(packageName)
        device.executeShellCommand("am force-stop $packageName")
        logger?.i("Replay", "强停 App: $packageName")
    }

    private fun requireSafePackage(packageName: String) {
        // 包名会拼进 shell 命令，校验防注入
        require(packageName.matches(Regex("^[a-zA-Z0-9._]+$"))) { "非法包名: $packageName" }
    }
}
