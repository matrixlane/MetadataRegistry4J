## Why

当前系统在单注册表模式及组织管理中存在以下注册机构相关的易用性与一致性问题：
1. **删除限制缺失**：单注册表模式下唯一的注册机构是不允许被删除的（仅允许编辑），但目前系统未对此进行限制。
2. **中英文名称同步问题**：
   - 系统初始化时只能配置注册机构的中文名称，无法设置英文名称。
   - 注册机构编辑界面中显示有“中文名称”和“英文名称”输入框，但这些字段与后端 DTO 属性不匹配，导致编辑保存后无法正确同步英文名称，且再次打开编辑时输入框显示为空白。
3. **组织列表未关联显示注册机构**：注册机构（Registration Authority）继承自组织（Organization），在概念上也是一种特殊的组织。但目前在“组织”列表页面中没有显示已创建的注册机构，且组织列表存在字段绑定错误，导致名称显示为空白。

## What Changes

1. **单注册表删除限制**：
   - **后端拦截**：在 `RegistrationAuthorityService.delete` 中增加校验，若运行模式为 `SINGLE`，则抛出异常拒绝删除。
   - **前端禁用**：在 `RegistrationAuthorityList.vue` 中，当运行模式为 `SINGLE` 时，禁用/隐藏表格行操作中的“删除”按钮。
2. **初始化与编辑中英文名称同步**：
   - **初始化向导**：在 `InitWizard.vue` 界面中增加“注册机构英文名称”输入项，并在 `InitWizardService` 中同时保存中英文名称至数据库。
   - **注册机构 DTO 升级**：为 `RegistrationAuthorityRequest` 和 `RegistrationAuthorityResponse` 增加 `englishName` 字段，并在 Service 层与实体中的 `name` 列表（第一项中文，第二项英文）进行双向映射。
   - **注册机构前端**：将 `RegistrationAuthorityList.vue` 的表单及列表字段绑定到 `name`、`englishName` 及 `organizationIdentifier`，从而实现编辑时的正确同步与加载。
3. **组织列表集成与修复**：
   - **组织 DTO 升级**：为 `OrganizationResponse` 增加 `identifier`（标识符）和 `englishName` 字段。
   - **组织列表后端**：在 `OrganizationService` 转换 `toResponse` 时，如果是 `RegistrationAuthority` 类型的实体，则将其 `organizationIdentifier` 映射到 `identifier` 字段中返回，以供组织列表显示；同时映射英文名称。
   - **组织列表前端**：修复 `OrganizationList.vue` 的列表字段绑定，将名称绑定由 bug 字段 `row.names?.[0]?.sign` 修正为标准的 `row.name || '-'`。

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `registry-specification`: 限制单注册表模式下注册机构的删除，以及支持组织列表与初始化配置中对注册机构中英文名称的管理。

## Impact

1. **后端模块 (`metadata-registry-web`)**：
   - DTO 修改：更新 `InitWizardSetupRequest`、`RegistrationAuthorityRequest`、`RegistrationAuthorityResponse`、`OrganizationResponse`、`OrganizationRequest`。
   - 服务层修改：更新 `InitWizardService`、`RegistrationAuthorityService`、`OrganizationService`。
2. **前端模块 (`metadata-registry-ui`)**：
   - 初始化组件：更新 `InitWizard.vue`。
   - 管理页面：更新 `RegistrationAuthorityList.vue` 和 `OrganizationList.vue`。
