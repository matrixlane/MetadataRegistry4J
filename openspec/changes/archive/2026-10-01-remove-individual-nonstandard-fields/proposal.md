# Proposal: remove-individual-nonstandard-fields

## Why

项目原则（2026-09-30 用户裁定）：schema 为 mdr 的表必须严格按照 ISO/IEC 11179 标准中定义过的内容设计；标准定义之外的部分统一使用 ext schema 作为扩展表存在（内部标识 id 列与审计字段除外）。

ISO/IEC 11179-3:2023 §6.3.2 Individual 类仅定义五个属性：name、title、mail_address、email_address、phone_number（见标准 Table 1 — Attributes of Individual class）。当前 `mdr.individual` 多出两个非标准列：

- `identifier`（V20260917001 加入）——源于已作废的 OpenSpec 变更 `add-individual-identifier`（2026-09-18 提交 `9d2a46c`，2026-09-30 用户裁定取消并回滚）
- `avatar`（V20260901016 加入）——非标准属性，且语义上是账号级属性，宿主错位

## What Changes

- **删除 `mdr.individual.identifier`**：实体字段、DTO 字段、IndividualService 搜索谓词/自动生成/透传、AuthService 三处 setIdentifier、RegistrarService 一处 setIdentifier、UI 标识符列与表单项，全部移除。
- **avatar 迁至 `ext.account`**：`Account` 实体新增 `avatar` 字段（`@Lob`）；AuthService 头像三方法（getMyAvatar/updateMyAvatar/deleteMyAvatar）改为读写 `Account.avatar`；API 路径改名 `/auth/me/avatar`；上传头像不再自动创建 Individual（头像纯账号级语义）。
- **三方言迁移 `V20260930001__Remove_individual_nonstandard_fields.sql`**：`account` 加 `avatar` 列 → 按 `account.individual_id` 关联子查询搬迁存量数据（三方言通用写法）→ 删 `individual.avatar` → 删 `individual.identifier`。幂等、不依赖人工预处理。
- **OpenSpec 作废处理**：`add-individual-identifier` 从未归档进主 specs，已 `git rm` 删除目录完成作废。
- **API 契约变更**：`IndividualRequest`/`IndividualResponse` 删 identifier 字段；openapi 再生成。

## Capabilities

### New Capabilities

- `individual-standard-compliance`: mdr.individual 列集与 ISO/IEC 11179-3:2023 §6.3.2 严格一致的约束，以及"非标准属性的归宿 = ext schema"规则在 Individual/avatar 上的落地（avatar 账号级语义 + API 路径 + 数据搬迁）。

### Modified Capabilities

（无既有能力的需求语义变更；本次为回滚性变更，恢复标准原状。）

## Impact

- `metadata-registry-common-facilities`：`Individual` 删 identifier/avatar 两字段；`Account` 加 avatar 字段。
- `metadata-registry-web`：三份新迁移；`IndividualService`、`AuthService`、`RegistrarService`、`IndividualRequest/Response`、`AuthController`（avatar 端点改名）；测试重构（AuthServiceTest / AuthServiceAvatarTest / IndividualServiceIntegrationTest）；openapi.json/yaml 再生成。
- `metadata-registry-ui`：`IndividualList.vue` 删标识符列/表单项；`MyProfile.vue` 头像端点改 `/auth/me/avatar`。
- `openspec/changes/add-individual-identifier`：已删除（作废）。
- 无第三方依赖变更；向后兼容性影响：Individual 管理 API 契约删字段（前后端同源发布，无外部消费者）、avatar 端点路径改名。
- 多方言要求：三方言迁移语义一致（幂等、关联子查询搬迁、列守卫）；达梦环境不可达时按项目惯例记"待实测"。

## 标准依据

ISO/IEC 11179-3:2023 §6.3.2 Individual class（Table 1 — Attributes of Individual class）：标准属性仅 name/title/mail_address/email_address/phone_number 五项。`identifier` 与 `avatar` 均不在标准定义内，按项目口径须从 mdr schema 移除；avatar 作为应用扩展属性迁入 ext schema（ext.account）。
