# Proposal: add-uom-measure-class-association

## Why

ISO/IEC 11179-31:2023 §7.5.3.2 规定 `unit_of_measure_class` 关联要求每个计量单位（Unit_of_Measure）必须归属一个或多个计量类（Measure_Class）。当前实现中，虽然数据库中间表 `unit_of_measure_measure_class` 与 JPA `@ManyToMany` 映射已存在，但计量单位 API 只接受通用 `ConceptRequest`，该关联没有任何写入口，前端计量单位页也无编辑控件，且 1..* 基数无任何校验——标准 shall 级约束完全未落实。这还导致 §7.4.5.3 维度一致性校验（measureClasses 交集非空）因 UoM 侧永远为空而永远走"不校验"旁路。

## What Changes

- **BREAKING**：`POST/PUT /api/v1/units-of-measure` 请求体由通用 `ConceptRequest` 更换为专用 `UnitOfMeasureRequest`（新增必填 `measureClassIds`，1..*）；响应由 `ConceptResponse` 更换为 `UnitOfMeasureResponse`（原字段超集 + `measureClasses`）。`measureClassIds` 为空返回 422，含不存在 id 返回 404。
- `MeasureClassService.delete` 增加删除保护：计量类被 ≥1 个计量单位引用时拒绝删除并返回 409 业务错误（防止删出违反 1..* 的孤儿计量单位）。
- 前端计量单位页（`/unit-of-measure`）新增计量类多选编辑入口与"计量类数"列。
- 不改动 DB schema（中间表已存在，约束全部落应用层，沿用 dimensionality-management 变更 D3 先例）。

## Capabilities

### New Capabilities

- `unit-of-measure-measure-class`: 计量单位—计量类关联（ISO/IEC 11179-31:2023 §7.5.3.2 unit_of_measure_class，UoM 侧 1..*）的端到端契约——专用请求/响应 DTO、写路径基数强制、计量类删除保护、前端编辑入口。

### Modified Capabilities

（无——dimensionality-management 的维度↔计量类关联与一致性校验行为不变，本变更仅使其校验在数据补齐后自然生效。）

## Impact

- 后端：`metadata-registry-web` 的 `UnitOfMeasureController`、`UnitOfMeasureService`、新增 `web/dto/unitofmeasure/*`、`MeasureClassService.delete`、`UnitOfMeasureRepository`（count 查询）。
- 前端：`metadata-registry-ui` 的 `UnitOfMeasureList.vue`；`api/resources.ts` 中 unitOfMeasureApi 载荷类型扩展。
- 标准符合性：补齐 11179-31 §7.5.3.2 的 1..* 基数约束与关联可维护性；§7.4.5.3 一致性校验不再被旁路。
- 兼容性：响应为原响应超集（列表仅多 `measureClasses`），读路径向后兼容；写路径为 BREAKING（旧客户端不带 `measureClassIds` 将收到 422）。
