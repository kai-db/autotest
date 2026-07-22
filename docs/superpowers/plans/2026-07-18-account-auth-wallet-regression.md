# 账号认证与钱包生命周期双 Run 回归实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重写并完整执行 2026-07-17 账号重验/可观测性与 2026-07-18 登录单飞/实例释放两个 AutoTest run，从空结果开始形成可复核的 Phase 2、Phase 5、Phase 6 证据闭环。

**Architecture:** 用一份现场构建的 DeBox 工作区 APK 作为共同被测制品，但两个 run 的 case 与结果独立。JVM/编译覆盖确定性并发状态机，gated androidTest harness 注入账号失效与重复登录事件，L1/L2/L3 设备覆盖真实 UI、后端登录、网络故障和多端踢下线。

**Tech Stack:** Android/Kotlin、Gradle、JUnit4、AndroidX Test、AutoTest 1.6.0、adb、android CLI、logcat、UiAutomator、iptables。

## Global Constraints

- 用户界面、测试文档和最终沟通使用中文。
- 以 `docs/testing/TEST_GUIDE.md` 的 Phase 1→6 为强制状态机；每条 case 执行前 `am force-stop` 后重启，除非 case 本身验证前后台/在途状态。
- 两个 `cases.md` 均为各自 run 的唯一执行源；不得用另一 run 的 PASS 代替本 run 的独立执行。
- 用户已授权本轮切账号、登出、创建账号和同账号多端被踢；仍禁止清数据、卸载、切环境、切测试链、删除钱包、导出/截图助记词或私钥、资金操作。
- 小米 `402714f0` 为正式环境，只读；账号与钱包操作仅在模拟器或三星测试环境执行。
- `adb install -r` 前必须比对签名且 versionCode 不降级；不允许用 uninstall 解决安装问题。
- L2 注入执行前后保存 `iptables -t nat -S OUTPUT` 和 `iptables -S OUTPUT`；只用与 `-A` 完全同参的 `-D` 精确复原，禁止 `-F`。
- 日志证据不得包含 token、Authorization、Bearer、签名、助记词、私钥或完整钱包地址；只保留固定事件、错误码、计数、布尔值和掩码 ID。
- 测试发现 FAIL 后先写两个 run 对应的 `results.md`；生产修复必须另建任务并走 `agent-dev-loop`，不得直接修改。
- 本轮不创建 git commit、不 push、不建 PR。
- 不使用 sub-agent；在当前会话内用 `superpowers:executing-plans` 顺序执行。

---

## 文件边界

**AutoTest 仓库**

- Modify: `docs/testing/runs/2026-07-17-账号重验与网络可观测性/cases.md`
- Modify: `docs/testing/runs/2026-07-17-账号重验与网络可观测性/results.md`
- Modify: `docs/testing/runs/2026-07-18-登录单飞与实例释放回归/cases.md`
- Create: `docs/testing/runs/2026-07-18-登录单飞与实例释放回归/results.md`
- Create: 两个 run 下 `evidence/{phase2,phase5,phase6}/` 与 `screenshots/{phase2,phase5,phase6}/`
- Modify only when discovered: `docs/testing/app-knowledge/screens/*.md`、`docs/testing/app-knowledge/devices.md`

**DeBox 仓库**

- Create: `app/src/androidTestAutotest/java/com/currency/wallet/testinjection/AccountAuthInjectionHarness.kt`
- Temporarily modify and restore: `app/build.gradle`，仅用于 CodePush debug shim。
- Do not modify production code unless Phase 4 由独立 `agent-dev-loop` 任务批准。

---

### Task 1: 冻结源码、设备与旧结果基线

**Files:**
- Create: 两个 run 的 `evidence/phase2/00-source-baseline.txt`
- Create: 两个 run 的 `evidence/phase2/00-device-baseline.txt`

- [x] **Step 1: 记录 DeBox 代码范围和工作区指纹**

Run in `/Users/xiaochengcheng/StudioProjects/debox-android`:

```bash
git rev-parse HEAD
git log --reverse --oneline b8fd28ddffca966c4cd9ff3b97e6dd1c1107e51f^..HEAD
git status --short
git diff --name-status HEAD
git diff --cached --name-status
git ls-files --others --exclude-standard
git diff HEAD -- . ':(exclude)docs/anr/**' | shasum -a 256
```

