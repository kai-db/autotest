# ShareDialogV2（分享弹窗 V2）

> 首次沉淀：2026-07-16（feat/share-refactor 测试）。debox 分享弹窗重构后的统一底部弹窗，
> 11 类型（WEB/DAPP/SWAP/QUOTES/QUOTES_DETAILS/GROUP/FRIEND/MOMENT/EVENT/SPACE/SPACE_FINISH/Image）。
> 实现类型：原生 DialogFragment（`io.rong.debox.share.ShareDialogFragmentV2`）。

## V2/V1 判定（重要）

- **主判据（可靠）**：`adb shell dumpsys activity top | grep -c ShareDialogFragmentV2`
  ——弹窗打开时命中 = V2；`ShareDialogFragment`（无 V2 后缀）= 老弹窗 V1。
- **弹窗 Fragment args 可读类型**：`dumpsys activity top` 内 `share_target=Quotes(url=...)` 等，
  能直接确认 ShareTarget 子类与深链 url（调试利器）。
- ⚠️ **uiautomator dump 不可靠**：DeBox「永不 idle」（常驻动画），dump 常返回陈旧树 →
  V2 布局 view-id（`scrollPreview`/`rvChannelList`/`rvSessionList`）经常 dump 不到。**优先用 Fragment 探针**。
- V1 老布局特征 id（若出现即回归）：`llShareImg` / `llQuotes`+`aaChartView`（`business/BaseModule` 老 `dialog_fragment_share.xml`）。

## 弹窗结构（截图观测，1344x2992）

- 上半：卡片预览区（类型自渲染，含二维码）。
- 中部：搜索框 +「会话横列」（最近会话头像，点击直接分享到该会话）。
- 下半：渠道行——`保存图片/分享图片`、`复制链接`（部分类型）、`更多`；底部「取消」。
- 复制链接成功 = toast「复制成功」+ 屏幕悬浮剪贴板气泡（含深链原文）。

## 各类型入口（本轮实测可达）

| 类型 | 入口路径 | 深链原文样例 |
|---|---|---|
| FRIEND | 好友/自己个人主页（`UserCenterActivity`）→ 右上 `ivMore`「...」→「推荐给好友」 | 名片二维码 |
| GROUP | 群会话 → 右上进群设置（`GroupDaoSettingActivity`）→ 右上分享箭头图标（bounds≈[1218,232]）| 群二维码 |
| MOMENT | 个人主页动态卡 → 卡片「...」更多菜单 →「分享」 | `https://s.debox.pro/moment?id=<id>&invite_code=<code>` |
| QUOTES | 发现页「行情」Tab → token 详情页（RN `ReactNativeContainerActivity`）→ 顶栏右分享图标 → 渠道点「复制链接」| `https://deswap.pro/market/detail/?quotesId=<addr>-<chain>&native=true` |
| QUOTES_DETAILS | 同 QUOTES 页 → 分享弹窗点「分享图片」= K 线卡片（含涨跌配色/市值/流动性/QR）| 同上 |
| WEB | 管理员页 `btnRN` 或深链 `debox://rn/profile/shares`（RN 名片分享页）| ⚠️ 页内「分享」按钮**未触发原生 V2 弹窗**（2026-07-16 实测，疑走 RN 内部分享；待确认预期） |

## 发送到会话（危险操作三类：用例明确要求 + 测试群 + 最小次数）

- 会话横列点头像 → 二次确认弹窗「确定分享到 <会话名> 吗?」→「确定」。
- 测试群 =「担保师测试群」（群名精确唯一匹配才可发；emulator-5554 会话列表可见）。
- 发送后卡片消息进目标会话（GROUP 显示「[群组分享]」邀请卡），点卡片可回跳。
- ⚠️ **无测试好友稳定 ID 登记** → FRIEND 端到端发送暂 BLOCKED（禁昵称匹配发送）。

## 保存图片

- 首次触发弹「权限申请」（DeBox 读图片/视频）→「开启」→ 系统 `GrantPermissionsActivity`「Allow all」。
- 保存成功 = MediaStore 新增行：`content query --uri content://media/external/images/media --projection _id:_display_name:date_added --sort '"date_added DESC"'`（冒号分隔 projection，本 AVD 实测可用）。

## 注意事项

- 旋转横竖屏弹窗自适应保留、不崩、进程不重启（Parcel 往返 OK）。
- 断网态弹窗仍可打开、卡片二维码正常渲染（OpenGraph 抓取失败降级不崩）。
- `cmd clipboard set-primary/get-primary` 在本 AVD 报 `No shell command implementation` → 用 toast+气泡验复制。
