package com.autotest.safety

import android.view.View
import android.widget.TextView
import androidx.test.uiautomator.UiObject2
import com.autotest.selector.ElementSnapshot

/** UiObject2 → 可审计点击目标（守卫比对输入） */
fun UiObject2.toClickTarget(): ClickTarget = ClickTarget(
    text = text ?: "",
    resourceId = resourceName ?: "",
    contentDesc = contentDescription ?: "",
    className = className ?: "",
    packageName = applicationPackage ?: "",
    bounds = visibleBounds.let { "[${it.left},${it.top}][${it.right},${it.bottom}]" }
)

/** ElementSnapshot（缓存/自愈快照）→ 可审计点击目标 */
fun ElementSnapshot.toClickTarget(): ClickTarget = ClickTarget(
    text = text,
    resourceId = resourceId,
    contentDesc = contentDesc,
    className = className,
    packageName = packageName,
    bounds = bounds
)

/** Espresso View → 可审计点击目标 */
fun View.toClickTarget(): ClickTarget = ClickTarget(
    text = (this as? TextView)?.text?.toString() ?: "",
    resourceId = try {
        if (id != View.NO_ID) resources.getResourceName(id) else ""
    } catch (_: Exception) {
        ""
    },
    contentDesc = contentDescription?.toString() ?: "",
    className = javaClass.name,
    packageName = context.packageName,
    bounds = ""
)
