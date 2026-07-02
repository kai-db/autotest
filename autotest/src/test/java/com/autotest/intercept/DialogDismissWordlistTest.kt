package com.autotest.intercept

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 P0-1 收敛：默认弹窗词表**只含 dismiss 语义**，不含确认语义。
 * 确认类（确定/OK/知道了）在钱包 App 可能是不可逆操作，必须由接入方经 confirmTexts 显式 opt-in，
 * 不能进默认自动点击集合。device 依赖的实际点击路径由 instrumented SafetyGateSmokeTest 覆盖。
 */
class DialogDismissWordlistTest {

    private val confirmWords = listOf("确定", "确认", "OK", "好的", "知道了", "我知道了", "同意")

    @Test
    fun `默认词表不含任何确认语义词`() {
        val defaults = DialogDismissInterceptor.DEFAULT_DISMISS_TEXTS
        for (w in confirmWords) {
            assertFalse("默认词表不应含确认语义「$w」（钱包不可逆操作风险）", defaults.contains(w))
        }
    }

    @Test
    fun `默认词表含常见 dismiss 语义`() {
        val defaults = DialogDismissInterceptor.DEFAULT_DISMISS_TEXTS
        assertTrue(defaults.contains("取消"))
        assertTrue(defaults.contains("跳过"))
        assertTrue(defaults.contains("稍后再说"))
    }

    @Test
    fun `权限豁免包名白名单仅系统权限控制器`() {
        val pkgs = DialogDismissInterceptor.PERMISSION_PACKAGES
        assertTrue(pkgs.contains("com.android.permissioncontroller"))
        assertTrue(pkgs.contains("com.google.android.permissioncontroller"))
        assertTrue(pkgs.contains("com.android.packageinstaller"))
        // 不应含任意第三方/被测 App 包（防 res-id 复用绕过）
        assertFalse(pkgs.contains("com.tm.security.wallet"))
    }
}
