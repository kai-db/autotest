package com.autotest.diagnosis

/** 失败根因类型 */
enum class RootCauseType {
    /** Java/Kotlin 崩溃（FATAL EXCEPTION） */
    CRASH,

    /** 应用无响应 */
    ANR,

    /** 内存溢出 */
    OOM,

    /** Native 崩溃（Fatal signal） */
    NATIVE_CRASH
}

/** 从 logcat 中识别出的失败根因 */
data class RootCause(
    val type: RootCauseType,
    /** 根因签名（异常类名+消息 / ANR 进程等），用于聚类同类失败 */
    val signature: String,
    /** 命中的原始日志行（证据） */
    val evidenceLine: String
)

/**
 * 失败根因分析器：扫描 logcat 识别 crash/ANR/OOM/native crash 签名，
 * 让报告直接呈现「环境问题 vs 产品 bug vs 用例问题」的判定依据，
 * AI 与人都不用再从几百行 logcat 里人肉找根因。
 */
object LogcatAnalyzer {

    private val FATAL_EXCEPTION = Regex("""FATAL EXCEPTION:.*""")
    private val EXCEPTION_LINE = Regex("""(?:^|\s)((?:[a-zA-Z_][\w]*\.)+[A-Z]\w*(?:Exception|Error))(:.*)?""")
    private val ANR_LINE = Regex("""ANR in ([\w.]+)""")
    private val OOM_LINE = Regex("""(?i)(java\.lang\.OutOfMemoryError|Out of memory)""")
    private val NATIVE_CRASH_LINE = Regex("""Fatal signal \d+ \((SIG\w+)\)""")

    /**
     * 分析 logcat 文本，返回识别出的根因；无明确签名返回 null。
     * @param packageName 可选：仅在 ANR 判定时校验是否本包（避免别的 App ANR 干扰判定）
     */
    fun analyze(logcat: String, packageName: String? = null): RootCause? {
        if (logcat.isBlank()) return null
        val lines = logcat.lineSequence().toList()

        lines.forEachIndexed { i, line ->
            // OOM 优先于普通 crash：OOM 引发的 FATAL EXCEPTION 根因是内存而非异常本身
            OOM_LINE.find(line)?.let {
                return RootCause(RootCauseType.OOM, it.groupValues[1], line.trim())
            }
        }

        lines.forEachIndexed { i, line ->
            if (FATAL_EXCEPTION.containsMatchIn(line)) {
                // 签名取 FATAL 块内的首个异常行（类名+消息）
                val exceptionLine = lines.drop(i + 1).take(5)
                    .firstNotNullOfOrNull { EXCEPTION_LINE.find(it) }
                val signature = exceptionLine?.let {
                    (it.groupValues[1] + it.groupValues[2]).take(160)
                } ?: "FATAL EXCEPTION"
                return RootCause(RootCauseType.CRASH, signature, line.trim())
            }

            ANR_LINE.find(line)?.let { m ->
                val process = m.groupValues[1]
                if (packageName == null || process.startsWith(packageName)) {
                    return RootCause(RootCauseType.ANR, "ANR in $process", line.trim())
                }
            }

            NATIVE_CRASH_LINE.find(line)?.let {
                return RootCause(RootCauseType.NATIVE_CRASH, "Fatal signal ${it.groupValues[1]}", line.trim())
            }
        }
        return null
    }
}
