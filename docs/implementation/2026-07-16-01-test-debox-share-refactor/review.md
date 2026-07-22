# Review — 测试 debox feat/share-refactor（分享弹窗 V2 + RN 图片分享）

> ③ 过程 & 问题。当前状态以 `index.md` 为准；本文件存各轮 impl-review 与修复**过程历史**（per-round VERDICT 是快照，非当前状态）。
> 每轮必有 `VERDICT`（`PASS | FAIL | PASS_WITH_ACCEPTED_RISK`）；正式问题按 Critical / Important + confidence ≥ 80。
> Codex 调用流水不在此记流水账——`index.md` Metadata 的 Codex Calls 计数即可。

## Implementation Review Log（per-round，含 VERDICT 快照）

### Implementation Review Round 1 — YYYY-MM-DD

```text
VERDICT: PASS | FAIL | PASS_WITH_ACCEPTED_RISK
```

Findings 与处置（一条一行）：

| 等级 | 维度 | 文件:行 | 置信 | 描述 | Claude 处置（修复/反驳/接受风险） | 验证 |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |


### impl-review Round 1 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Review Findings

VERDICT: FAIL

### Critical (block, must fix)
- [Bugs/逻辑] [results.md:53] [96] 汇总统计与明细不一致：按独立用例计应为 PASS 26 / BLOCKED 9 / 观察项 2 / 未执行 1；当前“PASS 20 / BLOCKED 8”还遗漏了已在环境快照执行的 TC-S-001 -> 补列 TC-S-001，并按统一口径重新统计全部 38 条用例。

### Important (warn, should fix or record)
- [Plan alignment] [results.md:39] [88] EVENT/DAPP 等入口未达项未附计划 Δ3 要求的尝试入口清单和截图引用；SPACE 仅称“本轮未建房”，也不是外部条件造成的阻塞 -> 为每条 BLOCKED 补充已尝试入口、证据路径和可操作复测条件；无法证明实际探索的应改为 NOT_RUN。
- [Test coverage] [results.md:49] [89] TC-T-003 明确未执行，但 Accepted Plan 要求 P0→P1→P2 全量执行；仅记录 Follow-up，没有未执行原因和复测条件 -> 执行该用例，或显式记录 NOT_RUN/BLOCKED、原因、owner 和复测条件。
- [Regression risk] [results.md:50] [87] TC-T-004 的原始合同覆盖 11 类弹窗，但实际仅观察到 5 类；将聚合用例整体标 PASS 会掩盖另外 6 类无 V1/V2 证据 -> 改为“已执行 5 类范围内 PASS，其余 BLOCKED”，不得宣称 11 类老弹窗路径已闭合。
- [Code quality] [results.md:58] [86] “5 种类型二维码/复制链接/保存图片/发送/关闭全链路正常”超出逐项证据：复制仅见 MOMENT/QUOTES，保存仅见 FRIEND，发送仅见 GROUP -> 将结论限定为各能力分别在已注明类型验证通过，避免表达为每种类型均完成全链路。
- [Plan alignment] [implementation.md:20] [85] toast+剪贴板气泡替代 Δ8 的基线写入/读取，虽能证明触发复制并观察链接，但不能证明剪贴板值相对基线发生变化；“未削弱证据强度”的声明不成立 -> 在偏离日志中登记环境限制，并将复制结论标为降级 UI 证据或补充等价的可重复读取证据。

### Accepted Risk Candidates
- [Security] [results.md:80] [89] RN HTTPS 图片下载仅校验初始 URL，Glide 重定向后未逐跳复检，存在 SSRF 暴露面；当前仅为提名，未代 debox 接受 -> 正式接受需 debox owner=Kai 确认，记录 reason=新增下载链路仅校验初始 URL、follow-up=逐跳同源/安全地址复检、expiry≤2026-09-30。
- [Security] [results.md:86] [89] RN 图片下载没有 contentLength/maxBytes 上限，可能造成资源耗尽；当前仅为提名 -> 正式接受需 debox owner=Kai 确认，记录 reason=仅有超时兜底、follow-up=增加下载大小上限及回归单测、expiry=2026-09-30。

### Verification Gaps
- 11 种 ShareTarget 中仅 5 种取得 L1 模拟器真包 UI 证据，EVENT/SPACE/SPACE_FINISH/DAPP/SWAP/WEB 未闭合。
- RN 图片分享没有业务调用入口，TC-F-201/202 未获得真实 LiveEventBus/Glide 集成证据。
- 真机门禁已正确标为 NOT_RUN，没有虚假闭合。
- Fragment 类名探针属于 Δ2 明确授权的兜底手段，本身不构成未声明偏离。
- GROUP 仅发送到“担保师测试群”，FRIEND 未发送，危险操作边界符合计划。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-impl-review-R1.txt

