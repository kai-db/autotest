# 测试指南

> 版本：v1.2 | 更新日期：2026-07-02（新增第七节：设备策略 emulator-first）
> 用例模板见 [TEST_CASES.md](TEST_CASES.md) | 结果模板见 [TEST_RESULTS.md](TEST_RESULTS.md)
> 框架侧机制（缓存回放/自愈/软断言）见 [docs/09](../09-AI驱动测试机制.md)

本目录是**测试规范**，定义怎么测、铁律、流程。每次实际测试在 `runs/` 下按 `日期-功能` 建文件夹。

---

## 目录结构

```
docs/testing/
├── TEST_GUIDE.md          # 本文件：规范（稳定不变）
├── TEST_CASES.md          # 用例模板（稳定不变）
├── TEST_RESULTS.md        # 结果模板（稳定不变）
├── app-knowledge/         # App 知识库（AI 探索沉淀，每轮回写）
│   ├── README.md          #   知识库规范
│   ├── dangerous-ops.md   #   危险操作清单（点击前必须比对）
│   ├── devices.md         #   设备清单/能力矩阵/已知噪声（选设备前必读）
│   └── screens/           #   页面元素表（_template.md 为模板）
├── cache/                 # 用例缓存（CachedCase JSON，AI 探索产出、回归回放）
└── runs/                  # 每次测试一个文件夹
    ├── 2026-06-12-RTC转CDN/
    │   ├── cases.md       # 本次用例（从模板拷贝或自定义）
    │   └── results.md     # 本次结果
    └── ...
```

**命名规则**：`YYYY-MM-DD-功能描述`（如 `2026-04-14-smoke`、`2026-04-15-ai-chat-swap`）

---

## 一、测试前检查

```
1. 按设备阶梯选设备（见第七节；默认模拟器，真机仅 L3 场景）
2. 确认设备在线（adb devices 或 MCP list_devices）
3. 屏幕保活（模拟器 svc power stayon true / 真机人工解锁一次 + stayon usb）
4. 查看当前状态
5. 启动目标 App
```

---

## 二、操作铁律

- **模拟器优先**：设备按 L1 普通模拟器 → L2 root 模拟器 → L3 真机阶梯路由，取最低可行层级（详见第七节）；人工介入仅限白名单环节（§7.6）
- **操作前先获取元素**找坐标，不要盲猜
- **遇到弹窗先处理弹窗**
- **每条用例重置环境**（terminate → launch）
- **回归必须全量跑**，不能只跑失败的
- **先读知识库再探索**：每次 AI 测试 session 开始前先读 `app-knowledge/`（页面元素表、已知弹窗、跳转关系），避免重复探索；探索到的新元素/新弹窗回写知识库
- **登录态与测试环境是易碎前提，只用不破坏**：
  - ❌ 禁止 `clearAppData` / `pm clear` / 卸载重装（丢登录态 + 切回正式环境）
  - ❌ 禁止登出 / 切换环境操作
  - ✅ "重置环境" 仅指 terminate → launch
  - ✅ 每轮开跑前校验"测试环境 + 已登录"，不符则停下报告，不跑无效结果

### 危险操作 pre-action 比对（钱包 App 专项，借鉴 Mobile-Agent GUI-Critic）

**每次点击前**，若目标元素文本/语义命中 `app-knowledge/dangerous-ops.md` 清单
（转账确认、删除钱包、登出、清数据、切环境、授权签名等），**必须停下**：

1. 不点击，截图记录现场
2. 比对当前用例步骤是否明确要求该操作
3. 用例未明确要求 → 跳过并在 results.md 记录"遇到危险操作已规避"
4. 用例明确要求 → 仅在测试环境且金额/对象为测试数据时执行

钱包 App 误操作不可逆，宁可用例 FAIL 也不误点。

---

## 三、Bug 修复原则

每个 bug 修复方案提交前，必须确认：

| # | 原则 | 要求 |
|---|------|------|
| 1 | **证据驱动** | 根因基于日志验证，不凭猜测 |
| 2 | **不引入新 bug** | 改动前评估影响范围，修复后回归验证 |
| 3 | **UI/交互正常** | 确认无显示异常 |
| 4 | **最小改动** | 只改必须改的，不顺手重构 |

### 检查清单模板

```
- [ ] 根因基于证据验证
- [ ] 评估影响范围，无回归
- [ ] 确认 UI 正常
- [ ] 改动范围最小
```

---

## 四、测试执行流程

每条用例按以下步骤执行：

