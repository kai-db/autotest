# 制作 AutoTest 项目技术分享 PPT（T1 轻任务 · 单文件记录）

> lite 模板（核心原则 0.1.1）。整个任务只此一个 `index.md`；当前 Status / final VERDICT 在本文件权威。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-06-01-create-techshare-ppt |
| Tier | T1（文档/演示产物任务，不改框架代码） |
| Created / Last Active | 2026-07-05 / 2026-07-07 |
| Status · Final VERDICT | done · PASS（Round 4：删抽象/重复页完成，24 页） |
| Codex Calls | 9（R1-R3 如下 7 次；R4：plan R6 PASS / impl R7 FAIL→R8 PASS） |
| History | Round 1（2026-07-05）28 页 done·PASS；Round 2（2026-07-06）压缩案例 28→27 页 done·PASS；Round 3（2026-07-07）配重加厚实践/规范、简化原理 27→26 页 done·PASS；Round 4（2026-07-07）删抽象/重复页（六大机制表合并、删五层递进、瘦身收尾）26→24 页 done·PASS |

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
| 2026-07-07 | plan R4 / impl R5（Round 3 配重，详见下方 Round 3 评审记录） | PASS | None（两轮均零 findings） |
| 2026-07-07 | plan R6 / impl R7→R8（Round 4 删抽象页，详见下方 Round 4 评审记录） | PASS | impl R7 两条 Important 修复后 R8 PASS |

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

---

## Round 3（2026-07-07）：重新配重——加厚实践/规范/价值，简化原理

### 需求

用户反馈（原文）：「share 目录下的分享优化下，就是技术方案可以简单介绍，然后主要是怎么测试，怎么实现 case、测试规范、解决了什么问题什么的可以详细点」。
澄清后的三点定调：
1. **优势章不压缩**（原 P7-P10 四页原样保留）；
2. **原理简化、页数少点**（原理深挖 P17-P21 五页 → 约 2 页）；
3. **「解决了什么问题」两层都要**：既讲这套框架解 UI 自动化的痛点（强化开场两难钩子作为「解决什么问题」的起点），也用 HTTPDNS 五次测试案例做「抓出什么真 bug」的实证（案例章保留）。
核心加厚落在**怎么测试 / 怎么实现 case / 测试规范**三块。

### 方案要点（Round 3 Proposal v1）

**重排目标：把 deck 的重心从「原理深挖」转到「测试实践 + 规范 + 价值」，总量 27 → ~26 页（原理 −3、是什么 −1、实践+规范 +3、优势/案例不动）。全部素材取自仓内文档，不新造事实。**

新结构（8 段，逐页说明改动 = 保留/新增/压缩/来源）：

**① 是什么 + 痛点（解决什么问题·起点）[4→3 页]**
- 封面：保留（版本注记 v1.8.2 沿用；副标不动）。
- 目录：**重写 agenda**——章节顺序与措辞突出「怎么测试 / 怎么实现 case / 测试规范 / 解决了什么问题」四块为主体（来源：本需求映射）。
- 背景痛点：原 P3 两难（纯脚本累/纯 AI 进不了 CI）**强化为「解决了什么问题」的开场钩子**，加一句「痛点 → 本框架解法」过渡（来源：docs/10 §0）。
- 是什么（双相架构）：原 P4 + 原 P6 系统全景**合并压成一页**（AI 在环 authoring → 固化 → 确定性 execution；路线 A/B 一句话带过），（来源：docs/10 §0、docs/01 §1-2）。
- 一组数字 + 技术栈：原 P5 保留（来源：docs/10 §0 §1.1、docs/01 §8）。

**② 优势 [4 页·原样保留]**：原 P7-P10 不动（为什么不内置 AI / 六大机制焊接表 / 开源借鉴全景 / 与同类工具区别 Q&A）。（用户明确不压缩。）

