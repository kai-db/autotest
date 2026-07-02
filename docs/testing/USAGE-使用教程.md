# 测试使用教程（新功能 → 建 case → 运行 → AI 驱动 → 遵循规范）

> 面向：开发完一个新功能后，怎么把它纳入这套测试。**读这一篇就够上手**；规范正文见 `TEST_GUIDE.md`（稳定不变），框架用例写法见 debox `docs/testing/autotest-authoring.md`。
>
> 相关文档：
> - `TEST_GUIDE.md` — 铁律 / 危险操作比对 / 流程 / 修复原则 / **设备策略（第七节）**（**规范源头**）
> - `TEST_CASES.md` / `TEST_RESULTS.md` — 用例 / 结果模板
> - `runs/日期-功能/` — 每次测试的 case + result（从模板拷）
> - `app-knowledge/`（`dangerous-ops.md` / `devices.md` / `network-domain.md` / `screens/`）— 知识库，**开测前必读**
> - debox `docs/testing/autotest-authoring.md` — 框架 instrumented 用例作者约定

---

## 0. 两种测试模式（先选对）

| 模式 | 是什么 | 适合 | 产物 |
|---|---|---|---|
| **A. AI 驱动黑盒**（主力） | Claude Code 用 mobile-mcp/adb 直接驱动真机 + 读日志判断，按 `runs/*/cases.md` 逐条执行 | 探索性、跨系统/网络/后端、需人类级判断的场景（本仓大多数 run，如 HTTPDNS 回环） | `runs/日期-功能/results.md` + evidence |
| **B. 框架 instrumented**（可回归） | 基于 `com.autotest:autotest` 写 Espresso/UiAutomator 用例，设备内自动跑（自愈定位 + DSL + AI 软断言 + 报告） | 确定性 UI 冒烟/回归、可重复跑、CI 化候选 | `report_*.json` + 失败截图 |

> **怎么选**：新功能先用 **A** 快速探索、覆盖复杂判断；把其中**稳定、可重复、值得每次回归**的关键路径**沉淀成 B 的 instrumented 用例**。两者互补，不是二选一。

### 0.1 每个新需求的 A+B 结合节奏（怎么配合）

**一句话**：A 是「每个新需求一次性的探索 + 发现」，B 是「累积的回归网」——**不是每次都两个全跑**，而是 **A 打头、B 沉淀、之后 B 兜底回归**。

```
新需求来
  │
  ├─①【A 先行·探索发现】建 runs/日期-功能/cases.md,AI 黑盒驱动跑
  │     覆盖:主流程 + 边界 + 异常 + 需人类判断/网络注入/跨系统的场景 → results.md
  │
  ├─②【发现 Bug → agent-dev-loop 修 → A 回归】直到 A 全 PASS
  │
  ├─③【筛选沉淀·A→B】从跑过的 case 里,挑「确定性 + 值得每次回归 + 纯 UI 能断言」的,
  │     写成 B 用例(继承 DeBoxBaseTest)纳入回归集。★ 不是全部 case 都 B 化
  │
  └─④【收尾】知识库回写 + lessons + autotest.enabled 复位 false

以后任意改动 / 发版:先跑【B 回归集】(快、确定、专抓回归)→ 再对改动 / 新增部分用【A】探索
```

**哪些留 A、哪些沉淀成 B（筛选标准）**

| 判据 | 留在 A（黑盒探索） | 沉淀成 B（instrumented 回归） |
|---|---|---|
| 确定性 | 时序/非确定、一次性排障 | 可稳定复现 |
| 判断类型 | 需人类级判断（视觉"合理吗"、根因分析） | 纯 UI 状态断言（可见/文本/跳转） |
| 依赖 | 网络/DNS 注入、后端、跨 App、验证码/登录 | App 内、已登录态下的 UI 流 |
| 回归价值 | 覆盖一次即可 | 每次改动都想复跑 |

> 典型分工：启动健康 / 页面 render / 导航 / 列表展示 / 表单确定性分支 → **B**；HTTPDNS 回环、多 navi failover、语音房、跨端消息、视觉合理性 → **留 A**。

**两条原则**
1. **A 发现的 bug，修复后补一条 B 回归用例**（fix-with-regression-test）—— 防复发，是 A→B 最高价值的沉淀点。
2. **B 是会越攒越多的金字塔底座**（每个需求沉淀几条），A 是每需求一次性的尖端探索 —— 时间久了 B 的回归网越密、A 越省力。

### 0.2 设备选择速查（emulator-first，规范见 `TEST_GUIDE.md` 第七节）

