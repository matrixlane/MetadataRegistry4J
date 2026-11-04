## Why

WS 363/364 基准数据补齐受管链后，两处缺口暴露：

1. **提交记录关联挂上即拖垮列表接口**。`registered_item_submission_record` 有 11,182 行时，
   `/api/v1/data-elements` 与 `/api/v1/administered-items` 40s+ 超时；清空即恢复 0.35s。
   根因是 `SubmissionRecordRepository` 的 JPQL 经 `JOIN sr.registeredItems` 展开
   `RegisteredItem` 的 30+ 子类 JOINED 继承，而 `buildItemMeta` 在列表响应中逐条调用它。
   关联表为空时该缺陷不可见，故长期未暴露。提交关联因此只能挂 0 条，
   与 ISO/IEC 11179-3:2023 §9.4.9（提交记录必备）冲突。
2. **参考文档关联无处查看**。条目已通过 §9.5.2 关联 23,449 条参考文档，
   但数据元对话框没有对应 Tab；而 `ItemMetaResponse` 不含参考文档字段，
   前端即便加 Tab 也取不到数据。

## What Changes

- **修复提交记录查询形状**：`findAllByRegisteredItemIdOrderByIdDesc` 改经
  `DatabaseEnvironment.qualify()` 按关联表列取 id，再 `findAllById` 取实体，
  完全绕开 Item 子类的继承展开。保持 `ORDER BY id DESC` 原有行序。
- **`ItemMetaResponse` 增加 `referenceDocumentIds`**，按页批量取值
  （一次传入本页全部 `registered_item_id`），不在逐条调用路径上触发懒加载。
- **数据元对话框新增「参考文档」Tab**：列表展示 `identifier` + `title`，
  支持增删；保存时对差异集调用既有 `POST` / `DELETE
  /administered-items/{id}/reference-documents`。
- **新增迁移 `V20261009011`**（sqlite/sqlserver/dm 三方言同版本号），
  为 `registered_item_reference_document` 与 `registered_item_submission_record`
  各建 join 列索引——两表原本无任何索引（已手工补 4 个，未进迁移）。
- **恢复提交关联**：D1 修复并实测通过后，将 11,182 条 `registered_item_submission_record`
  以集合式幂等脚本回填。
- **合并 `Identified_Item` 与 `Registered_Item` 的物理表**：`IdentifiedItem` 由 `@Entity`
  降为 `@MappedSuperclass`，不再占用独立表；其到 Item 的绑定与标识符集合并入
  `registered_item`。**类层次不变**（§9.4.1.1 的 Direct superclass 关系保留），
  实体继承守护测试白名单不变。

### 不改动

- 不改**类层次**：受管链的继承声明与标准条款引用一律不动，本次只合并物理表。
- 不改参考文档自身的 CRUD（端点已存在）。
- 不改 WS/T 数据的任何取值（注册状态 `STANDARD`、注册机构、主管/提交记录、引用关联
  均已在前一变更 `repair-administered-item-closure` 落地并验证）。
- 不把参考文档混入编辑表单的通用 payload（会把共用该对话框的 15+ 条目类型
  与受管项专属能力绑死）。

## Capabilities

### New Capabilities

- `administered-item-reference-documents-ui`: 受管项参考文档的展示与编辑——
  数据元对话框 Tab、批量取值契约、增删交互；以及提交记录关联查询的形状约束
  （不得展开 Item 子类继承）。

### Modified Capabilities

- `database-portability`: 补充关联表须具备 join 列索引的要求；补充业务查询
  方言中立（走 `DatabaseEnvironment.qualify` 而非原生 schema 硬编码）。

## Impact

**后端**：
- `SubmissionRecordRepository.findAllByRegisteredItemIdOrderByIdDesc` 改写
- `SubmissionRecordRepository` 增按 `registered_item_id` 取 id 的查询
- `ItemMetaResponse` 增字段；`ItemAdministrationService.buildItemMeta` 取值来源调整
- 新增批量取参考文档 id 的仓储方法

**前端**：
- `ItemFormDialog.vue` 新增 Tab；新增参考文档选择组件与对应 spec
- `types/common.ts` 补 `meta.referenceDocumentIds`
- `api/resources.ts` 复用既有 `referenceDocumentApi` 与增删端点

**迁移**：`V20261009011` 三方言；`V20261009012` 三方言（表合并：建新表 →
数据迁移 → 换外键 → 删旧表）。

**类型变更（调用方需跟进）**：`Item.identifiedItem` 与 `ScopedIdentifier.identifiedItem`
的 Java 类型由 `IdentifiedItem` 改为 `RegisteredItem`；`Item.getIdentifiedItem()` 的
返回类型随之变更。

**数据**：迁移与恢复提交关联作用于真实库；执行需用户单独确认。
关联回滚为清空 `registered_item_submission_record`（幂等、可重跑），索引只增不减。

**已知遗留**：达梦方言未实测（沿用项目惯例记 backlog）；关联表是否收紧为 UNIQUE
约束需先验证历史数据无重复，另行判定。