Observed: HEAD=`e0650d2b13...`；G3/G4/G5 与实例释放修复已提交于 `c27bf78f54`；工作区另含 BTC JS↔Native 派生、消息签名、PSBT 能力 androidTest。完整范围和指纹已写入两个 run 的基线证据。

- [x] **Step 2: 记录设备真实映射，禁止按序列号猜 AVD**

```bash
adb devices -l
adb -s emulator-5554 shell getprop ro.boot.qemu.avd_name
adb -s emulator-5556 shell getprop ro.boot.qemu.avd_name
for serial in emulator-5554 emulator-5556 402714f0 RFCYA0F9SSZ; do
  adb -s "$serial" shell dumpsys package com.tm.security.wallet | rg 'versionName=|versionCode=|userId='
done
```

Expected: 当前已知映射为 5554=`Pixel_10_Pro_XL`、5556=`debox_root`；若变化，以命令结果更新 cases/results。

- [x] **Step 3: 记录旧文档规模但不沿用旧结论**

```bash
wc -l docs/testing/runs/2026-07-17-账号重验与网络可观测性/{cases.md,results.md}
wc -l docs/testing/runs/2026-07-18-登录单飞与实例释放回归/cases.md
```

Expected: 7 月 17 日旧结果只作背景；7 月 18 日尚无 results。

### Task 2: 重写两个 cases 并从模板重建 results

**Files:**
- Modify: `docs/testing/runs/2026-07-17-账号重验与网络可观测性/cases.md`
- Modify: `docs/testing/runs/2026-07-17-账号重验与网络可观测性/results.md`
- Modify: `docs/testing/runs/2026-07-18-登录单飞与实例释放回归/cases.md`
- Create: `docs/testing/runs/2026-07-18-登录单飞与实例释放回归/results.md`

**Interfaces:**
- Consumes: 设计文档安全边界、DeBox 变更范围、`TEST_CASES.md`/`TEST_RESULTS.md`。
- Produces: 两份按 P0→P1→P2 可直接执行的唯一用例源。

- [x] **Step 1: 重写 7 月 17 日 run 的 32 条用例**

Use `apply_patch` to replace the file with these groups and IDs:

```text
P-001 AutoTest 自检与发布
P-002 DeBox JVM/编译门禁
P-003 APK 现场构建、签名、哈希与运行时归因
P-004 设备/环境/账号/钱包物料基线
A-001 -2007 本地钱包自动重签重登
A-002 -2018 本地钱包自动重签重登
A-003 -2023 本地钱包自动重签重登
A-004 高频失效同周期抑制
A-005 登录成功后门禁重开
A-006 旧认证世代回包抑制
A-007 TP/WC 重验失败重弹
A-008 CacheService 鉴权拉闸
A-009 换代后同步恢复
A-010 正常使用阴性观察
B-001 connect-refused call_id 关联
B-002 失败日志脱敏
B-003 connect-timeout 原因归类
B-004 业务错误与网络错误分离
B-005 Sol 链路 call_id
C-001 JIM 冷启动 episode
C-002 断网恢复 episode
C-003 同账号多端 KICKED_OFFLINE
C-004 被踢后重新登录与 IM 恢复
D-001 助记词/BTC 钱包登录
D-002 私钥钱包登录
D-003 同钱包重复登录单飞
D-004 登录中切钱包旧回包抑制
D-005 创建账号首次登录与重启保持
D-006 登出后重新登录
E-001 后台清理/冷启动实例释放
E-002 十轮释放与登录交错
E-003 四 Tab 与登录后业务冒烟
```

Every row includes: commit/worktree source, exact precondition, numbered actions, machine oracle, priority, device, cleanup, danger note. Do not retain the unrelated share QR case.

- [x] **Step 2: 优化 7 月 18 日现有 22 条专项 case**

Keep existing IDs, with these exact corrections:

