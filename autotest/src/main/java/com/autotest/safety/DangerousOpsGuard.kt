package com.autotest.safety

import com.autotest.log.TestLogger

/**
 * 危险操作点击被拦截。继承 AssertionError：测试直接 FAIL，不进 flaky 重试白名单之外的洗白路径。
 */
class DangerousOperationException(message: String) : AssertionError(message)

/**
 * 危险操作点击守卫（铁律#7「点击前比对危险操作清单」的**代码层下沉**，lessons L-002）。
 *
 * 框架全部点击入口（SelfHealingLocator/GuardedElement、UiAutomator/Espresso 扩展、
 * 缓存回放、弹窗拦截器）统一过本守卫：
 * - 词表命中且未显式 `allowDangerous` → 截图留证 + 事件记账 + 抛 [DangerousOperationException]
 * - 目标 text/res-id/desc 全空（不可审计）且未显式 `allowUnverifiable` → 同样阻断（fail-closed）
 * - 一切放行（显式 allow / 守卫关闭）都留 [GuardEvent]，禁止静默
 *
 * 词表 = 内置 [DEFAULT_DANGEROUS_TEXTS] 默认**合并**项目词表（见 [buildWordlist]）；
 * 完全替换必须显式 opt-in，防止项目配置误降级守卫。
 */
