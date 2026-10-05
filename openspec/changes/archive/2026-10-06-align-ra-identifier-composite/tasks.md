# Tasks: align-ra-identifier-composite

## 1. 后端

- [ ] 1.1 `RegistrationAuthorityRequest` 对 `internationalCodeDesignator`、`organizationIdentifier` 加 `@NotBlank`；验证：控制器校验测试（缺 ICD → 400；完整三段 → 200）

## 2. 前端

- [ ] 2.1 `RegistrationAuthorityList.vue` 对话框暴露完整复合标识（国际代码标识符必填、组织标识符必填、组织部件标识符选填）+ i18n；验证：组件测试断言字段存在与 payload

## 3. 验证与归档

- [ ] 3.1 全量测试 + build + openapi/static 同步 + detect_changes + 归档
