# App 知识库（DeBox）

> 借鉴 AppAgent 的「探索期/部署期」两阶段模式：AI 探索时把页面元素、已知弹窗、
> 跳转关系沉淀成结构化文档；后续每次 AI 测试 session **先读知识库再上手**，
> 不重复探索、不盲猜坐标。

## 目录约定

```
app-knowledge/
├── README.md            本文件（规范）
├── dangerous-ops.md     危险操作清单（点击前必须比对，见 TEST_GUIDE 铁律）
├── devices.md           设备清单/能力矩阵/已知噪声（选设备前必读，见 TEST_GUIDE 七）
└── screens/             页面知识，每页一个文件
    ├── _template.md     页面模板
    └── <ScreenName>.md  如 HomeScreen.md / WalletScreen.md
```

## 维护规则

1. **谁探索谁回写**：AI 黑盒探索中新确认的元素/弹窗/跳转，当轮就追加进对应页面文件
2. **定位方式按稳定性排序**：resource-id > content-desc > text > bounds（坐标只作兜底并注明来源分辨率）
3. **与指纹库/缓存的关系**：
   - 本知识库是「人和 AI 读的」语义层
   - `fingerprints.json`（指纹库）和 `*.cache.json`（用例缓存）是「框架回放消费的」机器层，由框架自动维护
   - 两层都可进版本库
4. **过期标注**：UI 改版导致元素失效时不要直接删，标 `~~已失效（日期）~~` 保留一轮，方便确认是回归还是改版

## 页面文件格式

见 `screens/_template.md`。每个页面记录：进入路径、元素表、已知弹窗、注意事项（RN/原生标记等）。
