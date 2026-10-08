# Tasks: registry-specification-singleton

## 1. 后端唯一性守卫

- [x] 1.1 `RegistrySpecificationService.create` 增加守卫：`repository.count() > 0` 时抛 `BusinessException(ApiCodes.VALIDATION_FAILED, "注册规范有且仅有一条，不允许新增")`（先于 `applyRequest`/`save`）。验证：新增"已存在时新增被拒"用例绿，断言未触达 `save`。
- [x] 1.2 核验向导路径不受影响：`InitWizardService.setup` 先 `clearBusinessDataOnly()`（JDBC 清空含 `registry_specification`）再 `createDefault`（委托 `create`）；`abort` 同。验证：`InitWizardServiceIntegrationTest`/`InitWizardServiceOrderTest` 绿；GitNexus impact 确认 `create` 调用方仅 Controller POST 与 `createDefault`（risk LOW）。
- [x] 1.3 核验只读路径不受影响：`DataGovernanceReadinessService` 与 `getWizardState` 仅用 `exists()`。验证：编译 + 相关测试绿。

## 2. 前端入口隐藏

- [x] 2.1 `RegistrySpecificationSettings.vue`：`DataTablePage` 传 `:can-create="false"`，隐藏"新建"按钮（与既有 `:can-delete="!systemInitialized"` 配合，设置页仅保留"编辑"）。验证：`vue-tsc --noEmit` 通过；eslint 无新增问题。

## 3. 规范与测试同步

- [x] 3.1 主规范 `openspec/specs/registry-specification/spec.md`：需求"初始化后注册规范禁止删除"改写为"注册规范唯一且初始化后禁止增删"，新增场景"已存在注册规范时再次新增"与"初始化向导重复提交后仍仅一条"。验证：`openspec validate registry-specification --type spec` 通过；本 change 的 spec delta 与主规范一致。
- [x] 3.2 回归测试：`RegistrySpecificationServiceTest`（含新增用例）、`RegistrySpecificationControllerTest`、`InitWizardServiceIntegrationTest`、`InitWizardServiceOrderTest` 全绿。

## 4. 收尾

- [x] 4.1 `openspec validate --archived` 中本 change 通过（任务完整性 + spec delta 格式）。
