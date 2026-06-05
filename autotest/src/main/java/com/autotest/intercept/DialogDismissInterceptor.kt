package com.autotest.intercept

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.autotest.log.TestLogger

/**
 * 弹窗自动关闭拦截器。
 * 参考 Kaspresso 的 SystemDialogSafetyBehaviorInterceptor。
 *
 * 在每个操作执行前自动检测并关闭：
 * - 系统权限弹窗（允许/拒绝）
 * - App 升级弹窗
 * - 通知提示弹窗
 * - 引导页弹窗
 *
 * 使用：
 * ```
 * interceptors.add(DialogDismissInterceptor(logger))
 * ```
 */
class DialogDismissInterceptor(
    private val logger: TestLogger,
    private val dismissTexts: List<String> = DEFAULT_DISMISS_TEXTS
) : Interceptor {

    companion object {
        val DEFAULT_DISMISS_TEXTS = listOf(
            // 权限弹窗
            "允许", "Allow", "允许本次", "始终允许",
            "ALLOW", "While using the app",
            // 关闭/跳过
            "稍后再说", "取消", "跳过", "Skip",
            "以后再说", "暂不升级", "下次再说",
            // 确认
            "知道了", "我知道了", "OK", "确定", "好的",
            // 关闭按钮 resource-id
        )

        val PERMISSION_RES_IDS = listOf(
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.packageinstaller:id/permission_allow_button"
        )
    }

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    override fun beforeAction(actionName: String, details: String) {
        dismissDialogs()
    }

    override fun beforeStep(stepNumber: String, stepName: String) {
        dismissDialogs()
    }

    private fun dismissDialogs() {
        // 一次抓取当前所有可点击元素后本地匹配，避免对每个候选 res-id/文本各做一次跨进程树遍历。
        // 原实现每步多达 23 次 findObject；被测 App 永不 idle 时这是主要耗时来源。
        val clickables = device.findObjects(By.clickable(true))
        if (clickables.isEmpty()) return

        // 系统权限弹窗：resource-id 精确匹配
        for (obj in clickables) {
            val res = obj.resourceName ?: continue
            if (res in PERMISSION_RES_IDS) {
                logger.d("DialogDismiss", "关闭权限弹窗: $res")
                obj.click(); device.waitForIdle(1000); return
            }
        }
        // App 内弹窗：文本匹配
        for (obj in clickables) {
            val text = obj.text ?: continue
            if (dismissTexts.any { text == it || text.contains(it) }) {
                logger.d("DialogDismiss", "关闭弹窗: \"$text\"")
                obj.click(); device.waitForIdle(1000); return
            }
        }
    }
}
