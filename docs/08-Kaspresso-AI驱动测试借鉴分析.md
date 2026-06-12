# Kaspresso 对 AI 驱动测试的借鉴分析

> 目标：服务 `Claude Code / Codex + mobile-mcp` 测 DeBox。
> 原则：**当前阶段不改 autotest 核心源码**，只借 Kaspresso 的方法论、证据链、用例固化方式和外围工具思路。

## 1. 结论

Kaspresso 本身是白盒 UI 自动化框架，不是 AI 测试框架。对我们最有价值的不是把它的核心能力搬进 `autotest`，而是借它解决 AI 测试最容易失败的三个问题：

1. **AI 怎么稳定看懂现场**：截图不够，需要 view hierarchy、logcat、设备状态一起作为证据。
2. **AI 怎么把黑盒探索变成可复用资产**：用 Page Object 思路沉淀页面元素，而不是每次重新猜坐标/文本。
3. **AI 怎么输出可审计结果**：每条用例必须有步骤、证据、失败原因、可复现路径。

所以当前阶段只做外围整合：

- 文档和流程
- `docs/testing/runs/` 产物规范
- DeBox 页面对象草稿规范
- AI 执行手册
- 后续可选脚本

不动 `autotest/src/main` 核心实现。

## 2. Kaspresso 项目里真正对 AI 有用的点

### 2.1 Page Object：把 AI 探索结果沉淀成页面资产

Kaspresso 区分两类页面对象：

| 模型 | 使用场景 | 对 DeBox 的价值 |
|---|---|---|
| `KScreen` | 有源码、有 `R.id` 的白盒页面 | 后续 DeBox 接入 AAR 后可用 |
| `UiScreen` | 系统页、三方 App、无源码页面 | 当前 AI 黑盒测试最有用 |

对 AI 测试来说，最重要的是 `UiScreen` 这个思路：即使不改 DeBox，也可以把页面元素以“语义名 + 定位方式”记录下来。

建议在 `docs/testing/runs/<run>/screens.md` 记录：

```markdown
## DeBoxHomeScreen

| 元素名 | 定位方式 | 备注 |
|---|---|---|
| messageTab | text=消息 | 底部 Tab |
| friendsTab | text=朋友 | 底部 Tab |
| discoverTab | text=发现 | 底部 Tab |
| mineTab | text=我的 | 底部 Tab |
```

这样下一轮 AI 不需要重新探索底部 Tab，只需要引用已有页面资产。

### 2.2 View hierarchy：让 AI 不只看截图

Kaspresso 失败时会 dump view hierarchy。这个对 AI 比截图更重要，因为 hierarchy 里能看到：

- `text`
- `resource-id`
- `content-desc`
- `bounds`
- 当前可访问节点层级

当前 AI 黑盒测试 DeBox 时，每个失败步骤都应该保留：

```text
step-03/
├── screenshot.png
├── view_hierarchy.xml
├── logcat.txt
└── note.md
```

这可以先作为流程要求写入 `results.md`，不需要改核心框架。

### 2.3 Artifacts：每一步都要留证据

Kaspresso 的 Allure/artifacts 体系说明了一点：UI 自动化不是只给 PASS/FAIL，而是要保存完整证据。

AI 驱动测试建议统一 run 目录：

```text
docs/testing/runs/YYYY-MM-DD-feature/
├── cases.md
├── results.md
├── screens.md
└── artifacts/
    ├── TC-S-001/
    │   ├── step-01-launch/
    │   │   ├── screenshot.png
    │   │   ├── view_hierarchy.xml
    │   │   └── logcat.txt
    │   └── step-02-check-home/
    └── TC-S-002/
```

这个目录结构是 AI 能否复盘的关键，比现在只写 `PASS/FAIL` 有用。

### 2.4 ADB timeout：AI 测试不能无限等

Kaspresso 最新更新是 ADB server timeout 可配置化。对我们当前 AI 测试的借鉴不是引入它的 AdbServer，而是流程里必须规定：

- 每次启动 App 有最大等待时间。
- 每次截图有最大等待时间。
- 每次查找元素有最大等待时间。
- 每次 logcat/hierarchy 采集有最大等待时间。
- 超时必须记录到 `results.md`，不能让 AI 卡住。

建议在 AI 测试手册里固定：

```text
单步操作超时：10s
页面稳定等待：2s
App 启动超时：30s
截图/层级采集超时：10s
单用例最大耗时：3min
```

### 2.5 Kautomator：黑盒页面 DSL 思想

Kautomator 把 UIAutomator 包成可读 DSL。对我们现阶段不需要实现代码，但可以借它的“写法规范”指导 AI 固化用例。

AI 输出固化建议时，不要写：

```text
点击坐标 (540, 1800)
```

而要写：

```text
点击 DeBoxHomeScreen.messageTab
定位：text=消息，bounds=[...]
```

坐标只能作为兜底，不作为首选定位。

### 2.6 page-object-code-gen：从 XML 生成页面草稿