```text
P-002: remove zero-balance restriction; require backed-up account and test environment.
S-004: logout is explicitly authorized; add relogin verification.
F-001/F-002: bind to AccountAuthInjectionHarness methods and safe log counts.
F-003: remain BLOCKED; wallet deletion is still unauthorized.
F-004/F-005: separate JVM breaker evidence from device remote-session invalidation; iptables cannot manufacture auth codes.
F-006: add UI responsiveness and ANR oracle on L1; L3 is confirmation.
F-007: record BTC address types/counts, never full addresses.
F-008/F-009: missing TP/WC material is BLOCKED, never substituted by local wallet.
T-002: lease expiry uses deterministic JVM evidence; device hang is supplemental.
T-004: aggregator path failure uses JVM evidence unless a real bridge fixture exists.
```

- [x] **Step 3: 从模板重建两个结果文件**

Use `apply_patch`; each result begins with:

```markdown
# 测试结果 — 账号重验与网络可观测性

> 测试日期：2026-07-18
> 被测代码：NOT_RUN（Phase 1 尚未执行）
> 被测 APK：NOT_RUN（Phase 1 尚未执行）
> 状态：Phase 1 环境确认中；旧结果全部作废，本文件从空结果重建

## 环境确认
## Phase 2 首轮全量测试
## Phase 3 缺陷与归因
## Phase 5 全量回归
## Phase 6 最终验收
## 环境复原
```

- [x] **Step 4: 文档静态校验**

```bash
rg -n '待定|稍后实现|沿用旧结果|待补' docs/testing/runs/2026-07-17-账号重验与网络可观测性/{cases.md,results.md} docs/testing/runs/2026-07-18-登录单飞与实例释放回归/{cases.md,results.md}
git diff --check -- docs/testing/runs/2026-07-17-账号重验与网络可观测性 docs/testing/runs/2026-07-18-登录单飞与实例释放回归
```

Expected: 无模糊占位；构建任务把 NOT_RUN 替换为实测值；diff check clean。7 月 18 日结果文件使用同一结构但标题为“登录单飞与实例释放回归”。

### Task 3: 新增 gated 账号认证注入 harness

**Files:**
- Create: `/Users/xiaochengcheng/StudioProjects/debox-android/app/src/androidTestAutotest/java/com/currency/wallet/testinjection/AccountAuthInjectionHarness.kt`
- Test: same file, invoked with AndroidJUnitRunner on L1/L2.

**Interfaces:**
- Consumes: `AccountTokenState(Int)`、`RequestFail`、`AppCacheManager`、`MainActivity`。
- Produces: `tokenOverdueBurst`、`accountValidaOnce`、`needAgainLoginOnce`、`repeatSameWallet`、`releaseInstanceDuringLogin`。

- [x] **Step 1: 创建 test-only harness**

Use `apply_patch` with this implementation:

```kotlin
package com.currency.wallet.testinjection

import android.os.SystemClock
import android.util.Log
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.app.base.business.livebus.AccountTokenState
import com.app.base.business.network.RequestFail
import com.app.business.base.module.AppCacheManager
import com.currency.wallet.main.activity.MainActivity
import com.jeremyliao.liveeventbus.LiveEventBus
import org.junit.Assert.assertNotNull
import org.junit.Test

class AccountAuthInjectionHarness {
    @Test fun tokenOverdueBurst() = withMainActivity("token_overdue_burst") {
        repeat(8) { postAuth(RequestFail.TOKEN_OVERDUE) }
    }
    @Test fun accountValidaOnce() = withMainActivity("account_valida_once") {
        postAuth(RequestFail.ACCOUNT_VALIDA)
    }
    @Test fun needAgainLoginOnce() = withMainActivity("need_again_login_once") {
        postAuth(RequestFail.NEED_AGAIN_LOGIN)
    }
    @Test fun repeatSameWallet() = withMainActivity("repeat_same_wallet") {
        val manager = AppCacheManager.get()
        val wallet = manager.getWallet()
        assertNotNull("wallet required", wallet)
        repeat(8) { manager.setWallet(wallet) }
    }
    @Test fun releaseInstanceDuringLogin() = withMainActivity("release_during_login") {
        val manager = AppCacheManager.get()
        val wallet = manager.getWallet()
        assertNotNull("wallet required", wallet)
        manager.setWallet(wallet)
        Thread { manager.setNull() }.start()
    }
    private fun postAuth(code: Int) {
        LiveEventBus.get(AccountTokenState::class.simpleName!!, AccountTokenState::class.java)
            .post(AccountTokenState(code))
    }
    private fun withMainActivity(name: String, block: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("launch intent required", launchIntent)
        context.startActivity(launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            Log.i(TAG, "event=$name stage=start")
            block()
            Log.i(TAG, "event=$name stage=posted")
        }
        SystemClock.sleep(15_000)
    }
    companion object { private const val TAG = "AccountAuthInject" }
}
```

