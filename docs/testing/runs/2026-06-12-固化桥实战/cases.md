# 固化桥实战 — 冒烟用例

> 目标：验证「AI 黑盒探索 → CachedCase 缓存落盘 → 真机确定性回放」完整链路（docs/09 §2）。
> 约束：只读导航，不碰 dangerous-ops 清单中任何操作；重置 = terminate → launch。

## TC-S-001 冷启动停在消息页 [P0]

1. terminate → launch DeBox
2. 处理弹窗（如有）
3. 验证：消息页标题出现、顶部分类「全部」可见、底部 4 Tab 齐全

## TC-S-002 底部 Tab 导航 [P0]

1. terminate → launch DeBox
2. 点「朋友」→ 验证朋友页特征
3. 点「发现」→ 验证发现页特征
4. 点「我的」→ 验证我的页特征
5. 点「消息」→ 验证回到消息页

## TC-S-003 登录态校验 [P0]

1. terminate → launch DeBox
2. 点「我的」
3. 验证已登录特征（总资产/转账可见）——本用例同时是每轮前提校验

## 产出要求（固化桥三件套）

- `docs/testing/cache/TC-S-00x.cache.json`（CachedCase 格式，target 含 res-id+text+bounds）
- `docs/testing/app-knowledge/screens/`：消息/朋友/发现/我的 四页元素表
- 本目录 `results.md`
- 回放验证：缓存推真机 → `connectedAndroidTest` CacheReplay 跑通