**默认跑模拟器，真机只做复核**——模拟器不锁屏、不被日常使用抢占、可无人值守；真机安全锁屏需人工解锁，是历史上最大的人工介入源。

| 层级 | 设备 | 什么用例放这层 |
|---|---|---|
| **L1** 普通模拟器 `Pixel_10_Pro_XL` | 默认层 | UI 流 / 导航 / 功能黑盒 / 弹窗表单 / B 模式回归 |
| **L2** root 模拟器 `debox_root` | 注入层 | DNS/iptables 故障注入、WiFi↔蜂窝切换、需 root 的观测 |
| **L3** 真机（小米/三星） | 复核层 | 模拟器网络噪声场景的真机复核、厂商特性/推送/性能、真实蜂窝、最终验收抽核 |

- 每条 case 在 `cases.md` 标「设备」列（L1/L2/L3），取**最低可行层级**。
- 模拟器网络层结论若命中 fake-IP 代理噪声（预检见 §7.3），只能标【模拟器实测，待真机复核】。
- 模拟器登录态打 AVD snapshot 固化（§7.4），验证码人工降为一次性。
- 真机不在线不阻塞：真机项标 `⏸️-待真机`，其余照跑。
- 设备清单/能力矩阵/已知噪声：`app-knowledge/devices.md`。

---

## 1. 开发新功能后 → 新建 case

### 模式 A（AI 驱动黑盒）—— 建一次 run
1. 拷模板建目录：`runs/YYYY-MM-DD-功能名/`，放 `cases.md`（拷 `TEST_CASES.md`）、`results.md`（拷 `TEST_RESULTS.md`）。
2. 写 `cases.md`：项目信息表 + 用例表（编号 / 用例 / 步骤 / 验证标准 / 优先级 P0-P2）。**先读 `app-knowledge/`** 拿真实元素/弹窗/危险操作，别臆造 selector。
3. 标注前置条件、故障注入手段、危险红线。（范例：`runs/2026-07-01-HTTPDNS回环bogon兜底/cases.md`）

### 模式 B（框架 instrumented）—— 写一条用例
> 详细模板见 debox `docs/testing/autotest-authoring.md`。要点：
1. 用例放 debox `app/src/androidTestAutotest/java/com/tm/security/wallet/autotest/`，**继承 `DeBoxBaseTest`**（已封装启动 + 关弹窗 + 登录 preflight + selector 常量），不要直接继承框架 `BaseUiTest`。
2. 用 `scenario("名"){ step(...){ ... } }.run()` DSL；定位优先 `byText/byResId/byDesc` + `locator.click(...)`（三级自愈）；断言用 `AppAssertions`；AI 软断言 `aiAsserter.assertWithAi(...)`。
3. **只读 + 危险红线**：不点转账/签名/删除/登出/切环境；有副作用的操作要复位。
4. 元素文案/id 用真机探测确认（截图或测试内 UiAutomator；`uiautomator dump` CLI 对永不 idle 的页面会失败）。
5. 范例：`DeBoxSmokeTest.kt`（首个通过范例）。

---

## 2. 怎么运行

### 模式 A
由 Claude Code 全权驱动（见第 3 节 AI 驱动）。人只做验证码等必需环节。

### 模式 B（框架 instrumented）
```bash
# ① 打开开关(debox/local.properties;默认 off、CICD 不配 → 绝不进正式包)
autotest.enabled=true
# ② 设备:优先模拟器(不锁屏,L1/L2 见 0.2 节);真机需已解锁。均要求已登录 + 停在被测前置页(登录 preflight 要求正向已登录信号)
# ③ 跑(二选一)
./gradlew :app:connectedAppDebugAndroidTest
# 或指定单个用例:
adb shell am instrument -w -e class \
  com.tm.security.wallet.autotest.DeBoxSmokeTest \
  com.tm.security.wallet.test/androidx.test.runner.AndroidJUnitRunner
# ④ 报告:目标 App 外部目录 /sdcard/Android/data/com.tm.security.wallet/files/autotest/report_*.json
# ⑤ 测完把 autotest.enabled 置回 false
```
> **首次接入/换机若报错**：多半是集成坑（protobuf-lite 冲突 / hamcrest 缺失 / 报告目录 UID / flag 落盘时间窗）——对照全局 `lessons-global.md` G3；debox 事实见 `docs/lessons.md` P2。

---

## 3. 怎么通过 AI 驱动（模式 A 核心）

Claude Code 按 `TEST_GUIDE.md` 五节流程**全权自主执行**，人最小介入。开测发一句即可，例如：
> “按 `runs/2026-07-XX-功能名/cases.md` 跑测试”。

