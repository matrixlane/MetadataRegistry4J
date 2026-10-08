# Proposal: remove-system-default-namespace

## Why

ISO/IEC 11179-3:2023 没有"系统默认/根命名空间"概念，`Namespace.shorthand_prefix` 仅是 0..1 的人类可读便捷前缀（标准无唯一性要求），Namespace 作为 Item 的识别也是可选的。实库核实：预置的 `sys:` 系统默认命名空间未 scope 任何 ScopedIdentifier，无任何 Namespace 实例被识别，其唯一运行职责是创建门槛与默认命名空间推导中的排除哨兵，且不参与任何业务数据承载（Registry Context 明确落在注册机构自有命名空间）。用户裁定：功能当前不需要，移除——标准没有、实现用不到的就不保留。

## What Changes

- **删除系统默认命名空间预置**：移除 `SystemDefaultNamespaceInitializer` 整类（含 `SYSTEM_DEFAULT_PREFIX` / `SYSTEM_DEFAULT_SCHEME` / `SYSTEM_ORGANIZATION_ID` 常量），`StaticDataPresetService.presetAll()` 不再预置 `sys:` 命名空间，初始化向导 `setup()` 移除 3.6 步 NamingAuthority 挂接调用。**BREAKING**（存量行为）：启动期/出厂后/向导兜底不再保证 `sys:` 命名空间存在。
- **简化命名空间门槛与默认推导**：`ItemAdministrationService` 外部命名空间判定简化为"无关联注册机构"（删除 system-default 排除）；默认命名空间推导删除 scheme 排除条件；`NamespaceRepository` 删除仅为 sys: 服务的两个 finder，新增 `findFirstByRegistrationAuthority_Id`。
- **移除遗留系统组织保护**：`OrganizationService` 删除系统组织（固定 UUID …0001）的列表过滤与删除保护；历史系统组织行由数据迁移统一清理。
- **存量数据迁移**：新增三方言 Flyway 迁移 `V20261004001__Remove_system_default_namespace.sql`——按 scheme 定位并删除存量 `sys:` 命名空间行（先子表后本体，带 ScopedIdentifier 引用防御检查），并清理无引用的历史系统组织行。
- **前端死逻辑清理**：`ItemRegistrationSection.vue` 删除 `SYSTEM_DEFAULT_SCHEME` 常量、门禁判定与默认推导排除条件及对应 spec 用例。
- **测试同步**：删除 `SystemDefaultNamespaceInitializerTest`；改造 `NamespaceGateIntegrationTest`、`ItemAdministrationServiceTest`、`InitWizardServiceIntegrationTest` 等的相关用例。

## Capabilities

### New Capabilities

（无。）

### Modified Capabilities

- `system-initialization`：静态预置集合移除"系统组织与 `sys:` 系统默认命名空间"；删除 `sys:` NamingAuthority 挂接要求；系统组织段落改写（迁移清理替代启动调和，删除保护 rationale 随 sys: 移除而消失）；setup 幂等场景中的"系统默认命名空间"表述移除。
- `identification`：命名空间前置条件删除"系统内部命名空间（system-default）SHALL NOT 计入该前置条件"条款。
- `default-namespace-by-active-registrar`：默认命名空间推导边界删除 `schemeReference != 'urn:metadata-registry:system-default'` 条件；对应 Scenario 改写。
- `active-registrar-ownership`：外部命名空间定义措辞删除"非 system-default"限定。

## Impact

- 代码：`metadata-registry-web` 9 个主类、6 个测试类；`metadata-registry-ui` 2 个组件文件 + 1 个 spec。
- 数据：三方言（SQL Server / SQLite / 达梦）存量库各新增一次 Flyway 迁移；`db-baseline` 头部注释与 README 表述修正（基线数据本身不含 sys: 行）。
- API：无契约变更；`getWizardState` 在"库设置完成重启后、向导未执行"时恢复返回 `READY`（nsCount 不再被 sys: 污染）。
- 依赖：无新增依赖。