### impl-review Round 2 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Review Findings

VERDICT: FAIL

### Critical (block, must fix)
None。

### Important (warn, should fix or record)
- [Plan alignment/计划对齐] [docs/testing/runs/2026-07-16-分享弹窗V2重构/results.md:结果汇总表] [88] Accepted Plan 要求覆盖 11 种分享类型并为入口不可达提供 BLOCKED 证据，但 EVENT、SPACE、SPACE_FINISH、DAPP 因时间不足被记为 NOT_RUN，且未实际探索、无入口证据；当前仅取得 5/11 类型的 L1 UI 证据，计划内执行仍未完成 -> 补做入口探索；可达则执行，不可达则按 L-003 记录 BLOCKED、原因、证据及复测条件。
- [Test coverage/测试覆盖] [docs/testing/runs/2026-07-16-分享弹窗V2重构/results.md:TC-T-003] [82] 渠道快速重复点击去重是计划内独立稳定性场景，但仅执行了两次单击并将压力场景记为 NOT_RUN，未验证重复发送、重复保存或重复回调风险 -> 按既定复测条件完成快速双击压力验证并记录可审计证据。

### Accepted Risk Candidates
- [Security/安全] [docs/testing/runs/2026-07-16-分享弹窗V2重构/results.md:RISK-A2] [89] Glide 下载仅校验初始 URL，重定向目标未逐跳复检，存在 SSRF 风险；当前明确为提名、未代 debox 接受，处置与 Accepted Plan 一致 -> owner=Kai / reason=新增 RN HTTPS 下载面未覆盖重定向目标校验 / follow-up=改为同源逐跳复检并补回归测试 / expiry≤2026-09-30。
- [Security/安全] [docs/testing/runs/2026-07-16-分享弹窗V2重构/results.md:RISK-B] [87] RN 图片下载没有响应体大小上限，仅靠超时无法防止磁盘或内存耗尽；当前明确为提名、未代 debox 接受，处置与 Accepted Plan 一致 -> owner=Kai / reason=当前下载链路仅有超时兜底 / follow-up=增加下载大小上限及对应单测 / expiry=2026-09-30。

### Verification Gaps
- EVENT、SPACE、SPACE_FINISH、DAPP 四类尚无入口探索证据；SWAP、WEB、RN 图片链路仍为 BLOCKED。
- FRIEND 发送按计划保持 BLOCKED，未向昵称匹配对象发送；GROUP 仅发送至“担保师测试群”，危险操作边界合规。
- Fragment 类名探针替代 view-id 能直接区分 V2/V1，且报告限定了覆盖范围，不构成证据削弱；toast 与系统剪贴板气泡共同替代 clipboard 命令，足以证明已执行复制及可见 URL，但不证明剪贴板底层值的完整精确读取。
- 真机门禁已明确标注 NOT_RUN，单测与 L1 模拟器真包 UI 分层清楚，未发现以单测冒充 UI 证据或将 BLOCKED/NOT_RUN 粉饰为 PASS。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-impl-review-R2.txt

### impl-review Round 3 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Review Findings

VERDICT: PASS_WITH_ACCEPTED_RISK

### Critical (block, must fix)
None。

### Important (warn, should fix or record)
None。

### Accepted Risk Candidates
- [Security/安全] [results.md:RISK-A2] [89] Glide 下载仅校验初始 URL，3xx 重定向后未逐跳复检，存在 SSRF 风险；已完整记录 owner=Kai / reason=新增 RN HTTPS 下载面 / follow-up=同源逐跳复检 / expiry≤2026-09-30，且明确仅作提名、不代 debox 接受。
- [Security/安全] [results.md:RISK-B] [87] RN 图片下载缺少响应体大小上限，可能造成磁盘或内存资源耗尽；已完整记录 owner=Kai / reason=当前仅有超时兜底 / follow-up=增加下载上限及单测 / expiry=2026-09-30，且明确仅作提名。