AI 会自动：
1. **先读知识库**（铁律#6）：`app-knowledge/` 元素表/弹窗/**危险操作清单**/**设备清单**。
2. **确认环境**（Phase 1）：按设备阶梯选设备（默认模拟器，见 0.2 节）→ 设备在线（无则启模拟器）→ 屏幕保活 → 模拟器网络噪声预检 → App 可启动。
3. **按用例执行**（铁律#2，Phase 2）：`cases.md` 是唯一用例来源，逐条重置 + 截图验证。
4. **危险操作 pre-action 比对**（铁律#7）：点击前比对 `dangerous-ops.md`，命中先停（钱包不可逆）。
5. **先记录再修复**（Phase 3）：结果写 `results.md`。
6. **监工模式**（铁律#3）：长测用 `/loop 5m` 每 5 分钟查执行状态、防卡住。
7. **探索产物回写知识库**：新发现的元素/弹窗/危险项补进 `app-knowledge/`。

> **AI 驱动 = 全程自主（铁律#1），不等人指示每一步**。人工介入只允许白名单五项
> （`TEST_GUIDE.md` §7.6）：①验证码/短信 ②真机首次解锁 ③危险操作确认
> ④外部系统（运维/后端）修复 ⑤secret 注入；白名单外出现人工 = 流程缺陷，回写 lessons。
> 外部依赖阻塞（如等运维）按 §7.5 自动降频轮询，不逐次请示。

---

## 4. 发现 Bug → 修复走 agent-dev-loop（强制，铁律#5）

**分析问题 / 改代码不直接改**，必须走 `agent-dev-loop` skill（Claude 计划/实现 + Codex 只读独立评审闭环）：
1. 在**被测项目**（debox）建 `docs/implementation/YYYY-MM-DD-动词-对象/`（index/plan/implementation/review 四件）。
2. 写 plan → **Codex plan review**（PASS 才落 Accepted Plan）→ 实现 → **Codex impl review**（无 Critical/unresolved Important 才收尾）→ 回写。
3. 范例（本次即用它接入框架）：debox `docs/implementation/2026-07-02-integrate-autotest-instrumented/`。
> 详见 `TEST_GUIDE.md` 第六节。修复完回到 Phase 5 回归、Phase 6 验收。

---

## 5. 遵循测试规范（速查清单）

- **铁律**（`TEST_GUIDE.md` 二节 / 项目 `CLAUDE.md`）：① AI 全程自主 ② 按 `cases.md` 用例执行 ③ `/loop 5m` 监工 ④ 最小人工介入 ⑤ 修复走 agent-dev-loop ⑥ 先读知识库 ⑦ 危险操作 pre-action 比对 ⑧ 模拟器优先（设备阶梯 L1→L2→L3，`TEST_GUIDE.md` 七）。
- **危险红线（硬）**：不清数据 / 不卸载 / 不登出 / 不切环境 / 不点转账·签名·删除；钱包·IM 只读浏览。
- **不写 secret**：token/私钥/助记词/密钥不进代码·日志·commit·断言信息（`local.properties` 存密钥、gitignored）。
- **结果如实记**：PASS/FAIL/⏸️ 都写 `results.md`，FAIL 附根因 + 证据；先记录后修复。
- **框架用例绝不进正式包**：仅 `androidTestImplementation` + `autotest.enabled` 开关（默认 off）；改动经 dex 级 + 正对照验证。
- **经验沉淀**：可泛化的工具/流程坑 → 全局 `lessons-global.md`；项目特有 → 项目 `docs/lessons.md`（append-only，开工前必读）。

---

## 6. 端到端示例（新功能「资产页刷新」为例）

1. **建 case（A）**：`runs/2026-07-10-资产页刷新/cases.md`，读 `app-knowledge/` 拿资产页元素，写 P0「进资产页→下拉刷新→数据更新且无 crash」（只读）。
2. **AI 驱动跑**：“按该 cases.md 跑测试” → AI 读知识库→确认环境→逐条执行→危险比对→`results.md` 记结果（长测 `/loop 5m` 监工）。
3. **发现 FAIL**：AI 建 debox `docs/implementation/2026-07-10-fix-asset-refresh/` → plan → Codex review → 修 → Codex review → 回写 → 回归。
4. **沉淀回归用例（B）**：把稳定的「资产页加载」关键路径写成 `AssetPageSmokeTest : DeBoxBaseTest`（`scenario{}` + `locator` + `aiAsserter`），`autotest.enabled=true` 真机验通过，纳入以后每次回归。
5. **收尾**：知识库补新元素；有可泛化坑写 lessons；`autotest.enabled` 复位 false。
