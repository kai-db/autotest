# ② 实现

## Implementation Log

### 2026-07-02 按 Accepted Plan v1 实施（docs-only）

| 步骤 | 文件 | 改动 |
|---|---|---|
| 1 | `docs/testing/app-knowledge/devices.md`（**新建**） | 设备清单（L1 `Pixel_10_Pro_XL` / L2 `debox_root` / L3 小米+三星）、能力矩阵、已知模拟器噪声（fake-IP 198.18.0.0/15、DNS 双层缓存）、一次性配置备忘（锁屏/保活/snapshot/注入复原） |
| 2 | `docs/testing/TEST_GUIDE.md` | 版本升 v1.2；目录树补 devices.md；一节「测试前检查」加设备阶梯+保活；二节铁律加「模拟器优先」条；Phase 1 描述扩充；**新增「七、设备策略（emulator-first）」**（7.1 阶梯与路由 / 7.2 锁屏保活 / 7.3 结论可信度+噪声预检 / 7.4 登录态快照 / 7.5 外部依赖自动轮询 / 7.6 人工介入白名单） |
| 3 | `docs/testing/TEST_CASES.md` | 模板头加设备层级说明；三张用例表加「设备」列（默认 L1） |
| 4 | `docs/testing/USAGE-使用教程.md` | 相关文档清单补 devices.md/第七节；**新增 0.2 设备选择速查**；第 2 节 B 模式前提改「优先模拟器」；第 3 节 AI 自动步骤同步 Phase 1 扩充 + 人工白名单五项 + §7.5 轮询；第 5 节速查铁律加 ⑧ 模拟器优先 |
| 5 | `CLAUDE.md` | 铁律 #4 细化为白名单五项引用 §7.6；新增铁律 #8 模拟器优先（指向 TEST_GUIDE 七 + devices.md） |

## Deviation Log

- **D1（轻微，已实施）**：`docs/testing/app-knowledge/README.md` 目录约定图补一行 `devices.md`。不在 Accepted Plan 文件清单内，但知识库 README 自身要求列出全部知识文件，属一致性必需；无行为影响。

## Consult Log

- N/A（无歧义/高风险决策点）。
