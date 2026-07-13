# 

> 飞书 wiki 原文：https://deboxsocial.sg.larksuite.com/wiki/MkLkwmTD5in4J4k7B0MlXOYdgld （本文件为 2026-07-13 导出的本地备份，流程图为画板导出 PNG）

# 一、五轮测试演进

整套方案由 AI 驱动真机测试（Claude Code + MCP，autotest 框架）验证，共五个轮次，是同一条"网络域名容灾"链路的连续演进——每一轮的产出（BUG / 观察项）都成为下一轮的输入：

| 轮次 | 主题 | 规模 | 关键产出 |
|-|-|-|-|
| 2026-06-12 | 阿里云 HTTPDNS 接入 | 67 条用例，8 轮迭代 | 🔴 BUG-001：OSS 配置格式契约不匹配，HTTPDNS 静默失效（已修复回归） |
| 2026-06-13 | 域名动态切换 | 26 条 | 🟡 OBS-01：离线重启误清已切域名（已修复：全不可达保留当前域名） |
| 2026-06-23 | 域名 / HTTPDNS 深度测试 | 81 自动化 + 25 真机 | 密钥激活后首次全链路实测；HTTPDNS happy 路径生产域名 PASS；厘清注入手段边界 |
| 2026-06-25 | 自愈鲁棒性增强 | A13+B6+D10 | 🔴 持久化域名不回灌 base URL（已修复，请求分布 100:4 → 102:14 翻转验证） |
| 2026-07-01 | 回环 bogon 兜底 + IM 多 navi | 50 条 | 回环兜底全链路闭环；BUG-002 IM 备用域名四步定位（运维两项修复） |

# 二、故障注入手段

域名容灾测试的难点在于"如何在真机上制造出目标故障"。五轮下来沉淀的注入手段及其能触发的路径：

| 注入手段 | 效果与边界 |
|-|-|
| Private DNS 坏 specifier（DNS 污染） | ✅ 产生 UnknownHostException，进 OkHttp 拦截器，触发请求级切换 + HTTPDNS FALLBACK |
| 整机断网（svc wifi/data disable） | ❌ 被 App 网络守卫短路（-100 网络未连接），不进拦截器；仅能测启动健康探测"全不可达"分支 |
| 本地 HTTP 服务 + adb reverse 模拟 OSS | ✅ 回放真实 OSS 配置，验证配置链路 |
| 可 root 模拟器 + 自建 DNS 响应器 + iptables DNAT | ✅ 注入回环污染（目标域名返回 ::1 / 127.0.0.1），补齐非 root 真机无法覆盖的盲区 |
| 可 root 模拟器 + iptables 单封 + 假 TLS 服务器 | ✅ 构造 TCP 通但 TLS 断的三层误判场景 |
| config-swap 死主 navi（wss://127.0.0.1）+ 真备 | ✅ 验证 IM 多 navi 竞速 failover |

# 三、关键结论

**域名切换全链路真机验证通过。**滑动窗口阈值触发、30 秒冷却防乒乓、TCP 可达优选（实测日志「域名切换 debox.pro -> dbxsocial.com (可达优选)」）、故障计数启动衰减（15→7→3）、请求自动重发，均在测试与正式环境验证。

**HTTPDNS happy 路径在生产域名实测通过。**DNS 污染期间 httpdns_hit ip_count=2，业务请求持续 200——HTTPDNS 用真实 IP 救活了被污染的请求。

**回环兜底实测闭环。**注入 ::1 污染后 connect 埋点回环 0 条（从未连过本机），完整升级链 poisoned → UnknownHost → FALLBACK → 切域名逐环坐实。

**IM 竞速实测确认。**死主 + 真备场景下备用域名被并发尝试并 onOpen 胜出，客户端 failover 机制健全。

**两道自愈协作验证。**同一 IOException 同时驱动域名切换与 HTTPDNS FALLBACK，口径一致、互不替代。

# 四、方法论：全绿掩盖被真实世界打破

五轮测试里最有价值的经验是两次"全绿掩盖"被打破的过程。BUG-001（HTTPDNS 静默失效）在单测、集成、真机全绿的情况下存活——因为测试用的模拟配置是嵌套格式，而线上真实 OSS 配置是扁平格式；直到拿真实线上配置做核对才暴露。回环黑洞场景则是任何内部测试都没想到的，由真实用户工单驱动。两条教训：**测试数据必须对齐真实生产物料**；**可 root 模拟器 + 网络层注入（iptables / 自建 DNS）是补齐真机覆盖盲区的关键手段**。

# 五、遗留待办

- [ ] AI 代理与 RN 通道接入容灾体系（当前裸 OkHttpClient，无切换 / 无 HTTPDNS / 无重发）

- [ ] IM 接入 HTTPDNS 或兜底 IP（主备域名同时被污染场景，P0-C3 ②③）

- [ ] 运维：测试环境 t.debox.pro 托管进 EMAS 控制台

- [ ] 运维：IM 备用域名迁出 EdgeOne，实现平台级冗余

- [ ] 产品决策：重启是否优先试主域名（当前切换跨启动"粘住"）

# 六、测试记录位置

完整用例与证据在 autotest 仓库：docs/testing/runs/ 下按"日期-功能"分目录（2026-06-12-阿里云HTTPDNS、2026-06-13-域名动态切换、2026-06-23-域名HTTPDNS深度测试、2026-06-25-域名HTTPDNS自愈鲁棒性增强、2026-07-01-HTTPDNS回环bogon兜底），每个目录含 cases.md、results.md 与 evidence/ 日志；机制权威总结在 docs/testing/app-knowledge/network-domain.md。
