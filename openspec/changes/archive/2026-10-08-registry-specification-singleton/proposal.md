# Proposal: registry-specification-singleton

## Why

注册规范（`Registry_Specification`）是注册表的固有组成部分（ISO/IEC 11179-3:2023 §9.4.10），作用域为系统全局——既有规范 `注册规范独立于注册机构且系统全局` 已明确"每个注册系统一条"。但现行实现只约束了初始化后禁止删除（见归档变更 `2026-10-08-registry-specification-delete-guard`），新增仍不受限：设置页"新建"按钮与 `POST /api/v1/registry-specifications` 均可创建第二条记录，破坏"有且仅有一条"的全局语义，也使治理先决条件（`Admission Gate`）出现歧义。

应用户要求收紧为完整的不变式：**注册规范不能新增、不能删除，有且只有一条记录**（初始化向导为唯一合法的创建入口）。

## What Changes

- **后端唯一性守卫**：`RegistrySpecificationService.create` 在 `repository.count() > 0` 时抛出业务校验异常（400），提示"注册规范有且仅有一条，不允许新增"。守卫在一切状态下生效（含未初始化），形成全局唯一不变式。
- **初始化向导路径核验**：`InitWizardService.setup` 在 `createDefault` 之前调用 `clearBusinessDataOnly`（JDBC 清空业务表，含 `registry_specification`），向导重复提交/abort 重试均先清空再创建，不受守卫影响；`getWizardState` 的 `IN_PROGRESS` 判定依赖 `exists()`，行为不变。
- **前端入口隐藏**：注册规范设置页"新建"按钮隐藏（`can-create=false`）；连同上一轮的删除/批量删除隐藏，设置页仅保留"编辑"。
- **规范同步**：`openspec/specs/registry-specification/spec.md` 中"初始化后注册规范禁止删除"需求升级为"注册规范唯一且初始化后禁止增删"，新增 2 个场景（重复新增被拒、向导重复提交仍仅一条）。
- **测试**：`RegistrySpecificationServiceTest` 新增"已存在时新增被拒"用例；既有创建用例因 `count()` 未打桩默认返回 0 不受影响。

非目标：

- 不改变编辑（update）行为；
- 不改动初始化向导的创建流程与默认值兜底逻辑；
- 不影响启动期孤儿记录清理与工厂重置（均不经 `create`/`delete` 守卫）。

## Capabilities

### New Capabilities

（无。）

### Modified Capabilities

- `registry-specification`：新增唯一性不变式（有且仅有一条，已存在时新增 400 拒绝）；设置页前端入口由"隐藏删除"扩展为"隐藏新增与删除，仅保留编辑"；需求"初始化后注册规范禁止删除"改写为"注册规范唯一且初始化后禁止增删"。

## Impact

- **代码**：`metadata-registry-web` 2 个文件（`RegistrySpecificationService.create` 加守卫；`RegistrySpecificationServiceTest` 补用例）；`metadata-registry-ui` 1 个文件（`RegistrySpecificationSettings.vue` 传 `can-create=false`）。
- **API**：`POST /api/v1/registry-specifications` 在已存在记录时由 200 变为 400 业务校验错误；其余端点契约不变。
- **数据**：无迁移；无数据模型变更。
- **兼容**：唯一性守卫在存量已初始化系统上即时生效（存量系统本就仅一条记录，正常操作无感知）；任何依赖"可创建多条注册规范"的调用方将收到 400（经 GitNexus impact 确认调用方仅 Controller POST 与向导 `createDefault`，均不受损）。
- **依赖**：无新增依赖。
