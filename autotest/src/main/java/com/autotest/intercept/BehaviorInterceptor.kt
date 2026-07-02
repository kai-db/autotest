package com.autotest.intercept

/**
 * 行为拦截器（对齐 Kaspresso BehaviorInterceptor）：步骤失败时按注册序依次尝试恢复。
 *
 * 与 [Interceptor]（watcher，纯旁路观察：日志/截图/性能采集）严格分离——
 * 只有 behavior 节点允许影响执行结果，watcher 永远不影响。
 * 恢复成功的节点会触发一次步骤重试；全链无能为力才把失败抛给上层。
 */
interface BehaviorInterceptor {

    /**
     * 尝试从失败中恢复（如关闭挡路弹窗、滚动到目标元素）。
     *
     * @return true 表示已做出恢复动作、值得重试一次该步骤；false 表示无能为力，交给下一个节点
     *
     * 注意：返回 true 只代表「恢复动作已执行」；是否真的重放步骤由 [InterceptorChain.runWithRecovery]
     * 结合步骤的 retriable 决定——retriable=false 的步骤即便恢复成功也不重放（副作用保护，D1）。
     */
    fun tryRecover(ctx: StepContext, error: Throwable): Boolean
}
