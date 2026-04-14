package com.autotest.device

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * 设备操作封装。
 * 参考 Kaspresso Device API，提供网络/权限/App 管理等便捷方法。
 * 底层通过 adb shell 命令实现，无需 root。
 */
class DeviceActions(
    private val device: UiDevice,
    private val context: Context
) {

    // ==================== 网络控制 ====================

    fun enableWifi() {
        device.executeShellCommand("svc wifi enable")
    }

    fun disableWifi() {
        device.executeShellCommand("svc wifi disable")
    }

    fun enableMobileData() {
        device.executeShellCommand("svc data enable")
    }

    fun disableMobileData() {
        device.executeShellCommand("svc data disable")
    }

    fun enableAirplaneMode() {
        device.executeShellCommand("settings put global airplane_mode_on 1")
        device.executeShellCommand("am broadcast -a android.intent.action.AIRPLANE_MODE")
    }

    fun disableAirplaneMode() {
        device.executeShellCommand("settings put global airplane_mode_on 0")
        device.executeShellCommand("am broadcast -a android.intent.action.AIRPLANE_MODE")
    }

    // ==================== App 管理 ====================

    fun clearAppData(packageName: String) {
        device.executeShellCommand("pm clear $packageName")
    }

    fun forceStopApp(packageName: String) {
        device.executeShellCommand("am force-stop $packageName")
    }

    fun isAppInstalled(packageName: String): Boolean {
        val output = device.executeShellCommand("pm list packages $packageName")
        return output.contains(packageName)
    }

    // ==================== 权限管理 ====================

    fun grantPermission(packageName: String, permission: String) {
        device.executeShellCommand("pm grant $packageName $permission")
    }

    fun revokePermission(packageName: String, permission: String) {
        device.executeShellCommand("pm revoke $packageName $permission")
    }

    fun grantAllPermissions(packageName: String) {
        val commonPermissions = listOf(
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_CONTACTS",
            "android.permission.POST_NOTIFICATIONS"
        )
        commonPermissions.forEach { perm ->
            try {
                grantPermission(packageName, perm)
            } catch (_: Throwable) {
                // 忽略不支持的权限
            }
        }
    }

    // ==================== 屏幕控制 ====================

    fun setScreenOn() {
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
    }

    fun setScreenOff() {
        device.executeShellCommand("input keyevent KEYCODE_SLEEP")
    }

    fun unlockScreen() {
        setScreenOn()
        device.executeShellCommand("input keyevent KEYCODE_MENU")
    }

    fun setAutoRotate(enabled: Boolean) {
        device.executeShellCommand("settings put system accelerometer_rotation ${if (enabled) 1 else 0}")
    }

    fun setPortrait() {
        setAutoRotate(false)
        device.executeShellCommand("settings put system user_rotation 0")
    }

    fun setLandscape() {
        setAutoRotate(false)
        device.executeShellCommand("settings put system user_rotation 1")
    }

    // ==================== 系统设置 ====================

    fun disableAnimations() {
        device.executeShellCommand("settings put global window_animation_scale 0")
        device.executeShellCommand("settings put global transition_animation_scale 0")
        device.executeShellCommand("settings put global animator_duration_scale 0")
    }

    fun enableAnimations() {
        device.executeShellCommand("settings put global window_animation_scale 1")
        device.executeShellCommand("settings put global transition_animation_scale 1")
        device.executeShellCommand("settings put global animator_duration_scale 1")
    }

    // ==================== Logcat ====================

    fun clearLogcat() {
        device.executeShellCommand("logcat -c")
    }

    fun dumpLogcat(tag: String? = null, lines: Int = 200): String {
        val tagFilter = tag?.let { "-s $it" } ?: ""
        return device.executeShellCommand("logcat -d -t $lines $tagFilter")
    }

    fun dumpCrashLog(packageName: String): String {
        val safePkg = packageName.replace("'", "\\'")
        return device.executeShellCommand(
            "sh -c 'logcat -d -t 500 | grep -i \"$safePkg\\|FATAL\\|crash\\|exception\"'"
        )
    }

    companion object {
        fun create(): DeviceActions {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            return DeviceActions(
                device = UiDevice.getInstance(instrumentation),
                context = instrumentation.context
            )
        }
    }
}
