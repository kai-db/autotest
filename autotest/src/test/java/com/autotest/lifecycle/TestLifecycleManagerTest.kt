package com.autotest.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Test

class TestLifecycleManagerTest {

    @Test
    fun hooks_fireInOrder() {
        val log = mutableListOf<String>()
        val manager = TestLifecycleManager()

        manager.register(object : TestLifecycleHook {
            override fun beforeTest(testName: String) { log.add("before:$testName") }
            override fun afterTestSuccess(testName: String) { log.add("success:$testName") }
            override fun afterTestFinally(testName: String) { log.add("finally:$testName") }
        })

        manager.fireBeforeTest("test1")
        manager.fireAfterTestSuccess("test1")
        manager.fireAfterTestFinally("test1")

        assertEquals(listOf("before:test1", "success:test1", "finally:test1"), log)
    }

    @Test
    fun hookException_doesNotBreakOthers() {
        val log = mutableListOf<String>()
        val manager = TestLifecycleManager()

        manager.register(object : TestLifecycleHook {
            override fun beforeTest(testName: String) { throw RuntimeException("hook error") }
        })
        manager.register(object : TestLifecycleHook {
            override fun beforeTest(testName: String) { log.add("second") }
        })

        manager.fireBeforeTest("test1")
        assertEquals(listOf("second"), log)
    }

    @Test
    fun failureHook_receivesError() {
        val errors = mutableListOf<String>()
        val manager = TestLifecycleManager()

        manager.register(object : TestLifecycleHook {
            override fun afterTestFailure(testName: String, error: Throwable) {
                errors.add("${testName}:${error.message}")
            }
        })

        manager.fireAfterTestFailure("test1", RuntimeException("boom"))
        assertEquals(listOf("test1:boom"), errors)
    }

    @Test
    fun hookException_isLoggedNotSwallowed() {
        // E1：hook 异常被隔离时必须留痕（原实现空 catch 静默吞掉）
        val warnings = mutableListOf<String>()
        val manager = TestLifecycleManager()
        manager.logger = object : com.autotest.log.TestLogger {
            override fun d(tag: String, msg: String) {}
            override fun i(tag: String, msg: String) {}
            override fun w(tag: String, msg: String) { warnings.add("$tag:$msg") }
            override fun e(tag: String, msg: String, throwable: Throwable?) {}
        }

        manager.register(object : TestLifecycleHook {
            override fun beforeTest(testName: String) { throw IllegalStateException("hook 内部错误") }
        })
        manager.fireBeforeTest("test1")

        assertEquals(1, warnings.size)
        val entry = warnings.single()
        assertEquals(true, entry.startsWith("Lifecycle:beforeTest"))
        assertEquals(true, entry.contains("hook 内部错误"))
    }
}
