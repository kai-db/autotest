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
        // 先检查系统权限弹窗（通过 resource-id 精确匹配）
        for (resId in PERMISSION_RES_IDS) {
            val btn = device.findObject(By.res(resId))
            if (btn != null) {
                logger.d("DialogDismiss", "关闭权限弹窗: $resId")
                btn.click()
                device.waitForIdle(1000)
                return
            }
        }

        // 再检查文本匹配的弹窗
        for (text in dismissTexts) {
            val btn = device.findObject(By.text(text))
            if (btn != null && btn.isClickable) {
                logger.d("DialogDismiss", "关闭弹窗: \"$text\"")
                btn.click()
                device.waitForIdle(1000)
                return
            }
        }
    }
}
