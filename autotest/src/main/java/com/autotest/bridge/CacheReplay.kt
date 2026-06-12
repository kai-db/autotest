package com.autotest.bridge

import com.autotest.dsl.Scenario
import com.autotest.dsl.scenario
import com.autotest.intercept.InterceptorChain
import com.autotest.report.ReportCollector

/**
 * 单步回放执行器：设备胶水的抽象。
 * 回放编排只依赖此接口，便于 JVM 单测与未来扩展（如远程设备）。
 * 生产实现见 [UiReplayExecutor]。
 */
interface ReplayExecutor {
    /** 执行一个缓存步骤；失败抛异常（由 Scenario 机制截图/记录/上报） */
    fun execute(case: CachedCase, step: CachedStep)
}

/**
 * 缓存回放编排：把 CachedCase 转成框架 Scenario 执行——
 * 复用既有的拦截器链、证据回填、报告机制，回放用例与手写用例同一条产出链路。
 */
object CacheReplay {

    fun toScenario(
        case: CachedCase,
        executor: ReplayExecutor,
        collector: ReportCollector? = null,
        interceptors: InterceptorChain? = null
    ): Scenario = scenario(
        name = case.caseId + (case.description.ifEmpty { "" }.let { if (it.isEmpty()) "" else " $it" }),
        collector = collector,
        interceptors = interceptors
    ) {
        case.steps.forEach { cachedStep ->
            step(cachedStep.name) { executor.execute(case, cachedStep) }
        }
    }
}