**③ 怎么测试 + 怎么实现 case [3→5 页·加厚]**
- 3a 两种测试模式 A/B（是什么·适合·产物·怎么选）：原 P11 保留（来源：USAGE §0）。
- 3b **怎么建 case（新增）**：模式 A——拷模板建 `runs/日期-功能/`（cases.md 编号/步骤/验证/优先级 P0-P2）、**先读 app-knowledge 拿真实元素别臆造 selector**、标前置/注入/危险红线；模式 B——继承 `DeBoxBaseTest`、`scenario{ step{} }` DSL、`locator.click`（三级自愈）、`AppAssertions`/`aiAsserter.assertWithAi`、只读红线+副作用复位（来源：USAGE §1）。
- 3c A+B 结合节奏 + 筛选标准：原 P12 强化——A 先行探索→发现 Bug 走 agent-dev-loop 修→A 回归全 PASS→**A→B 筛选（确定性/判断类型/依赖/回归价值四判据表）**→收尾；两条原则（**A 发现的 bug 修复后补一条 B 回归用例**、**B 是越攒越密的金字塔底座**）（来源：USAGE §0.1）。
- 3d **端到端示例（新增）**：新功能「资产页刷新」五步走完 A+B 全链路（建 case→AI 驱动跑→发现 FAIL 走 agent-dev-loop→沉淀 B 回归用例→收尾复位）（来源：USAGE §6）。
- 3e 跑起来：原 P13 保留——模式 A 一句话口令 + AI 7 步自动流程；模式 B 五步命令（autotest.enabled=true→选设备→gradlew connectedAppDebugAndroidTest→报告路径→复位 false）（来源：USAGE §2 §3）。

**④ 测试规范 [3→4 页·加厚]**
- 4a 八条铁律 + 危险红线：原 P14 保留（来源：CLAUDE.md 铁律；docs/10 §5；USAGE §5）。
- 4b 监工 6-Phase 闭环：原 P15 保留（来源：docs/10 §5.2；TEST_GUIDE 五节）。
- 4c 设备阶梯 L1/L2/L3（emulator-first）+ **人工介入白名单五项（新增）**：验证码/短信、真机首次解锁、危险操作确认、外部系统修复、secret 注入；白名单外人工=流程缺陷回写 lessons（来源：USAGE §0.2、TEST_GUIDE §7.1 §7.6）。
- 4d **bug 修复原则 + 修复走 agent-dev-loop（新增）**：修复原则四条（证据驱动/不引入新 bug/UI 正常/最小改动）；修复闭环（建 `docs/implementation/` 任务目录→plan→Codex 只读 plan review→实现→Codex impl review→回写 results.md；VERDICT 驱动状态机、循环上限、Codex 只读）（来源：TEST_GUIDE 三节 + 六节）。

**⑤ 原理速览 [5→2 页·简化]**
- 5a **原理速览一表**：六大机制（三级自愈定位/固化桥缓存回放/AI 软断言/统计化重试/双链拦截器/根因签名）× 「AI 在环 vs CI 执行」一页概览；点睛保留两条招牌公式——自愈加权打分（res-id 0.40/text 0.30/desc 0.20/class 0.10、阈值 0.75）、重试次数 n=⌈ln(1−T)/ln(1−p)⌉（来源：docs/10 §3、docs/09）。原 P17-P20 的逐机制展开细节压进此表 + 讲稿备注。
- 5b 安全网关 + 约束即代码：DangerousOpsGuard 统一点击闸（L-002 教训：确定性执行路径必须代码级守卫）+ TestSuite 只暴露 runAll()、MonitorMode 硬超时——**与④的危险红线呼应**（来源：docs/09 §0、docs/10 §3.5、docs/lessons.md L-002）。

**⑥ 案例：解决了什么真问题（实证）[4 页·保留]**：原 P22-P25 保留（五次演进时间线 / 案例① 06-12 配置契约 Critical / 案例② 07-01 回环 bogon 工单 / 五层递进洞察）；**章节标题改为突出「解决了什么真问题」**，呼应需求点③的实证层（来源：runs/ 五目录）。

**⑦ 收尾 [2 页·保留]**：原 P26 留痕不静默哲学 + P27 三论点/Q&A（来源：docs/10 §7 §9）。

**页数核算**：2（封面+目录）+3（是什么+痛点）+4（优势）+5（测试+case）+4（规范）+2（原理）+4（案例）+2（收尾）= **26 页**。

