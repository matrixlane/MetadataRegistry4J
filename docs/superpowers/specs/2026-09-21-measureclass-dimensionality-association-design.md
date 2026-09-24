# 计量类→维度关联维护设计（MeasureClass → Dimensionality）

日期：2026-09-21
OpenSpec 变更：`add-measureclass-dimensionality-association`
标准依据：ISO/IEC 11179-31:2023 §7.5.3.1 `dimensionality_measure_class`（0..* ↔ 0..*），经本地标准 PDF 原文核实

## 1. 背景与问题

`dimensionality_measure_class` 关联的 JPA 拥有方在 `Dimensionality`（`@JoinTable("dimensionality_measure_class")`），因此：

- 维度页可维护计量类（既有，`DimensionalityRequest.measureClassIds`）；
- 计量类页**完全无法查看或维护**已关联维度——`MeasureClassController/Service` 只用通用 `ConceptRequest/ConceptResponse`，无写入口，前端计量类页无对应控件。

标准要求该关联 0..* ↔ 0..*（§7.5.3.1："binds zero, one or more instances of the Dimensionality class with zero, one or more instances of the Measure_Class class"），双向可维护是合理预期；本次补齐计量类侧。

## 2. 方案（已选定方案 A）

照 `add-uom-measure-class-association` 已建立的模式实现，差异仅在**反向持久化**与**基数 0..\***（允许清空）。

- 方案 B（被维度引用时删除 409）、方案 C（响应附带计量单位只读列表）均留作后续，本次不做。

## 3. 设计决策

### D1：专用 DTO（照 UoM 模式）

- 新包 `web.dto.measureclass`：`MeasureClassRequest(String sourceUri, designations, definitions, registration, List<UUID> dimensionalityIds)`；`MeasureClassResponse(id, label, sourceUri, designations, definitions, meta, List<DimensionalityRef> dimensionalities)`。
- `DimensionalityRef(UUID id, String label)` 放入 `web.dto.dimensionality`（与 `MeasureClassRef` 同包同风格）。
- `MeasureClassController/Service` 从 `ConceptRequest/ConceptResponse` 切换。

### D2：反向调和持久化（核心）

`Dimensionality.measureClasses` 是拥有方，`MeasureClass.dimensionalities` 为 `mappedBy` 反向——从计量类侧写入必须改拥有方集合：

- `null`/空集 = 清空（0..* 允许；与 UoM 的 1..* 不同，**不拒绝**）。
- 任一 id 不存在 → `ResourceNotFoundException("Dimensionality", missing)`（404 语义）。
- create：先 `repository.save(entity)` 取得 id，再 `applyDimensionalities`：加载当前已关联维度（`DimensionalityRepository.findByMeasureClasses_Id`）与目标维度，差异集双向增删（维度侧 `measureClasses` add/remove 计量类，计量类侧内存 `dimensionalities` 同步维护），保存变更的维度。
- update：同上（实体已有 id），随后 `repository.save(entity)`。
- 响应从内存中的双向集合组装 `dimensionalities`，保证 create/update 立即返回一致结果。

### D3：删除保护不动

`MeasureClassService.delete` 仅保留 UoM 引用 409 保护（本次新增于上一变更）；被维度引用时维持既有静默清理行为（dimensionality-management 变更 D2 决策）。

### D4：前端

`MeasureClassList.vue`：模板 `#fields` 槽加"维度"多选（`SmartItemPicker item-types=['Dimensionality']`、`allow-multiple`）、新增"维度数"列（`row.dimensionalities?.length ?? 0`）、`handleCreate/handleEdit` 回填 `dimensionalityIds`——全部照 `UnitOfMeasureList.vue`/`DimensionalityList.vue` 既有模式。

### D5：UI 文案合规（AGENTS.md 标准依据表述规则）

用户可见文案（placeholder、列名、错误提示）不引用标准条款号、不扩写标准；代码注释可标注条款出处（与既有文件风格一致）。

## 4. 影响面

- 后端：`MeasureClassController`、`MeasureClassService`、`MeasureClassRepository`（无新增派生查询需求——维度侧查 `findByMeasureClasses_Id` 加在 `DimensionalityRepository`）、新 DTO 3 个文件。
- 前端：`MeasureClassList.vue`。
- 零 schema 变更；不改维度侧任何代码；`ValueDomainAssociationValidator` 不动。
- GitNexus 影响分析：`MeasureClassService` 上游仅 `MeasureClassController`，risk LOW。

## 5. 验证计划

- TDD：`MeasureClassServiceTest` 扩展（创建带维度、空集清空、404、更新整组替换、反向集合同步）；`DimensionalityRepository.findByMeasureClasses_Id` 由 Service 单测 mock 验证。
- 全量：web `mvn test`、前端 vitest + build、openapi.json 再生成。
- 冒烟：scratch 副本库（:18084 模式）——创建维度、计量类带维度创建、响应含 dimensionalities、更新整组替换、清空、维度侧视角一致性（维度详情仍显示该计量类）。
- 提交前 `detect_changes` 无 partial/truncated。
