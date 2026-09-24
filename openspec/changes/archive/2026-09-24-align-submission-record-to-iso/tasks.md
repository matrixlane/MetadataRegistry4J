# Tasks: align-submission-record-to-iso

## 1. 数据模型与迁移

- [ ] 1.1 `SubmissionRecord` 实体增加 `source` 字段（`@Enumerated(STRING)`，长度 32，默认 `USER_SUBMISSION`）
- [ ] 1.2 新建枚举 `SubmissionRecordSource`（`USER_SUBMISSION`, `SYSTEM_DRAFT`），路径 `metadata-registry-common-facilities/.../registration/SubmissionRecordSource.java`
- [ ] 1.3 `V20260924002__Submission_record_source.sql` × 3 方言（sqlite / sqlserver / dm）：
  - 加列 `source varchar(32) null`；
  - 一次性回填：若 SR 在 `submission_record_registered_item` 中是该 SR 唯一关联 Item 且创建早于 2026-09-24 16:00，标 `SYSTEM_DRAFT`，否则 `USER_SUBMISSION`

## 2. 仓储与 DTO

- [ ] 2.1 `SubmissionRecordRepository` 已有 `findAllByRegisteredItemIdOrderByIdDesc` + `findLatestByRegisteredItemId` default，保留
- [ ] 2.2 新建 `SubmissionRecordSummary` record（`id, source, contactLabel, organizationLabel, submissionDate, registeredItemIds`）
- [ ] 2.3 `ItemMetaResponse` 增加 `submissionRecordSummaries: List<SubmissionRecordSummary>` 字段
- [ ] 2.4 新建 `SubmissionRecordMapper.toSummary(SubmissionRecord)` 静态方法（接触 Contact / Organization 名称 + 关联条目数）

## 3. 后端业务逻辑

- [ ] 3.1 `ItemAdministrationService.recordSubmission(Item, ItemRegistrationRequest, SubmissionRecordSource)` 新签名：source 必传
- [ ] 3.2 新增 `ItemAdministrationService.recordSubmissionForBatch(List<Item>, SubmissionInfo, SubmissionRecordSource)`：一条 SR 关联多条 Item（source=USER_SUBMISSION）
- [ ] 3.3 `WorkspaceService.submit(request, accountId)` 改造：
  - 逐项校验与 `administrativeStatusService.applyLanding` 不变；
  - 累计成功 itemIds；`submit` 末尾若有成功项，调用 `recordSubmissionForBatch(items, submission, USER_SUBMISSION)`
- [ ] 3.4 `WorkspaceService.requestCandidate` 沿用单 item `recordSubmission(item, req, USER_SUBMISSION)`
- [ ] 3.5 13 个 CRUD Service 移除 create/update 末尾 `itemAdministrationService.recordSubmission(saved, request.registration())` 调用：
  - `DataElementService`、`DataElementConceptService`、`ConceptualDomainService`、`ValueDomainService`、
    `ContextService`、`ObjectClassService`、`PropertyService`、`DataTypeService`、`DataTypeSchemeService`、
    `UnitOfMeasureService`、`MeasureClassService`、`DimensionalityService`、`ValueMeaningService`、
    `ItemMappingCrudService`
  - 保留 `ensureAdministeredItem`（其中已含 `applyStewardshipRecord`）；删除调用前确保 `request.registration()` 仍可用于主管记录更新（必要时改 `applyStewardshipRecord(ai, req)` 显式调用）
- [ ] 3.6 联邦路径 `GenericItemCreator.java` 不动（已合规）
- [ ] 3.7 `ensureAdministeredItem` 中的 `applyStewardshipRecord` 调用确认仍生效（移除 recordSubmission 不应破坏该路径）

## 4. 前端 UI

- [ ] 4.1 `ItemFormDialog.vue`：删除 `<el-collapse-item name="submission">` 两个可编辑 el-form-item
- [ ] 4.2 `ItemFormDialog.vue`：新增 `<el-collapse-item name="submissionRecords">`，用 `<el-descriptions>` 只读展示 SR 列表（每行：来源 tag + 联系人/组织 + 关联条目数 + 提交时间）
- [ ] 4.3 `ItemFormDialog.vue`：`stewardship` collapse 维持可编辑不变
- [ ] 4.4 `zh-CN.json`：`itemForm.submissionRecords.title`、来源枚举文案（`USER_SUBMISSION`/"用户提交"、`SYSTEM_DRAFT`/"草稿自动生成"）、空态文案
- [ ] 4.5 `ItemFormDialog.spec.ts`：迁移"submission 选择器"断言 → 新增"提交记录只读列表"断言；保留 stewardship 切换测试
- [ ] 4.6 `types/common.ts`：新增 `SubmissionRecordSummary` 类型与 `SubmissionRecordSource` 类型（如前端需要展示）

## 5. 测试与回归

- [ ] 5.1 `ItemAdministrationServiceTest`：更新 recordSubmission 用例使用新签名；新增 `recordSubmissionForBatch` 用例
- [ ] 5.2 `WorkspaceServiceTest`：新增"批量提交一次创建 SR、失败 item 不挂 SR"用例；requestCandidate 单 SR 用例
- [ ] 5.3 `DataElementServiceTest` 等：移除"create 后 SR +1"的断言；保留"ensureAdministeredItem 后 stewardshipRecord 已设置"的断言
- [ ] 5.4 新增 Flyway 迁移测试（若项目已有）：旧 SR 行回填为 SYSTEM_DRAFT
- [ ] 5.5 前端 `ItemFormDialog.spec.ts`：跑通
- [ ] 5.6 后端 `mvn -pl metadata-registry-web -am test` 全量无回归

## 6. Spec 同步

- [ ] 6.1 `openspec/specs/submission-record/spec.md`：删除 Requirement `随条目保存生成提交记录` 及其 Scenario（编辑不创建 SR）
- [ ] 6.2 新增 Requirement `工作空间提交按批次创建提交记录`：
  - Scenario: 单条工作空间提交创建 1 条 SR 关联该 item
  - Scenario: 批量工作空间提交创建 1 条 SR 关联全部成功 item，失败 item 不挂 SR
  - Scenario: request-candidate 创建 1 条 SR 关联该 item
- [ ] 6.3 新增 Requirement `编辑界面提交记录只读`：编辑表单提供 SR 列表只读展示，来源 / 联系人 / 组织 / 关联条目数
- [ ] 6.4 新增 Requirement `主管记录在编辑表单可维护`：保留 applyStewardshipRecord 行为
- [ ] 6.5 新增 Requirement `历史脏数据标注 SYSTEM_DRAFT`：迁移回填规则

## 7. 端到端冒烟

- [ ] 7.1 启动服务，登录 admin
- [ ] 7.2 创建 DataElement（编辑路径）：DB `mdr.submission_record` 行数不变
- [ ] 7.3 工作空间批量提交 2 个 item：DB 新增 1 条 SR，`submission_record_registered_item` 关联 2 行
- [ ] 7.4 打开 ItemFormDialog：提交记录面板渲染历史 SR 列表（只读）；主管记录面板可编辑
- [ ] 7.5 `/api/v1/workspace/items?view=DRAFTS` 返回 200（验证之前的 500 已根除）