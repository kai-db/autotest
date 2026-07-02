package com.autotest.selector

import androidx.test.uiautomator.UiObject2
import com.autotest.safety.DangerousOpsGuard
import com.autotest.safety.toClickTarget

/**
 * 受守卫保护的元素句柄：[SelfHealingLocator.element] 的返回类型。
 *
 * 点击类操作（[click]/[longClick]）一律先过危险操作守卫（铁律#7 代码层下沉）；
 * 只读/输入类操作直接委托底层 [UiObject2]。**刻意不暴露裸 UiObject2**——
 * 裸对象上的 click() 会绕过守卫（该绕过路径已被 plan review 判定为不可接受）。
 */
class GuardedElement internal constructor(
    private val obj: UiObject2,
    private val guard: DangerousOpsGuard?
) {

    /** 点击（过守卫）。命中危险词/不可审计且未显式放行时抛 DangerousOperationException */
    fun click(allowDangerous: Boolean = false, allowUnverifiable: Boolean = false) {
        guard?.checkClick(obj.toClickTarget(), allowDangerous, allowUnverifiable)
        obj.click()
    }

    /** 长按（过守卫） */
    fun longClick(allowDangerous: Boolean = false, allowUnverifiable: Boolean = false) {
        guard?.checkClick(obj.toClickTarget(), allowDangerous, allowUnverifiable)
        obj.longClick()
    }

    // ---- 只读/输入操作：无危险语义，直接委托 ----

    val text: String? get() = obj.text
    val resourceName: String? get() = obj.resourceName
    val contentDescription: String? get() = obj.contentDescription
    val isEnabled: Boolean get() = obj.isEnabled
    val isChecked: Boolean get() = obj.isChecked

    fun setText(value: String) {
        obj.text = value
    }
}
