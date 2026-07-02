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
            CachedActionType.CLICK -> findTarget(step).click(step.allowDangerous, step.allowUnverifiable)
            CachedActionType.LONG_CLICK -> findTarget(step).longClick(step.allowDangerous, step.allowUnverifiable)
            CachedActionType.INPUT_TEXT -> findTarget(step).setText(
                requireNotNull(step.payload) { "INPUT_TEXT 步骤缺少 payload: ${step.name}" }
            )
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

    private fun findTarget(step: CachedStep): com.autotest.selector.GuardedElement {
        val target = requireNotNull(step.target) { "${step.action} 步骤缺少 target: ${step.name}" }
        // 缓存 target 三属性全空（== ClickTarget.isUnverifiable）时无法产生选择器、根本定位不到元素，
        // 快速失败即 fail-closed（比守卫更早的一道闸）：回放不支持无定位属性的 target。
        // step.allowUnverifiable 作用于「已定位到、但实时快照不可审计」的元素——由 GuardedElement.click 在点击时判定。
        val spec = requireNotNull(target.toSelectorSpec()) {
            "target 无可用定位属性（res-id/desc/text 均为空），无法回放定位: ${step.name}"
        }
        return locator.element(spec, step.timeoutMs)
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
