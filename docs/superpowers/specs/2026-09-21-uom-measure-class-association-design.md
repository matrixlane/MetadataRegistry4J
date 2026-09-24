# 计量单位—计量类关联补齐设计（UoM ↔ Measure_Class）

日期：2026-09-21
OpenSpec 变更：`add-uom-measure-class-association`
标准依据：ISO/IEC 11179-31:2023 §7.5（Measurement metamodel region），经本地标准 PDF 原文逐条核实

## 1. 背景与问题

Measurement 元模型区有三条关联，符合性审计结论：

| 关联 | 标准基数 | 实现现状 | 判定 |
|---|---|---|---|
| `dimensionality_measure_class`（§7.5.3.1） | 0..* ↔ 0..* | 已有 API + 前端多选，可维护 | 符合 |
| `unit_of_measure_class`（§7.5.3.2） | **UoM 侧 1..*** ↔ 0..* | 中间表 `unit_of_measure_measure_class` 存在，但 UoM API 只接受通用 `ConceptRequest`，关联无任何写入口；基数无 1..* 校验 | **不符合** |

标准原文（§7.5.3.2）：*"The unit_of_measure_class association binds **one or more** instances of the Unit_of_Measure class with zero, one or more instances of the Measure_Class."*

连带后果：§7.4.5.3 维度一致性校验（`ValueDomainAssociationValidator` 的 measureClasses 交集非空判断）因 UoM 侧永远为空而走"任一侧未指定不校验"旁路，约束形同虚设。

## 2. 方案（已选定方案 A）

- **方案 A（选定）**：只补计量单位侧全链路，零 schema 变更。
- 方案 B（A + 计量类页反向只读视图）、方案 C（B + 存量数据巡检）：留作后续变更，本次不做。

## 3. 设计决策

### D1：专用 DTO，模式照 Dimensionality

- 新增 `web/dto/unitofmeasure/UnitOfMeasureRequest`：`sourceUri`、`designations`、`definitions`、`registration`、`List<UUID> measureClassIds`。
- 新增 `web/dto/unitofmeasure/UnitOfMeasureResponse`：ConceptResponse 同构字段（id/label/sourceUri/designations/definitions/meta）+ `List<MeasureClassRef> measureClasses`。
- 复用既有 `web.dto.dimensionality.MeasureClassRef`（id + label），不新建重复类型。
- `UnitOfMeasureController`/`UnitOfMeasureService` 从 `ConceptRequest/ConceptResponse` 切换到专用 DTO。响应为原响应超集（列表仅多 `measureClasses` 字段），向后兼容。

### D2：1..* 基数落实于应用层（写路径强制）

- create/update 时 `measureClassIds` 为 null 或空集 → 拒绝，`ApiCodes.VALIDATION_FAILED`(422) 业务错误："计量单位必须至少归属一个计量类（ISO/IEC 11179-31 §7.5.3.2）"。
- 任一 id 不存在 → 404 语义错误（`ResourceNotFoundException("MeasureClass", missing)`），与 `DimensionalityService.applyMeasureClasses` 行为一致。
- 整组替换语义（先 clear 再 set），与维度侧一致；更新时不允许清空。
- 沿用维度变更 D3 先例：**不改 DB schema、不加 FK/NOT NULL**，约束全部在应用层。存量无关联数据不受影响（不追溯）。

### D3：MeasureClass 删除保护（防孤儿）

- `MeasureClassService.delete` 增加：`UnitOfMeasureRepository.countByMeasureClasses_Id(id) > 0` 时拒绝，返回 `ApiCodes.CONFLICT`(409)："计量类正被 N 个计量单位引用，无法删除"。
- 维度侧引用不动（既有决策：dimensionality 变更 D2 明确 MeasureClass 对维度的删除行为保持原样）。

### D4：前端计量单位页

- `UnitOfMeasureList.vue`：模板 `#fields` 槽加入计量类 `SmartItemPicker`（`item-types=['MeasureClass']`、`allow-multiple`）；新增"计量类数"列；`handleCreate/handleEdit/submitFn` 模式照 `DimensionalityList.vue`（编辑回填 `measureClassIds = measureClasses.map(id)`）。
- 前端做非空提示（可选），强制校验在后端。

## 4. 影响面

- 后端：`UnitOfMeasureController`、`UnitOfMeasureService`（改为专用 DTO + 关联维护）、新增 2 个 DTO、`MeasureClassService.delete`（删除保护）、`UnitOfMeasureRepository`（新增 count 查询）。
- 前端：`UnitOfMeasureList.vue`、`api/resources.ts` 中 unitOfMeasureApi 类型（如需）。
- 不影响：`ValueDomainAssociationValidator`（交集校验逻辑不变，补齐数据后自然生效）、维度侧全部代码、DB schema。
- GitNexus 影响分析：`UnitOfMeasureService` 上游仅 `UnitOfMeasureController`，risk LOW。

## 5. 验证计划

- TDD：`UnitOfMeasureServiceTest` 新增（1..* 校验/404/整组替换/删除保护）；`MeasureClassServiceTest` 补删除保护用例；Controller 层 mock 校验。
- 全量：`metadata-registry-web` mvn test；前端 vitest + build；openapi.json 再生成。
- 冒烟：scratch 副本库起实例，走 create→update→删除保护→一致性校验生效全链路（参照 scratch/e2e_dimensionality_api.py 模式）。
- 提交前 `detect_changes` 无 partial/truncated。