1. **重置环境** → terminate_app → launch_app → 等待启动
2. **执行操作** → 按用例步骤操作
3. **验证** → 检查页面状态
4. **判定** → PASS 或 FAIL + 抓取证据
5. **记录** → 写入本次 `runs/日期-功能/results.md`

---

## 五、自动化闭环流程（Claude Code 全权执行）

> 启动口令：「跑一轮完整测试」

> ⚠️ **强制规则（mandatory）**：本节流程不可跳过、不可简化、不可合并步骤。必须严格按
> Phase 1→2→3→4→5→6 顺序执行，每个 Phase 的子步骤都要完整跑完。任何"为了效率"省略环节
> 的行为都是违规。对流程有优化建议，先提出 → 获得用户确认 → 改本文档，再执行，不得自行省略。
>
> **修复必须走 `agent-dev-loop` skill**：Phase 4 分析问题、修复代码**不直接改**，而是用
> Claude 计划/实现/修复/记录 + Codex 只读独立 review 的协作闭环（详见「六、修复阶段走
> agent-dev-loop」）。

### 前提条件

| 前提 | 说明 |
|------|------|
| 设备已连接 | adb devices 或 MCP 可见设备 |
| App 已安装 | 被测 App 已安装且可用 |
| 用户已授权 | Claude Code 拥有测试和修复权限 |

### 开始测试时

1. 在 `runs/` 下新建文件夹，命名为 `YYYY-MM-DD-功能描述`
2. 基于 `TEST_CASES.md` 模板创建 `cases.md`，填入本次测试用例
3. 基于 `TEST_RESULTS.md` 模板创建 `results.md`（初始为空）
4. 执行闭环流程

> **AI 自动创建**：当用户提供测试用例（文字、文件、或口头描述）时，AI 应自动完成上述步骤——创建 `runs/日期-功能/` 目录，将用例整理到 `cases.md`，创建空 `results.md`，然后开始测试。

### 闭环流程

```
══════════════════════════════════════════════════════════
  迭代修复阶段（循环，直到 0 个 FAIL）
══════════════════════════════════════════════════════════

  Phase 1: 确认环境
  按设备阶梯选设备（第七节）→ 设备在线（无设备则启动模拟器）
  → 屏幕保活 → 模拟器网络噪声预检（§7.3）→ App 可启动
      │
      ▼
  Phase 2: 全量测试
  按 P0→P1→P2 遍历 cases.md 全部用例
  每条：重置环境 → 操作 → 验证
      │
      ▼
  Phase 3: 更新文档
  将本轮全部结果写入 results.md（先记录再修复）
      │
      ▼
  Phase 4: 全部修复（走 agent-dev-loop skill，见第六节）
  汇总 FAIL → 逐个用 agent-dev-loop 修：建任务目录 →
  plan → Codex plan review → 实现 → Codex 实现 review → 回写 results.md
      │
      ▼
  Phase 5: 回归测试
  全量重跑（不只跑 FAIL 的，防止回归）
      │
      ├── 仍有 FAIL → 回到 Phase 3
      └── 全部 PASS → 进入最终验收
              │
══════════════════════════════════════════════════════════
  最终验收阶段
══════════════════════════════════════════════════════════
              │
              ▼
  Phase 6: 全量验收
  不改代码，完整跑一遍
      │
      ├── 全部 PASS → ✅ 测试通过，流程结束
      └── 有 FAIL → 回到迭代修复阶段
```

### 关键规则

- 每轮都是**全量测试**，不能只跑 FAIL 用例；**"剩几条 ⏸️ 待执行"不算跑完**，要么跑完要么写明阻塞原因
- 必须**先更新文档（Phase 3）再修复代码（Phase 4）**
- **Phase 4 修复必须走 `agent-dev-loop` skill**（建 `docs/implementation/` 任务目录 + Codex review 闭环），不直接改代码——见第六节
- 最终验收是**独立的全新测试**，不复用之前结果；**验收阶段发现问题回到迭代修复阶段，不在验收阶段内就地修**

### 监工模式

测试期间使用 Claude Code 内置 `/loop 5m` 开启监工模式，每 5 分钟自动检查 AI 执行状态，防止卡住：

```
/loop 5m 检查当前测试执行状态，如果卡住了就恢复继续
```

---

## 六、修复阶段走 agent-dev-loop（强制）

> Phase 4 的每个 FAIL，以及测试中发现的代码问题，都必须经此闭环修复，**不直接改代码**。

### 为什么

