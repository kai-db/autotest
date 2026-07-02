package com.autotest.intercept

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InterceptorChainTest {

    @Test
    fun intercept_callsBeforeAndAfter() {
        val log = mutableListOf<String>()
        val chain = InterceptorChain()
        chain.add(object : Interceptor {
            override fun beforeAction(actionName: String, details: String) {
                log.add("before:$actionName")
            }
            override fun afterAction(actionName: String, durationMs: Long) {
                log.add("after:$actionName")
            }
        })

        chain.intercept("click") { "result" }

        assertEquals(listOf("before:click", "after:click"), log)
    }

    @Test
    fun intercept_callsOnFailure() {
        val log = mutableListOf<String>()
        val chain = InterceptorChain()
        chain.add(object : Interceptor {
            override fun onActionFailure(actionName: String, error: Throwable) {
                log.add("fail:${error.message}")
            }
        })

        try {
            chain.intercept<Unit>("click") { throw RuntimeException("boom") }
            fail("Should throw")
        } catch (e: RuntimeException) {
            assertEquals("boom", e.message)
        }

        assertEquals(listOf("fail:boom"), log)
    }

    @Test
    fun interceptorException_doesNotBreakChain() {
        val log = mutableListOf<String>()
        val chain = InterceptorChain()

        // 第一个拦截器抛异常
        chain.add(object : Interceptor {
            override fun beforeAction(actionName: String, details: String) {
                throw RuntimeException("interceptor error")
            }
        })
        // 第二个拦截器应该仍然执行
        chain.add(object : Interceptor {
            override fun beforeAction(actionName: String, details: String) {
                log.add("second")
            }
        })

        chain.fireBeforeAction("click")
        assertEquals(listOf("second"), log)
    }

    @Test
    fun intercept_returnValue() {
        val chain = InterceptorChain()
        val result = chain.intercept("action") { 42 }
        assertEquals(42, result)
    }

    @Test
    fun stepInterceptors_fire() {
        val log = mutableListOf<String>()
        val chain = InterceptorChain()
        chain.add(object : Interceptor {
            override fun beforeStep(ctx: StepContext) {
                log.add("before:${ctx.stepNumber}:${ctx.stepName}")
            }
            override fun afterStep(ctx: StepContext, durationMs: Long) {
                log.add("after:${ctx.stepNumber}")
            }
        })

        val ctx = StepContext(caseId = "c", stepNumber = "1", stepName = "启动App", runId = "r0")
        chain.fireBeforeStep(ctx)
        chain.fireAfterStep(ctx, 100)

        assertEquals(listOf("before:1:启动App", "after:1"), log)
    }
}