The harness must not read or log token/signature/address/key values.

- [x] **Step 2: 编译 harness，确认只进入 androidTest**

```bash
NODE_PATH="$PWD/ReactNative/node_modules" ./gradlew :app:compileAppDebugAndroidTestKotlin --no-configure-on-demand
```

Expected: `BUILD SUCCESSFUL`; normal app compilation excludes the source.

- [x] **Step 3: 静态安全扫描**

```bash
rg -n 'token\s*=|signature\s*=|mnemonic|privateKey|Authorization|Bearer' app/src/androidTestAutotest/java/com/currency/wallet/testinjection/AccountAuthInjectionHarness.kt
git diff --check -- app/src/androidTestAutotest/java/com/currency/wallet/testinjection/AccountAuthInjectionHarness.kt
```

Expected: no secret-value logging; diff check clean.

### Task 4: 运行框架、JVM 与编译门禁

**Files:**
- Modify: both `results.md` environment sections.
- Create: both runs' `evidence/phase2/01-unit-gates.txt`.

- [x] **Step 1: 验证并发布 AutoTest**

```bash
./gradlew :autotest:compileReleaseKotlin :autotest:test :autotest:publishToMavenLocal
```

Expected: `BUILD SUCCESSFUL`, 0 failed tests.

- [x] **Step 2: BaseBusiness 网络/认证世代门禁**

```bash
NODE_PATH="$PWD/ReactNative/node_modules" ./gradlew :business:BaseBusiness:testDebugUnitTest --tests '*AuthTokenStoreTest' --tests '*StaleAuthFlowTest' --tests '*StaleAuthGuardTest' --tests '*NetworkCallTraceTest' --tests '*NetEventListenerSanitizeTest' --tests '*NetEventListenerTraceIntegrationTest' :business:BaseBusiness:compileDebugKotlin --no-configure-on-demand
```

Expected: all selected tests PASS and compile succeeds.

- [x] **Step 3: BaseModule 登录/BTC/熔断门禁**

```bash
NODE_PATH="$PWD/ReactNative/node_modules" ./gradlew :business:BaseModule:testDebugUnitTest --tests '*LoginAttemptCoordinatorTest' --tests '*BtcDerivationAggregatorTest' --tests '*AuthSyncBreakerTest' --tests '*CacheServiceBatchKeyTest' --tests '*AppLoginFailurePolicyTest' :business:BaseModule:compileDebugKotlin --no-configure-on-demand
```

Expected: LoginAttemptCoordinator 21 tests plus BTC/breaker/batch/failure-policy suites all PASS.

- [x] **Step 4: moduleMain 与 imKit 状态机门禁**

```bash
NODE_PATH="$PWD/ReactNative/node_modules" ./gradlew :business:moduleMain:testDebugUnitTest --tests '*AccountRevalidationGateTest' --tests '*RevalidationReshowPolicyTest' :business:moduleMain:compileDebugKotlin --no-configure-on-demand
NODE_PATH="$PWD/ReactNative/node_modules" ./gradlew :im:imKit:testDebugUnitTest --tests '*ImConnectionEpisodeTrackerTest' :im:imKit:compileDebugKotlin --no-configure-on-demand
```

Expected: all tests and both compilation tasks succeed.

### Task 5: 现场构建、签名预检与保数据安装

**Files:**
- Temporarily modify: DeBox `app/build.gradle` if required.
- Modify: both results with SHA, APK hash, version, signer, device list.

- [x] **Step 1: 用 apply_patch 临时加入 CodePush shim**（N/A：现有配置直接构建成功，未修改）

Insert immediately before `apply from: codePushGradle`:

