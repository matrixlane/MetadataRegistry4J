# Tasks: remove-registry-running-mode

## 1. 后端移除

- [ ] 1.1 `SystemSettingService`：删除 `KEY_RUNNING_MODE`、`getRunningMode()`、`setRunningMode()`、`setRegistryPolicy()`；补/改测试。验证：编译通过 + SystemSetting 相关测试绿
- [ ] 1.2 `SystemController`：删除治理策略切换端点（及其导入/测试）。验证：编译 + 控制器测试绿
- [ ] 1.3 `RegistrationAuthorityService.create`：删除 TEAM SINGLE 限建分支与"单注册表模式下只能创建一个注册机构"错误；javadoc 更新为"任意版本不限数量"。验证：`RegistrationAuthorityServiceTest` 中 TEAM SINGLE 拒绝用例改为放行后全绿
- [ ] 1.4 `InitWizardService`：getStatus 去掉 runningMode；commit 去掉 `setRunningMode("SINGLE")` 与相关注释；类 javadoc/行内注释清理。`InitWizardStatusResponse`/`InitWizardSetupRequest` 删除 `runningMode` 组件。验证：向导相关测试（InitWizardServiceIntegrationTest/InitWizardControllerTest/InitWizardServiceOrderTest）全绿
- [ ] 1.5 注释口径：`ActiveRegistrarService`、`ItemAdministrationService`、`NamespacePermissionEvaluator`、`RegistryContextBootstrapService`、`DeploymentEdition` javadoc 中"单注册表模式"表述改为按"系统唯一机构（计数=1）"的客观描述

## 2. 前端移除

- [ ] 2.1 `stores/auth.ts` 删除 `runningMode` 字段（及其赋值/读取点）。验证：vue-tsc 通过
- [ ] 2.2 `InitWizard.vue`：payload 与本地状态删除 `runningMode`。验证：vitest 相关用例绿
- [ ] 2.3 `RegistrationAuthorityList.vue`：删除"团队版 SINGLE 隐藏新建入口"门控（新建入口常显；个人版提示保留），注释更新；`RegistrationAuthorityList.spec.ts` 删除/改写 SINGLE 门控用例。验证：该 spec 全绿
- [ ] 2.4 全仓 grep 确认无残留 `runningMode|SINGLE.*MULTI|单注册表|多注册表|注册表模式`（排除构建产物、数据字典 ExportMode、归档目录）。验证：grep 输出经人工核对

## 3. 验证与归档

- [ ] 3.1 后端全量测试（仅基线环境性失败）+ 前端 vitest 全绿 + build；openapi.json 再生成；dist→static 同步
- [ ] 3.2 `openspec validate --all`；提交后 `detect_changes` compare 无 partial/truncated；归档本变更
