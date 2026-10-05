# Proposal: expose-organization-attributes-in-ra-form

## Why

Registration_Authority 是 Organization 的子类（ISO/IEC 11179-3:2023 §9.4.6 extends §6.3.3，JOINED 共表），创建一条注册机构记录即同时创建一条组织记录。当前注册机构对话框仅采集名称与复合标识，未陈列组织属性（通信地址、电子邮箱、电话、网址），后端 `RegistrationAuthorityRequest`/服务虽已具备这些字段，但界面无法录入。

## What Changes

- 注册机构新建/编辑对话框陈列全部组织属性：中文名称（已有）、英文名称（已有）、通信地址（mail_address，0..1）、电子邮箱（email_address，0..*，多值）、电话号码（phone_number，0..*，多值）、网址（web_address，0..1）。
- 无后端 API 变更（字段已存在）；补集成测试断言"新增注册机构同时写入组织行及其属性"。

## Capabilities

### Modified Capabilities

- `registration-authority-lifecycle`: 注册机构创建/编辑表单须陈列组织（Organization，§6.3.3 表 2）全部属性。

## Impact

- 前端：`RegistrationAuthorityList.vue` 对话框（+4 字段，邮箱/电话为多值标签输入）、i18n、组件测试。
- 后端测试：新增集成测试断言 organization/organization_email/organization_phone 行随注册机构创建写入。