```gradle
if (project.extensions.findByName("react") == null) {
    project.extensions.add("react", [debuggableVariants: project.objects.listProperty(String).convention(["appDebug"])])
}
```

- [x] **Step 2: 构建 app 与 androidTest APK**

```bash
NODE_PATH="$PWD/ReactNative/node_modules" ./gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest --no-configure-on-demand
find app/build/outputs/apk -type f -name '*.apk' -print
shasum -a 256 app/build/outputs/apk/app/debug/debox-debug.apk
```

Expected: `BUILD SUCCESSFUL`; app and androidTest APKs exist.

- [x] **Step 3: 反向 apply_patch 移除 shim**（N/A：未加入 shim）

Expected: `git diff -- app/build.gradle` equals pre-build state.

- [x] **Step 4: 比对签名、版本并保数据安装**

```bash
test_apk=$(find app/build/outputs/apk/androidTest -type f -name '*.apk' -print -quit)
test -n "$test_apk"
apksigner verify --print-certs app/build/outputs/apk/app/debug/debox-debug.apk
for serial in emulator-5554 emulator-5556; do
  adb -s "$serial" shell dumpsys package com.tm.security.wallet | rg 'versionName=|versionCode='
  adb -s "$serial" install -r app/build/outputs/apk/app/debug/debox-debug.apk
  adb -s "$serial" install -r "$test_apk"
  adb -s "$serial" shell run-as com.tm.security.wallet pwd
done
```

Expected: signer equal, no downgrade, both installs `Success`; signer mismatch on Samsung means L3 BLOCKED without uninstall.

### Task 6: Phase 1 环境与账号物料确认

**Files:**
- Modify: both results' environment sections.
- Modify when newly learned: `docs/testing/app-knowledge/devices.md`.

- [ ] **Step 1: 屏幕保活、冷启动和 UI 基线**

```bash
for serial in emulator-5554 emulator-5556; do
  adb -s "$serial" shell svc power stayon true
  adb -s "$serial" shell am force-stop com.tm.security.wallet
  adb -s "$serial" shell monkey -p com.tm.security.wallet -c android.intent.category.LAUNCHER 1
done
android layout --device=emulator-5554 -p
android layout --device=emulator-5556 -p
```

Expected: MainActivity visible, no crash. Use fresh layout before every tap.

- [ ] **Step 2: 只读核验环境、账号和钱包类型**

Record nickname/test ID and wallet type only. Confirm mnemonic/BTC, private-key, TP/WC material. Missing material blocks only dependent cases.

- [ ] **Step 3: 网络噪声预检**

```bash
adb -s emulator-5554 shell getent hosts t.debox.pro
adb -s emulator-5556 shell getent hosts t.debox.pro
```

Expected: `198.18.0.0/15` is recorded as fake-IP noise.

### Task 7: 执行 7 月 17 日 run 的 Phase 2

**Files:**
- Create: run 17 `evidence/phase2/*`, `screenshots/phase2/*`
- Modify: run 17 `results.md`

- [ ] **Step 1: 执行 P0 账号失效与单飞注入**

