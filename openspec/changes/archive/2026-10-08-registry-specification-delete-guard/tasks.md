# Tasks: registry-specification-delete-guard

## 1. 后端删除守卫

- [x] 1.1 `RegistrySpecificationService` 注入 `SystemSettingService`；`delete(UUID id)` 在存在性检查之前增加守卫：`isInitialized()` 为 true 时抛 `BusinessException(ApiCodes.VALIDATION_FAILED, "系统初始化后注册规范不允许删除，仅允许编辑")`。验证：`RegistrySpecificationServiceTest` 新增"初始化后单条删除被拒"用例绿，未初始化删除用例（存在/不存在）补充 stub 后绿。
- [x] 1.2 确认守卫覆盖批量删除路径（前端批量删除逐条调用同一 `DELETE /{id}`）。验证：新增"初始化后批量删除逐条被拒"用例绿；GitNexus impact 确认 `delete` 唯一调用方为 `RegistrySpecificationController.delete`（risk LOW）。
- [x] 1.3 确认不受影响路径：`update` 编辑、`cleanupOrphanRecords` 孤儿清理（`repository.deleteAll`）、`FactoryResetService` 均不经删除守卫。验证：代码走读 + 全量编译。

## 2. 前端入口隐藏

- [x] 2.1 `RegistrySpecificationSettings.vue`：页面加载时经 auth store `checkInitialized()` 获取初始化状态；已初始化时 `DataTablePage` 传 `:can-delete="false"`（隐藏行内删除按钮、多选列、批量删除条）。查询失败按已初始化处理（fail-safe 隐藏删除）。验证：`vue-tsc --noEmit` 通过；eslint 无新增问题。

## 3. 规范与测试同步

- [x] 3.1 `openspec/specs/registry-specification/spec.md` 新增需求"初始化后注册规范禁止删除"及 3 个场景（删除被拒、设置页无删除入口、未初始化放行）。验证：本变更的 spec delta 与主 spec 内容一致。
- [x] 3.2 回归测试：`RegistrySpecificationServiceTest` 10/10 绿、`RegistrySpecificationControllerTest` 6/6 绿（`./mvnw -pl metadata-registry-web -am test`）。验证：surefire 报告零失败。

## 4. 收尾

- [x] 4.1 `openspec validate --all` 通过（本 change 的 proposal/spec delta/tasks 格式合规）。
