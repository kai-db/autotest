# debox 新需求深度测试：ZEGO 语音房会话恢复（f09ca3f20a）+ 字号档位（eaada99344）

> **canonical 入口**（T2 标准任务）。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。
> 反杂硬规则（0.1.3）：不复述、定稿无空壳（用不上的节直接删）、不 commit 二进制。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-11-01-test-debox-zego-recovery |
| Tier | T2 |
| Created / Last Active | 2026-07-11 / 2026-07-11 |
| Status | draft / planning / implementing / done / blocked |
| Risk Level | low / medium / high |
| Codex Calls · High-Cost Modes | 0 · none |
| History | （复用/改名时记录历次目录名与轮次日期；首轮写 None） |

## 导航

- ① 计划 / 方案：[plan.md](./plan.md) · ② 实现：[implementation.md](./implementation.md) · ③ 过程 & 问题：[review.md](./review.md)
- 开工前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`），相关条目（L?）：

## Final Status / VERDICT / Outcome（唯一权威）

- Current Status: 深度测试完成（逻辑层）。Zego 声称属实+45单测+静态在包+smoke 通过；独立核码新发现 Z-1(潜在致命线程假设)+Z-2/Z-3(集成缺口)。行为 reconnect 黑盒不可达（需活跃语音房+2账号+RTC）。
- Final VERDICT: PASS_WITH_ACCEPTED_RISK（逻辑层通过；Z-1~3 集成风险待用户决定修/真机验；行为 reconnect 移交真实语音房环境）
- Summary（latest / final 合一，只写一处）:
- Follow-up（含未完成验证）:

## Completion Checklist（核心 5 项；完整清单见主协议「最终完成定义」）

- [ ] 原始需求完成。
- [ ] 无未处理 Critical / Important finding（见 review.md）。
- [ ] 验证通过或缺失原因已说明（见 review.md；未完成的验证已移入 Follow-up）。
- [ ] 未写入任何 secrets。
- [ ] Retro 已分流（`[candidate]` 进账本）或确认无教训。
