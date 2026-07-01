# 测试指南

> 版本：v1.1 | 更新日期：2026-06-12
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
1. 确认设备在线（adb devices 或 MCP list_devices）
2. 查看当前状态
3. 启动目标 App
```

---

## 二、操作铁律

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
  设备在线（无设备则启动模拟器）→ App 可启动
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
