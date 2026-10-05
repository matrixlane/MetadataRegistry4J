# Tasks: expose-organization-attributes-in-ra-form

## 1. 前端表单补全

- [ ] 1.1 `RegistrationAuthorityList.vue` 对话框增加通信地址/电子邮箱（多值）/电话号码（多值）/网址 + i18n；验证：组件测试断言字段存在

## 2. 后端测试

- [ ] 2.1 集成测试：经服务创建 RA 携带组织属性 → 断言 organization/organization_email/organization_phone 行写入且回显一致

## 3. 验证与归档

- [ ] 3.1 全量测试 + build + static 同步 + detect_changes + 归档
