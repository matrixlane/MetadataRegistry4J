## Context

当前系统在单注册表模式、中英文名称同步以及组织管理集成方面存在一些一致性和易用性缺陷。为了提升系统的体验，并且在 ISO 11179 概念模型下保持一致（注册机构本质上是一个特殊的组织，且注册机构本身可以具有多语言名称），需要对系统进行一系列的前后端调整。

## Goals / Non-Goals

**Goals:**
- **限制删除**：单注册表模式下，禁止删除唯一的注册机构。
- **支持英文名称设置与编辑同步**：
  - 系统初始化引导阶段即支持设置英文名称。
  - 注册机构编辑界面中的中英文名称能正确在前后端双向同步，且再次打开编辑时可以回显。
- **组织列表显示注册机构**：组织列表以多态方式查出并显示所有的组织，同时将注册机构包含在内，且正确显示其标识符和名称（修复显示为 `-` 的缺陷）。

**Non-Goals:**
- 不为普通组织添加新的 `identifier` 数据库列（因为概念上普通组织暂时没有该字段需求）。

## Decisions

### 1. 单注册表删除限制
- **后端拦截**：
  - 在 `RegistrationAuthorityService.delete` 方法中注入并调用 `SystemSettingService`。
  - 若 `systemSettingService.getRunningMode()` 为 `SINGLE`，则直接抛出 `BusinessException(ApiCodes.VALIDATION_FAILED, "单注册表模式下不允许删除注册机构")`。
- **前端禁用**：将 `DataTablePage` 的 `can-delete` 属性动态绑定为 `!isSingleMode`。当单注册表模式激活时，删除按钮直接对用户隐藏。

### 2. 初始化向导支持英文名称设置
- **DTO 扩展**：
  - `InitWizardSetupRequest` 增加 `registrationAuthorityEnglishName` 字段。
- **服务层修改**：
  - `InitWizardService.setup` 方法在创建 `RegistrationAuthority` 时，如果英文名不为空，则将 `Sign.of(request.registrationAuthorityEnglishName())` 追加到 `ra.getName()` 列表中（使中文为第一项，英文为第二项）。
- **前端扩展**：
  - 在 `InitWizard.vue` 的第二步“默认注册机构配置”中增加“注册机构英文名称”的表单项。

### 3. 注册机构编辑界面中英文及标识符同步
- **DTO 升级**：
  - `RegistrationAuthorityRequest` 增加 `englishName` 字段。
  - `RegistrationAuthorityResponse` 增加 `englishName` 字段。
- **服务映射**：
  - 在 `RegistrationAuthorityService.applyRequest` 中：
    - 中文名称保存到 `name` 列表的第一项。
    - 英文名称（如果不为空）保存到 `name` 列表的第二项；如果为空且列表有第二项，则将其从列表中移去。
  - 在 `RegistrationAuthorityService.toResponse` 中：
    - 读取列表第一项为 `name`（中文名称）。
    - 读取列表第二项（若存在）为 `englishName`（英文名称）。
- **前端界面**：
  - 修改 `RegistrationAuthorityList.vue` 的表单，将中文名称绑定到 `form.name`，英文名称绑定到 `form.englishName`，标识符绑定到 `form.organizationIdentifier`。
  - 表格的“标识符”列的 prop 绑定更新为 `prop="organizationIdentifier"`。

### 4. 组织列表显示注册机构与字段绑定修复
- **后端 DTO 扩展与多态映射**：
  - `OrganizationResponse` 增加 `identifier` 和 `englishName` 字段。
  - 在 `OrganizationService.toResponse` 中，判断 `entity instanceof RegistrationAuthority`：
    - 若是，则将其 `organizationIdentifier` 设置到 `identifier` 属性，同时将其 `getName()` 的第二项（若存在）设置到 `englishName`。
- **前端列表绑定修复**：
  - 修改 `OrganizationList.vue` 的表格列，将名称列的 `{{ row.names?.[0]?.sign || '-' }}` 修正为标准的 `{{ row.name || '-' }}`。

## Risks / Trade-offs

- **[Risk]** 多名称列表项定位：目前使用列表索引 0 代表中文，1 代表英文，若后续需要支持更多语言，可能需要引入更复杂的语言标识关联。
  - **Mitigation**：由于目前系统暂不需要支持除中英以外的复杂多语，保持索引映射为最简洁也是改动最小的最佳方案。
