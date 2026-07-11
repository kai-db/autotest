# 用例：DeBox ANR 治理 + Realm key 恢复回归（dev 07ae7655..8960bb29）

> 从 `docs/testing/TEST_CASES.md` 模板生成。任务档案：`docs/implementation/2026-07-10-01-test-debox-anr-realm-regression/`（Accepted Plan v4）。
> **执行顺序 P0→P1→P2 全量**（⏸️ 不算跑完）；FAIL → 先记录（results.md）→ debox 仓另立修复任务（铁律#5）→ 全量重跑 → 不改码验收。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox：① IM ANR 治理（07ae7655）② PicSel 埋点（fc77707a）③ Realm key 恢复（75d44b39） |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | Claude Code + mobile-mcp（黑盒）+ adb + L2 instrumented 注入（组 R） |
| 前置条件 | 用户 AS 打含 8960bb29 的 debug APK + `assembleAppDebugAndroidTest`（autotest.enabled=true）test APK，装 L1/L2 |
| 被测包归因 | HEAD SHA + APK sha256 + versionCode + 签名 + 三族运行时探针（见 P0 前置） |

## 执行规范（全用例通用，plan v4 Phase C 总则）

- **知识库前置**：开测第 0 步通读 `app-knowledge/`（README/screens/弹窗/dangerous-ops/devices/network-domain），版本+已读清单记 results.md。
- **点击守卫**：黑盒每次点击前 dangerous-ops 比对（目标文本/坐标 + allow/stop 结论记 results.md）；命中危险词表 stop+截图+核对用例。
- **客观判据**：每例起止 `adb shell log -t autotest "TC-x START|END"` + 记 app PID；ANR 判定 = 窗口内 logcat 无 `ANR in com.tm.security.wallet` / 无 `Input dispatching timed out` /（L2）`/data/anr/` 无新 trace / 无系统 ANR 对话框；崩溃 = `mobile_list_crashes`+DropBox 对基线快照仅计窗口新增，PID 变化需归因（受控 force-stop 除外）。开测前采基线快照。
- **参数固定**：滚动=固定坐标 fling ×20；清未读循环 ×5 会话；冷启=force-stop→am start ×10；等待上限逐条列。

---

## 0. 框架 + 被测包前置（P0，阻断）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-B-001 | 被测包归因链 | 用户回报打包 HEAD SHA；`adb shell pm path`+pull APK 算 sha256；`dumpsys package` 取 versionName/Code/签名 | HEAD = 8960bb29 或后代；versionCode ≥ 21400001；签名与现装一致 | P0 | L1/L2 |
| TC-B-002 | 三族运行时探针（provenance 增强） | ①打开相册看 `PicSel` 面包屑（fc77707a）②进消息列表滚动看 cached-only/LocalAttrCache 行为日志（07ae7655）③（组 R 内）触发恢复见 `RealmKey recover*`（75d44b39） | 三族探针各自出现 = 三组改动确在包内 | P0 | L1/L2 |
| TC-B-003 | 安装兼容性预检 | 装包前核 applicationId/签名/versionCode | 三项兼容→`adb install -r`；任一不兼容→停止报告（绝不卸载/清数据） | P0 | L1/L2 |

## 1. 冒烟（P0）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-S-001 | 整体冒烟 | 冷启→主页→四 Tab 遍历→收发消息→退后台回前台 | 无新 FATAL/ANR/新异常弹窗；各页正常渲染 | P0 | L1/L2 |
| TC-R-001 | Realm 健康冷启动无回归 | force-stop→am start ×10（L1+L2 各跑） | 10/10 进 MainActivity，无 boot-loop、无 `RealmFileException`/`RealmUnavailable`；无 `RealmKey recovered`（健康路径不该触发恢复） | P0 | L1+L2 |

## 2. 功能回归（P1）