Kaspresso 有工具从 UI dump XML 生成 Page Object。我们当前不改核心，也可以先借流程：

1. AI 通过 mobile-mcp / adb 获取 `view_hierarchy.xml`。
2. AI 从 XML 提取 `text/resource-id/content-desc/bounds`。
3. 生成 `screens.md` 页面元素表。
4. 后续需要白盒回归时，再把 `screens.md` 转成 Kotlin Screen 类。

这是“黑盒探索 → 白盒固化”的桥。

## 3. 当前不建议借的点

| Kaspresso 能力 | 当前不借原因 |
|---|---|
| 完整拦截器体系 | 会动核心，且复杂度过高 |
| AdbServer 桌面端 | 需要额外 jar/端口/权限，当前 mobile-mcp + adb 已够 |
| Allure 全量接入 | AI 更适合读 Markdown/JSON，不需要先引入 Allure |
| Compose support | DeBox 当前重点不是 Compose |
| Marathon runner | 还没跑通第一轮 AI 黑盒，不应先做并行调度 |
| Robolectric sharedTest | AI 端到端测试依赖真机状态，短期价值不高 |

## 4. 对当前项目的整合方式

不改核心源码，只改文档和运行产物规范：

| 要整合的内容 | 放置位置 | 目的 |
|---|---|---|
| AI 测试证据目录规范 | `docs/testing/TEST_GUIDE.md` | 规定每步留截图/hierarchy/logcat |
| DeBox 页面对象表 | `docs/testing/runs/<run>/screens.md` | 固化 AI 探索出来的元素 |
| 单步超时策略 | `docs/testing/TEST_GUIDE.md` | 防 AI 卡住 |
| 失败记录模板 | `docs/testing/TEST_RESULTS.md` | 让失败可复盘 |
| Kaspresso 借鉴说明 | 本文档 | 说明为什么这么做 |

## 5. 建议更新的测试结果模板

后续每条 FAIL 不要只写原因，建议写成：

```markdown
### TC-S-001 冷启动

- 结果：FAIL
- 失败步骤：step-02-check-home
- 现象：启动后停留在权限弹窗
- 首选定位：text=允许
- 兜底定位：bounds=[...]
- 证据：
  - screenshot: artifacts/TC-S-001/step-02-check-home/screenshot.png
  - hierarchy: artifacts/TC-S-001/step-02-check-home/view_hierarchy.xml
  - logcat: artifacts/TC-S-001/step-02-check-home/logcat.txt
- 判断：测试环境问题 / 产品问题 / 用例问题 / 待确认
- 下一步：关闭权限弹窗后重试
```

## 6. AI 执行策略

AI 每执行一个用例时按这个顺序：

1. 启动前检查设备和 App 状态。
2. 执行步骤前先截图 + 获取可访问元素，避免盲点坐标。
3. 优先用 `text/resource-id/content-desc` 定位。
4. 坐标点击只作为兜底，并必须记录 bounds 来源。
5. 失败时采集 screenshot + hierarchy + logcat。
6. 更新 `results.md`，再考虑是否修复或重试。
7. 把稳定元素追加到 `screens.md`。

## 7. 后续可做但仍不动核心的脚本

可以新增 `scripts/`，不改 `autotest/src/main`：

| 脚本 | 作用 |
|---|---|
| `scripts/create-test-run.sh` | 创建 `runs/YYYY-MM-DD-feature/` 目录 |
| `scripts/extract-screen-elements.kt` | 从 `view_hierarchy.xml` 提取元素表 |
| `scripts/check-run-artifacts.sh` | 检查每条 FAIL 是否有截图/hierarchy/logcat |
| `scripts/summarize-results.kt` | 汇总 results.md 为简短报告 |

这比改核心更适合当前阶段。

## 8. 最小落地顺序

1. 更新 `docs/testing/TEST_GUIDE.md`：加入证据链、超时、页面对象表规则。
2. 更新 `docs/testing/TEST_RESULTS.md`：加入 FAIL 证据模板。
3. 第一次 DeBox 冒烟测试时创建 `screens.md`。
4. 跑完一轮后，根据真实 hierarchy 再决定要不要写脚本。
5. 只有当黑盒流程稳定后，再考虑把 Screen 转成 Kotlin 白盒用例。

## 9. 参考

- Kaspresso：https://github.com/KasperskyLab/Kaspresso
- Page Object：https://kasperskylab.github.io/Kaspresso/Wiki/Page_object_in_Kaspresso/
- Kautomator：https://kasperskylab.github.io/Kaspresso/Wiki/Kautomator-wrapper_over_UI_Automator/
- ADB commands：https://kasperskylab.github.io/Kaspresso/Wiki/Executing_adb_commands/
- Kaspresso configuration：https://kasperskylab.github.io/Kaspresso/Wiki/Kaspresso_configuration/
- Screenshot tests：https://kasperskylab.github.io/Kaspresso/Wiki/Screenshot_tests/