**PPT 重生成方式**：原 pptxgenjs 脚本在旧 session scratchpad、未入库（Round 1/2 Follow-up 已记）→ **本轮重建生成脚本**：从现有 pptx 提取的设计令牌复刻视觉系统（深蓝 0F2540/1E2A38/1B3A5C + 青绿 17A88B/0E7A65 + 浅底 D8E2EC/F0F5FA + 静音 5A6B7E + 警示红 E05252/琥珀 E8A93D；CJK 字体 PingFang SC）；用 pptxgenjs 按修订大纲渲染 26 页，覆盖 `docs/share/2026-07-05-AutoTest技术分享.pptx`（文件名沿用创建日前缀）。生成脚本本轮**入库到任务目录或 scratchpad**（择一，避免再次丢失——倾向 scratchpad 留副本 + Follow-up 记录，与前两轮一致）。

**事实溯源约束**：沿用 Round 1 v2——大纲每页保留「来源」字段；新增页（3b 建 case、3d 端到端示例、4c 白名单、4d 修复闭环、5 原理速览）全部逐条绑定 USAGE/TEST_GUIDE/docs 章节，不新造断言；数字类（用例数/公式/阈值/命令）逐条溯源。

**验证方式**：① python-pptx（pip 装）提取 26 页全文与修订大纲比对一致；② LibreOffice→PDF→JPG 渲染新增/改动页 + 顺移页逐页读图，无溢出/无 CJK 拆词/页码正确；③ 数字与公式抽查逐条溯源仓内文档；④ 确认删除/合并的内容（原 P6 全景、原理逐机制细节）无断链（要点在合并页/讲稿备注兜住）。

**评审方式**：plan review 评本节结构与覆盖度（声明 NO EDITS YET / NO PPT WRITTEN YET）；impl review 评大纲 diff + 新 pptx 提取文本（新文件先 git add -N）。

### Round 3 Accepted Plan（基线）

- 基线 = 上节 Proposal v1，无差异。
- Accepted at / Decision: 2026-07-07 · plan R4 VERDICT: PASS（零 findings；Critical/Important/Questions/Required Changes 四节全空）。
- Deviation Policy: 沿用 Round 1（偏离高风险面先 consult Codex；本任务为文档/演示产物，无安全/API/数据结构面）。

### Round 3 评审记录（每轮一行）

| 日期 | 阶段/轮次 | VERDICT | findings 处置 |
| --- | --- | --- | --- |
| 2026-07-07 | plan R4 | PASS | None（Proposal v1 一次通过，零 findings） |
| 2026-07-07 | impl R5 | PASS | None（Critical/Important/Accepted Risk/Verification Gaps 四节全空） |

### Round 3 改动摘要与验证

- 实际改动（与 Accepted Plan 一致，均为文档/演示产物）：
  - `docs/share/2026-07-05-AutoTest技术分享-大纲.md` — 全量重排为 **26 页**新结构：是什么+痛点(P3-5) → 优势(P6-9 原样) → 怎么测试&实现 case(P10-14，新增 P11 建 case / P13 端到端示例) → 测试规范(P15-18，新增 P17 白名单 / P18 修复原则+agent-dev-loop) → 原理速览(P19-20，原 5 页压成 2) → 案例(P21-24 保留，章题改「抓出什么真问题」) → 收尾(P25-26)。头部结构/修订行同步更新；每页保留「来源」字段。
  - `docs/share/2026-07-05-AutoTest技术分享.pptx` — 26 页重生成覆盖（**本轮重建 pptxgenjs 生成脚本**，复刻既有设计令牌：深蓝 0F2540/1E2A38/1B3A5C + 青绿 17A88B/0E7A65 + 浅底 + 警示红/琥珀；PingFang SC）。
