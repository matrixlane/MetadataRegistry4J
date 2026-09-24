# 变更提案:Dimensionality(维度)管理功能

## Why

ISO/IEC 11179-31:2023 §7.5.2.3 定义的 Dimensionality 类(Concept 子类,Table 22 属性 `coordinate_indicator`,§7.5.3.1 关联 `dimensionality_measure_class`)在本仓库中只有实体与表结构(`dimensionality`、`dimensionality_measure_class` 已在基线迁移中),缺少完整管理功能:

1. 后端无任何 Dimensionality 管理 API(无 Repository/Service/Controller),维度条目无法登记、检索、维护;
2. 前端 `/dimensionality` 菜单项已配置但路由指向 `PlaceholderPage`,注册状态为 `planned`;
3. `ConceptualDomain.dimensionality`(0..1,Table 2)属性虽已落库(审计 dc5a3da),但 Request/Response DTO 未暴露,概念域页无法选择维度——维度无挂接点;
4. §7.4.2.1.5.2 / §7.4.5.3 的维度一致性规则(概念域指定维度后,基于它的值域的计量单位必须与该维度一致)无任何校验。

## What Changes

### 1. Dimensionality CRUD API(新增)

- `/api/v1/dimensionalities`:分页列表(`q` 按指称忽略大小写包含)、`GET /{id}`、`POST`、`PUT /{id}`、`DELETE /{id}`。
- 请求体覆盖:sourceUri、designations、definitions、registration(Concept 公共内容,经 `ConceptContentApplier`) + `coordinateIndicator`(Boolean 三态) + `measureClassIds`(0..\*)。
- 响应在 ConceptResponse 同构字段上附加 `coordinateIndicator` 与 `measureClasses`(id+label 列表)。
- 删除保护:被 conceptual_domain 引用时拒绝并返回引用数。
- 迁移 `V20260921001`(sqlite/sqlserver/dm 三方言):存量 `coordinate_indicator NOT NULL` 放宽为可空(Table 22 三态 NULL=Not applicable;验证期发现审计 #7 仅改实体未改存量列)。

### 2. ConceptualDomain 维度接线

- `ConceptualDomainRequest` 增加 `dimensionalityId`(空=清除);`ConceptualDomainResponse` 增加 `dimensionalityId`/`dimensionalityLabel`。
- `ConceptualDomainService` create/update 应用维度(存在性校验),响应回填;类型重建(rehydrate)路径保留维度。

### 3. 维度一致性校验

`ValueDomainAssociationValidator` 在 VD↔CD 关联与 VD↔UoM 关联时校验:CD.dimensionality 与 VD.unitOfMeasure 均存在时,UOM 的 measureClasses 与维度的 measureClasses 交集必须非空(§7.4.2.1.5.2、§7.4.5.3)。

### 4. 前端

- 新增 `/dimensionality` 维度管理页(列表 + 新建/编辑对话框;属性页含坐标指示三态选择与计量类多选;SmartItemPicker 已支持 MeasureClass/Dimensionality 类型)。
- 路由注册状态 `planned` → `implemented`;`meta.itemType.dimensionality` i18n。
- 概念域页新增维度选择(SmartItemPicker 单选),提交透传 `dimensionalityId`。

### 5. 测试与契约

- `DimensionalityServiceTest` 新建;`ConceptualDomainServiceTest`、`ValueDomainAssociationValidator` 测试增补。
- 再生成 `docs/technical/api/openapi.json`。

## Non-goals

- MeasureClass 删除保护(既有行为不动)。
- 维度/计量单位换算功能(标准范围外)。
- Constraint_Set 强制 designated/defined 指示器(与 MeasureClass 一致,交由约束集机制,不硬编码)。

## Capabilities

### New Capabilities

- `dimensionality-management`:Dimensionality(ISO/IEC 11179-31:2023 §7.5.2.3)的端到端管理契约——REST CRUD、Table 22 三态属性透传、measure_class 关联维护、删除保护、维度管理页,以及 ConceptualDomain.dimensionality 接线与 §7.4.2.1.5.2/§7.4.5.3 一致性校验。
