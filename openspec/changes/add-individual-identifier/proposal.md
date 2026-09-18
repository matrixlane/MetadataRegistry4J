# Proposal: add-individual-identifier

## Why

个人（Individual）管理页自 WI-004 起就设有"标识符"列与必填表单项，但后端 `Individual` 实体和 `IndividualResponse` 从未提供该字段——列表该列恒为空、前端必填校验形同虚设。同时注册人员（Registrar）列表的名称列只回退到 identifier，导致账号自动创建的注册人员名称空白。需要补全 identifier 的端到端实现。

## What Changes

- `Individual` 实体新增可空 `identifier` 扩展属性（应用扩展属性，非 ISO/IEC 11179-3 标准属性），三方言迁移 `V20260917001__Individual_identifier.sql`。
- `IndividualRequest`/`IndividualResponse` 透传 identifier；管理端创建/更新时留空自动生成（`individual-{8位hex}`），资料页保存用 `{username}-individual`。
- `IndividualService.list` 关键词搜索覆盖 identifier 列。
- 保存个人资料时，将账号绑定的、未关联 Individual 的 Registrar 回填，使注册人员列表显示姓名。
- UI：IndividualList 标识符改为选填（"留空自动生成"）、名称渲染修正为 `row.name`；RegistrarList 名称列改为 `individualName || organizationName || identifier` 兜底。

## Capabilities

### New Capabilities

- `individual-identifier`: Individual 标识符字段的存储、自动生成、透传与列表搜索契约，以及 Registrar 名称展示的回填联动。

### Modified Capabilities

（无既有能力的需求语义变更；为既有 UI 列补后端契约。）

## Impact

- `metadata-registry-common-facilities`：`Individual` +1 字段。
- `metadata-registry-web`：三份迁移；`IndividualRequest/Response`、`IndividualService`、`AuthService.updateMyIndividual`；`AuthServiceTest`/`IndividualServiceIntegrationTest` 用例补充。
- `metadata-registry-ui`：`IndividualList.vue`、`RegistrarList.vue`。
- 无第三方依赖变更；存量数据惰性回填，不清洗。
