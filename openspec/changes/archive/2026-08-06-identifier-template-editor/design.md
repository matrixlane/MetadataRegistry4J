# Design: identifier-template-editor

## Context

段模板引擎（`metadata-registry-common-facilities/identification/segment`）已具备 9 种段类型、三向派生（生成/校验正则/反向解析）、scope_key 化顺序号注册表（`(namespace_id, scope_key)` 唯一 + 行级排他锁）；策略管理 REST（草稿/发布/预览/预设/回填）与命名空间设置页设计器（`SegmentTemplateDesigner.vue`）均已上线。现状缺口集中在：初始化向导仍保留"段模板/UUID"策略单选且不能就地编排模板；引擎未强制唯一性段；顺序号作用域以 `scopeRefs` 原始参数暴露；对象缩写映射逐模板重复配置；系统内残留 WS/T 363 特例（预设资源、向导文案、测试金样本）。

约束：`scoped_identifier.identifier` 列长 255（`sequence_registry.scope_key` 长 512）；Hibernate `ddl-auto` 管理表结构，本期无表结构变更；初始化向导提交契约 `InitWizardSetupRequest.identifierTemplate` 已能携带模板 JSON。

## Goals / Non-Goals

**Goals**

- 标识生成策略在全部入口收敛为段模板唯一选择；编辑器规则内建（唯一性段恰好一个、作用域自动推导、缩写统一映射），用户编排即可得合法模板。
- 初始化向导就地嵌入编辑器，预览权威化（后端渲染，非前端模拟）。
- 清除 WS/T 363 特例字样与 RANDOM 段类型。

**Non-Goals**

- 变量段大类/小类编排及段间联动（选大类过滤小类）——忽略，二期再议。
- 模板表达式文本的粘贴解析（`{...}` 字符串到段列表）——本期表达式视图只读，二期可做双向。
- 显式作用域组合能力（如按 yyyyMM 月度重置）——随 scopeRefs 下线移出本期；如需恢复将以新的可视化作用域设计重新立项。
- 对象缩写映射的运行时管理 UI——本期为 JSON 配置文件调整，不做管理界面。

## Decisions

### D1：唯一性段 = SEQUENCE / TIMESTAMP / UUID 恰好一个

引擎 `validate()` 统计三类段出现次数，不等于 1 即报错；编辑器添加唯一性段时若已存在则引导替换。`RANDOM` 不计入且整个移除——短随机无唯一性保证，保留只会诱导错误配置。备选"允许多个唯一性段拼接"被否：语义冗余且增加反向解析歧义。

### D2：TIMESTAMP 作唯一性段强制毫秒级精度

判定规则：pattern 中连续 `S` 不少于 3 位（`SSS` 起），不足则校验报错而非警告——用户以时间戳承担唯一性职责，弱精度（秒级）在并发下必然撞车。微秒（`SSSSSS`）通过预设下拉推荐。`LocalDateTime.now()` 在现代 JDK 提供微秒级时钟，精度真实有效；并发撞车残余风险由 `(namespace_id, identifier)` 唯一约束兜底（见 Risks）。

### D3：顺序号作用域强制隐式，引擎自动推导

`SEQUENCE` 段不再读取用户提交的 `scopeRefs`：模板含 `OBJECT_ABBREVIATION` 段则作用域取该段解析值（每类型独立计数）；否则取 `__GLOBAL__`。实现上复用现有 scope_key 机制：引擎在模板装载/校验时把对象缩写段名归一化为内部 scopeRef，`SequenceRegistry`、分配锁、回填逻辑零改动。备选"保留 scopeRefs 仅供高级用户"被否：违背"强制、隐式、不可切换"的需求原文，且双通道配置必然产生不一致。

存量兼容：库中已发布模板若含显式 `scopeRefs`，装载时按 D3 归一化（静默重写为自动推导结果）并记 WARN 日志；含 `RANDOM` 段或缺唯一性段的存量模板在下次编辑/发布时报错，不影响只读展示历史配置。

### D4：对象缩写系统级静态映射表

新增 `object-abbreviations.json`（classpath 资源，用户可直接编辑调整），覆盖全部已注册元数据对象类型，初始内容（实现时以持久化类清单核对）：

| 对象类型（持久化类） | 缩写 |
|---|---|
| DataElement | DE |
| DataElementConcept | DEC |
| ObjectClass | OC |
| Property | PR |
| ConceptualDomain | CD |
| ValueDomain | VD |
| ValueMeaning | VM |
| DataType | DT |
| DataTypeScheme | DTS |
| UnitOfMeasure | UOM |
| MeasureClass | MC |
| Dimensionality | DIM |
| ClassificationScheme | CS |
| Concept | CN |

