# 制作 AutoTest 项目技术分享 PPT（T1 轻任务 · 单文件记录）

> lite 模板（核心原则 0.1.1）。整个任务只此一个 `index.md`；当前 Status / final VERDICT 在本文件权威。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-06-01-create-techshare-ppt |
| Tier | T1（文档/演示产物任务，不改框架代码） |
| Created / Last Active | 2026-07-05 / 2026-07-06 |
| Status · Final VERDICT | done · PASS（Round 2） |
| Codex Calls | 5（Round 1：plan R1 FAIL / plan R2 PASS / impl R1 PASS；Round 2：plan R3 PASS / impl R2 PASS） |
| History | Round 1（2026-07-05）：28 页 PPT + 大纲交付，done · PASS |

## 背景与方案要点

- 需求（一两句）：深度分析 autotest 项目，产出一份**技术分享 PPT**（由浅到深）：项目是做什么的、优势、实际开发中怎么用、debox HTTPDNS 几次测试案例、流程与实现原理。
- 已读两层账本，相关条目：全局 L13/L14/L16/L20/L23/L25/L26（codex exec 驱动姿势、计划评审声明 NO CODE YET、stdin/输出捕获、git add -N）；L27（文档集回写一致性）。项目 L-001~L-003（与本任务无直接约束，PPT 内容会引用 L-002 作为案例素材）。
- 方案要点：
  - **素材来源（不新造事实，全部取自仓内文档）**：
    - 底稿：`docs/10-项目技术分享.md`（v1.8.2 分享底稿，已含 PPT 骨架 §8）
    - 架构：`docs/01-架构设计.md`、`docs/09-AI驱动测试机制.md`、`CLAUDE.md`
    - 使用方式：`docs/testing/USAGE-使用教程.md`（模式 A/B、A+B 节奏、设备阶梯、命令）
    - 案例：`docs/testing/runs/` 下 5 个 HTTPDNS/域名 run（06-12 阿里云 HTTPDNS、06-13 域名动态切换、06-23 深度测试、06-25 自愈鲁棒性、07-01 回环 bogon 兜底），由 Explore agent 汇总为演进故事线
  - **产出物（两件）**：
    1. `docs/share/2026-07-05-AutoTest技术分享-大纲.md` — 逐页大纲+讲稿要点（纯文本，供 Codex 评审与后续维护）
    2. `docs/share/2026-07-05-AutoTest技术分享.pptx` — 实际 PPT（16:9 中文，用 document-skills:pptx 生成；二进制不进任务目录，放 docs/share/）
  - **PPT 结构（由浅到深，约 26±4 页）**：
    1. 开场：UI 自动化两难（纯脚本 vs 纯 AI）→ 是什么（一句话定位 + 双相架构图 + 一组数字）
    2. 优势：与 Appium/Maestro/Midscene 对比 + 六大机制「AI 在环 vs CI 执行」焊接表 + 开源借鉴全景（Kaspresso 骨架 + 5 器官）
    3. 实际怎么用：模式 A/B + A+B 结合节奏 + 运行命令 + 铁律/危险红线 + 监工 6-Phase + 设备阶梯
    4. 实现原理深挖（4-5 个模块）：三级自愈定位（打分公式）、固化桥（三层固化阶梯）、双链拦截器、统计重试（n=⌈ln(1-T)/ln(1-p)⌉）、DangerousOpsGuard/约束即代码
    5. HTTPDNS 案例：5 次测试演进时间线 + 2-3 页关键 run 深挖（配置格式 Critical、生产域名漏配、回环 bogon 兜底）+ 案例洞察
    6. 收尾：留痕不静默哲学 + 三论点 + Q&A 备料
  - **事实溯源约束（plan R1 修订，v2）**：
    - 大纲**每页带「来源」字段**（仓内文件+章节），无仓内来源的事实性断言不得出现。
    - 竞品对比页：只采用 `docs/10` §0（两难表述）与 §9.2（Q&A 对比要点）中**已有的定位性表述**，不新造 Appium/Maestro/Midscene 的能力断言。
    - 开源借鉴页：`docs/10` §2 表原样搬运（每项均有源码注释实证的表述）。
    - 「一组数字」：`docs/10` §0 + `docs/01` §8（71 文件 / 19 模块 / ~5846 行 / 323 单测 / 68 测试文件 / v1.8.2）。
    - HTTPDNS 案例 **claim 级溯源**：每条结论标注 run 目录+文件；「记录事实」与「分享者归纳/洞察」显式分开（洞察页标「归纳」）；Explore agent 汇总仅作导航索引，写入大纲前逐条与 run 原始文件核对。
  - **验证方式**：pptx 生成后提取全部文本与大纲比对一致；抽查 3-5 页渲染截图无溢出/乱版；数字类断言（用例数/请求数/行数）逐条溯源到仓内文档。
  - **评审方式**：plan review 评大纲结构与覆盖度（声明 NO PPT WRITTEN YET）；impl review 评 `大纲.md` + pptx 提取文本（新文件先 `git add -N`）。

