# Design: add-measureclass-dimensionality-association

## Context

参见 proposal.md - Why。技术现状与约束：

- `Dimensionality.measureClasses` 为 `@ManyToMany` **拥有方**（`@JoinTable("dimensionality_measure_class")`）；`MeasureClass.dimensionalities` 为 `mappedBy` 反向。从计量类侧写入必须修改拥有方集合，否则不落库。
- 刚完成的 `add-uom-measure-class-association` 已建立同构模式（专用 DTO + `applyMeasureClasses` + 前端 SmartItemPicker 多选 + 计数列），本次对照实现，差异为：基数 0..*（允许清空，不拒绝）、持久化方向相反。
- `add-dimensionality-management` 的维度侧代码（Controller/Service/DTO）本次一律不动。
- AGENTS.md 标准依据表述规则：用户可见文案不得引用条款号、不得扩写标准原文；代码注释标注条款与既有文件风格一致。

## Goals / Non-Goals

**Goals:**

- 计量类 REST 写路径可维护 `dimensionality_measure_class` 关联（0..*，整组替换，空集=清空）。
- 响应携带 `dimensionalities`（id + label），列表/详情/创建/更新一致。
- 前端计量类页提供维度多选编辑与计数列。
- 与维度侧维护路径（`DimensionalityRequest.measureClassIds`）互通：任一侧写入后另一侧视角一致。

**Non-Goals:**

- 计量类被维度引用时的删除保护（维持既有静默清理，dimensionality-management D2 决策）。
- 计量单位（UoM）在计量类页的反向视图。
- DB schema / 迁移脚本——零变更。

## Decisions

### D1：专用 DTO（照 UoM 模式）

`web.dto.measureclass.MeasureClassRequest`（sourceUri/designations/definitions/registration/dimensionalityIds）与 `MeasureClassResponse`（同构 + `List<DimensionalityRef> dimensionalities`）；`DimensionalityRef(UUID id, String label)` 置于 `web.dto.dimensionality`（与 `MeasureClassRef` 同包同风格）。

- 备选：复用 `ConceptRequest` 加可选字段——拒绝：污染 8 实体共用基类 DTO（同 UoM 变更 D1 理由）。

### D2：反向调和持久化

`applyDimensionalities(entity, dimensionalityIds)`：

1. `null`/空集 → 目标集为空（**清空合法**，0..*）。
2. 非空：校验全部 id 存在，缺 → `ResourceNotFoundException("Dimensionality", missing)`。
3. 差异调和：当前已关联 = `dimensionalityRepository.findByMeasureClasses_Id(entity.getId())`；`toRemove` = 当前 − 目标（从各维度 `measureClasses` 移除本计量类）；`toAdd` = 目标 − 当前（加入各维度 `measureClasses`）；同步维护内存中 `entity.getDimensionalities()` 双向一致；保存被修改的维度。
4. create 顺序：`repository.save(entity)` 取 id → `applyDimensionalities` → 响应直接由内存集合组装；update：load → applyConcept → applyDimensionalities → `repository.save(entity)`。

- 备选：把拥有方改到 MeasureClass 侧——拒绝：改动维度侧既有行为与迁移风险，违反"不动维度侧"边界。
- 备选：Hibernate `@PreUpdate` 同步——拒绝：隐式逻辑难测试，显式 Service 调和与 UoM 模式对称。

### D3：删除保护不动

`MeasureClassService.delete` 保持 UoM 引用 409 保护（上一变更），维度引用维持静默清理。删除保护如需对称化，连同计量单位反向视图另立变更。

### D4：前端照 UoM/维度页模式

`MeasureClassList.vue`：`#fields` 槽"维度"多选（`SmartItemPicker item-types=['Dimensionality']`、`allow-multiple`）、"维度数"列、`handleCreate/handleEdit` 回填 `dimensionalityIds = (dimensionalities||[]).map(d => d.id)`。

### D5：UI 文案合规

placeholder 用中性搜索提示（"搜索维度（可多选）"），不引用条款号；错误消息沿用通用业务错误格式。

## Risks / Trade-offs

- [旧客户端 PUT 不带 `dimensionalityIds` 会清空关联] → 0..* 语义下"清空"即回到无关联状态；本项目前后端同源发布，无外部写消费者；文档在变更说明中标注。
- [反向调和引入多步写（维度集合变更）] → 全部在单一事务内（Service 已 `@Transactional`）；单测覆盖差异集计算。
- [响应与库内状态短暂不一致（并发两侧同时写）] → 与维度侧整组替换同一并发模型，超出本次范围。

## Migration Plan

无 DB 迁移。后端先行（响应超集兼容读、写路径新语义）→ 前端同版本发布。回滚即代码回滚。

## Open Questions

无。
