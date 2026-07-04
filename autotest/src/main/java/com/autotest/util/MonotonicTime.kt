package com.autotest.util

/**
 * 框架统一单调时间源（Q3）：时长/超时类控制流计时一律走 [nowMs]，
 * 墙钟（System.currentTimeMillis）被 NTP 同步/手动调时回拨·前跳时不再影响超时判定。
 *
 * 默认源在类初始化时运行时探测一次：
 * - Android 运行时：`SystemClock.elapsedRealtime()`（CLOCK_BOOTTIME，计 deep sleep，对所有入口生效）
 * - JVM 单测：android.jar stub 抛异常 → 回退 `System.nanoTime()/1_000_000`（CLOCK_MONOTONIC）
 *
 * 返回值只保证单调不减，起点无意义——只能用于「两次取值相减」求时长，
 * 不能当时间戳持久化或展示（那类语义留墙钟，见各调用点注释）。
 *
 * 注意：Robolectric 类环境会探测到假 SystemClock（本项目未用）；如需自定源用 [overrideSourceForTest]。
 */
object MonotonicTime {

    private val DEFAULT: () -> Long = try {
        android.os.SystemClock.elapsedRealtime() // 探针：JVM 单测中 stub 抛 not mocked
        ({ android.os.SystemClock.elapsedRealtime() })
    } catch (t: Throwable) {
        ({ System.nanoTime() / 1_000_000 })
    }

    @Volatile
    private var source: () -> Long = DEFAULT

    fun nowMs(): Long = source()

    /**
     * 假钟注入（仅本模块单测可用，AAR 外部不可及）。
     * 非并发安全：测试须串行注入，并在 finally 里 [resetForTest] 复位，防止污染同 JVM 后续测试。
     */
    @JvmSynthetic // internal 在字节码仍是 public，加 synthetic 挡住 AAR 的 Java/IDE 触达
    internal fun overrideSourceForTest(source: () -> Long) {
        this.source = source
    }

    /** 复原到探测所得默认源（与 [overrideSourceForTest] 配对，测试 finally 必调） */
    @JvmSynthetic
    internal fun resetForTest() {
        source = DEFAULT
    }
}
