# GPT-6 适配变更说明

2026-09-08：记录本次项目 AI 配置重构的依据、逐入口静态审查、冲突处理和验证范围。本文件是审计记录，不是需要逐轮加载的执行提示词。

# 项目 AI 配置审查

## 范围与结论

- 按本会话提供的可用 Skills 目录核对 **160 个 SKILL.md 入口**，全部路径可读；逐文件扫描入口正文的触发条件、授权、停止条件及流程规则，对发现的冲突读取上下文。下表保留入口、内容指纹与处置。关键词命中只是定位线索，不等于问题数量。
- 审查是入口级静态审查；没有递归审计全部引用文档、脚本和外部服务，也没有运行 160 套工作流。未来实际调用时仍须按项目冲突规则处理新发现的引用条款。
- Android Skills 存在 **26 对内容相同但物理路径不同的副本**；openai-docs、imagegen、pdf 另有多入口或版本差异。项目规则指定单一入口选择策略，未卸载全局技能。
- 项目原无自定义系统提示词文件；项目和全局 Codex 配置均未设置 `model_instructions_file` / `developer_instructions`。本次新增项目 `developer_instructions` 作为补充，保留宿主内置系统提示词，不声称修改了平台系统指令。
- AGENTS.md 为项目主入口；CLAUDE.md 导入它；`.codex/skills-policy.md` 负责统一授权、领域例外和冲突覆盖。本次“删除限制”指删除项目执行流程中的无条件确认要求，不是修改所有全局 SKILL.md 的字面内容。
- 未改动全局配置、插件缓存、模型选择、沙箱权限、MCP 内容和已有测试资产；`.codex/config.toml` 的原有 MCP 改动保留。配置重构阶段未执行 commit、push、PR、外部消息或真机操作；后续提交与推送按用户另行授权执行。

## 官方依据（2026-09-08 查询）

以下为官方建议的项目化归纳；具体配置取舍是本项目决策，不将其冒充 OpenAI 的统一强制要求。

