# Proposal: identifier-template-editor

## Why

`namespace-identifier-strategy` 已落地段模板引擎与段规则设计器，但初始化向导的命名空间步骤仍暴露"段模板 / UUID"策略单选，模板只能接受内置默认、无法就地编排；同时引擎层缺少"唯一性段"强制规则，顺序号作用域（scopeRefs）以原始参数形式暴露给用户，理解门槛高，且系统中残留 WS/T 363 特例预设与变量段（大类/小类）编排。需要把标识生成策略收敛为段模板唯一选择，把设计器提升为功能完整、规则内建的标识模板编辑器，并在初始化向导中就地可用。

## What Changes

- **策略收敛为段模板唯一选择（BREAKING）**：初始化向导删除"策略类型"单选，`InitWizardService` 策略白名单收敛为仅接受 `SEGMENT_TEMPLATE`；`UUID`/`SEQUENCE`/`TEMPLATE` 旧值一律拒绝（初始化面向新装系统，无存量迁移包袱）。UUID 仍保留为模板中的一个段组件。
- **唯一性段规则**：模板 MUST 恰好包含一个唯一性段（`SEQUENCE` / `TIMESTAMP` / `UUID` 三者择其一），引擎校验与编辑器实时提示双侧强制。`TIMESTAMP` 作唯一性段时，pattern MUST 精确到毫秒（`S` 至少 3 位），否则校验报错。
- **顺序号作用域强制隐式化（BREAKING）**：模板含 `OBJECT_ABBREVIATION` 段时，顺序号 MUST 按对象缩写值区分作用域（每种对象类型独立计数），该行为强制、隐式、不可切换回全局；不含对象缩写段时为命名空间级全局计数。模板 JSON 不再接受显式 `scopeRefs`；原"按大类.小类计数""按月重置"两个场景本期移出（变量段大类/小类忽略）。
- **对象缩写静态映射表**：`OBJECT_ABBREVIATION` 段的缩写解析改为系统级静态映射表（覆盖全部已注册元数据对象类型），存于 JSON 配置文件供用户调整；段参数中的 `mapping` 废止，保留可选 `default` 兜底。
- **移除 RANDOM 段类型（BREAKING）**：从段类型目录、编辑器调色板与引擎校验中移除；含 `RANDOM` 段的模板校验报错。
- **清除 WS/T 363 特例**：删除 `identifier-presets/wst363-data-element.json` 及代码、UI、测试中的全部 WS/T 363 字样；向导默认模板改为 `{对象缩写}.{顺序号}`（如 `DE.00001`），预设库更换为通用编排：对象缩写+顺序号、固定前缀+对象缩写+顺序号、对象缩写+UUID、对象缩写+时间戳。
- **编辑器增强**：唯一性段互斥引导（已有其一时再添加提示替换）；顺序号作用域不再暴露配置项（自动推导）；时间戳格式预设下拉（含毫秒/微秒）+ 自定义；新增模板表达式视图（如 `{LITERAL:WN}.{OBJECT_ABBREVIATION}.{SEQUENCE(pad=5)}`，只读）；试生成多个样例；模板各段最大输出长度之和 MUST ≤ 255（`scoped_identifier.identifier` 列上限），变量段按声明最大长度计（未声明按 32 计）。
- **初始化向导内嵌编辑器**：命名空间步骤就地嵌入标识模板编辑器（默认载入"对象缩写+顺序号"编排）；`MANUAL_ONLY` 输入模式下编辑器禁用；删除向导中 `SEQUENCE`/`TEMPLATE` 死代码表单与对应字段。
- **无命名空间上下文预览端点**：新增 `POST /identifier-strategy/preview`（configJson + 变量样例 + 条目类型），向导与设置页共用，预览/正则/校验与运行时生成完全一致，取代向导场景的本地简化预览。

## Capabilities

### New Capabilities

（无新能力；全部落在既有能力的需求变更上。）

### Modified Capabilities

- `identifier-template-designer`：段类型目录移除 RANDOM；新增唯一性段规则与时间戳精度规则；顺序号作用域改强制隐式（撤销显式 scopeRefs 及大类/小类、按月重置场景）；对象缩写改系统级静态映射表；设计器 UI 增强（互斥引导、表达式视图、试生成、长度校验、预设更换）；新增无命名空间预览端点。
- `identifier-auto-generation`：生成期作用域推导规则（含对象缩写按其值计数、否则全局）；唯一性段缺失/多占的生成期拒绝。
- `system-initialization`：向导策略收敛为段模板唯一选择并内嵌编辑器；默认模板更换；策略白名单 BREAKING 收敛；清除 WS/T 363 字样。

## Impact

- **后端引擎（common-facilities）**：`SegmentTemplateEngine.validate` 新增唯一性段/时间戳精度/长度上限/禁显式 scopeRefs/禁 RANDOM 规则；`ObjectAbbreviationSegmentHandler` 改系统级映射表解析；`SegmentType`/`SegmentTypeRegistry` 移除 RANDOM；`SequenceSegmentHandler` 作用域自动推导。
- **web 模块**：`InitWizardService.createNamespaceStrategyConfig` 白名单收敛、默认模板更换；`IdentifierStrategyAdminController` 新增无命名空间预览端点；预设资源目录更换；新增 `object-abbreviations.json` 配置资源。
- **前端**：`SegmentTemplateDesigner.vue` 增强（互斥引导、作用域 UI 移除、时间戳预设、表达式视图、试生成、长度校验、本地预览逻辑同步）；`InitWizard.vue` 删除策略单选与死代码、内嵌编辑器；`identifierStrategy.ts` 增加新端点调用。
- **数据库存量**：既有已发布模板若含显式 `scopeRefs`/`RANDOM`/段内 `mapping`，装载时按新规则归一化或报错（详见 design.md 兼容性处理）；无表结构变更。
- **测试**：段引擎单测按新规则重写；向导集成测试更新；清除 WS/T 363 金样本并改为通用编排金样本。