class DangerousOpsGuard(
    dangerousTexts: List<String> = DEFAULT_DANGEROUS_TEXTS,
    private val logger: TestLogger? = null,
    /** 命中时截图（入参为文件名前缀，返回截图路径；null=无截图能力） */
    private val evidenceCapture: ((String) -> String?)? = null
) {

    /** 词长 <2 的词条剔除（单字 contains 误命中面过大），比对统一小写（拉丁词大小写不敏感） */
    private val words: List<String> = dangerousTexts.filter { it.trim().length >= 2 }.map { it.trim() }

    private val _events = mutableListOf<GuardEvent>()

    /** 全部守卫事件（阻断/放行/禁用），报告独立 section 消费 */
    val events: List<GuardEvent> get() = _events.toList()

    /**
     * 点击前比对。命中且未放行时抛 [DangerousOperationException]（fail-stop）。
     *
     * @param allowDangerous 用例明确要求执行危险操作时的显式放行（记 DANGEROUS_ALLOWED 留痕）
     * @param allowUnverifiable 调用方确知目标安全但无语义字段时的显式放行（记 UNVERIFIABLE_ALLOWED 留痕）
     */
    fun checkClick(
        target: ClickTarget,
        allowDangerous: Boolean = false,
        allowUnverifiable: Boolean = false
    ) {
        if (target.isUnverifiable) {
            if (allowUnverifiable) {
                record(GuardEvent(GuardEventType.UNVERIFIABLE_ALLOWED, target.describe()))
                logger?.w(TAG, "不可审计点击已显式放行(allowUnverifiable): ${target.describe()}")
                return
            }
            val shot = capture("guard_unverifiable")
            record(GuardEvent(GuardEventType.UNVERIFIABLE_BLOCKED, target.describe(), screenshotPath = shot))
            logger?.e(
                TAG,
                "拦截不可审计点击（text/res-id/desc 全空，无法比对危险操作清单）: ${target.describe()}" +
                    (shot?.let { "，截图: $it" } ?: "")
            )
            throw DangerousOperationException(
                "拦截不可审计点击：目标无 text/res-id/desc，无法比对危险操作清单（fail-closed）。" +
                    "确认安全后用 allowUnverifiable=true 显式放行。target=${target.describe()}"
            )
        }

        val hit = matchWord(target) ?: return
        if (allowDangerous) {
            record(GuardEvent(GuardEventType.DANGEROUS_ALLOWED, target.describe(), matchedWord = hit))
            logger?.w(TAG, "危险操作点击已显式放行(allowDangerous): 命中「$hit」 ${target.describe()}")
            return
        }
        val shot = capture("guard_dangerous")
        record(GuardEvent(GuardEventType.DANGEROUS_BLOCKED, target.describe(), matchedWord = hit, screenshotPath = shot))
        logger?.e(
            TAG,
            "拦截危险操作点击: 命中「$hit」 ${target.describe()}" + (shot?.let { "，截图: $it" } ?: "")
        )
        throw DangerousOperationException(
            "拦截危险操作点击：命中危险词「$hit」（钱包误操作不可逆，铁律#7）。" +
                "用例明确要求执行时用 allowDangerous=true 显式放行。target=${target.describe()}"
        )
    }

    /**
     * 非抛出式查询：目标是否命中危险词表（返回命中词，未命中返回 null）。
     * 供「自动点击类组件」（如弹窗拦截器）在候选筛选阶段跳过危险候选，而非中断流程。
     */
    fun matchWord(target: ClickTarget): String? = words.firstOrNull { w ->
        containsWord(target.text, w) || containsWord(target.contentDesc, w) || containsWord(target.resourceId, w)
    }

    /** safety.enabled=false 时由装配方调用：留可审计痕迹，不允许静默关闭 */
    fun recordDisabled() {
        record(GuardEvent(GuardEventType.GUARD_DISABLED, "safety.enabled=false（守卫已关闭，本轮点击不比对危险操作清单）"))
        logger?.w(TAG, "⚠️ 危险操作守卫已关闭（safety.enabled=false）——仅限调试/非钱包 App 接入使用")
    }

    private fun containsWord(field: String, word: String): Boolean =
        field.isNotEmpty() && field.lowercase().contains(word.lowercase())

    private fun capture(prefix: String): String? = try {
        evidenceCapture?.invoke(prefix)
    } catch (e: Throwable) {
        logger?.w(TAG, "守卫留证截图失败: ${e.message}")
        null
    }

    private fun record(event: GuardEvent) {
        _events.add(event)
    }

    companion object {
        private const val TAG = "DangerousOpsGuard"

        /**
         * 内置危险词表（钱包场景导向，中英）。项目词表默认与之合并（见 [buildWordlist]）；
         * 项目特化清单来源见 app-knowledge/dangerous-ops.md。
         */
        val DEFAULT_DANGEROUS_TEXTS: List<String> = listOf(
            // 资金操作
            "转账", "付款", "支付", "提现", "汇款", "确认交易", "确认支付", "确认转账",
            "Send", "Transfer", "Pay", "Withdraw", "Confirm Transaction",
            // 钱包/账户毁灭性操作
            "删除钱包", "删除账户", "移除钱包", "重置钱包", "清除数据", "恢复出厂",
            "Delete Wallet", "Remove Wallet", "Reset Wallet", "Clear Data",
            // 登录态/环境（测试前提易碎，铁律禁止）
            "登出", "退出登录", "注销", "切换环境",
            "Log out", "Logout", "Sign out",
            // 链上授权
            "签名", "授权", "Approve", "Sign"
        )

        /**
         * 组装最终词表：默认「内置 + 项目」合并去重；`replace=true`（显式 opt-in）才用项目词表
         * 完全替换内置（项目词表为空时仍回退内置，防止配置成空表把守卫掏空）。
         */
        fun buildWordlist(projectTexts: List<String>, replace: Boolean = false): List<String> =
            if (replace) projectTexts.ifEmpty { DEFAULT_DANGEROUS_TEXTS }
            else (DEFAULT_DANGEROUS_TEXTS + projectTexts).distinct()
    }
}

/**
 * 全局守卫注册点：无法构造注入的入口（UiDevice/ViewInteraction 扩展函数）从这里取守卫。
 * 由 BaseUiTest.setUp 注册、tearDown 反注册；未注册（框架类独立使用）= 无守卫，行为向后兼容。
 */
object GuardRegistry {
    @Volatile
    var current: DangerousOpsGuard? = null
}