### Verification Gaps
- L3 两台真机门禁均为 NOT_RUN；记录已明确限定结论为 L1 模拟器增量证据，未虚假闭合真机验收。
- EVENT、DAPP、SWAP、WEB 四类未取得 V2 UI 证据；均以 BLOCKED 记录原因、owner 和复测条件，未粉饰为 PASS。
- FRIEND 发送及 RN 图片分享集成路径仍为 BLOCKED；保存图片的 MediaStore 证据已单独限定为 FRIEND 用例中的局部能力验证。
- Fragment 类名探针比共享 view-id 更能直接区分 V2/V1，不构成证据削弱；toast 与剪贴板气泡能证明用户可见的复制结果，但 QUOTES 的完整剪贴板载荷未获得机器读取证据。
- 发送仅发生于“担保师测试群”，FRIEND 未发送；未发现越过危险操作边界的记录。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-impl-review-R3.txt
## Accepted Risks Log

| Finding | Owner | Reason | Follow-up | Expiry |
| --- | --- | --- | --- | --- |
| RISK-A2：Glide 图片下载 3xx 重定向后无逐跳 SSRF 复检（新增 RN HTTPS 下载暴露面） | Kai（提名，待 debox 承接） | SSRF 校验仅作用于原始 URL | Glide 下载改经同源逐跳复检，与「抓取层替换」独立任务同批 | ≤2026-09-30 |
| RISK-B：RN 图片下载无响应体大小上限（磁盘/内存耗尽风险） | Kai（提名，待 debox 承接） | 当前仅超时兜底 | 补下载大小上限 + 回归单测 | 2026-09-30 |
| RISK-A1（承接核验）：SSRF:TextCrawler 重定向/DNS rebinding 后目标不复检 | Kai | debox `2026-07-15-01` 已登记，老实现既有缺口等价迁移不新增暴露面 | OkHttp 禁自动重定向 + ≤3 跳逐跳 isSafeUrl + 自定义 Dns | 2026-09-30（未过期，承接有效） |

## Verification

- 命令与结果：分支单测 197/197（4 模块 25 类，`--offline`）；autotest 框架 `compileReleaseKotlin`+`test` PASS；L1 UI 7/11 类型 V2 弹窗 PASS + 发送/旋转/断网/去重 PASS，4 类 BLOCKED（有探索证据），0 FAIL/0 崩溃（PID 全程 20628）。截图 20 张存 `runs/2026-07-16-分享弹窗V2重构/screenshots/`。
- 未完成验证移入 index.md Follow-up。
- Secret 扫描已完成、未记录命中行：是（adl-finalize 门禁）。

- 2026-07-16 · verify: 分支单测 197/197 全绿（4 模块 25 类，offline）；autotest 框架编译+单测 PASS
- 2026-07-16 · verify: L1 UI：GROUP/FRIEND/MOMENT/QUOTES/QUOTES_DETAILS 5 类型 V2 弹窗 PASS + 发送到担保师测试群 PASS + 旋转/断网 PASS + V1 老弹窗 0 命中；6 类型 BLOCKED（入口未达）；0 FAIL/0 崩溃
## Final Review

```text
VERDICT: PASS_WITH_ACCEPTED_RISK
```

Codex final assessment: impl-review R1 FAIL（1 Critical 统计口径 + 5 Important 如实性）→ R2 FAIL（补测诉求：EVENT/SPACE/SPACE_FINISH/DAPP 应实际探索、TC-T-003 应执行）→ **R3 PASS_WITH_ACCEPTED_RISK**（无 Critical、无 Important）。修正过程：补 TC-S-001；实际补测 SPACE/SPACE_FINISH（建语音房→PASS）、EVENT（活动 Tab 空态 BLOCKED）、DAPP（详情弹层无入口 BLOCKED）、TC-T-003（快速双击去重 PASS）；证据措辞按类型限定；剪贴板降级证据登记偏离日志。
Unresolved risks: RISK-A2 / RISK-B（安全缺口提名，待 debox 承接，expiry≤2026-09-30）；4 类型（EVENT/DAPP/SWAP/WEB）UI 未闭合 + 真机门禁 NOT_RUN，均登记 Follow-up。

## Retro（教训分流）

- **项目级（→ 已在 `docs/lessons.md` 体系内，本轮复用未新增）**：证据分层如实标注（L-004）、无静默缺口（L-003）在本轮 impl-review 中被 Codex 反复校验，均已遵循，无新增可泛化教训。
- **候选教训 [candidate]（→ 项目 `docs/lessons.md`）**：测试执行任务的「入口不可达」必须**实际探索到证据**（空态截图/无入口截图）才可记 BLOCKED——「时间不足未探索」只能记 NOT_RUN，且 impl-review 会要求补做。二者不可混用（本轮 R2 因把未探索类型记 NOT_RUN 被判 Important，补测后转 PASS）。已追加至 `docs/lessons.md` L-005 [candidate]。
