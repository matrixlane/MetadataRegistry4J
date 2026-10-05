# Proposal: align-ra-identifier-composite

## Why

注册机构新建/编辑对话框仅暴露 `organizationIdentifier`（界面标签"标识符"），未覆盖 ISO/IEC 11179-3:2023 §6.3.9 `Registration_Authority_Identifier` 的完整复合结构（international_code_designator + organization_identifier + organization_part_identifier），导致不带 ICD 创建的机构落库失败（500）或语义不完整。需与标准对齐，暴露完整三段录入。

## What Changes

- 后端：`RegistrationAuthorityRequest` 对 `internationalCodeDesignator` 与 `organizationIdentifier` 增加 `@NotBlank` 必填校验（标准 Table 8 两者基数 1，organization_part_identifier 0..1 选填）；按项目先例落应用层校验，不改 DB 列可空性。
- 前端：注册机构对话框暴露完整复合标识——国际代码标识符（必填）、组织标识符（必填，原"标识符"改名）、组织部件标识符（选填）。
- 初始化向导路径不变（已采集 ICD，个人版缺省 "0000"）。

## Capabilities

### Modified Capabilities

- `registration-authority-lifecycle`: 注册机构创建/编辑须按标准复合结构提供国际代码标识符与组织标识符。

## Impact

- 后端：`RegistrationAuthorityRequest`（+2 处 @NotBlank）、控制器校验测试。
- 前端：`RegistrationAuthorityList.vue` 对话框（+2 字段）、i18n、`RegistrationAuthorityList.spec.ts`。
- 兼容：旧客户端未传 ICD 时由 400 校验错误明确提示（此前为 500）。
