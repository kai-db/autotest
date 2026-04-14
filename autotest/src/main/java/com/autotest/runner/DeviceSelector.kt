package com.autotest.runner

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

enum class DevicePreference {
    FIRST_AVAILABLE,
    SPECIFIC_SERIAL
}

object DeviceSelector {

    fun getDevice(): UiDevice {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    fun getSerial(): String = try {
        @Suppress("DEPRECATION")
        android.os.Build.SERIAL.takeIf { it != "unknown" } ?: "unknown"
    } catch (_: SecurityException) {
        "unknown"
    }
}
