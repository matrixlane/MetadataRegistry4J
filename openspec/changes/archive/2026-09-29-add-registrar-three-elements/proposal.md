## Why

注册人员（Registrar）三要素（个人姓名、角色、组织）目前散落在两处语义不一致的位置：
1. 列表里仅展示"名称"列，且该列 fallback 到 `organizationName`（即注册机构名），既不是个人姓名也不是角色名——这是 `RegistrarList.vue:16` 的巧合性表象，碰巧之前 RA 默认名"注册管理员"撞字。
2. 后端 `RegistrarService.createRegistrarForUser`（`RegistrarService.java:142-160`）有意不创建 `Individual` 与 `Role`，依赖用户后续"编辑个人资料"和"角色管理"流程补完，导致 init 后注册人员页面三要素一片空白。
3. `RegistrarRequest` 没有 `roleId` 字段，`RegistrarService.applyRequest`（:82-109）也未处理 Role——编辑表单无法编辑角色。

用户明确要求：**列表三列独立展示（姓名/角色/组织），编辑表单支持编辑个人姓名+选择角色+选择组织，初始化向导让管理员直接填写三要素并创建对象与关联。**

## What Changes

- **列表三列**：删除 `RegistrarList.vue` 的 fallback 链，改为「标识符 | 姓名 | 角色 | 组织 | 操作」五列；姓名来自 `Individual.name`，角色来自 `Role.roleName`，组织来自 `Organization.name`。
- **编辑表单**：将「中文名称/英文名称」字段替换为「个人姓名」（输入框，编辑即创建/更新 Individual）、「角色」（下拉，来源 `RoleService.options()`，可清空）、「组织」（下拉，来源现有组织选项）。
- **`RegistrarRequest`**：增加 `roleId` 字段。
- **`RegistrarService.applyRequest`**：补全 Role 处理；edit 时支持清空 roleId。
- **`RegistrarService.createRegistrarForUser`**：接收 `individualName`、`roleName`、`organizationId` 三参数；按需创建 `Individual(name)`、`Role(roleName)` 实体并 set 关联；`roleName` 已存在则复用。
- **初始化向导**（`InitWizard.vue` 注册机构步骤）：增加三字段——
  - 个人姓名（可选，PERSONAL 默认"注册管理员"）
  - 角色（PERSONAL 默认"注册管理员"，必填；TEAM 必填）
  - 组织（默认 = 当前创建的 RA）
- **`InitWizardService.setup` 请求 DTO** 增加对应字段并透传到 `createRegistrarForUser`。
- **`active-registrar-ownership` 与 `individual-identifier` 兼容**：
  - 既有 `Registrar.individual` 回填规则不变；init 已绑定的不再走回填。
  - 既有 `add-submitter-contact-identity` "Registrar 行仅允许编辑 Role"约束将变更为"可编辑 Role、Individual、Organization"，由本变更同时修订对应 spec。

## Capabilities

### New Capabilities

- `registrar-identity-elements`：注册人员的三要素（个人姓名、角色、组织）完整管理，包括列表展示契约、编辑契约、初始化写入契约。这是新的独立能力，路径遵循项目现有 `system-initialization` 等既有 kebab-case 命名风格。

### Modified Capabilities

- `system-initialization`：增加初始化向导中"管理员成为 Registrar 时填写个人姓名/角色/组织"的契约；扩展 `setup` 请求 DTO 形态；扩展 PERSONAL 版默认填充语义。
- `contact-identity-management`：解除"Registrar 行 SHALL 仅允许修改 Role 关联"的硬约束，改为 Registrar 行的"我的"联系人入口允许同时编辑 Role、Individual、Organization 三要素，并仍允许 Role 清空。
- `user-profile-individual`：调整 Registrar 行编辑入口场景，使其体现上述新规约。

## Impact

| 层 | 受影响 |
| --- | --- |
| 后端 Service | `RegistrarService`、`InitWizardService` |
| 后端 DTO | `RegistrarRequest`（+roleId）、`InitWizardSetupRequest`（+individualName/+roleName/+organizationId） |
| 后端 Repository | `IndividualRepository`（读/写）、`RoleRepository`（按名称查重） |
| 前端 | `RegistrarList.vue`、`RegistrarList` 编辑表单 `ResourceFormDialog` 字段、`InitWizard.vue` |
| OpenSpec | 增量能力 `registrar-identity-elements/spec.md`；修订 `system-initialization`、`active-registrar-ownership`；兼容 `individual-identifier` |
| 数据 | 零 schema 变更；现有数据兼容性：已 init 的 `admin-registrar` 仍无三要素，列表显示为空（不再 fallback），用户可在编辑表单补完 |

## Out of Scope

- 团队版 `selfAsRegistrar` 开关（本变更不改变其语义）
- `Role` 的完整 CRUD（已存在）
- `Organization` 的完整 CRUD（已存在，本变更仅消费 options）
- 自动生成"管理员个人资料"页面引导（init 已建好则无需首次保存引导）
