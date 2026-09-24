# Design: add-uom-measure-class-association

## Context

参见 proposal.md - Why。补充技术现状：

- `UnitOfMeasure.measureClasses` 已是 `@ManyToMany` 拥有方（`@JoinTable("unit_of_measure_measure_class")`），`MeasureClass.unitOfMeasures` 为 `mappedBy` 反向——**关系模型不缺，缺的是写路径与约束**。
- 维度（Dimensionality）侧已有同构实现可作模板：`DimensionalityRequest.measureClassIds` 整组替换、`MeasureClassRef`（id+label）响应、`DimensionalityService.applyMeasureClasses` 的 404 语义、前端 `SmartItemPicker` 多选 + 编辑回填。
- 全库基线不加 FK，约束落应用层（dimensionality-management 变更 D3 先例）。
- `ApiCodes` 已有 `VALIDATION_FAILED`(422) 与 `CONFLICT`(409)。

## Goals / Non-Goals

**Goals:**

- 计量单位 REST 写路径可维护 `unit_of_measure_measure_class` 关联，并强制 UoM 侧 1..*（ISO/IEC 11179-31:2023 §7.5.3.2）。
- 计量类不可在被计量单位引用时删除（防孤儿，保 1..* 可持续成立）。
- 前端计量单位页提供计量类多选编辑与计数列。
- 数据补齐后，`ValueDomainAssociationValidator` 的 §7.4.5.3 交集校验自然生效（本变更不改该校验器）。

**Non-Goals:**

- 计量类页面反向只读视图（已关联维度/计量单位）——后续变更。
- 存量无关联计量单位的数据巡检/回填——不追溯，写路径强制即可。
- `dimensionality_measure_class` 侧的计量类删除保护——既有决策（dimensionality-management D2）不动。
- DB schema / FK / 迁移脚本——零变更。

## Decisions

### D1：专用 DTO 替换通用 Concept DTO（照 Dimensionality 模式）

`UnitOfMeasureRequest`（sourceUri/designations/definitions/registration/measureClassIds）与 `UnitOfMeasureResponse`（Concept 同构 + `List<MeasureClassRef> measureClasses`）放入新包 `web.dto.unitofmeasure`，复用 `web.dto.dimensionality.MeasureClassRef` 而不新建同构类型。

- 备选：给 `ConceptRequest` 加可选字段——拒绝：Concept 是 8 个实体共用的基类 DTO，为 UoM 特例污染基类。
- 备选：复用 `DimensionalityRequest`——拒绝：语义不同且会耦合两个资源。

### D2：1..* 强制于 Service 写路径，422 + 404 语义

`create`/`update` 统一走 `applyMeasureClasses`：`measureClassIds` null/空 → `BusinessException(VALIDATION_FAILED, "计量单位必须至少归属一个计量类（ISO/IEC 11179-31 §7.5.3.2）")`；任一 id 不存在 → `ResourceNotFoundException("MeasureClass", missing)`（404 语义，与维度侧逐字一致）；否则整组替换。更新不允许清空（同一条校验覆盖）。

- 备选：Bean Validation `@NotEmpty`——拒绝：需要统一的业务错误语义与消息，且 Service 内校验可与 404 检查共享加载结果。
- 备选：DB 加 NOT NULL/FK——拒绝：沿用 D3 应用层先例，零迁移。

### D3：MeasureClass 删除保护（409）

`MeasureClassService.delete` 前置 `unitOfMeasureRepository.countByMeasureClasses_Id(id)`，>0 时 `BusinessException(CONFLICT, "计量类正被 %d 个计量单位引用，无法删除")`。维度侧引用维持既有静默清理行为。

- 备选：不加保护，删后允许孤儿——拒绝：直接违反本次要落实的 1..* shall 约束。

### D4：前端照 DimensionalityList.vue 模式

`UnitOfMeasureList.vue`：`#fields` 槽加 `SmartItemPicker`（MeasureClass 多选）；新增"计量类数"列；`handleCreate/handleEdit` 回填 `measureClassIds`；`submitFn` 原样透传。前端做非空提示（picker required 星标），强制以服务端 422 为准。

## Risks / Trade-offs

- [旧客户端/脚本直接 POST 不带 `measureClassIds` 将 422] → 本项目前后端同源发布，无外部 API 消费者；响应保持超集兼容读路径。
- [存量计量单位无关联，之后编辑时必须补选计量类] → 预期行为（标准 shall 约束），错误消息给出标准依据；数据巡检留作后续变更。
- [删除保护改变 MeasureClass 既有删除行为] → 仅拦截"被计量单位引用"场景，错误消息含引用数，与维度删除保护 UX 一致。
- [`MeasureClassRef` 复用跨包（dto.dimensionality）] → 语义为通用计量类引用，注释已标注；若后续反向视图需要可下沉 dto.common，本次不做。

## Migration Plan

无 DB 迁移。部署顺序：后端先行（新 DTO 兼容读、写路径新校验）→ 前端同版本发布。回滚：代码回滚即可，无数据形态变更。

## Open Questions

无。