### 组 R · Realm key 恢复（L2 · 五步安全协议 + AVD 整盘快照兜底，见任务 plan Phase B-4）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-R-000 | 前置取证 | `emu avd snapshot save realm-pretest` + 五步②全量文件备份→scratchpad + 记旧库 md5/size/mtime | 快照成功；备份齐全；基线 md5 记录 | P1 | L2 |
| TC-R-002 | key 丢失+备份正确→自动恢复 | harness `copyMainKeyToBackup`+`removeMainKey`→force-stop 确认 PID 消失→冷启 | 进 App；logcat `RealmKey recovered=true source=PLAIN_BACKUP backfilled=true`；钱包数据完好；备份位被清除；再冷启正常（回填生效） | P1 | L2 |
| TC-R-003 | key 丢失+备份错误→受控退出 | harness `plantWrongBackup`+`removeMainKey`→冷启 | logcat `recover candidate miss source=PLAIN_BACKUP`（走 native probe 且失败，非 `candidate invalid`）→ `recovered=false` 受控退出（无 boot-loop/无 mint）；**旧库字节不变**（md5/size/mtime） | P1 | L2 |
| TC-R-004 | key 丢失+无备份→受控退出 | harness `clearBackup`+`removeMainKey`→冷启 | `recovered=false` 受控退出；旧库字节不变 | P1 | L2 |
| — | 每例复原 | `emu avd snapshot load realm-pretest`→冷启验证进 App+钱包数据在 | 复原成功；异常→保留快照+备份+报告 | P1 | L2 |

> ⚠️ 分轨断言：仅 TC-R-003/004（错误 key/全败）断言旧库字节不变；TC-R-002 正确恢复允许 migration/compaction 变化，断言=开库成功+数据在+回填+备份清除。

### 组 I · IM ANR 治理回归（L1 为主；三星测试机复核内容丰富面）

| # | 用例 | 步骤 | 验证标准（挂 implementation.md A-4 的 R1–R10） | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-I-001 | 消息列表滚动（R3/R4/R9） | 进较多消息会话 fling ×20 | 头像/昵称/扩展显示正确、无白块久留（cached-only miss 首帧降级可接受、不永久旧值）、无 ANR | P1 | L1/三星 |
| TC-I-002 | 进出会话清未读（R1/R2） | 造未读（bot/adb 灌）→进会话→退出 ×5 | 徽标最终清零、不残留、不错乱、无 ANR | P1 | L1/三星 |
| TC-I-003 | 本地搜索（R8） | 全局搜索最近联系人关键词 | 结果正确返回、输入不卡、无 ANR | P1 | L1/三星 |
| TC-I-004 | 会话置顶/免打扰（R5/R6） | 开关切换→退出重进 | 状态读取正确、开关初值不顽固错、无卡顿 | P1 | L1/三星 |
| TC-I-005 | 发送图片（P5' 正向） | 正常发图 | 发送成功、进程存活 | P1 | L1/三星 |
| TC-P-001 | PicSel 全序列（I-84） | 相册入口→授权→等 ≤3s `page_resumed`→返回键离场→等 ≤2s `page_paused` | 事件顺序 `open_gallery→perm_agree wait_ms→launch config_ms→page_resumed→page_paused`，各段数值非负合理 | P1 | L1 |

## 3. 稳定性/边界（P2）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-I-006 | 切号 epoch 边界 | **默认不执行**（登出=一类禁令）；用户显式授权+snapshot 兜底才跑：登出→登另一测试号→检未读/会话不串号→snapshot 恢复 | 不串号、不崩、无错误弹窗 | P2 | L2（授权后） |
| TC-I-007 | 弱网/断网（F-2 靶点） | 断网启动 App（connect 长挂 connecting）→消息列表 bind→恢复网络 | bind 用 extra 降级可用、不崩；CONNECTED 后缓存恢复 | P2 | L1/L2 |
| TC-I-008 | 空路径上传（I-87，P5' 目标分支） | harness 进程内构造 localPath=null 的 JImageMessage 调 startUpload（helper 落地）；否则→AR-3 | 不崩、消息置失败态、队列继续推进 | P2 | L2 |
| TC-P-002 | 权限拒绝分支 | revoke 权限后拒绝/取消 | `perm_reject`/`perm_cancel` 终止事件出现 | P2 | L1 |

---

## 用例数：前置 3 · P0 冒烟 2 · P1 回归 10 · P2 边界 4 = 19（TC-I-006 授权前跳过）
