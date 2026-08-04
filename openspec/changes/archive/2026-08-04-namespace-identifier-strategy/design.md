# Design: 命名空间性质与段模板标识生成策略

## 背景

现状（代码事实）：

- `IdentifierGenerationStrategy` 接口（`generate(Namespace)`）+ UUID/URN/SEQUENCE/TEMPLATE 四种策略；`IdentifierStrategyConfig` 已支持版本化（DRAFT/PUBLISHED/DISABLED）、命名空间绑定与全局默认回退。
- TEMPLATE 为扁平占位符字符串（`{seq:N}`、`{yyyy}`、`{prefix}` 等），白名单字符校验；无"段"结构、拿不到条目上下文、不能反向解析。
- `SequenceRegistry` 每个命名空间仅一个计数器（uk: `namespace_id`），不支持按标识组成部分为作用域计数。
- `ScopedIdentifierService` 生成入口仅传 `Namespace`，无变量值通道；策略配置无 REST API、无 UI。
- WS/T 363 附录 A 编排方式：前缀 DE + 大类 2 位 + 小类 2 位 + 序号 3 位 + 附加码 2 位（实例 `DE01.00.001.00`），序号按"大类.小类"作用域计数。
- RA 与自有命名空间关系 1:1（两边 0..1），遵循 ISO/IEC 11179-3 原文档。

## 决策

### D1：业务标识策略收敛为段模板一种

`Scoped_Identifier` 是业务标识，标识本身由具有直观意义的段组合而成；顺序号、UUID 等都是模板设计的组件而非独立策略。策略选项收敛为段模板；UUID/URN/SEQUENCE 不再作为独立策略选项——SEQUENCE 由"单顺序号段模板"等价表达，UUID 可作为组合标识中的一个段组件使用。

### D2：模板 = 段的有序列表

模板不再是一维占位符字符串，而是段（Segment）的有序列表，JSON 持久化于 `IdentifierStrategyConfig.configJson`。段类型目录：

| 类型 | 效果 | 关键参数 |
|---|---|---|
| LITERAL 固定字符串 | 规则固定部分（如自定义前缀） | value |
| OBJECT_ABBREVIATION 元数据对象缩写 | 按被注册条目的对象类型取缩写（数据元→DE、值域→VD/CV） | mapping（对象类型→缩写，可配置） |
| SEQUENCE 顺序号 | 自动递增 | start、increment、pad、scopeRefs、overflow |
| UUID 段 | 随机 UUID（完整或短型） | form（full/short） |
| SEPARATOR 分隔符 | 段间分隔，多种字符可选 | char（受限字符集：`.` `-` `_` `/` `:` 等） |
| TIMESTAMP 时间戳 | 当前日期/时间格式化 | pattern（yyyy、yyyyMM、yyyyMMddHHmmss 等） |
| CHECKSUM 校验位 | 对前序段拼接结果计算校验 | algorithm（luhn / mod11 / 自定义权重） |
| VARIABLE 输入变量 | 注册时由用户/调用方给值（如大类、小类） | name、label、pattern、pad、default、required |
| RANDOM 随机（可选，低优先） | 定长随机字符 | length、alphabet |

说明：

- **OBJECT_ABBREVIATION** 使同一套模板可跨对象类型复用：注册数据元时该段产出 DE，注册值域时产出 VD/CV（WS/T 363 的 DE 与 WS/T 364 的 CV 正是同一编排的两类实例）。生成上下文因此必须携带被注册条目的对象类型（由 `ScopedIdentifierService` 从 identifiedItem 推导传入）。
- **SEPARATOR** 作为一等组件，取值受限于分隔字符集——既贴合"分隔符（多种字符可选）"的用户心智，也让正则派生与反向解析有明确的切割点。
- **组件目录可扩展**：每种段类型以"类型注册表"项实现（参数 schema + 生成器 + 正则片段 + 解析片段 + UI 配置面板），后续新增组件类型只需新增注册项，不改引擎核心。
- VARIABLE 段使"不强制分类流程"成立：大类/小类在注册表单按模板动态渲染输入框，系统只负责校验与拼装。

### D3：顺序号作用域 = 引用段值组成 scope key

序号作用域由顺序号段的 `scopeRefs`（引用同模板中其他段的名称）决定：

- 不引用任何段 → 命名空间级全局计数（现行行为的特例）；
- 引用变量段（大类 + 小类）→ WS/T 363 的按小类各自计数（`DE02.01.xxx`）；
- 引用日期段（yyyyMM）→ 按月自动归零的流水号。

实现：`SequenceRegistry` 增加 `scope_key` 列，唯一约束改为 (`namespace_id`, `scope_key`)；空 scope_key 即现行行为，向后兼容。计数分配沿用既有悲观锁路径（`SequenceRegistryService`），仅把查询键从 namespace 扩展为 namespace + scope_key。

### D4：一份段定义派生三种能力

1. **生成器**：注册时按段拼装；
2. **格式校验正则**：用于自有命名空间内手工录入时的格式校验，以及导入既有标识时的批量校验；
3. **反向解析器**：把既有标识拆回段值，支持**序号回填**——导入一批既有标识后，按解析结果对齐各 scope 的计数器，后续生成不撞号。

### D5：命名空间性质二分与设置分支

- 性质：初始化时随注册机构创建 = 自有；此后创建 = 外部。性质创建时确定，MUST NOT 变更（变更会使既有标识的语义归属混乱）。
- 自有命名空间设置：段规则设计器（段块条 + 段类型调色板 + 类型化参数面板 + 实时预览 + 预设库）+ 版本自动化策略（沿用 `Namespace.versioningPolicy`：SEMVER/SEQUENTIAL/DATE）。
- 外部命名空间设置：标识/版本手动编辑；可增加可选的格式校验规则（直接配置正则表达式，附样例即时测试；仅校验不生成）；无自动生成配置入口。
- 预设库以卫生行业为首（WS/T 363 数据元标识符），预设为 JSON 资源，可扩展。

### D6：外部命名空间禁止生成，全局回退短路

现有解析顺序（命名空间专属 → 全局默认回退）若对外部命名空间生效，全局默认会悄悄替外部命名空间生成标识，违反"外部标识由外部签发"的语义。生成入口先判性质（判定依据来自 `active-registrar-ownership`）：外部命名空间直接拒绝无标识创建请求，MUST NOT 进入策略解析与全局回退。

### D7：初始化必须创建自有命名空间

初始化必须创建注册机构，且必须同时创建其关联的自有命名空间（取消 `2026-08-02-governance-init-all-editions` 引入的"命名空间步骤可跳过"语义）。`setup` 的 `createNamespace` 语义改为强制 true：缺省与显式 true 正常执行；显式 false 返回业务校验错误。本变更归档顺序应在 `2026-08-02-governance-init-all-editions` 之后，spec 增量按最终语义协调。

### D8：与 active-registrar-ownership 的边界

本变更只消费"自有/外部"判定结果；判定本身（活动 Registrar 硬约束、判定链捋直）在 `active-registrar-ownership` 中定义。两个变更可独立实施：若判定能力尚未就绪，本变更落地时以"命名空间关联 RA 是否为本系统注册机构"作为临时判定（单 RA 部署下等价），待其就绪后切换为基于活动 Registrar 的会话判定。
