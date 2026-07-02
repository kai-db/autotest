package com.autotest.intercept

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.autotest.log.TestLogger
import androidx.test.uiautomator.UiObject2
import com.autotest.safety.GuardRegistry
import com.autotest.safety.toClickTarget

/**
 * 弹窗自动关闭拦截器（behavior 链角色）。
 * 参考 Kaspresso 的 SystemDialogSafetyBehaviorInterceptor。
 *
 * 步骤失败时检测并关闭：
 * - 系统权限弹窗（仅限系统权限控制器包，res-id + 包名双条件豁免）
 * - App 升级/引导/提示弹窗（dismiss 语义词，**精确匹配**）
 *
 * 设计约束（2026-07-02 收敛，plan `2026-07-02-fix-framework-safety-gate` P0-1）：
 * - **只做 behavior 链**（失败恢复）。不再实现 watcher 角色——watcher 链契约是「纯旁路观察、
 *   永不影响执行」，主动点击违反契约；弹窗挡路会让步骤失败，由本恢复链关掉后重试一次，能力等价。
 * - 默认词表**只含 dismiss 语义**（取消/跳过/稍后），不含确认语义（确定/OK/知道了）——
 *   钱包 App 的「确定」可能是不可逆操作（"确定删除钱包？"）。确认语义由接入方经
 *   [confirmTexts] 显式 opt-in。
 * - 匹配方式为**文本精确相等**（contains 会误命中「取消订单」这类业务按钮）。
 * - 每次点击前过危险操作守卫的非抛出式查询（[com.autotest.safety.DangerousOpsGuard.matchWord]），
 *   命中危险词的候选**跳过不点**并告警（不中断恢复流程）。
 *
 * 使用：
 * ```
 * interceptors.addBehavior(DialogDismissInterceptor(logger))
 * // 确需自动点确认类按钮的项目显式传入：
 * DialogDismissInterceptor(logger, confirmTexts = listOf("知道了"))
 * ```
 */
class DialogDismissInterceptor(
    private val logger: TestLogger,
    private val dismissTexts: List<String> = DEFAULT_DISMISS_TEXTS,
    /** 确认语义词（确定/OK/知道了…）——默认空，接入方显式 opt-in 才自动点 */
    private val confirmTexts: List<String> = emptyList()
) : BehaviorInterceptor {

    companion object {
        /** 默认只含 dismiss 语义 + 系统权限「允许」组；确认语义已移除（见类 KDoc） */
        val DEFAULT_DISMISS_TEXTS = listOf(
            // 权限弹窗文本兜底
            "允许", "Allow", "允许本次", "始终允许",
            "ALLOW", "While using the app",
            // 关闭/跳过
            "稍后再说", "取消", "跳过", "Skip",
            "以后再说", "暂不升级", "下次再说"
        )

        val PERMISSION_RES_IDS = listOf(
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.packageinstaller:id/permission_allow_button"
        )

        /** 权限豁免的包名白名单：res-id 匹配还须元素属于系统权限控制器（防第三方页面复用同名 res-id 绕过） */
        val PERMISSION_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller"
        )
    }

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private val clickableTexts: Set<String> = (dismissTexts + confirmTexts).toSet()

    /**
     * behavior 链角色：步骤失败时扫一次弹窗（弹窗可能是步骤执行中途弹出的）。
     * 关掉了任何弹窗就返回 true 触发步骤重试。
     */
    override fun tryRecover(ctx: StepContext, error: Throwable): Boolean {
        val dismissed = dismissDialogs()
        if (dismissed) {
            logger.i("DialogDismiss", "步骤[${ctx.stepNumber}]失败后发现并关闭了弹窗")
        }
        return dismissed
    }

    /** @return 是否关闭了弹窗 */
    private fun dismissDialogs(): Boolean {
        // 一次抓取当前所有可点击元素后本地匹配，避免对每个候选各做一次跨进程树遍历
        val clickables = device.findObjects(By.clickable(true))
        if (clickables.isEmpty()) return false

        // 系统权限弹窗：res-id 精确匹配 + 包名双条件
        for (obj in clickables) {
            val res = obj.resourceName ?: continue
            if (res in PERMISSION_RES_IDS && obj.applicationPackage in PERMISSION_PACKAGES) {
                logger.d("DialogDismiss", "关闭权限弹窗: $res")
                guardedClick(obj); device.waitForIdle(1000); return true
            }
        }
        // App 内弹窗：文本精确匹配 + 危险词守卫筛查
        for (obj in clickables) {
            val text = obj.text ?: continue
            if (text !in clickableTexts) continue
            val dangerHit = GuardRegistry.current?.matchWord(obj.toClickTarget())
            if (dangerHit != null) {
                logger.w("DialogDismiss", "候选弹窗按钮「$text」命中危险词「$dangerHit」，跳过不点")
                continue
            }
            logger.d("DialogDismiss", "关闭弹窗: \"$text\"")
            guardedClick(obj); device.waitForIdle(1000); return true
        }
        return false
    }

    /** 点击前统一过守卫（保持「全部点击入口有守卫」不变量；系统权限按钮非危险词、会干净通过并留可审计事件） */
    private fun guardedClick(obj: UiObject2) {
        GuardRegistry.current?.checkClick(obj.toClickTarget())
        obj.click()
    }
}
