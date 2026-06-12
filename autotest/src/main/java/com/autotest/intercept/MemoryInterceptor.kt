package com.autotest.intercept

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.log.TestLogger

/**
 * 内存采集拦截器：每步结束后采集被测 App 的 TOTAL PSS（`dumpsys meminfo`），
 * 并对"相对首步基线的持续增长"做泄漏告警——反复进出同一页面若 PSS 只涨不降，往往是泄漏。
 *
 * 仅采集与告警、不中断测试；读取失败留痕不抛。
 */
class MemoryInterceptor(
    private val logger: TestLogger,
    private val packageName: String,
    /** 相对首步基线的累计增长告警阈值（KB），默认 80MB */
    private val leakWarnKb: Long = 80_000
) : Interceptor {

    init {
        // packageName 会拼进 shell 命令，校验防注入（输入通常来自 TestConfig，此处为防御性加固）
        require(packageName.matches(Regex("^[a-zA-Z0-9._]+$"))) { "非法包名: $packageName" }
    }

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /** stepNumber -> TOTAL PSS(KB)，保序便于看增长趋势 */
    private val stepPssKb = linkedMapOf<String, Long>()
    private var baselineKb: Long = -1

    override fun afterStep(stepNumber: String, stepName: String, durationMs: Long) {
        val pss = readTotalPssKb()
        if (pss < 0) return
        stepPssKb[stepNumber] = pss
        if (baselineKb < 0) baselineKb = pss

        val growth = pss - baselineKb
        val sign = if (growth >= 0) "+" else ""
        logger.d("Memory", "步骤[$stepNumber] PSS=${pss / 1024}MB (相对基线 $sign${growth / 1024}MB)")
        if (growth > leakWarnKb) {
            logger.w("Memory", "疑似内存泄漏：PSS 较基线增长 ${growth / 1024}MB，超阈值 ${leakWarnKb / 1024}MB")
        }
    }

    fun getStepPssKb(): Map<String, Long> = stepPssKb.toMap()
    fun getPeakPssKb(): Long = stepPssKb.values.maxOrNull() ?: -1

    private fun readTotalPssKb(): Long {
        return try {
            parseTotalPssKb(device.executeShellCommand("dumpsys meminfo $packageName"))
        } catch (e: Throwable) {
            logger.w("Memory", "读取内存失败: ${e.message}")
            -1
        }
    }

    companion object {
        /** 解析 dumpsys meminfo 的 TOTAL PSS（KB）；兼容不同 Android 版本的表头格式 */
        internal fun parseTotalPssKb(out: String): Long =
            // 优先 App Summary 的明确标签 "TOTAL PSS:"，回退到详情表格行首 "TOTAL <pss> ..."
            (Regex("""TOTAL\s+PSS:\s+(\d+)""").find(out)
                ?: Regex("""(?m)^\s*TOTAL\s+(\d+)""").find(out))
                ?.groupValues?.get(1)?.toLongOrNull() ?: -1
    }
}