## Accepted Plan（基线）

- 基线 = 上节方案要点 v2（含事实溯源约束节）+ 差异：None
- Accepted at / Decision: 2026-07-05 · plan R2 VERDICT: PASS（R1 两条 Important 经 v2 修订后 R2 零 findings）
- Deviation Policy: 偏离高风险面（安全 / 公开 API / 数据结构 / 依赖 / 部署）必须先 consult Codex。

## 改动摘要

- 实际改动（与基线一致，均为新增文件，不改框架代码）：
  - `docs/share/2026-07-05-AutoTest技术分享-大纲.md` — 28 页逐页大纲（每页带来源字段；头部声明「幻灯片为大纲凝练版，数字/结论以大纲溯源为准」）
  - `docs/share/2026-07-05-AutoTest技术分享.pptx` — 28 页 16:9 中文 PPT（pptxgenjs 生成；深蓝+青绿配色；生成脚本在 session scratchpad，未入库）
  - 与基线的差异：页数取 28（基线约 26±4 内）；P24 标题在视觉修复中缩短为「案例② 运维依赖层 ＆ 加固反逼出回归」（大纲已同步）
- 验证结果：
  - 内容 QA：python-pptx 提取 28 页全文，关键数字抽查全部与素材一致（5846/323/67 条/8 轮/26 条/81 自动化/100:4→102:14/ttl 600000/n 公式/0.75/0.85/24h TTL/50 次窗口/PASS 35/400→101）
  - 视觉 QA：3 轮子代理逐页读渲染图（LibreOffice→PDF→JPG）。R1 发现 2 高（封面对比度、目录页越界）+ 多处中文拆词；R2 复核 6/8 修复到位、新查 15-28 页发现 5 高（标题压眉、卡片越界、公式拆行、文字溢出、卡片相触）；R3 复核 10/13 通过，剩 3+2 处拆词定向修复后本人读图确认全部干净
  - HTTPDNS 案例素材：Explore agent 通读 5 个 run 目录 10 个文件产出带来源引用的结构化摘要，写入大纲前逐条核对

## 评审记录（每轮一行）

| 日期 | 阶段/轮次 | VERDICT | findings 处置 |
| --- | --- | --- | --- |
| 2026-07-05 | plan R1 | FAIL | Important-85 事实断言未逐项绑定仓内来源 → v2 增加逐页「来源」字段+竞品/借鉴/数字来源锁定；Important-82 案例叙事缺 claim 级溯源 → v2 增加案例 claim 溯源+事实/归纳分离约束 |
| 2026-07-05 | plan R2 | PASS | None（v2 修订确认充分，Questions 已答） |
| 2026-07-06 | 视觉 QA R1-R3（子代理，非 Codex） | — | 7 高 + 多处中问题（对比度/越界/公式拆行/溢出/中文拆词）全部修复并复核通过 |
| 2026-07-06 | impl R1 | PASS | None（Critical/Important/Risk/Gaps 四节全空） |
| 2026-07-06 | plan R3（Round 2 压缩案例） | PASS | None（Proposal v1 一次通过，零 findings） |
| 2026-07-06 | impl R2（Round 2 压缩案例） | PASS | None（Critical/Important/Risk/Gaps 四节全空） |

## Accepted Risks

None

## 结论与 Follow-up

- 结论：28 页技术分享 PPT 与逐页大纲已交付至 `docs/share/`。内容全部溯源仓内文档（大纲每页带来源字段），HTTPDNS 五次测试整理为「接入验证→深度实测→主动加固→真实工单兜底」演进故事线（claim 级溯源、事实与归纳分离）。plan 两轮（R1 FAIL→v2 修订→R2 PASS）、视觉 QA 三轮、impl review 一轮 PASS 零 findings。
- Follow-up：PPT 生成脚本（pptxgenjs）在 session scratchpad，未入库；如需迭代 PPT 可基于大纲重生成。渲染 QA 使用 LibreOffice 字体替换，真机演示请用 PowerPoint/Keynote（PingFang SC）。
- Retro：已向全局 `lessons-global.md` 追加 `[candidate]` 一条（pptxgenjs 中文 PPT 的 CJK 拆词断行 QA 方法）。

---

## Round 2（2026-07-06）：压缩案例章节

### 需求

用户反馈「案例有点啰嗦」。指第 6 节实战案例（P22–P26 共 5 页）：占全 deck 18%，且每页 bullet 密度过高（塞满 67 条/8 轮、102:14、ttl 600000 等报告级数字），像测试报告不像分享页。