```bash
adb -s emulator-5554 shell am instrument -w -e class 'com.currency.wallet.testinjection.AccountAuthInjectionHarness#tokenOverdueBurst' com.tm.security.wallet.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'com.currency.wallet.testinjection.AccountAuthInjectionHarness#accountValidaOnce' com.tm.security.wallet.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'com.currency.wallet.testinjection.AccountAuthInjectionHarness#needAgainLoginOnce' com.tm.security.wallet.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class 'com.currency.wallet.testinjection.AccountAuthInjectionHarness#repeatSameWallet' com.tm.security.wallet.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: one revalidation episode per burst, one effective app_login, all three codes auto-resign local wallet, no stale terminal event.

- [ ] **Step 2: 执行 B 组 L2 网络故障并逐条复原**

Resolve `APP_UID`; snapshot both OUTPUT chains; add exact DNAT/DROP; trigger real request; delete exact rule; compare snapshots. Expected refused/timeout reason, phase, elapsed, and same call_id.

- [ ] **Step 3: 执行真实钱包生命周期**

Use UI for mnemonic/BTC login, private-key login, in-flight switch, account creation, logout/relogin. Never capture seed/private-key pages.

- [ ] **Step 4: 执行双端 kicked 与恢复**

Bring same test-environment account online on emulator and Samsung; observe `KICKED_OFFLINE_BY_OTHER_CLIENT`; recover by explicit relog/switch. Expected no infinite reconnect/login storm and new connected episode.

- [ ] **Step 5: 执行余下 P1/P2 和阴性观察**

Run all remaining IDs, including 10-minute normal browsing and 10 restart interleavings. Expected no unexpected revalidation, FATAL, ANR, or ERROR storm.

- [ ] **Step 6: 写完 Phase 2/3 结果后才允许修复**

Every ID gets PASS/FAIL/BLOCKED/NOT_RUN and evidence. BLOCKED includes owner/reason/follow-up/expiry.

### Task 8: 执行 7 月 18 日 run 的 Phase 2

**Files:**
- Create: run 18 `evidence/phase2/*`, `screenshots/phase2/*`
- Modify: run 18 `results.md`

- [ ] **Step 1: 独立重跑 P/S 组**

Reset app/logcat per case. Expected independent evidence for mnemonic/private-key login, switch, logout/relogin.

- [ ] **Step 2: 独立执行 F-001/F-002**

Run harness `repeatSameWallet` and `releaseInstanceDuringLogin`; cold-start after release. Expected C1 merge at most one app_login; disposed instance no SUCCESS/FAIL; new instance logs in.

- [ ] **Step 3: 执行 F-004~F-007**

Combine deterministic JVM breaker evidence with real remote-session invalidation; measure responsiveness/ANR; inspect BTC types/counts only. Expected one breaker per generation and recovery after login.

- [ ] **Step 4: 执行 F-008/F-009 与 T 组**

Use actual TP/WC only; otherwise BLOCKED. Lease/BTC failure use named JVM tests. Run ten release/login interleavings and sync/switch interleaving.

- [ ] **Step 5: 保留 F-003 BLOCKED 并完成结果**

Wallet deletion is not executed and not counted PASS.

### Task 9: Phase 3 归因与 Phase 4 修复循环

**Files:**
- Modify: both results Phase 3.
- Create only if FAIL: DeBox `docs/implementation/2026-07-18-fix-${CASE_ID}/`，`${CASE_ID}` 使用实际失败用例号（例如 `A-001`）。

- [ ] **Step 1: 对每个 FAIL 做证据归因**

Classify product defect, fixture defect, environment issue, or unreachable prerequisite. Do not downgrade a product failure to BLOCKED.

- [ ] **Step 2: FAIL>0 时逐个走 agent-dev-loop**

Create accepted plan, plan review, implementation, implementation review, and backlink. No direct production edits.

- [ ] **Step 3: FAIL=0 时显式记录无 Phase 4 代码改动**

Expected: no empty Phase 4 section.

### Task 10: Phase 5 全量回归与 Phase 6 独立验收

**Files:**
- Create: both runs `evidence/phase5/*`, `evidence/phase6/*`, screenshots
- Modify: both results Phase 5/6/statistics/restoration

- [ ] **Step 1: Phase 5 独立全量重跑两个 cases.md**

Expected: all executable cases rerun; FAIL=0 before Phase 6.

- [ ] **Step 2: 冻结代码与 APK 指纹**

Expected: Phase 6 uses exact Phase 5-passed source/APK; any change restarts Phase 5.

- [ ] **Step 3: Phase 6 零代码改动全量验收**

Expected: new evidence, no Phase 5 reuse; both runs FAIL=0.

- [ ] **Step 4: 复原设备与网络**

Verify Wi-Fi/data enabled, proxy `:0`, L2 iptables equal baseline, no stale instrumentation, app cold-starts on intended environment/account.

- [ ] **Step 5: 最终文档校验**

```bash
git diff --check -- docs/testing/runs/2026-07-17-账号重验与网络可观测性 docs/testing/runs/2026-07-18-登录单飞与实例释放回归 docs/testing/app-knowledge
git status --short
```

Expected: both results contain Phase 1/2/3/5/6, statistics, restoration; no secrets; unrelated user changes untouched.
