# Implementation — debox 新需求深度测试（Zego 会话恢复 + 字号）

## 被测范围（b113587c 之后新提交）

| 提交 | 内容 | 测试策略 |
|---|---|---|
| `f09ca3f20a` | ZEGO 语音房会话状态机+受控重进（消 1002001/1002033）：3 新 Kotlin 类 + ZegoManager/ChatRoomManager 改造 | 独立核码 + 45 单测 + 静态/smoke（行为 reconnect 见下限制） |
| `eaada99344` | 字号档位 0.9/1.0/1.1/1.2/1.3（上限 1.5→1.3） | ✅ 代码核实（MAX_SCALE=1.3、SCALE_LEVELS 已改，trivial） |

## 已完成验证

- **Zego 单测（当前树重跑）**：ZegoRoomSessionStateMachine 16/16 + Controller 16/16 + RecoveryCoordinator 13/13 = **45/45 PASS**（核心状态机/控制器/恢复编排逻辑无回归）。
- **静态 provenance**：`debox/ZegoRoomSessionStateMachine`/`Controller`/`RecoveryCoordinator` 均在 APK classes8.dex → Zego 修复确在被测包。
- **自主构建**：含 Zego 的 debug 包（react shim + assembleAppDebug，构建后还原 shim）→ 246MB，装 L1 Success。
- **Smoke（L1）**：冷启进 MainActivity、存活、0 FATAL/ANR；四 Tab 遍历（消息/好友/浏览/我的）正常。
- **字号**：代码核实通过（无需真机——档位常量改动，UI 设置页会读该常量）。

## 行为 reconnect 测试限制（如实）

Zego 恢复的**真实行为路径**（语音房断网→JIM 重连→RECONNECT_FAILED→check_current_room→受控重进）需：① 活跃语音房 ② 2 账号 ③ RTC 音频 ④ 网络注入触发重连。L1 账号 10b92305 空（无房间可进）；L2 root 被 GeeGuard 拦；语音房 UI 可能 RN（debug 无 bundle）。→ **行为 reconnect 黑盒不可达**，核心靠 45 单测 + 独立核码兜底（同 IM ANR 深路径口径）。独立核码报告见下。

## 独立核码报告（Zego）

（待 agent 完成合入）

## 独立核码结论（Zego f09ca3f20a，2026-07-11）

**声称 vs 实码：全部属实且实现严谨**——①1002001（JIM 重连不再 loginRoom、消除 join_init 误静音）②1002033 受控重进（清场 clearCdnRouteState 严格早于 applyInitialPlayRoute 早于 loginRoom，无凭证撕裂窗口）③5s logout 超时强制推进不悬挂 ④非法状态转移全拒 ⑤generation 门禁在**执行时**校验（正确）。45 单测（16+16+13）覆盖纯逻辑不变量扎实。文案实为 1 条 string 五语言化（顺带丢弃 extendedData 展示）。

### 独立发现的风险（单测覆盖不到的集成边界，按严重性）

| # | 等级 | 发现 | 状态 |
|---|------|------|------|
| Z-1 | **Important（潜在致命）** | `onChatroomJoin`(ChatRoomManager.kt:236) **同步**调 `attemptZegoLogin`→状态机 `requestLogin`(threadGuard=main)，**无 runOnMain 保护**。若 JIM 该回调非主线程→登录被静默 Rejected+DEBUG 崩。整个 1002001 修复押在"JIM chatroom 回调在主线程"这一**代码未保证**的假设上。**本席已核实：该入口确实无 post/runOnMain，而同文件 L1277 有现成 main 守卫助手却未用于此入口。** | 真 bug 风险，修法 trivial（入口 wrap runOnMain） |
| Z-2 | Important | logout 失败/超时停 `ReconnectFailed`，回 Idle 仅靠跨 manager 隐式链 `quit()→logoutRoom()→reset()`；状态机自身无失败态超时自愈。某失败分支若绕过 quit→同房间永久 `AlreadyActive` 进不去（至进程重启） | 需真机验每条失败分支都 reset |
| Z-3 | Important | coordinator 仅 logout 阶段有 5s 超时；`fetchCurrentRoom`(check_current_room)/login 阶段**无独立超时**，靠 HTTP 框架 onFinish 兜底。若请求既不 onSuccess 也不 onFinish→coordinator 永停 FETCHING | 需真机压测受控重进途中反复退/切房 |
| Z-4~7 | Low | 孤儿编排(lost-race logout 靠调用方 cancel)/extendedData 丢失/成功回调读可变字段/AlreadyActive 非 LoggedIn 分支不刷房信息 | 观察项 |

### 行为 reconnect 真机测试（R1-R15，见独立报告）——**本环境黑盒不可达**

真实 reconnect 路径需 活跃语音房+2 账号+RTC+网络注入；L1 空号无房间、L2 GeeGuard 拦、语音房 UI 疑 RN(debug 无 bundle)。→ 核心靠 45 单测 + 独立核码兜底；行为 R1-R15 与 Z-1~3 的真机验证**移交用户环境**（需真实语音房）。

## 结论

Zego 改动**逻辑层验证通过**（声称属实+45 单测+静态在包+smoke 无崩）。**独立核码新发现 Z-1（潜在致命的线程假设）+ Z-2/Z-3（状态回收/超时兜底集成缺口）**——单测覆盖不到,建议 Z-1 补 runOnMain（trivial）,Z-2/Z-3 真机压测或补超时兜底。字号改动 trivial 通过。
