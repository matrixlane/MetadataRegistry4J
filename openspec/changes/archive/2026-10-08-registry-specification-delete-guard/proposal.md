# Proposal: registry-specification-delete-guard

## Why

注册规范（`Registry_Specification`）是注册表的固有组成部分（ISO/IEC 11179-3:2023 §9.4.10），作用域为系统全局（每个注册系统一条），也是数据治理工作的先决条件（`Admission Gate for Projects and Metadata Registry`）。但现行实现中注册规范可随时删除/批量删除，存在以下问题：

- 系统初始化完成后删除注册规范，使注册表丧失治理先决条件，与"注册规范为注册表固有组成部分"的标准语义冲突；
- 前端设置页与列表页均提供单条删除与批量删除入口，误操作即破坏系统全局配置，且无任何提示或阻断；
- 与既有治理先例不一致：个人版主要注册机构（Primary）已实施删除保护（`RegistrationAuthorityService`），注册规范作为更基础的全局对象却无同等保护。

应用户要求：初始化后的注册规范只允许编辑更改，不允许删除或批量删除。

## What Changes

- **后端删除守卫**：`RegistrySpecificationService.delete` 增加初始化状态守卫——系统已初始化（`system.initialized=true`）时抛出业务校验异常（400），提示"系统初始化后注册规范不允许删除，仅允许编辑"，不再触达数据库删除；未初始化（初始化向导草稿阶段）保持原行为放行。批量删除前端逐条调用同一 `DELETE /api/v1/registry-specifications/{id}` 接口，一处守卫覆盖单条与批量两种入口。
- **前端入口隐藏**：注册规范设置页加载时经 auth store `checkInitialized()` 获取初始化状态，已初始化时向 `DataTablePage` 传 `can-delete=false`——行内"删除"按钮、多选列、批量删除条一并隐藏，仅保留"编辑"。初始化状态查询失败时按已初始化处理（fail-safe 方向为隐藏删除）。
- **规范同步**：`openspec/specs/registry-specification/spec.md` 新增需求"初始化后注册规范禁止删除"及 3 个场景；既有"CRUD 端点"需求不变（端点仍暴露，初始化后调用返回 400）。
- **测试**：`RegistrySpecificationServiceTest` 新增 2 个守卫用例（初始化后单条删除被拒、批量删除逐条被拒），既有删除用例补充未初始化 stub；Controller 测试不受影响（service 为 mock）。

非目标：

- 不限制注册规范**创建**（系统全局一条的约束另行处理，不在本次范围）；
- 不改变编辑（update）行为；
- 不影响启动期孤儿记录清理（`cleanupOrphanRecords` 走 `repository.deleteAll`，不经删除守卫）与工厂重置（`FactoryResetService` 不经此服务）。

## Capabilities

### New Capabilities

（无。）

### Modified Capabilities

- `registry-specification`：新增"初始化后注册规范禁止删除"需求（后端 400 拒绝 + 前端隐藏删除入口）；既有删除语义按初始化状态差异化（未初始化放行）。

## Impact

- **代码**：`metadata-registry-web` 2 个文件（`RegistrySpecificationService` 注入 `SystemSettingService` 并加守卫；`RegistrySpecificationServiceTest` 补用例）；`metadata-registry-ui` 1 个文件（`RegistrySpecificationSettings.vue` 初始化状态驱动 `can-delete`）。
- **API**：`DELETE /api/v1/registry-specifications/{id}` 契约不变，系统已初始化时返回 400 业务校验错误（原返回 200）。
- **数据**：无迁移；无数据模型变更。
- **兼容**：未初始化系统行为完全不变；已初始化系统删除操作由"成功"变为"400 拒绝"，属有意行为收紧。
- **依赖**：无新增依赖。