- 验证：
  - 2026-07-07 · verify: 内容 QA：unzip+XML 提取 26 页文本，关键数字/文案抽查全部在位（5846 / 323 / 0.75 / n=⌈ln(1−T)/ln(1−p)⌉ / res-id 0.40 / PASS 35 / 7→19 / DeBoxBaseTest / autotest.enabled=true / connectedAppDebugAndroidTest / 67 条 / 白名单五项 / agent-dev-loop×7）。
  - 2026-07-07 · verify: 视觉 QA：LibreOffice→PDF→JPG 渲染 26 页，本人逐页读图。首轮发现 P3 kicker 胶囊「解决什么问题」换行（pillW 对 CJK 估宽不足）→ 改 pillW 按 CJK 字宽计算 → 重生成复核一行显示。全 26 页无溢出/重叠/截断。
  - 说明：LibreOffice 部分 CJK 正文回退为非目标字体（观感"手写体"）= 字体替换伪象，非 deck 缺陷；真机演示用 PowerPoint/Keynote（PingFang SC）。
- 与基线差异：None（页数 26、章节配重、新增/压缩页均按 Accepted Plan 执行）。

### Round 3 结论

- deck 重心从「原理深挖」转到「测试实践 + 规范 + 价值」：原理深挖 5 页 → 原理速览 2 页；「怎么用」3 页 → 「怎么测试 & 实现 case」5 页（新增建 case 步骤 P11、端到端示例 P13）；「规范」3 页 → 4 页（新增人工介入白名单 P17、bug 修复原则 + agent-dev-loop 闭环 P18）；优势章 4 页原样保留；案例 4 页保留作「解决什么真问题」实证；开场两难强化为「解决什么问题」钩子；原「系统全景」并入「是什么」。全 deck 27 → 26 页。
- 本轮重建了 pptxgenjs 生成脚本（原脚本在旧 session scratchpad 丢失），复刻既有设计令牌。plan R4 / impl R5 均一次 PASS 零 findings。
- Follow-up：本轮 pptxgenjs 生成脚本在 session scratchpad（`ppt-gen/gen.js`），未入库——沿用前两轮约定（如需再迭代基于大纲重生成）；真机演示用 PowerPoint/Keynote（PingFang SC），LibreOffice 渲染仅供 QA。

---

## Round 4（2026-07-07）：删抽象/重复页，让分享更有干货

### 需求

用户反馈「感觉还是有点逻辑，可以再优化下」，进一步点名：**五层递进、小结、六大机制表 感觉有点没用**。定性：这三页属「抽象归纳/重复」型，逻辑有余、干货不足。经对齐确认三条处置。

### 方案要点（Round 4 Proposal v1）

**目标：删掉抽象重复页，deck 更punchy。26 → 24 页。只删/合并/瘦身，不新增事实断言。**

1. **合并两处「六大机制表」冗余**：六大机制在 P7（优势②·机制×AI在环×CI执行）与 P19（原理速览①·机制→一句话原理）各出现一张表 = 重复。
   - **保留 P7**（「AI在环 vs CI执行」焊接视角 = 核心卖点，原样不动）。
   - **删 P19 的六大机制 glossary 表**，并把原理 2 页（P19 表+两公式、P20 安全网关）**合并成 1 页「原理精华」**：4 张紧凑卡——三级自愈（含加权打分公式 res-id 0.40…阈值 0.75）· 固化桥（元素快照）· 统计重试（含 n=⌈ln(1−T)/ln(1−p)⌉）· 安全网关（DangerousOpsGuard + L-002「纪律+代码双保险」）。保留两条招牌公式与安全网关故事，去掉重复表。（原理 2→1 页）
2. **删「五层递进」（原 P24）整页**：五层是对同批 run 的第二次抽象；时间线页(P21)底部已有「每一轮逼出更深一层的真问题」兜住核心洞察，两个案例页已讲故事。**整页删除**；其「方法论主线 4 步」（用例映射→分层推进→真实配置核对→agent-dev-loop 闭环）**并入收尾页**作「怎么用起来」的具体落点（避免丢有价值内容）。
3. **收尾（原 P26）瘦身**：去掉抽象三段式「一条主线/三个融合点/一以贯之」（像复述目录）；改为 **一句金句（Agentic authoring + Deterministic execution）+ 方法论 4 步落点（从五层递进并入）+ Q&A 备料 + 谢谢**，让结尾有力且具体。