直接改代码 = 无独立 review、无 plan 基线、无 VERDICT 把关，容易引入回归、漏验证根因。
agent-dev-loop 让 Codex 以**只读**身份独立评审 plan 与改动，形成"计划→评审→实现→评审"闭环。
（历次测试都没走这步——`docs/implementation/` 至今为空，这正是要纠正的偏离。）

### 步骤（每个待修问题一遍）

1. **建任务目录** `docs/implementation/YYYY-MM-DD-动词-对象/`，默认四件：
   `index.md`（权威入口：当前 Status + 最终 VERDICT）/ `plan.md` / `implementation.md` / `review.md`
2. **写 plan** → 调 Codex 做 plan review（只读）→ 修到 `VERDICT: PASS` 或
   `PASS_WITH_ACCEPTED_RISK` → 在 `plan.md` 写下 `Accepted Plan` 基线
3. **按基线实现**，进度/偏差记 `implementation.md`；真遇歧义/风险再 consult Codex
4. **自检后**调 Codex review 改动（对照 Accepted Plan）→ 修到无 Critical、无遗留 Important
5. **收尾**：`index.md` 写权威 Status / 最终 VERDICT / 小结；可复用经验回写
   `docs/lessons.md`（项目级）或全局 ledger
6. **回写 results.md**：该 FAIL 的修复结论 + 链接到对应 `docs/implementation/` 任务目录

### 硬规则

- `VERDICT`（`PASS` / `FAIL` / `PASS_WITH_ACCEPTED_RISK`）驱动状态机；findings 只报 confidence ≥ 80 的 `Critical` / `Important`
- 循环上限：plan review 3 轮、实现 review/fix 5 轮、consult 3 轮 → 超限升级给用户
- Codex **只读**（不传 `--write`）；不写密钥到代码/文档/日志；高风险操作先问用户

> 完整协议见 `agent-dev-loop` skill。被测 App（DeBox）代码在 `debox-android` 工作区；
> autotest 框架自身代码改动同样走本闭环。

---

## 七、设备策略（emulator-first）

> 目标：**尽量减少人工参与**。模拟器不锁屏、不被日常使用抢占、可 root 注入，
> 是默认执行环境；真机降为「复核层」，只在模拟器给不出权威结论时使用。
> 设备清单/能力矩阵见 `app-knowledge/devices.md`（开测前必读）。

### 7.1 设备阶梯与路由规则

| 层级 | 设备 | 定位 | 适用 | 判据 |
|---|---|---|---|---|
| **L1** | 普通模拟器（`Pixel_10_Pro_XL`） | 默认层 | UI 流/导航/页面 render/功能黑盒/弹窗/表单/B 模式回归 | 不需要 root，不依赖真实网络路径与厂商特性 |
| **L2** | root 模拟器（`debox_root`） | 注入层 | 故障注入（DNS/iptables/hosts/回环）、WiFi↔蜂窝切换、需 root 的观测 | 需要 root 能力或多网卡 |
| **L3** | 真机（小米/三星） | 复核层 | ①模拟器网络噪声场景的 positively 复核 ②厂商特性/推送/性能/传感器 ③真实蜂窝/短信 ④最终验收抽核关键路径 | 结论受模拟器环境伪象威胁，或依赖真机独有硬件/网络 |

- **路由规则**：每条用例取**能满足判据的最低层级**；`cases.md` 用例表标注「设备」列（L1/L2/L3）。
- **降级规则**：L3 真机不在线/锁死 → 该 case 标 `⏸️-待真机`，**继续跑其余层级用例，不整体阻塞**。
- 危险红线不因层级放松：模拟器同样执行危险操作 pre-action 比对；L2 注入后按 `devices.md` 复原清单复原。

### 7.2 锁屏与屏幕保活

- **模拟器**：无锁屏（AVD 一次性配置屏幕锁 = None）；每轮开测 `adb shell svc power stayon true`。
- **真机**：安全锁屏（指纹/Bouncer）adb 不可绕过——真机用例**集中批跑**，开跑前人工解锁一次（白名单环节 §7.6）+ `svc power stayon usb`，跑完恢复设置。
- Phase 1 检查项：「屏幕保活已设置（模拟器 stayon / 真机已解锁+stayon）」。

### 7.3 模拟器结论可信度 + 网络噪声预检

