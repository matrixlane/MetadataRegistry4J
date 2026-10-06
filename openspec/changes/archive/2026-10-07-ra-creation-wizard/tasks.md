# Tasks: ra-creation-wizard

## 1. 后端

- [ ] 1.1 抽取 `NamespaceProvisioningService`（designation+双挂+标识设置+策略配置，从 InitWizardService 原样搬迁）；InitWizardService 改为委托；验证：向导既有测试（InitWizardServiceIntegrationTest 命名空间相关用例）原样通过
- [ ] 1.2 `RegistrationAuthorityRequest` 扩展 selfAsRegistrar/createNamespace/namespaceName/identifierInputMode/versionInputMode/versionPolicy/identifierTemplate；验证：编译
- [ ] 1.3 `RegistrationAuthorityService.create` 编排：PERSONAL 恒建注册人员；TEAM 按 selfAsRegistrar=true 为当前超管建 Registrar；createNamespace=true 时同事务 provisionOwnNamespace 并回挂 ra.namespace；验证：服务单测（TEAM 开关两分支、PERSONAL 忽略开关、namespace 分支 mock 断言）+ 集成测试（创建后 namespace 行/策略配置/双挂存在）

## 2. 前端

- [ ] 2.1 `RegistrationAuthorityList.vue` 新建向导（el-steps 两步 + 命名空间可选开关 + SegmentTemplateDesigner 复用；个人版注册人员固定提示/团队版开关缺省开）+ i18n；验证：组件测试（字段存在、开关显隐、payload 组装）

## 3. 验证与归档

- [ ] 3.1 全量测试 + build + static 同步 + detect_changes + 归档
