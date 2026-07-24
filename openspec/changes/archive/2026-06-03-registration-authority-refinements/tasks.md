## 1. 后端修改与校验

- [x] 1.1 修改 `RegistrationAuthorityService.delete` 添加单注册表模式下的删除校验，防止删除注册机构
- [x] 1.2 更新 `InitWizardSetupRequest` 以支持注册机构的英文名称输入
- [x] 1.3 更新 `InitWizardService`，在系统初始化时同时保存注册机构的中文和英文名称
- [x] 1.4 更新 `RegistrationAuthorityRequest` 和 `RegistrationAuthorityResponse` 以包含 `englishName` 属性
- [x] 1.5 修改 `RegistrationAuthorityService` 的 `applyRequest` 和 `toResponse`，支持中英文名称的双向数据映射
- [x] 1.6 升级 `OrganizationResponse` 和 `OrganizationRequest`，增加 `identifier` 和 `englishName` 属性
- [x] 1.7 更新 `OrganizationService.toResponse`，若是注册机构，映射其 `organizationIdentifier` 至 `identifier`，同时映射英文名称
- [x] 1.8 编写并运行相关测试，确保所有后端修改通过编译与功能验证

## 2. 前端界面控制与名称同步

- [x] 2.1 修改 `InitWizard.vue`，在第二步的表单中新增“注册机构英文名称”的输入域
- [x] 2.2 修改 `RegistrationAuthorityList.vue` 的表格删除操作及 `DataTablePage` 的 `can-delete` 属性绑定，在单注册表模式下禁用删除
- [x] 2.3 修改 `RegistrationAuthorityList.vue` 的表单及表格字段映射，正确绑定 `name`、`englishName` 和 `organizationIdentifier`
- [x] 2.4 修改 `OrganizationList.vue`，将名称字段的绑定修复为 `row.name || '-'`，并显示 `identifier` 属性