### 方案要点（Round 2 Proposal v1）

- **压缩策略：删 1 页 + 全章逐页减密度**（5 页 → 4 页，章节文字量目标 ≈ 减 40-50%）：
  1. **P22 时间线总览：保留**（案例骨架，一页表本身不啰嗦）。微调：「标志性发现」列去掉括号内实现细节，每格一短句；「规模/轮次」列简化（如「67 条 · 8 轮」→「67 条用例」，轮次信息对听众无增量）。
  2. **P23 案例①（Run1 BUG-001）：保留但减半**。砍「真机手法」卡片的 4 条 bullet → 1 行带过（手法细节移讲稿备注）；「闭环」卡片的长串数字流水（62/0、7→19、8 个请求 200）压成一句定性结论 + 1 个关键数字；保留故事主线：全绿掩盖 → 拿真实线上配置核对 → Critical + 底部洞察条。
  3. **P24 案例②（Run3+Run4）：整页删除**。理由：两个 run 的标志性发现已在 P22 时间线各占一行，其洞察（外部运维层/状态一致性层）在洞察页五层递进中完整保留——本页是三页深挖中信息增量最低的一页。
  4. **P25 案例③（Run5 工单）：保留但减密**。保留工单红条（最有共鸣的开场）+ 4 Track 排查链（叙事亮点）；砍「修复 4 项 + 注入手法升级」卡片的实现细节（P0-A/DNAT :53 等 → 一行定性）；「结果」卡片压成一句。
  5. **P26 洞察页：保留骨架、减右侧**。五层递进阶梯保留（归纳核心，Run3/Run4 的洞察在此兜住）；右侧「能力同步升级」卡片删除（内容并入讲稿）；底部方法论主线 7 步 → 4 步（映射→分层推进→真实配置核对→agent-dev-loop 闭环）。
- **联动改动**：全 deck 28 页 → 27 页；案例章节后续页（原 P27/P28）自动顺移，页面内容不变；目录页 P2 的章节列表不含页码，无需改。
- **产物与工具**：改 `docs/share/2026-07-05-AutoTest技术分享-大纲.md`（对应节同步重写，保留每页「来源」字段与事实溯源约束——只删内容不新增事实断言，无新溯源负担）；PPT 用 Round 1 留存的 pptxgenjs 脚本（scratchpad `gen-ppt.js`，node_modules 完好）修改后整体重生成，覆盖 `docs/share/2026-07-05-AutoTest技术分享.pptx`（文件名不变，含日期前缀是创建日，沿用）。
- **验证方式**：python-pptx 提取全文与新大纲比对；渲染（LibreOffice→JPG）逐页检查改动的 4 页 + 顺移的 2 页无溢出/拆词回归；确认删除的内容不产生断链（时间线/洞察页仍覆盖 Run3/Run4 结论）。
- **评审方式**：plan review 评本节（NO EDITS YET）；impl review 评大纲 diff + 新 pptx 提取文本。

### Round 2 Accepted Plan（基线）

- 基线 = 上节 Proposal v1，无差异。
- Accepted at / Decision: 2026-07-06 · plan R3 VERDICT: PASS（零 findings）
- Deviation Policy: 沿用 Round 1（偏离高风险面先 consult Codex）。

### Round 2 改动摘要与验证

- 实际改动（与基线一致）：
  - `docs/share/2026-07-05-AutoTest技术分享-大纲.md` — 案例节重写为 P22-P25（删原 P24 案例②运维依赖层页，Run5 升为案例②）；被删/移出内容在大纲中标注去向（时间线/洞察页/讲稿备注）；头部结构行与修订说明更新；后续页 P27/P28 → P26/P27
  - `docs/share/2026-07-05-AutoTest技术分享.pptx` — 27 页重生成覆盖（沿用 Round 1 pptxgenjs 脚本；本轮脚本副本在本 session scratchpad）
- 验证：
  - 2026-07-06 · verify: 内容 QA：python-pptx 提取 27 页确认；P22-25 文本与新大纲一致，P26/P27 顺移内容不变
  - 2026-07-06 · verify: 视觉 QA：LibreOffice→PDF→JPG 渲染 P22-P27 六页逐页读图，无溢出/无拆词/页码正确
- 与基线差异：None（删页/减密度/页数均按基线执行）

### Round 2 结论

- 案例章节 5 页 → 4 页，页面文字量约减 45%：时间线表去轮次与括号细节；案例①（Run1）删手法卡片、闭环流水压缩；原案例②整页删除（要点由时间线与洞察页兜住）；案例②（Run5）修复/结果并为一条；洞察页删右侧卡片、方法论 7 步 → 4 步。全 deck 28 → 27 页。plan R3 / impl R2 均一次 PASS 零 findings。
