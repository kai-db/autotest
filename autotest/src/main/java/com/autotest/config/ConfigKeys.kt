package com.autotest.config

object ConfigKeys {
    const val APP_PACKAGE_NAME = "app.packageName"
    const val APP_LAUNCH_TIMEOUT = "app.launchTimeout"
    const val APP_MAX_LAUNCH_TIME = "app.maxLaunchTime"
    const val APP_BOTTOM_TABS = "app.bottomTabs"
    const val APP_SCREENSHOT_DIR = "app.screenshotDir"
    const val APP_SCREENSHOT_ON_FAILURE = "app.screenshotOnFailure"
    const val APP_WAIT_FOR_IDLE_TIMEOUT = "app.waitForIdleTimeout"

    /** 危险操作守卫开关（默认 true；关闭仅限调试/非钱包接入，关闭会留审计事件） */
    const val SAFETY_ENABLED = "safety.enabled"

    /** 项目危险词表（逗号分隔）；默认与内置词表合并 */
    const val SAFETY_DANGEROUS_TEXTS = "safety.dangerousTexts"

    /** 显式 opt-in：项目词表完全替换内置词表（默认 false = 合并） */
    const val SAFETY_DANGEROUS_TEXTS_REPLACE = "safety.dangerousTextsReplace"
}
