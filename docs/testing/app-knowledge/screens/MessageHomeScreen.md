# MessageHomeScreen（消息主页，冷启动落地页）

> 进入路径：冷启动默认页 / 底部 Tab「消息」
> 实现类型：原生 XML（元素树完整可取）
> 最后核实：2026-06-12

## 元素表

| 元素名 | 首选定位 | 备选定位 | 备注 |
|---|---|---|---|
| 页面标题 | resource-id=com.tm.security.wallet:id/tvTitle | text=DeBox | 顶部居中；tvTitle 在多页复用，需配合文本消歧 |
| 分类-全部 | text=全部 | bounds≈[72,118][130,144]（1080x2340） | 顶部分类条，选中态绿底 |
| 分类-私信/群组/俱乐部/P2P | text=私信 / 群组 / 俱乐部 / P2P | - | 同一分类条 |
| 消息 Tab | resource-id=com.tm.security.wallet:id/tvDao | bounds=[0,2126][270,2294] | ImageView 无文本无 desc |
| 朋友 Tab | resource-id=com.tm.security.wallet:id/tvFriend | bounds=[270,2126][540,2294] | 同上 |
| 发现 Tab | resource-id=com.tm.security.wallet:id/tvBrowse | bounds=[540,2126][810,2294] | 同上 |
| 我的 Tab | resource-id=com.tm.security.wallet:id/tvMine | bounds=[810,2126][1080,2294] | 同上 |
| 搜索 | 顶栏右侧放大镜图标，bounds 中心≈(848,169) | - | 未探索 |
| 加号菜单 | 顶栏最右 ⊕，bounds 中心≈(985,169) | - | 未探索 |

## 已知弹窗

| 弹窗 | 触发时机 | 处理方式 |
|---|---|---|
| （本轮未遇到） | 冷启动 ×3 均无弹窗 | 如遇权限/升级弹窗按通用规则处理 |

## 跳转关系

- 点 tvFriend → FriendsScreen；tvBrowse → DiscoverScreen；tvMine → MineScreen

## 注意事项

- **App 永不 idle**（常驻动画/轮询）：`uiautomator dump` 会报 could not get idle state，
  需用 `dumpsys activity top` 或框架内 waitForIdleTimeout=0 的查找；关动画也无效
- 页面判定特征词：「全部」（分类条，启动加载完成的标志）