- [GPT-6 Astra 提示指南](https://developers.openai.com/api/docs/guides/latest-model#prompting-best-practices)：明确行动请求后持续完成；复用授权；审查 AGENTS.md/Skills 冲突；用户指令优先于 Skill 指南；测试范围与改动风险相称。
- [AGENTS.md 加载规则](https://learn.chatgpt.com/docs/agent-configuration/agents-md)：项目指导按目录层级合并；应将项目约定放在可被发现的入口中。
- [Skills 指南](https://learn.chatgpt.com/docs/build-skills)：按任务加载工作流及所需资源，支持按路径配置启停。
- [配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)：`developer_instructions` 为附加指令；`model_instructions_file` 替换内置指令。本次使用前者。
- 本机源码 [`skill_config_rules_from_stack`](/Users/xiaochengcheng/code/codex-status-countdown/codex-rs/config/src/skills_config.rs:150) 只取 User / SessionFlags 启停配置层。因此不把 `skills.config` 写进项目配置冒充有效停用。此项是本机实现证据，其他宿主/版本需另行核实。

## 主要冲突与处置

| 原始规则与证据 | 冲突 | 处理 |
|---|---|---|
| [using-superpowers:12](/Users/xiaochengcheng/.codex/skills/using-superpowers/SKILL.md:12) 的 1% 触发门槛 | 单纯可用就强制启动流程 | 项目按真实相关性选择 |
| [brainstorming:14](/Users/xiaochengcheng/.codex/skills/brainstorming/SKILL.md:14) 的无条件设计审批 | 已明确要求修改仍先停工 | 明确任务直接执行，探索任务才访谈 |
| [test-driven-development:23](/Users/xiaochengcheng/.codex/skills/test-driven-development/SKILL.md:23) 的配置例外需问用户 | 低影响配置维护被额外审批阻断 | 静态校验与行为风险对应 |
| [using-git-worktrees:41](/Users/xiaochengcheng/.codex/skills/using-git-worktrees/SKILL.md:41) 的工作区选择确认 | 普通隔离决策交给用户 | 在保护现有改动前提下自主判断 |
| [executing-plans:42](/Users/xiaochengcheng/.codex/skills/executing-plans/SKILL.md:42) 缺依赖/测试失败即停止 | 可自行修复的问题也中断任务 | 先诊断和修复，真实阻塞才提问 |
| [gh-fix-ci:11](/Users/xiaochengcheng/.codex/skills/gh-fix-ci/SKILL.md:11) 修复计划再次审批 | 用户“修 CI”的授权未复用 | 授权范围内直接修复 |
| [figma-generate-library:33](/Users/xiaochengcheng/.codex/skills/figma-generate-library/SKILL.md:33) 分阶段/组件审批 | 已授权设计被固定检查点打断 | 自主实现和视觉核验，保留具体风险决定 |
| [security-threat-model:49](/Users/xiaochengcheng/.codex/skills/security-threat-model/SKILL.md:49) 等待反馈后才交报告 | 无回复无法交付证据明确的部分 | 用显式假设与限制完成报告 |
| 项目原第 5/9 条：分析一律走 ADL、规则只能提议 | 与本次明确要求修改 AI 配置冲突 | 配置/文档维护直接完成；产品修复保留独立评审 |
| 项目与 CLAUDE.md 重复维护测试规则 | 双份规则易漂移 | 单一主入口 |
| 原 `/loop 5m` 硬绑定 | 缺命令可能被误判为不能开工 | 保留 5 分钟检查目标，允许宿主等效机制 |
| [lark-workflow-standup-report:57](/Users/xiaochengcheng/.agents/skills/lark-workflow-standup-report/SKILL.md:57) 摘要默认 20 条 | 与“全量未完成任务”口径可能冲突 | 全量请求遵循用户全局两组分页与去重规则 |
| [lark-whiteboard-cli:163](/Users/xiaochengcheng/.agents/skills/lark-whiteboard-cli/SKILL.md:163) 非空覆盖预检 | 属于实际数据丢失边界 | 保留预检，复用已明确的同目标覆盖授权 |

完整适用方式见 [项目 Skills 规则](../.codex/skills-policy.md)。保留安全限制不等于保留通用重复审批。

## 逐入口清单

“流程覆盖”表示执行时适用项目替代规则；“按需保留”不等于递归证明不存在任何冲突。所有条目都遵循统一授权与任务边界规则。SHA-256 前 12 位用于定位本次读取版本，不包含凭据。

| # | Skill 入口 | 行数 | SHA-256 前缀 | 处置 |
|---|---|---:|---|---|
| 1 | [~/.codex/skills/adaptive/SKILL.md](/Users/xiaochengcheng/.codex/skills/adaptive/SKILL.md) | 301 | `31eac36bc6f7` | 同内容副本；优先 .agents 入口 |
| 2 | [~/.codex/skills/agent-dev-loop/SKILL.md](/Users/xiaochengcheng/.codex/skills/agent-dev-loop/SKILL.md) | 124 | `33edb2fe6d75` | 流程覆盖；保留有效工程方法 |
| 3 | [~/.codex/skills/agp-9-upgrade/SKILL.md](/Users/xiaochengcheng/.codex/skills/agp-9-upgrade/SKILL.md) | 103 | `b24e772c6067` | 同内容副本；优先 .agents 入口 |
| 4 | [~/.codex/skills/android-cli/SKILL.md](/Users/xiaochengcheng/.codex/skills/android-cli/SKILL.md) | 284 | `b79254ca7999` | 同内容副本；优先 .agents 入口 |
| 5 | [~/.codex/skills/android-intent-security/SKILL.md](/Users/xiaochengcheng/.codex/skills/android-intent-security/SKILL.md) | 547 | `02576b7430b4` | 同内容副本；优先 .agents 入口 |
| 6 | [~/.codex/skills/android-profiler/SKILL.md](/Users/xiaochengcheng/.codex/skills/android-profiler/SKILL.md) | 57 | `6385aeb41e96` | 同内容副本；优先 .agents 入口 |
| 7 | [~/.codex/skills/appfunctions/SKILL.md](/Users/xiaochengcheng/.codex/skills/appfunctions/SKILL.md) | 71 | `d41b75cb243d` | 同内容副本；优先 .agents 入口 |
| 8 | [~/.codex/skills/brainstorming/SKILL.md](/Users/xiaochengcheng/.codex/skills/brainstorming/SKILL.md) | 250 | `74edf03ea6d2` | 流程覆盖；保留有效工程方法 |
| 9 | [~/.codex/skills/camera1-to-camerax/SKILL.md](/Users/xiaochengcheng/.codex/skills/camera1-to-camerax/SKILL.md) | 270 | `e894ad6f744a` | 同内容副本；优先 .agents 入口 |
| 10 | [~/.codex/skills/camerax/SKILL.md](/Users/xiaochengcheng/.codex/skills/camerax/SKILL.md) | 132 | `9ff427e29be4` | 同内容副本；优先 .agents 入口 |
| 11 | [~/.codex/skills/debox-android-development-standards/SKILL.md](/Users/xiaochengcheng/.codex/skills/debox-android-development-standards/SKILL.md) | 26 | `dfb257b6df67` | 按需保留；适用统一授权规则 |
| 12 | [~/.codex/skills/debox-dev/SKILL.md](/Users/xiaochengcheng/.codex/skills/debox-dev/SKILL.md) | 97 | `be16787d3907` | 按需保留；适用统一授权规则 |
| 13 | [~/.codex/skills/dispatching-parallel-agents/SKILL.md](/Users/xiaochengcheng/.codex/skills/dispatching-parallel-agents/SKILL.md) | 167 | `1968923066f3` | 流程覆盖；保留有效工程方法 |
| 14 | [~/.codex/skills/display-glasses-with-jetpack-compose-glimmer/SKILL.md](/Users/xiaochengcheng/.codex/skills/display-glasses-with-jetpack-compose-glimmer/SKILL.md) | 337 | `d0e1e04a3fb2` | 同内容副本；优先 .agents 入口 |
| 15 | [~/.codex/skills/edge-to-edge/SKILL.md](/Users/xiaochengcheng/.codex/skills/edge-to-edge/SKILL.md) | 426 | `dce851187b25` | 同内容副本；优先 .agents 入口 |
| 16 | [~/.codex/skills/engage-sdk-integration/SKILL.md](/Users/xiaochengcheng/.codex/skills/engage-sdk-integration/SKILL.md) | 127 | `01863927f00d` | 同内容副本；优先 .agents 入口 |
| 17 | [~/.codex/skills/executing-plans/SKILL.md](/Users/xiaochengcheng/.codex/skills/executing-plans/SKILL.md) | 64 | `c4c3d8b628c5` | 流程覆盖；保留有效工程方法 |
| 18 | [~/.codex/skills/figma/SKILL.md](/Users/xiaochengcheng/.codex/skills/figma/SKILL.md) | 42 | `5b11f7c8d0ce` | 按需保留；适用统一授权规则 |
| 19 | [~/.codex/skills/figma-code-connect-components/SKILL.md](/Users/xiaochengcheng/.codex/skills/figma-code-connect-components/SKILL.md) | 349 | `0f19edad8aed` | 领域保留；覆盖通用确认步骤 |
| 20 | [~/.codex/skills/figma-create-design-system-rules/SKILL.md](/Users/xiaochengcheng/.codex/skills/figma-create-design-system-rules/SKILL.md) | 537 | `1f4d7e16ba53` | 按需保留；适用统一授权规则 |
| 21 | [~/.codex/skills/figma-generate-design/SKILL.md](/Users/xiaochengcheng/.codex/skills/figma-generate-design/SKILL.md) | 341 | `45d2b97103bd` | 按需保留；适用统一授权规则 |
| 22 | [~/.codex/skills/figma-generate-library/SKILL.md](/Users/xiaochengcheng/.codex/skills/figma-generate-library/SKILL.md) | 314 | `7f42103c59fc` | 领域保留；覆盖通用确认步骤 |
| 23 | [~/.codex/skills/figma-implement-design/SKILL.md](/Users/xiaochengcheng/.codex/skills/figma-implement-design/SKILL.md) | 258 | `63f3d12d4445` | 按需保留；适用统一授权规则 |
| 24 | [~/.codex/skills/figma-use/SKILL.md](/Users/xiaochengcheng/.codex/skills/figma-use/SKILL.md) | 233 | `afa9c4ae068a` | 按需保留；适用统一授权规则 |
| 25 | [~/.codex/skills/finishing-a-development-branch/SKILL.md](/Users/xiaochengcheng/.codex/skills/finishing-a-development-branch/SKILL.md) | 225 | `8db5a922b242` | 流程覆盖；保留有效工程方法 |
| 26 | [~/.codex/skills/gh-address-comments/SKILL.md](/Users/xiaochengcheng/.codex/skills/gh-address-comments/SKILL.md) | 25 | `77389eefd3fb` | 领域保留；覆盖通用确认步骤 |
| 27 | [~/.codex/skills/gh-fix-ci/SKILL.md](/Users/xiaochengcheng/.codex/skills/gh-fix-ci/SKILL.md) | 69 | `7b326b4a2f0f` | 领域保留；覆盖通用确认步骤 |
| 28 | [~/.codex/skills/imagegen/SKILL.md](/Users/xiaochengcheng/.codex/skills/imagegen/SKILL.md) | 356 | `59981d235192` | 按需保留；适用统一授权规则 |
| 29 | [~/.codex/skills/jetpack-compose-m3/SKILL.md](/Users/xiaochengcheng/.codex/skills/jetpack-compose-m3/SKILL.md) | 281 | `16eeaf9aa1c2` | 同内容副本；优先 .agents 入口 |
| 30 | [~/.codex/skills/jupyter-notebook/SKILL.md](/Users/xiaochengcheng/.codex/skills/jupyter-notebook/SKILL.md) | 107 | `62f102e8554b` | 按需保留；适用统一授权规则 |
| 31 | [~/.codex/skills/lark-mcp-smoke-test/SKILL.md](/Users/xiaochengcheng/.codex/skills/lark-mcp-smoke-test/SKILL.md) | 125 | `d6b2392b67a6` | 按需保留；全局 Lark 口径优先 |
| 32 | [~/.codex/skills/leanback-to-compose-tv-migration/SKILL.md](/Users/xiaochengcheng/.codex/skills/leanback-to-compose-tv-migration/SKILL.md) | 740 | `b8cbd8baf43a` | 同内容副本；优先 .agents 入口 |
| 33 | [~/.codex/skills/linear/SKILL.md](/Users/xiaochengcheng/.codex/skills/linear/SKILL.md) | 87 | `ce0f39c95b6c` | 按需保留；适用统一授权规则 |
| 34 | [~/.codex/skills/loopx-benchmark/SKILL.md](/Users/xiaochengcheng/.codex/skills/loopx-benchmark/SKILL.md) | 196 | `c177663c1431` | 按需保留；适用统一授权规则 |
| 35 | [~/.codex/skills/loopx-doc-registry/SKILL.md](/Users/xiaochengcheng/.codex/skills/loopx-doc-registry/SKILL.md) | 66 | `c5ad255cab5c` | 按需保留；适用统一授权规则 |
| 36 | [~/.codex/skills/loopx-pr-program/SKILL.md](/Users/xiaochengcheng/.codex/skills/loopx-pr-program/SKILL.md) | 163 | `feb0428eb9ef` | 按需保留；适用统一授权规则 |
| 37 | [~/.codex/skills/loopx-pr-review/SKILL.md](/Users/xiaochengcheng/.codex/skills/loopx-pr-review/SKILL.md) | 179 | `da5068899d1a` | 按需保留；适用统一授权规则 |
| 38 | [~/.codex/skills/loopx-project/SKILL.md](/Users/xiaochengcheng/.codex/skills/loopx-project/SKILL.md) | 1048 | `1d5de3416c86` | 按需保留；适用统一授权规则 |
| 39 | [~/.codex/skills/loopx-self-repair/SKILL.md](/Users/xiaochengcheng/.codex/skills/loopx-self-repair/SKILL.md) | 157 | `57c1f7f03695` | 按需保留；适用统一授权规则 |
| 40 | [~/.codex/skills/media3-cast-integration/SKILL.md](/Users/xiaochengcheng/.codex/skills/media3-cast-integration/SKILL.md) | 233 | `351a49c084f0` | 同内容副本；优先 .agents 入口 |
| 41 | [~/.codex/skills/migrate-xml-views-to-jetpack-compose/SKILL.md](/Users/xiaochengcheng/.codex/skills/migrate-xml-views-to-jetpack-compose/SKILL.md) | 124 | `8b8e4ca4cfd3` | 同内容副本；优先 .agents 入口 |
| 42 | [~/.codex/skills/navigation-3/SKILL.md](/Users/xiaochengcheng/.codex/skills/navigation-3/SKILL.md) | 119 | `38d660f7d898` | 同内容副本；优先 .agents 入口 |
| 43 | [~/.codex/skills/openai-docs/SKILL.md](/Users/xiaochengcheng/.codex/skills/openai-docs/SKILL.md) | 161 | `057d24b4f36d` | 领域保留；覆盖通用确认步骤 |
| 44 | [~/.codex/skills/pdf/SKILL.md](/Users/xiaochengcheng/.codex/skills/pdf/SKILL.md) | 67 | `d108cf2b3635` | 按需保留；适用统一授权规则 |
| 45 | [~/.codex/skills/perfetto-sql/SKILL.md](/Users/xiaochengcheng/.codex/skills/perfetto-sql/SKILL.md) | 142 | `13e701190c52` | 同内容副本；优先 .agents 入口 |
| 46 | [~/.codex/skills/perfetto-trace-analysis/SKILL.md](/Users/xiaochengcheng/.codex/skills/perfetto-trace-analysis/SKILL.md) | 79 | `cb1b5b16a4c7` | 同内容副本；优先 .agents 入口 |
| 47 | [~/.codex/skills/play-billing-library-version-upgrade/SKILL.md](/Users/xiaochengcheng/.codex/skills/play-billing-library-version-upgrade/SKILL.md) | 93 | `59a0cf44961f` | 同内容副本；优先 .agents 入口 |
| 48 | [~/.codex/skills/play-policy-insights/SKILL.md](/Users/xiaochengcheng/.codex/skills/play-policy-insights/SKILL.md) | 169 | `cc7b9074b746` | 同内容副本；优先 .agents 入口 |
| 49 | [~/.codex/skills/playwright/SKILL.md](/Users/xiaochengcheng/.codex/skills/playwright/SKILL.md) | 147 | `0ffaabcc8e09` | 按需保留；适用统一授权规则 |
| 50 | [~/.codex/skills/playwright-interactive/SKILL.md](/Users/xiaochengcheng/.codex/skills/playwright-interactive/SKILL.md) | 693 | `f6c1155d923e` | 按需保留；适用统一授权规则 |
| 51 | [~/.codex/skills/r8-analyzer/SKILL.md](/Users/xiaochengcheng/.codex/skills/r8-analyzer/SKILL.md) | 69 | `16e5a9831b00` | 同内容副本；优先 .agents 入口 |
| 52 | [~/.codex/skills/receiving-code-review/SKILL.md](/Users/xiaochengcheng/.codex/skills/receiving-code-review/SKILL.md) | 205 | `091df1629510` | 流程覆盖；保留有效工程方法 |
| 53 | [~/.codex/skills/requesting-code-review/SKILL.md](/Users/xiaochengcheng/.codex/skills/requesting-code-review/SKILL.md) | 95 | `d71cc01ba56d` | 流程覆盖；保留有效工程方法 |
| 54 | [~/.codex/skills/restore-credentials/SKILL.md](/Users/xiaochengcheng/.codex/skills/restore-credentials/SKILL.md) | 340 | `a8fdc3c14029` | 同内容副本；优先 .agents 入口 |
| 55 | [~/.codex/skills/screenshot/SKILL.md](/Users/xiaochengcheng/.codex/skills/screenshot/SKILL.md) | 267 | `081935a6a163` | 按需保留；适用统一授权规则 |
| 56 | [~/.codex/skills/security-best-practices/SKILL.md](/Users/xiaochengcheng/.codex/skills/security-best-practices/SKILL.md) | 86 | `7b3dae1ffc54` | 按需保留；适用统一授权规则 |
| 57 | [~/.codex/skills/security-ownership-map/SKILL.md](/Users/xiaochengcheng/.codex/skills/security-ownership-map/SKILL.md) | 206 | `f06c1a592475` | 按需保留；适用统一授权规则 |
| 58 | [~/.codex/skills/security-threat-model/SKILL.md](/Users/xiaochengcheng/.codex/skills/security-threat-model/SKILL.md) | 81 | `1283c0dd62a8` | 领域保留；覆盖通用确认步骤 |
| 59 | [~/.codex/skills/sentry/SKILL.md](/Users/xiaochengcheng/.codex/skills/sentry/SKILL.md) | 120 | `508c6f5c1005` | 按需保留；适用统一授权规则 |
| 60 | [~/.codex/skills/styles/SKILL.md](/Users/xiaochengcheng/.codex/skills/styles/SKILL.md) | 226 | `c75a42ebc818` | 同内容副本；优先 .agents 入口 |
| 61 | [~/.codex/skills/subagent-driven-development/SKILL.md](/Users/xiaochengcheng/.codex/skills/subagent-driven-development/SKILL.md) | 568 | `8dd1b8e698ed` | 流程覆盖；保留有效工程方法 |
| 62 | [~/.codex/skills/systematic-debugging/SKILL.md](/Users/xiaochengcheng/.codex/skills/systematic-debugging/SKILL.md) | 283 | `808fc5717aa8` | 流程覆盖；保留有效工程方法 |
| 63 | [~/.codex/skills/test-driven-development/SKILL.md](/Users/xiaochengcheng/.codex/skills/test-driven-development/SKILL.md) | 320 | `bf1b8216e523` | 流程覆盖；保留有效工程方法 |
| 64 | [~/.codex/skills/testing-setup/SKILL.md](/Users/xiaochengcheng/.codex/skills/testing-setup/SKILL.md) | 198 | `f459de0df64d` | 同内容副本；优先 .agents 入口 |
| 65 | [~/.codex/skills/ui-ux-pro-max/SKILL.md](/Users/xiaochengcheng/.codex/skills/ui-ux-pro-max/SKILL.md) | 214 | `ea087c341bfb` | 按需保留；适用统一授权规则 |
| 66 | [~/.codex/skills/using-git-worktrees/SKILL.md](/Users/xiaochengcheng/.codex/skills/using-git-worktrees/SKILL.md) | 167 | `8cfb86f12126` | 流程覆盖；保留有效工程方法 |
| 67 | [~/.codex/skills/using-superpowers/SKILL.md](/Users/xiaochengcheng/.codex/skills/using-superpowers/SKILL.md) | 63 | `30f2ab78e20d` | 流程覆盖；保留有效工程方法 |
| 68 | [~/.codex/skills/verification-before-completion/SKILL.md](/Users/xiaochengcheng/.codex/skills/verification-before-completion/SKILL.md) | 120 | `2befe7fc55bc` | 按需保留；适用统一授权规则 |
| 69 | [~/.codex/skills/verified-email/SKILL.md](/Users/xiaochengcheng/.codex/skills/verified-email/SKILL.md) | 422 | `855b234d520c` | 同内容副本；优先 .agents 入口 |
| 70 | [~/.codex/skills/wear-compose-m3/SKILL.md](/Users/xiaochengcheng/.codex/skills/wear-compose-m3/SKILL.md) | 354 | `fb5ea420fba9` | 同内容副本；优先 .agents 入口 |
| 71 | [~/.codex/skills/writing-daily-weekly-reports/SKILL.md](/Users/xiaochengcheng/.codex/skills/writing-daily-weekly-reports/SKILL.md) | 112 | `4e1e9c21abd0` | 按需保留；全局 Lark 口径优先 |
| 72 | [~/.codex/skills/writing-plans/SKILL.md](/Users/xiaochengcheng/.codex/skills/writing-plans/SKILL.md) | 171 | `48508f44bbfd` | 流程覆盖；保留有效工程方法 |
| 73 | [~/.codex/skills/writing-skills/SKILL.md](/Users/xiaochengcheng/.codex/skills/writing-skills/SKILL.md) | 679 | `d34db5c8aed6` | 流程覆盖；保留有效工程方法 |
| 74 | [~/.agents/skills/adaptive/SKILL.md](/Users/xiaochengcheng/.agents/skills/adaptive/SKILL.md) | 301 | `31eac36bc6f7` | 领域保留；覆盖通用确认步骤 |
| 75 | [~/.agents/skills/agp-9-upgrade/SKILL.md](/Users/xiaochengcheng/.agents/skills/agp-9-upgrade/SKILL.md) | 103 | `b24e772c6067` | 领域保留；覆盖通用确认步骤 |
| 76 | [~/.agents/skills/ai-shaped-readiness-advisor/SKILL.md](/Users/xiaochengcheng/.agents/skills/ai-shaped-readiness-advisor/SKILL.md) | 935 | `0877bad486ae` | 按需保留；适用统一授权规则 |
| 77 | [~/.agents/skills/android-cli/SKILL.md](/Users/xiaochengcheng/.agents/skills/android-cli/SKILL.md) | 284 | `b79254ca7999` | 按需保留；适用统一授权规则 |
| 78 | [~/.agents/skills/android-intent-security/SKILL.md](/Users/xiaochengcheng/.agents/skills/android-intent-security/SKILL.md) | 547 | `02576b7430b4` | 按需保留；适用统一授权规则 |
| 79 | [~/.agents/skills/android-profiler/SKILL.md](/Users/xiaochengcheng/.agents/skills/android-profiler/SKILL.md) | 57 | `6385aeb41e96` | 按需保留；适用统一授权规则 |
| 80 | [~/.agents/skills/appfunctions/SKILL.md](/Users/xiaochengcheng/.agents/skills/appfunctions/SKILL.md) | 71 | `d41b75cb243d` | 按需保留；适用统一授权规则 |
| 81 | [~/.agents/skills/camera1-to-camerax/SKILL.md](/Users/xiaochengcheng/.agents/skills/camera1-to-camerax/SKILL.md) | 270 | `e894ad6f744a` | 按需保留；适用统一授权规则 |
| 82 | [~/.agents/skills/camerax/SKILL.md](/Users/xiaochengcheng/.agents/skills/camerax/SKILL.md) | 132 | `9ff427e29be4` | 按需保留；适用统一授权规则 |
| 83 | [~/.agents/skills/context-engineering-advisor/SKILL.md](/Users/xiaochengcheng/.agents/skills/context-engineering-advisor/SKILL.md) | 775 | `d2e192e3d19d` | 按需保留；适用统一授权规则 |
| 84 | [~/.agents/skills/display-glasses-with-jetpack-compose-glimmer/SKILL.md](/Users/xiaochengcheng/.agents/skills/display-glasses-with-jetpack-compose-glimmer/SKILL.md) | 337 | `d0e1e04a3fb2` | 按需保留；适用统一授权规则 |
| 85 | [~/.agents/skills/edge-to-edge/SKILL.md](/Users/xiaochengcheng/.agents/skills/edge-to-edge/SKILL.md) | 426 | `dce851187b25` | 按需保留；适用统一授权规则 |
| 86 | [~/.agents/skills/engage-sdk-integration/SKILL.md](/Users/xiaochengcheng/.agents/skills/engage-sdk-integration/SKILL.md) | 127 | `01863927f00d` | 按需保留；适用统一授权规则 |
| 87 | [~/.agents/skills/epic-breakdown-advisor/SKILL.md](/Users/xiaochengcheng/.agents/skills/epic-breakdown-advisor/SKILL.md) | 679 | `4200d75b5c74` | 按需保留；适用统一授权规则 |
| 88 | [~/.agents/skills/epic-hypothesis/SKILL.md](/Users/xiaochengcheng/.agents/skills/epic-hypothesis/SKILL.md) | 298 | `745140f00f5c` | 按需保留；适用统一授权规则 |
| 89 | [~/.agents/skills/jetpack-compose-m3/SKILL.md](/Users/xiaochengcheng/.agents/skills/jetpack-compose-m3/SKILL.md) | 281 | `16eeaf9aa1c2` | 按需保留；适用统一授权规则 |
| 90 | [~/.agents/skills/lark-approval/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-approval/SKILL.md) | 99 | `2ac464c97029` | 按需保留；全局 Lark 口径优先 |
| 91 | [~/.agents/skills/lark-apps/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-apps/SKILL.md) | 157 | `ec93c646ddc2` | 按需保留；全局 Lark 口径优先 |
| 92 | [~/.agents/skills/lark-attendance/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-attendance/SKILL.md) | 57 | `a0ea5f3fa176` | 按需保留；全局 Lark 口径优先 |
| 93 | [~/.agents/skills/lark-base/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-base/SKILL.md) | 284 | `663f104c9393` | 按需保留；全局 Lark 口径优先 |
| 94 | [~/.agents/skills/lark-calendar/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-calendar/SKILL.md) | 243 | `9a90ed21de5f` | 按需保留；全局 Lark 口径优先 |
| 95 | [~/.agents/skills/lark-contact/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-contact/SKILL.md) | 71 | `79041173aad9` | 按需保留；全局 Lark 口径优先 |
| 96 | [~/.agents/skills/lark-doc/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-doc/SKILL.md) | 49 | `1c2aa910831a` | 按需保留；全局 Lark 口径优先 |
| 97 | [~/.agents/skills/lark-drive/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-drive/SKILL.md) | 216 | `b140cd55b62b` | 按需保留；全局 Lark 口径优先 |
| 98 | [~/.agents/skills/lark-event/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-event/SKILL.md) | 159 | `e82b31451938` | 按需保留；全局 Lark 口径优先 |
| 99 | [~/.agents/skills/lark-im/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-im/SKILL.md) | 274 | `8d272993a64b` | 按需保留；全局 Lark 口径优先 |
| 100 | [~/.agents/skills/lark-mail/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-mail/SKILL.md) | 290 | `bee1da98483c` | 按需保留；全局 Lark 口径优先 |
| 101 | [~/.agents/skills/lark-markdown/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-markdown/SKILL.md) | 70 | `dd533844109c` | 按需保留；全局 Lark 口径优先 |
| 102 | [~/.agents/skills/lark-meeting/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-meeting/SKILL.md) | 150 | `87664de707ed` | 按需保留；全局 Lark 口径优先 |
| 103 | [~/.agents/skills/lark-minutes/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-minutes/SKILL.md) | 15 | `1058e122bb7d` | 按需保留；全局 Lark 口径优先 |
| 104 | [~/.agents/skills/lark-note/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-note/SKILL.md) | 15 | `4d55ad009f74` | 按需保留；全局 Lark 口径优先 |
| 105 | [~/.agents/skills/lark-okr/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-okr/SKILL.md) | 176 | `898fc8886d9c` | 按需保留；全局 Lark 口径优先 |
| 106 | [~/.agents/skills/lark-openapi-explorer/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-openapi-explorer/SKILL.md) | 153 | `ae89debc3f80` | 按需保留；全局 Lark 口径优先 |
| 107 | [~/.agents/skills/lark-shared/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-shared/SKILL.md) | 48 | `9fa68bb1f3ce` | 按需保留；全局 Lark 口径优先 |
| 108 | [~/.agents/skills/lark-sheets/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-sheets/SKILL.md) | 252 | `09393e1be052` | 按需保留；全局 Lark 口径优先 |
| 109 | [~/.agents/skills/lark-skill-maker/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-skill-maker/SKILL.md) | 85 | `8591158289a3` | 按需保留；全局 Lark 口径优先 |
| 110 | [~/.agents/skills/lark-slides/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-slides/SKILL.md) | 317 | `1f49aad386ac` | 按需保留；全局 Lark 口径优先 |
| 111 | [~/.agents/skills/lark-task/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-task/SKILL.md) | 187 | `f69e7f684327` | 按需保留；全局 Lark 口径优先 |
| 112 | [~/.agents/skills/lark-vc/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-vc/SKILL.md) | 15 | `46b56963508d` | 按需保留；全局 Lark 口径优先 |
| 113 | [~/.agents/skills/lark-vc-agent/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-vc-agent/SKILL.md) | 15 | `ca7478c596f9` | 按需保留；全局 Lark 口径优先 |
| 114 | [~/.agents/skills/lark-whiteboard/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-whiteboard/SKILL.md) | 55 | `78a3fbffa0da` | 按需保留；全局 Lark 口径优先 |
| 115 | [~/.agents/skills/lark-whiteboard-cli/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-whiteboard-cli/SKILL.md) | 236 | `081dfaacfa84` | 按需保留；全局 Lark 口径优先 |
| 116 | [~/.agents/skills/lark-wiki/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-wiki/SKILL.md) | 119 | `d77b966ab246` | 按需保留；全局 Lark 口径优先 |
| 117 | [~/.agents/skills/lark-workflow-meeting-summary/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-workflow-meeting-summary/SKILL.md) | 129 | `2cc95a5db67b` | 按需保留；全局 Lark 口径优先 |
| 118 | [~/.agents/skills/lark-workflow-standup-report/SKILL.md](/Users/xiaochengcheng/.agents/skills/lark-workflow-standup-report/SKILL.md) | 122 | `a283de746e65` | 按需保留；全局 Lark 口径优先 |
| 119 | [~/.agents/skills/lean-ux-canvas/SKILL.md](/Users/xiaochengcheng/.agents/skills/lean-ux-canvas/SKILL.md) | 575 | `514f8debfeea` | 按需保留；适用统一授权规则 |
| 120 | [~/.agents/skills/leanback-to-compose-tv-migration/SKILL.md](/Users/xiaochengcheng/.agents/skills/leanback-to-compose-tv-migration/SKILL.md) | 740 | `b8cbd8baf43a` | 按需保留；适用统一授权规则 |
| 121 | [~/.agents/skills/media3-cast-integration/SKILL.md](/Users/xiaochengcheng/.agents/skills/media3-cast-integration/SKILL.md) | 233 | `351a49c084f0` | 按需保留；适用统一授权规则 |
| 122 | [~/.agents/skills/migrate-xml-views-to-jetpack-compose/SKILL.md](/Users/xiaochengcheng/.agents/skills/migrate-xml-views-to-jetpack-compose/SKILL.md) | 124 | `8b8e4ca4cfd3` | 领域保留；覆盖通用确认步骤 |
| 123 | [~/.agents/skills/navigation-3/SKILL.md](/Users/xiaochengcheng/.agents/skills/navigation-3/SKILL.md) | 119 | `38d660f7d898` | 按需保留；适用统一授权规则 |
| 124 | [~/.agents/skills/opportunity-solution-tree/SKILL.md](/Users/xiaochengcheng/.agents/skills/opportunity-solution-tree/SKILL.md) | 441 | `ecd7eb3f8641` | 按需保留；适用统一授权规则 |
| 125 | [~/.agents/skills/perfetto-sql/SKILL.md](/Users/xiaochengcheng/.agents/skills/perfetto-sql/SKILL.md) | 142 | `13e701190c52` | 按需保留；适用统一授权规则 |
| 126 | [~/.agents/skills/perfetto-trace-analysis/SKILL.md](/Users/xiaochengcheng/.agents/skills/perfetto-trace-analysis/SKILL.md) | 79 | `cb1b5b16a4c7` | 按需保留；适用统一授权规则 |
| 127 | [~/.agents/skills/pestel-analysis/SKILL.md](/Users/xiaochengcheng/.agents/skills/pestel-analysis/SKILL.md) | 397 | `78b9aea0238e` | 按需保留；适用统一授权规则 |
| 128 | [~/.agents/skills/play-billing-library-version-upgrade/SKILL.md](/Users/xiaochengcheng/.agents/skills/play-billing-library-version-upgrade/SKILL.md) | 93 | `59a0cf44961f` | 按需保留；适用统一授权规则 |
| 129 | [~/.agents/skills/play-policy-insights/SKILL.md](/Users/xiaochengcheng/.agents/skills/play-policy-insights/SKILL.md) | 169 | `cc7b9074b746` | 按需保留；适用统一授权规则 |
| 130 | [~/.agents/skills/pol-probe-advisor/SKILL.md](/Users/xiaochengcheng/.agents/skills/pol-probe-advisor/SKILL.md) | 506 | `beeb07725be2` | 按需保留；适用统一授权规则 |
| 131 | [~/.agents/skills/positioning-statement/SKILL.md](/Users/xiaochengcheng/.agents/skills/positioning-statement/SKILL.md) | 242 | `44d46a65fa95` | 按需保留；适用统一授权规则 |
| 132 | [~/.agents/skills/positioning-workshop/SKILL.md](/Users/xiaochengcheng/.agents/skills/positioning-workshop/SKILL.md) | 438 | `81554cbf6a1a` | 按需保留；适用统一授权规则 |
| 133 | [~/.agents/skills/prd-development/SKILL.md](/Users/xiaochengcheng/.agents/skills/prd-development/SKILL.md) | 673 | `27c34983656e` | 按需保留；适用统一授权规则 |
| 134 | [~/.agents/skills/press-release/SKILL.md](/Users/xiaochengcheng/.agents/skills/press-release/SKILL.md) | 290 | `1c972b1f2794` | 按需保留；适用统一授权规则 |
| 135 | [~/.agents/skills/prioritization-advisor/SKILL.md](/Users/xiaochengcheng/.agents/skills/prioritization-advisor/SKILL.md) | 462 | `effd2ff5c89d` | 按需保留；适用统一授权规则 |
| 136 | [~/.agents/skills/product-strategy-session/SKILL.md](/Users/xiaochengcheng/.agents/skills/product-strategy-session/SKILL.md) | 447 | `617fd60be64e` | 按需保留；适用统一授权规则 |
| 137 | [~/.agents/skills/r8-analyzer/SKILL.md](/Users/xiaochengcheng/.agents/skills/r8-analyzer/SKILL.md) | 69 | `16e5a9831b00` | 按需保留；适用统一授权规则 |
| 138 | [~/.agents/skills/recommendation-canvas/SKILL.md](/Users/xiaochengcheng/.agents/skills/recommendation-canvas/SKILL.md) | 396 | `bc06e2a209e2` | 按需保留；适用统一授权规则 |
| 139 | [~/.agents/skills/restore-credentials/SKILL.md](/Users/xiaochengcheng/.agents/skills/restore-credentials/SKILL.md) | 340 | `a8fdc3c14029` | 按需保留；适用统一授权规则 |
| 140 | [~/.agents/skills/roadmap-planning/SKILL.md](/Users/xiaochengcheng/.agents/skills/roadmap-planning/SKILL.md) | 517 | `7818444b9e30` | 按需保留；适用统一授权规则 |
| 141 | [~/.agents/skills/skill-authoring-workflow/SKILL.md](/Users/xiaochengcheng/.agents/skills/skill-authoring-workflow/SKILL.md) | 198 | `e4eaf4efaf4e` | 按需保留；适用统一授权规则 |
| 142 | [~/.agents/skills/styles/SKILL.md](/Users/xiaochengcheng/.agents/skills/styles/SKILL.md) | 226 | `c75a42ebc818` | 领域保留；覆盖通用确认步骤 |
| 143 | [~/.agents/skills/testing-setup/SKILL.md](/Users/xiaochengcheng/.agents/skills/testing-setup/SKILL.md) | 198 | `f459de0df64d` | 领域保留；覆盖通用确认步骤 |
| 144 | [~/.agents/skills/verified-email/SKILL.md](/Users/xiaochengcheng/.agents/skills/verified-email/SKILL.md) | 422 | `855b234d520c` | 按需保留；适用统一授权规则 |
| 145 | [~/.agents/skills/wear-compose-m3/SKILL.md](/Users/xiaochengcheng/.agents/skills/wear-compose-m3/SKILL.md) | 354 | `fb5ea420fba9` | 按需保留；适用统一授权规则 |
| 146 | [~/.codex/skills/.system/imagegen/SKILL.md](/Users/xiaochengcheng/.codex/skills/.system/imagegen/SKILL.md) | 315 | `681ddb4ad6d0` | 按需保留；适用统一授权规则 |
| 147 | [~/.codex/skills/.system/openai-docs/SKILL.md](/Users/xiaochengcheng/.codex/skills/.system/openai-docs/SKILL.md) | 38 | `7cb8fa1b2a0c` | 领域保留；覆盖通用确认步骤 |
| 148 | [~/.codex/skills/.system/plugin-creator/SKILL.md](/Users/xiaochengcheng/.codex/skills/.system/plugin-creator/SKILL.md) | 249 | `71b95b821964` | 按需保留；适用统一授权规则 |
| 149 | [~/.codex/skills/.system/skill-creator/SKILL.md](/Users/xiaochengcheng/.codex/skills/.system/skill-creator/SKILL.md) | 229 | `6656e5475563` | 按需保留；适用统一授权规则 |
| 150 | [~/.codex/skills/.system/skill-installer/SKILL.md](/Users/xiaochengcheng/.codex/skills/.system/skill-installer/SKILL.md) | 58 | `d68b77e5bbb3` | 按需保留；适用统一授权规则 |
| 151 | [~/.codex/plugins/cache/openai-bundled/record-and-replay/1.0.1000926/skills/record-and-replay/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-bundled/record-and-replay/1.0.1000926/skills/record-and-replay/SKILL.md) | 43 | `9e25a9c0c7c4` | 按需保留；工具/外部操作边界有效 |
| 152 | [~/.codex/plugins/cache/openai-bundled/visualize/1.0.29/skills/visualize/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-bundled/visualize/1.0.29/skills/visualize/SKILL.md) | 514 | `be82c4e573ff` | 按需保留；工具/外部操作边界有效 |
| 153 | [~/.codex/plugins/cache/openai-bundled/sites/0.1.57/skills/sites-building/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-bundled/sites/0.1.57/skills/sites-building/SKILL.md) | 233 | `7d69bd668238` | 按需保留；工具/外部操作边界有效 |
| 154 | [~/.codex/plugins/cache/openai-bundled/sites/0.1.57/skills/sites-hosting/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-bundled/sites/0.1.57/skills/sites-hosting/SKILL.md) | 51 | `a94792641384` | 按需保留；工具/外部操作边界有效 |
| 155 | [~/.codex/plugins/cache/openai-primary-runtime/documents/26.905.11957/skills/documents/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-primary-runtime/documents/26.905.11957/skills/documents/SKILL.md) | 534 | `c6ad99c5c601` | 按需保留；工具/外部操作边界有效 |
| 156 | [~/.codex/plugins/cache/openai-primary-runtime/pdf/26.905.11957/skills/pdf/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-primary-runtime/pdf/26.905.11957/skills/pdf/SKILL.md) | 150 | `afc4472ec4d6` | 按需保留；工具/外部操作边界有效 |
| 157 | [~/.codex/plugins/cache/openai-primary-runtime/presentations/26.905.11957/skills/presentations/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-primary-runtime/presentations/26.905.11957/skills/presentations/SKILL.md) | 226 | `4b40a55cd774` | 按需保留；工具/外部操作边界有效 |
| 158 | [~/.codex/plugins/cache/openai-primary-runtime/spreadsheets/26.905.11957/skills/spreadsheets/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-primary-runtime/spreadsheets/26.905.11957/skills/spreadsheets/SKILL.md) | 445 | `d5d9e4b18635` | 按需保留；工具/外部操作边界有效 |
| 159 | [~/.codex/plugins/cache/openai-primary-runtime/spreadsheets/26.905.11957/skills/excel-live-control/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-primary-runtime/spreadsheets/26.905.11957/skills/excel-live-control/SKILL.md) | 512 | `cdc929cabe81` | 按需保留；工具/外部操作边界有效 |
| 160 | [~/.codex/plugins/cache/openai-primary-runtime/template-creator/26.905.11957/skills/template-creator/SKILL.md](/Users/xiaochengcheng/.codex/plugins/cache/openai-primary-runtime/template-creator/26.905.11957/skills/template-creator/SKILL.md) | 206 | `f1d14fd2847a` | 按需保留；工具/外部操作边界有效 |

## 验证与生效边界

- TOML 解析通过；已有两个 MCP 配置保持原值，仅新增项目附加提示词。
- 经原有 `~/.local/bin/codex` 倒计时入口启动临时 app-server，调用只读 `config/read`：有效配置中的 `developer_instructions` 与项目文件完全一致。检查后已结束临时进程，没有启动模型任务。
- 5 个交付文件均以“GPT-6 适配变更说明”开头；引用路径可达；159 个原始 Skill 入口内容指纹未变化；agent-dev-loop 在执行期间被外部操作更新，已重新读取并将清单指纹更新到复核版本（此次更新非本任务写入）。新版已增加授权复用与普通维护豁免，与项目规则兼容。
- `git diff --check` 对本次已有文件通过；新增文档另行检查空白和链接。测试 Phase 0–6 内容及钱包危险操作条款保持原文。
- 当前全局 AGENTS.md 与项目 AGENTS.md 合计 28,802 字节，未超出官方默认 32 KiB 指令发现上限；Skills 规则按需读取，审计清单不自动加载。
- 本次为配置与文档变更，没有运行 Android 构建/真机测试，也没有启动模型行为评估；不将静态检查表述为已证明自主性提升。

项目规则不会即时移除当前会话已经注入的 Skills 目录；新会话会重新加载，平台更高层级规则始终有效。Skills 原文件和启停状态未改动，本次通过项目规则覆盖冲突。
