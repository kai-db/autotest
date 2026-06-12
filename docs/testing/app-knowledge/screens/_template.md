# <ScreenName>（页面中文名）

> 进入路径：<如：启动 App → 底部 Tab「我的」>
> 实现类型：原生 XML / ReactNative / H5（影响元素可取性）
> 最后核实：YYYY-MM-DD

## 元素表

| 元素名 | 首选定位 | 备选定位 | 备注 |
|---|---|---|---|
| messageTab | resource-id=com.tm.security.wallet:id/xxx | text=消息 | 底部 Tab |

## 已知弹窗

| 弹窗 | 触发时机 | 处理方式 |
|---|---|---|
| 升级提示 | 冷启动偶现 | 点「稍后再说」 |

## 跳转关系

- 点 <元素> → <目标页面>

## 注意事项

- <如：列表为 RecyclerView，目标项可能需滚动；该页含 RN 区域，元素树不可见时退坐标>
