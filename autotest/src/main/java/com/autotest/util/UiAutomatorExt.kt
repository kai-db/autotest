package com.autotest.util

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** 等待某个文本出现 */
fun UiDevice.waitForText(text: String, timeout: Long = 5000): Boolean {
    return wait(Until.hasObject(By.textContains(text)), timeout)
}

/** 等待某个 resource-id 出现 */
fun UiDevice.waitForResId(resId: String, timeout: Long = 5000): Boolean {
    return wait(Until.hasObject(By.res(resId)), timeout)
}

/** 点击包含指定文本的元素。找不到时抛异常。 */
fun UiDevice.clickText(text: String) {
    val obj = findObject(By.textContains(text))
        ?: throw AssertionError("找不到包含文本 \"$text\" 的元素")
    obj.click()
}

/** 点击指定 resource-id 的元素。找不到时抛异常。 */
fun UiDevice.clickResId(resId: String) {
    val obj = findObject(By.res(resId))
        ?: throw AssertionError("找不到 resource-id \"$resId\" 的元素")
    obj.click()
}

/** 尝试点击文本，找不到则忽略（用于弹窗等可选操作）。 */
fun UiDevice.tryClickText(text: String): Boolean {
    val obj = findObject(By.textContains(text)) ?: return false
    obj.click()
    return true
}

/** 处理系统权限弹窗——点击「允许」 */
fun UiDevice.allowPermission() {
    val allowButtons = listOf(
        "com.android.permissioncontroller:id/permission_allow_button",
        "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
        "com.android.packageinstaller:id/permission_allow_button"
    )
    for (btn in allowButtons) {
        findObject(By.res(btn))?.let { it.click(); return }
    }
    // 兜底
    findObject(By.textContains("允许"))?.click()
        ?: findObject(By.textContains("Allow"))?.click()
}

/** 处理系统权限弹窗——点击「拒绝」 */
fun UiDevice.denyPermission() {
    val denyButtons = listOf(
        "com.android.permissioncontroller:id/permission_deny_button",
        "com.android.packageinstaller:id/permission_deny_button"
    )
    for (btn in denyButtons) {
        findObject(By.res(btn))?.let { it.click(); return }
    }
    findObject(By.textContains("拒绝"))?.click()
        ?: findObject(By.textContains("Deny"))?.click()
}

/** 等待 App 包名出现在前台 */
fun UiDevice.waitForApp(packageName: String, timeout: Long = 10000): Boolean {
    return wait(Until.hasObject(By.pkg(packageName).depth(0)), timeout)
}

/** 在屏幕上从下往上滑动（向上翻页） */
fun UiDevice.scrollUp(steps: Int = 20) {
    val w = displayWidth
    val h = displayHeight
    swipe(w / 2, h * 3 / 4, w / 2, h / 4, steps)
}

/** 在屏幕上从上往下滑动（向下翻页） */
fun UiDevice.scrollDown(steps: Int = 20) {
    val w = displayWidth
    val h = displayHeight
    swipe(w / 2, h / 4, w / 2, h * 3 / 4, steps)
}