**联动**：全 deck 26→24 页；页码自动顺移（本轮把 gen.js 页码改为按幻灯片顺序自动编号 + 总数取 S.length，杜绝手工renumber 出错）。目录页(P2)章节列表不含页码，措辞无需改。

**产物与工具**：改 `docs/share/2026-07-05-AutoTest技术分享-大纲.md`（对应页重写/删除，保留每页「来源」字段——只删/合并不新增事实断言）；PPT 用本 session scratchpad 的 `ppt-gen/gen.js`（Round 3 重建、仍在）修改后整体重生成，覆盖 `docs/share/2026-07-05-AutoTest技术分享.pptx`。

**验证**：unzip+XML 提取 24 页文本核对（保留的关键数字/公式仍在、删除页内容确已移除且无断链）；LibreOffice→PDF→JPG 渲染改动页（原理合并页、收尾页）+ 顺移页逐页读图无溢出/重叠/页码错。

**评审**：plan review 评本节（NO EDITS YET）；impl review 评大纲 diff + 新 pptx 提取文本。

### Round 4 Accepted Plan（基线）

- 基线 = 上节 Proposal v1，无差异。
- Accepted at / Decision: 2026-07-07 · plan R6 VERDICT: PASS（零 findings）。
- Deviation Policy: 沿用（本任务文档/演示产物，无高风险面）。

### Round 4 评审记录（每轮一行）

| 日期 | 阶段/轮次 | VERDICT | findings 处置 |
| --- | --- | --- | --- |
| 2026-07-07 | plan R6 | PASS | None（Proposal v1 一次通过，零 findings） |
| 2026-07-07 | impl R7 | FAIL | 2 Important：① P19 自愈招牌公式只给权重未成公式本体 → 改显式等式 s=0.40·resId+0.30·text+0.20·desc+0.10·class；② slide 12 误显示「来源：USAGE §0.1」→ 删除（来源字段只留大纲，全 deck 一致） |
| 2026-07-07 | impl R8 | PASS | None（两条 Important 已修复，Critical/Important/Risk/Gaps 四节全空） |

### Round 4 改动摘要与验证

- 实际改动（与 Accepted Plan 一致）：
  - `docs/share/2026-07-05-AutoTest技术分享-大纲.md` — 合并原 P19+P20 为 P19「原理精华」（4 机制 + 2 显式公式 + 安全网关，删重复六大机制 glossary 表）；删原 P24「五层递进」整页（方法论 4 步并入收尾）；收尾 P24 去抽象三论点改金句+方法论4步+Q&A；P20-24 重编号；头部结构/修订行更新。
  - `docs/share/2026-07-05-AutoTest技术分享.pptx` — 24 页重生成覆盖（gen.js 改为按幻灯片顺序自动编号 + 总数 S.length）。
- 验证：
  - 2026-07-07 · verify: 内容 QA：unzip+XML 提取 24 页，关键事实在位（res-id 0.40 / ln(1−T) / 元素快照 / L-002 / 方法论 4 步 / 留痕不静默）；「五层递进」「三个融合点」「六大机制一表」均已清零；页码「/ 24」正确。
  - 2026-07-07 · verify: 视觉 QA：LibreOffice→JPG 渲染改动页（原理精华 P19 / 收尾 P24 / 时间线 P20 顺移 / 目录 P2）逐页读图，无溢出/重叠/页码错。
  - impl R7 FAIL 两条 Important 修复后 R8 复评 PASS。
- 与基线差异：None。

### Round 4 结论

- deck 26 → 24 页，去掉抽象/重复：两处六大机制表合并（保留 P7 焊接表，原理 2 页并 1 页「原理精华」）；五层递进整页删除（洞察已在时间线，方法论 4 步并入收尾）；收尾去三论点改金句+方法论+Q&A。plan R6 一次 PASS；impl R7 FAIL（2 Important）→ 修复 → R8 PASS。
- Follow-up：gen.js（Round 3/4 迭代版）在 session scratchpad `ppt-gen/gen.js`，未入库；真机演示用 PowerPoint/Keynote（PingFang SC），LibreOffice 仅供 QA。
