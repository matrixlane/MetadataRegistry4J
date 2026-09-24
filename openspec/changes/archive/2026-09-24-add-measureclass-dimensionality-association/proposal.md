# Proposal: add-measureclass-dimensionality-association

## Why

ISO/IEC 11179-31:2023 §7.5.3.1 的 `dimensionality_measure_class` 关联（0..* ↔ 0..*）目前只能从维度一侧维护（`DimensionalityRequest.measureClassIds`，JPA 拥有方在 `Dimensionality`）。计量类（Measure_Class）的 REST 写路径只接受通用 `ConceptRequest`，前端计量类页无任何维度关联入口——用户无法站在计量类视角查看或维护"该计量类被哪些维度引用"，双向维护能力缺一半。

## What Changes

- **BREAKING**：`POST/PUT /api/v1/measure-classes` 请求体由通用 `ConceptRequest` 更换为专用 `MeasureClassRequest`（新增 `dimensionalityIds`，0..*，空集=清空）；响应由 `ConceptResponse` 更换为 `MeasureClassResponse`（原字段超集 + `dimensionalities`（含 id 与 label））。任一维度 id 不存在返回 404 语义错误。
- 持久化为反向调和：因 JPA 拥有方在 `Dimensionality`，从计量类侧写入时按差异增删各维度 `measureClasses` 集合并同步内存双向集合。
- 前端计量类页（`/measure-class`）新增"维度"多选编辑入口与"维度数"列。
- 不改动 DB schema；计量类被维度引用时的删除行为维持既有静默清理（不在本次范围）。

## Capabilities

### New Capabilities

- `measure-class-dimensionality-association`: 计量类→维度方向的 `dimensionality_measure_class` 关联（ISO/IEC 11179-31:2023 §7.5.3.1，0..* ↔ 0..*）维护契约——专用请求/响应 DTO、反向调和持久化、前端计量类页编辑入口。

### Modified Capabilities

（无——dimensionality-management 的维度→计量类方向行为不变，本变更与其共享同一中间表但写入路径独立。）

## Impact

- 后端：`metadata-registry-web` 的 `MeasureClassController`、`MeasureClassService`、`DimensionalityRepository`（新增 `findByMeasureClasses_Id`）、新增 `web.dto.measureclass/*` 与 `DimensionalityRef`。
- 前端：`metadata-registry-ui` 的 `MeasureClassList.vue`。
- 兼容性：响应为原响应超集（读路径兼容）；写路径 BREAKING（旧客户端不带 `dimensionalityIds` 视为清空——0..* 语义下等价于维持现状的无关联状态）。
-  GitNexus 影响分析：`MeasureClassService` 上游仅 `MeasureClassController`，risk LOW。