- **预检（模拟器开测必做）**：解析 1 个已知域名，若结果落在 `198.18.0.0/15`（宿主 fake-IP 代理特征）→ 本轮为噪声环境，写入 results.md 环境节。
- **结论分级**：
  - 涉及 DNS 解析路径/连接目标/证书链的**网络层结论**，在命中噪声环境的模拟器上只能标 **【模拟器实测，待真机复核】**，不得直接判 PASS/FAIL 定论（TC-N-05/BUG-002 教训：模拟器伪象险些误判客户端 bug）；
  - 纯 App 内 UI/业务逻辑结论，**模拟器即终局**，无需真机复跑。

### 7.4 登录态快照（模拟器专属）

- 模拟器完成登录（含验证码，人工一次）后打 AVD snapshot（`logged-in-<env>`）；后续轮次从快照恢复，验证码人工降为一次性。
- 快照恢复后**必做健康基线**：时间同步、网络可达、登录态有效（IM onOpen / 首页数据正常）；不健康才人工重登。
- 真机不适用 snapshot，沿用「登录态易碎，只用不破坏」规范（第二节）。

### 7.5 外部依赖等待自动轮询

阻塞在外部系统（运维/后端/证书/DNS 下发）时，不逐次请示用户：

```
/loop 20min 间隔轮询 ×6（≈2h）
  → 未解除：自动降频为每小时一次
  → 累计约半个工作日仍未解除：results.md 记录证据链，
    该 case 转 ⏸️-外部阻塞，继续其余用例，汇报用户一次
```

每次轮询结果（时间点 + 观测值）记入 results.md，形成证据链（07-01 证书轮询实践固化）。

### 7.6 人工介入白名单

**唯一合法**的人工环节（白名单之外一律 AI 自主；出现计划外人工介入 = 流程缺陷，回写 lessons）：

1. **验证码/短信/2FA**（模拟器 snapshot 化后应趋近一次性）；
2. **真机首次解锁**（安全锁屏，技术上不可绕过）；
3. **危险操作确认**（`dangerous-ops.md` 命中且用例明确要求执行时的最终确认）；
4. **外部系统修复动作**（运维/后端侧，不属于本测试可自动化范围）；
5. **secret 注入**（签名口令、API key 等，禁止 AI 索要明文进对话/日志/文档）。

### 7.7 被测包自主构建与安装（AI 自主，非白名单）

> **规范来源**：2026-07-11 实证。此前「用户 AS 打包」是无据的临时做法——打包/安装**不在** §7.6 白名单，按铁律必须 AI 自主。本节固化自主流程，**严格遵循，勿再退回人工打包**。

**debox `:app` debug 包 CLI 自主构建**（RN 0.78 手动集成 + CodePush 的坑，详见 debox `docs/lessons.md` L-BUILD-01）：

1. **补 `react` 扩展 shim**（构建前）：`app/build.gradle` 的 `apply from: codePushGradle` **之前**插入——
   ```gradle
   if (project.extensions.findByName("react") == null) {
       project.extensions.add("react", [debuggableVariants: project.objects.listProperty(String).convention(["appDebug"])])
   }
   ```
   （原因：手动集成未 apply RN 插件 → codepush 读不到 debuggableVariants → 误对 flavored `appDebug` 触发 JS bundle → 撞未注册任务。shim 让 debug 正确跳过 bundling，走 Metro。）
2. **构建**：`NODE_PATH="$PWD/ReactNative/node_modules" ./gradlew :app:assembleAppDebug`；需 Realm 注入 harness 时加 `:app:assembleAppDebugAndroidTest`（`local.properties` 先设 `autotest.enabled=true`）。
3. **构建后 `git checkout app/build.gradle` 还原 shim**（只为构建，不入库）。
4. **安装预检（I-89）**：`apksigner verify --print-certs` 比对新 APK 与已装包签名一致（debug 用 release keystore，通常同签名）+ 新 versionCode ≥ 已装 → `adb install -r`（保留登录/钱包数据）。**签名不符或降级 → 停止报告，绝不 uninstall/清数据**（触犯 §7.6 与危险红线）。
5. **归因链**：记录源码 HEAD SHA + APK sha256 + versionCode + 签名摘要 + 安装后 `dumpsys package`；版本号可能与线上重号，**用运行时行为探针证「改动确在包内」**（如 PicSel 面包屑 / RealmKey 恢复日志 / 缓存行为）。
6. **产物校验**：`adb ... am start` 冷启验证进 MainActivity、登录态在、logcat 无 FATAL —— 通过才算被测包就绪。

> CLI 只覆盖 debug；release/上架包仍走正式签名流程。模块级编译/单测（`:im:imKit`/`:business:*`）加 `NODE_PATH` 即可（L18）。