`ObjectAbbreviationSegmentHandler` 改为经映射表提供者解析；段参数 `mapping` 废止（校验忽略并提示），`default` 保留为未命中兜底。备选"映射表入库并提供管理 UI"被否：超出本期范围，JSON 文件满足"供用户调整"。

### D5：默认模板与预设库

向导默认模板（同时作为首个预设）：

```json
{"segments":[
  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
  {"type":"SEPARATOR","params":{"char":"."}},
  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5,"overflow":"REJECT"}}
]}
```

`identifier-presets/` 下四个通用预设（JSON 资源，key 与名称不含任何标准特例字样）：对象缩写+顺序号（`DE.00001`）、固定前缀+对象缩写+顺序号（`WN.DE.00001` 样式）、对象缩写+UUID、对象缩写+时间戳（`yyyyMMddHHmmssSSS`）。

### D6：无命名空间预览端点

`POST /identifier-strategy/preview`（body：configJson、variables、itemType）复用 `IdentifierStrategyAdminService` 的引擎渲染管线，返回 sample/formatRegex/validationErrors，与按命名空间预览端点共用实现（命名空间版仅多一步把持久化配置并入上下文）。向导与设置页设计器统一调用；设计器内本地简化预览逻辑删除，避免双实现漂移。

### D7：向导契约收敛

`InitWizardSetupRequest` 保留 `identifierStrategy` 字段但仅接受 `SEGMENT_TEMPLATE`（空值视为默认）；`identifierTemplate` 携带编辑器产出的模板 JSON，空则落 D5 默认模板；`createNamespaceStrategyConfig` 删除 `SEQUENCE`/`TEMPLATE`/`UUID` 分支；请求 DTO 中 `identifierPrefix`/`strategySeqStart`/`strategySeqPad` 字段随死代码一并移除。前端删除策略单选、两段死表单与相关校验规则。

### D8：编辑器交互增强

- 调色板分组：内容段（固定字符串/对象缩写/输入变量）、唯一性段（顺序号/时间戳/UUID）、修饰段（分隔符/校验位）。
- 唯一性段已存在时再点添加，弹确认框"替换现有唯一性段？"。
- 顺序号参数面板移除作用域项；面板底部静态说明作用域自动规则（含对象缩写则按类型计数，否则全局）。
- 时间戳 pattern 下拉预设：年月日 `yyyyMMdd`、完整到秒 `yyyyMMddHHmmss`、毫秒 `yyyyMMddHHmmssSSS`、微秒 `yyyyMMddHHmmssSSSSSS`，另允许自定义输入；选作唯一性段时若精度不足即时红字提示。
- 段块条上方只读模板表达式行，形如 `{LITERAL:WN}.{OBJECT_ABBREVIATION}.{SEQUENCE(pad=5)}`，随编辑实时刷新。
- 预览区新增"再生成一个"按钮（最多连续 5 个样例），顺序号样例按起始值递增展示。
- 变量段新增可选 `maxLength` 参数（默认 32），用于总长校验。

## Risks / Trade-offs

- [TIMESTAMP 唯一性段在并发下同一毫秒撞车] → `(namespace_id, identifier)` 唯一约束兜底；生成端捕获冲突后按有界重试（重取时间戳）处理；spec 场景固化该行为。
- [作用域强制隐式后，高级用户失去按月重置等组合能力] → 本期接受；Non-Goals 已声明，二期以可视化作用域设计恢复。
- [废止段内 `mapping` 使已发布模板语义迁移到系统级表] → 装载归一化加 WARN；系统级表初始内容含原常用映射（DE/VD 等），行为不回归。
- [唯一性段规则可能误伤合法双段诉求（如日期加序号）] → 规则来自需求原文"三者择其一"；日期加序号组合可用 TIMESTAMP 单段（含日期加毫秒）或变量段加 SEQUENCE 表达。
- [编辑器删除本地预览后，后端不可达时向导预览空白] → 预览失败降级为仅前端静态校验（唯一性段计数、必填参数），不阻断提交流程。

## Migration Plan

1. 引擎与 web 后端先行（校验规则、映射表、端点、向导契约），单元/集成测试同步重写。
2. 前端设计器增强与向导改造随后；预设资源整体替换。
3. 无需数据库迁移脚本（无表结构变更）；存量模板归一化在装载期完成。
4. 回滚：Git 还原本变更全部提交即可；无持久化格式变更，归一化只发生在内存装载。

## Open Questions

- 无。四个决策点（RANDOM 移除、时间戳精度报错、WS/T 363 清除与缩写静态表、作用域强制隐式）已与需求方确认。
