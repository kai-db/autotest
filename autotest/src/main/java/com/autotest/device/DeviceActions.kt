package com.autotest.device

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.log.TestLogger

/**
 * 校验拼入 shell 命令的参数（包名/权限名）仅含安全字符，防止命令注入。
 * 输入通常来自 TestConfig（测试者自填），此处为防御性加固。
 * 提为顶层 internal 便于 JVM 单测（安全关键纯逻辑，Q1）。
 */
internal fun requireSafeArg(value: String): String {
    require(value.matches(SAFE_ARG_REGEX)) {
        "非法参数（仅允许字母/数字/点/下划线）: $value"
    }
    return value
}

/**
 * 校验 logcat tag：允许单 tag（`^[A-Za-z0-9_.]+$`）或受控 `Tag:Priority`（Priority∈V/D/I/W/E/F/S）。
 * 禁止首字符 `-`（防 `-v` 等 option-like 注入）、`*`（filter-spec 通配）、空格及 shell 元字符（Q5）。
 */
internal fun requireSafeTag(tag: String): String {
    require(tag.matches(SAFE_TAG_REGEX)) {
        "非法 logcat tag（仅允许单 tag 或 Tag:Priority，禁止 - 开头/*/空格/shell 元字符）: $tag"
    }
    return tag
}

private val SAFE_ARG_REGEX = Regex("^[a-zA-Z0-9._]+$")
private val SAFE_TAG_REGEX = Regex("^[A-Za-z0-9_.]+(:[VDIWEFS])?$")

/**
 * 设备操作封装。
 * 参考 Kaspresso Device API，提供网络/权限/App 管理等便捷方法。
 * 底层通过 adb shell 命令实现，无需 root。
 */
class DeviceActions(
    private val device: UiDevice,
    private val context: Context,
    private val logger: TestLogger? = null
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

    /**
     * 开飞行模式。API≥28 用 `cmd connectivity airplane-mode`（可靠）；24–27 回退 `settings put + 保护广播`
     * （26/27 广播可能被系统拒，无线电不实际切换）。**执行后读回校验，未生效返回 false 并告警（不静默 no-op，Q5）。**
     * @return true=已生效
     */
    fun enableAirplaneMode(): Boolean = setAirplaneMode(true)

    /** 关飞行模式，语义同 [enableAirplaneMode]。@return true=已生效 */
    fun disableAirplaneMode(): Boolean = setAirplaneMode(false)

    private fun setAirplaneMode(on: Boolean): Boolean {
        val want = if (on) "1" else "0"
        if (Build.VERSION.SDK_INT >= 28) {
            device.executeShellCommand("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
        } else {
            device.executeShellCommand("settings put global airplane_mode_on $want")
            device.executeShellCommand("am broadcast -a android.intent.action.AIRPLANE_MODE")
        }
        // 读回校验：不接受静默 no-op
        val actual = device.executeShellCommand("settings get global airplane_mode_on").trim()
        val ok = actual == want
        if (!ok) {
            logger?.w("Device", "飞行模式切换未生效（期望 $want，实际 '$actual'，SDK ${Build.VERSION.SDK_INT}）；可能被 ROM/权限拒绝")
        }
        return ok
    }

    // ==================== App 管理 ====================

    fun clearAppData(packageName: String) {
        device.executeShellCommand("pm clear ${requireSafeArg(packageName)}")
    }

    fun forceStopApp(packageName: String) {
        device.executeShellCommand("am force-stop ${requireSafeArg(packageName)}")
    }

    fun isAppInstalled(packageName: String): Boolean {
        val output = device.executeShellCommand("pm list packages ${requireSafeArg(packageName)}")
        return output.contains(packageName)
    }

    // ==================== 权限管理 ====================

    fun grantPermission(packageName: String, permission: String) {
        device.executeShellCommand("pm grant ${requireSafeArg(packageName)} ${requireSafeArg(permission)}")
    }

    fun revokePermission(packageName: String, permission: String) {
        device.executeShellCommand("pm revoke ${requireSafeArg(packageName)} ${requireSafeArg(permission)}")
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
            } catch (e: Throwable) {
                // 某些权限在当前设备/SDK 不存在，授予失败属预期；仅留痕不中断
                logger?.w("Device", "权限授予失败（可能不支持）: $perm — ${e.message}")
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

    /**
     * 解锁屏幕。
     * 先唤醒，再滑动解锁。仅适用于"无密码"或"滑动"锁屏模式。
     * 如果设备有密码/指纹锁，需要先手动关闭。
     */
    fun unlockScreen() {
        setScreenOn()
        Thread.sleep(500)
        // 检查是否在锁屏
        val powerState = device.executeShellCommand("dumpsys power | grep mWakefulness")
        if (powerState.contains("Dozing") || powerState.contains("Asleep")) {
            device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
            Thread.sleep(1000)
        }
        // 滑动解锁
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 10)
        Thread.sleep(1000)
    }

    /** 检查屏幕是否亮着且已解锁 */
    fun isScreenUnlocked(): Boolean {
        val power = device.executeShellCommand("dumpsys power | grep mWakefulness")
        val lock = device.executeShellCommand("dumpsys window | grep mDreamingLockscreen")
        return power.contains("Awake") && !lock.contains("mDreamingLockscreen=true")
    }

    /** 保持屏幕常亮（设置 10 分钟超时） */
    fun keepScreenOn() {
        device.executeShellCommand("settings put system screen_off_timeout 600000")
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
        val tagFilter = tag?.let { "-s ${requireSafeTag(it)}" } ?: ""  // tag 过校验，与同类方法一致（Q5）
        return device.executeShellCommand("logcat -d -t $lines $tagFilter")
    }

    fun dumpCrashLog(packageName: String): String {
        val safePkg = requireSafeArg(packageName)
        return device.executeShellCommand(
            "sh -c 'logcat -d -t 500 | grep -i \"$safePkg\\|FATAL\\|crash\\|exception\"'"
        )
    }

    companion object {
        fun create(logger: TestLogger? = null): DeviceActions {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            return DeviceActions(
                device = UiDevice.getInstance(instrumentation),
                context = instrumentation.context,
                logger = logger
            )
        }
    }
}
