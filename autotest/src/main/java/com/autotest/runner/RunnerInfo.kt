package com.autotest.runner

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry

data class RunnerInfo(
    val deviceManufacturer: String,
    val deviceModel: String,
    val sdkVersion: Int,
    val androidVersion: String,
    val appPackage: String,
    val testPackage: String,
    val locale: String
) {
    val deviceName: String get() = "$deviceManufacturer $deviceModel".trim()

    companion object {
        fun collect(appPackage: String): RunnerInfo {
            val instr = InstrumentationRegistry.getInstrumentation()
            return RunnerInfo(
                deviceManufacturer = Build.MANUFACTURER,
                deviceModel = Build.MODEL,
                sdkVersion = Build.VERSION.SDK_INT,
                androidVersion = Build.VERSION.RELEASE,
                appPackage = appPackage,
                testPackage = instr.context.packageName,
                locale = java.util.Locale.getDefault().toString()
            )
        }
    }
}
