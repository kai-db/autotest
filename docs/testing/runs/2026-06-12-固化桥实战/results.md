# 固化桥实战 — 测试结果

> 用例见同目录 `cases.md`
> 测试日期：2026-06-12 | 设备：三星 SM-S9210（RFCYA0F9SSZ，Android 16）
> 被测：DeBox 正式包 `com.tm.security.wallet`（已登录 + 测试环境，只用不破坏）
> 前提校验：✅ 冷启动消息列表含「测试club112 / Kobe测试 / 测试通知」（测试环境数据特征）+ 我的页 Lv.16 账号（已登录）

## 第 1 轮：AI 黑盒探索（Claude Code + mobile-mcp）

| 用例 | 结果 | 判定依据 |
|---|---|---|
| TC-S-001 冷启动停在消息页 | ✅ PASS | 截图：DeBox 标题 + 分类条「全部/私信/群组/俱乐部/P2P」+ 底部 4 Tab |
| TC-S-002 底部 Tab 导航 | ✅ PASS | 朋友（新增关注/关注41/粉丝484）→ 发现（行情/DApp）→ 我的（总资产/转账）→ 回消息，截图齐 |
| TC-S-003 登录态校验 | ✅ PASS | 独立重置后我的页「总资产 + 转账 + Lv.16」可见 |

**黑盒小计：3/3 PASS**

### 探索中发现的问题（环境/工具层，非产品 bug）

| # | 现象 | 处理 |
|---|---|---|
| 1 | `uiautomator dump` 持续报 `could not get idle state`（App 常驻动画/轮询，关动画也无效） | 改用 `dumpsys activity top` 取 view 树；已记入 MessageHomeScreen.md 注意事项 |
| 2 | mobile-mcp `list_elements_on_screen` 报 `Cannot read properties of undefined (reading 'node')` | 同因（a11y 树取不到）；截图 + dumpsys 兜底 |
| 3 | 底部 4 Tab 为 ImageView，无文本无 content-desc | 黑盒只能坐标点击；缓存 target 用 res-id（tvDao/tvFriend/tvBrowse/tvMine），白盒回放不受影响 |

## 第 2 轮：固化桥回放验证（connectedAndroidTest，零 LLM）

缓存 `docs/testing/cache/TC-S-00{1,2,3}.cache.json` 经 androidTest assets 打进 test APK，
`CaseCacheStore(READ_ONLY)` 加载 + `CacheReplay` → `UiReplayExecutor` + `SelfHealingLocator` 真机回放：

```
Starting 3 tests on SM-S9210 - 16
replay_TC_S_001_coldStart      8.3s  ✅ PASS
replay_TC_S_002_tabNavigation 11.0s  ✅ PASS
replay_TC_S_003_loginState     7.0s  ✅ PASS
BUILD SUCCESSFUL（testsuite: tests=3 failures=0 errors=0）
```

**回放小计：3/3 PASS | 自愈事件：0（全部确定性命中，无 UI 回归）**

## 结论

✅ **固化桥链路首次端到端跑通**：AI 黑盒探索 → CachedCase 缓存落盘（进版本库）→
test APK 确定性回放（terminate/launch/click-by-res-id/wait-text 全部真实执行）。
回归阶段重跑这 3 条用例的命令（无需任何 LLM）：

```bash
./gradlew :autotest:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.autotest.debox.DeBoxCacheReplayTest
```

## 产物清单

- `docs/testing/cache/TC-S-00{1,2,3}.cache.json` —— 首批用例缓存
- `app-knowledge/screens/{MessageHome,Friends,Discover,Mine}Screen.md` —— 四页元素表（Mine 标注危险元素）
- `autotest/src/androidTest/.../DeBoxCacheReplayTest.kt` —— 回放测试（缓存经 assets 打包）
- `autotest/build.gradle` —— androidTest assets 源指向 docs/testing/cache
