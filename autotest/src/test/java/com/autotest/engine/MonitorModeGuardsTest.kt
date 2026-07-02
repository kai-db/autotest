package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** P0-6 / P0-7 回归：maxRounds 上限、超时后 terminal 拒绝并发下一轮、reset 清除。 */
class MonitorModeGuardsTest {

    private lateinit var suite: TestSuite
    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Before
    fun setUp() {
        val runner = TestRunner(appPackage = "com.test", logger = fakeLogger)
        suite = TestSuite("测试", runner, fakeLogger)
        suite.addTest("TC-001", "快用例") { step("s") {} }
    }

    @Test
    fun `超过 maxRounds 抛异常，不再无限累积`() {
        val monitor = MonitorMode(suite, fakeLogger, resultsDir = tmp(), maxRounds = 2)
        monitor.runRound()
        monitor.runRound()
        try {
            monitor.runRound()
            fail("第 3 轮应超过 maxRounds=2 抛出")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("轮次已达上限"))
        }
    }

    @Test
    fun `某轮超时后拒绝启动新一轮，reset 后恢复`() {
        val monitor = MonitorMode(suite, fakeLogger, resultsDir = tmp())
        // 制造一个卡死用例触发超时
        val slowRunner = TestRunner(appPackage = "com.test", logger = fakeLogger)
        val slowSuite = TestSuite("卡死", slowRunner, fakeLogger).apply {
            addTest("TC-SLOW", "卡死") { step("hang") { Thread.sleep(60_000) } }
        }
        val slowMonitor = MonitorMode(slowSuite, fakeLogger, resultsDir = tmp())
        slowMonitor.runRound(timeoutMs = 200) // 超时，置 timedOut

        try {
            slowMonitor.runRound(timeoutMs = 200)
            fail("上一轮超时后应拒绝启动新一轮")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("拒绝启动新一轮"))
        }

        slowMonitor.reset() // 人工确认后 reset 清 timedOut
        // reset 后可再跑（这里用快 suite 验证不再抛 timedOut——换回快用例避免再卡）
        // 注：reset 只清 MonitorMode 状态，suite 仍是卡死的，故此处只验证 timedOut 已清（不抛 IllegalStateException 的 timedOut 分支）
        try {
            slowMonitor.runRound(timeoutMs = 200)
        } catch (e: IllegalStateException) {
            fail("reset 后不应再因 timedOut 抛出，实际: ${e.message}")
        }
    }

    private fun tmp() = System.getProperty("java.io.tmpdir")
}
