# Proposal: align-submission-record-to-iso

## Why

`SubmissionRecord` 当前实现违反 ISO/IEC 11179-3:2023 表 27 语义：标准规定一条 Submission_Record
表示**一次提交动作**（可关联多个 Administered_Item），而当前 `ItemAdministrationService.recordSubmission(item, req)`
被 13+ 个 CRUD Service 的 `create`/`update` 末尾无条件调用，导致：

- 同一管理项每次保存（含新建后第一次保存、每次内容修订）都新建一条 SubmissionRecord；
- 工作空间批量提交按"每个 item 一条 SR"而非"每批次一条 SR"创建记录；
- 历史脏数据导致 `findLatestByRegisteredItemId` 在关联多条历史 SR 的 item 上抛
  `IncorrectResultSizeDataAccessException`，触发 `/api/v1/workspace/items` 500。
- Item 编辑界面从未暴露已关联的 SubmissionRecord，编辑者无法得知历史提交（影响审计与归属判定）。
- 主管记录（Stewardship_Record）应在编辑表单中维护（已实现），但被"提交信息"表单语义掩盖。

需要按 ISO 标准重设计 SR 生命周期：编辑保存不创建 SR；工作空间提交按批次创建 SR；
Item 编辑界面提供只读 SR 列表展示历史提交。

## What Changes

- `SubmissionRecord` 实体新增 `source` 字段（`SubmissionRecordSource` 枚举：`USER_SUBMISSION`/`SYSTEM_DRAFT`），区分用户提交与历史脏数据。
- 三方言迁移 `V20260924002__Submission_record_source.sql`：加列 + 一次性回填旧数据为 `SYSTEM_DRAFT`。
- `ItemAdministrationService.recordSubmission(item, req)` 签名变更：增加 `SubmissionRecordSource` 必传参数；
  新增 `recordSubmissionForBatch(List<Item>, SubmissionInfo, source)` 重载用于工作空间批量提交。
- 13+ 个 CRUD Service 的 `create`/`update` 末尾移除 `itemAdministrationService.recordSubmission(...)` 调用；
  保留 `ensureAdministeredItem`（含 `applyStewardshipRecord`）以维持主管记录更新。
- `WorkspaceService.submit(request, accountId)` 改造：批量提交成功时创建 1 条 SR 关联所有成功 item，失败 item 不挂 SR。
- `WorkspaceService.requestCandidate(itemId, submission, accountId)` 沿用单 item SR 创建（提交动作=1）。
- 新增 `SubmissionRecordSummary` DTO；`ItemMetaResponse` 增加 `submissionRecordSummaries` 字段。
- `ItemAdministrationService.isItemOwnedByAccount` 复用现有 `findLatestByRegisteredItemId`（已修复 List+Optional）。
- 前端 `ItemFormDialog.vue`：删除"提交信息"可编辑区，新增"提交记录（只读）"折叠面板展示历史 SR；
  "主管信息"维持可编辑；同步更新 `ItemFormDialog.spec.ts` 与 `zh-CN.json`。
- `openspec/specs/submission-record/spec.md` 同步更新：移除"编辑保存生成 SR"的旧 Requirement，新增"批次粒度 SR"、
  "编辑界面只读"、"历史脏数据标注"三项 Requirement。

## Capabilities

### Modified Capabilities

- `submission-record`：Submission_Record 生命周期重定义为按"提交动作"批次粒度创建；
  编辑保存不再生成 SR；前端编辑界面只读展示；历史脏数据标注 SYSTEM_DRAFT。

## Impact

- `metadata-registry-common-facilities`：`SubmissionRecord` 实体加 1 字段；新增 `SubmissionRecordSource` 枚举。
- `metadata-registry-web`：
  - 1 份 `V20260924002__Submission_record_source.sql` ×3 方言（sqlite / sqlserver / dm）；
  - `SubmissionRecordRepository.java`（已修）+ 后续随 DTO 调整；
  - `ItemAdministrationService.java`（recordSubmission 签名 + 新增 batch 重载）；
  - `WorkspaceService.java`（批量提交 1 条 SR）；
  - 13 个 CRUD Service 移除末尾调用（`DataElementService`、`DataElementConceptService`、
    `ConceptualDomainService`、`ValueDomainService`、`ContextService`、`ObjectClassService`、
    `PropertyService`、`DataTypeService`、`DataTypeSchemeService`、`UnitOfMeasureService`、
    `MeasureClassService`、`DimensionalityService`、`ValueMeaningService`、`ItemMappingCrudService`）；
  - 新增 `SubmissionRecordSummary.java`；`ItemMetaResponse.java` 加字段；
  - 测试更新：`ItemAdministrationServiceTest`、`WorkspaceServiceTest`、`DataElementServiceTest` 等。
- `metadata-registry-ui`：`ItemFormDialog.vue`、`ItemFormDialog.spec.ts`、`zh-CN.json`、`types/common.ts`。
- `openspec/specs/submission-record/spec.md`：替换为对齐 ISO 的新版本。
- 联邦路径 `GenericItemCreator` 不动（已只调 `ensureAdministeredItem`）。
- 历史脏数据：保留原 SR 行，标注 `SYSTEM_DRAFT` 来源；不删除、不迁移。

## Out of Scope

- 删除旧 SR（历史保留）；
- SR 的 reject / withdraw 业务动作（保持现有）；
- 联邦路径 SR 同步（联邦路径不产生 SR，无需同步）；
- SubmissionInfo DTO 字段变更（保持 `{ contactId, organizationId }`）。