# Proposal: ra-creation-wizard

## Why

注册机构创建现仅是一个单页对话框，无法同时创建自有命名空间；注册人员身份仅个人版自动建立（团队版无入口）。用户要求：创建注册机构走与系统初始化相似的向导——机构信息与命名空间（可选步骤，勾选后才展现编辑界面）一并完成；注册人员个人版默认且不可修改、团队版可自行指定。

## What Changes

- 后端：`RegistrationAuthorityRequest` 扩展 `selfAsRegistrar`、`createNamespace` 及命名空间配置字段（namespaceName、identifierInputMode、versionInputMode、versionPolicy、identifierTemplate）；`RegistrationAuthorityService.create` 编排：注册人员（PERSONAL 强制建立/TEAM 按开关）+ 可选自有命名空间创建（复用从 InitWizardService 抽取的 `NamespaceProvisioningService`，语义与初始化一致：SEGMENT_TEMPLATE 策略、RA 自挂 registrationAuthority/namingAuthority）。
- 前端：`RegistrationAuthorityList.vue` 新建改为两步向导对话框（①机构信息：复合标识+组织属性+注册人员选项；②命名空间：默认不创建，勾选后展现编辑界面，内嵌段模板编辑器）；编辑仍用简单对话框。
- 注册人员选项按版本：个人版固定为"自动成为注册人员"（不可修改）；团队版开关缺省开启、可关闭。

## Capabilities

### Modified Capabilities

- `registration-authority-lifecycle`: 注册机构创建向导与可选命名空间同事务创建；注册人员按版本差异化指定。

## Impact

- 后端：新增 `NamespaceProvisioningService`（InitWizardService 命名空间块抽取重构）、`RegistrationAuthorityRequest/Service`、测试。
- 前端：`RegistrationAuthorityList.vue` 新建向导、i18n、组件测试。
