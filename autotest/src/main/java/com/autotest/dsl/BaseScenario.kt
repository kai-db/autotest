package com.autotest.dsl

/**
 * 可复用的测试场景基类。
 * 参考 Kaspresso BaseScenario 设计：把常见步骤组封装为类，跨测试一行调用。
 *
 * 用法：
 * ```
 * // 定义可复用场景
 * class LoginScenario(
 *     private val phone: String,
 *     private val code: String
 * ) : BaseScenario("登录流程") {
 *     override fun ScenarioBuilder.steps() {
 *         step("点击登录") { device.clickText("登录") }
 *         step("输入手机号") { viewById(R.id.phone).typeText(phone) }
 *         step("输入验证码") { viewById(R.id.code).typeText(code) }
 *         step("点击确认") { viewById(R.id.submit).click() }
 *     }
 * }
 *
 * // 在测试中使用
 * scenario("完整流程") {
 *     step("启动App") { launchApp() }
 *     include(LoginScenario("138...", "1234"))
 *     step("验证首页") { ... }
 * }
 * ```
 */
abstract class BaseScenario(val scenarioName: String) {

    /**
     * 子类实现此方法定义步骤。
     */
    abstract fun ScenarioBuilder.steps()

    /**
     * 将此场景的步骤注入到 ScenarioBuilder 中。
     */
    internal fun applyTo(builder: ScenarioBuilder) {
        builder.steps()
    }
}

/**
 * ScenarioBuilder 扩展：嵌入一个可复用场景。
 */
fun ScenarioBuilder.include(scenario: BaseScenario) {
    scenario.applyTo(this)
}
