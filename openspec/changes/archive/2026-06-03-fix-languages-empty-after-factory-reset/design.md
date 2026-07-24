## Context

系统当前使用 `LanguageInitializer` (作为 `ApplicationRunner`) 在启动时初始化内置的 G20 语言标识记录。
当用户执行恢复出厂设置（`FactoryResetService.executeFactoryReset`）时，所有业务表（包括 `language_identification` 及其关联表）都会被清空。由于应用服务器并没有重启，这 22 种内置语言记录将被彻底删除。
当用户接下来通过系统初始化向导（`/api/v1/system/setup` 对应的 `InitWizardService.setup`）重新初始化系统时，系统并未重新触发语言数据的加载。这导致重新初始化后系统的语言列表是空的。

此外，系统默认命名空间 `system-default` (由 `SystemDefaultNamespaceInitializer` 负责) 也在恢复出厂设置时被删除，并且在向导初始化时也未被重建。

## Goals / Non-Goals

**Goals:**
- 在系统通过初始化向导重新初始化（`InitWizardService.setup`）时，自动触发并重新加载 22 种内置语言数据。
- 重新触发创建系统默认命名空间 `system-default`。
- 确保重新加载的语言和命名空间逻辑在多次调用时都是幂等的，避免出现重复键或重复数据。

**Non-Goals:**
- 不涉及除语言标识和系统默认命名空间外的其他种子数据（如演示账号 admin 以外的 registrar、viewer 等测试数据，因为这些由 `DataInitializer` 控制，且该类只在演示环境/配置启用时作为开发期演示数据，生产初始化仅需要空白系统）。

## Decisions

### 方案 1: 在 `InitWizardService` 中直接注入并调用 Initializer 逻辑
- **决策**: 我们将 `LanguageInitializer` 重构，提取出一个 public 的 `initializeLanguages()` 方法。同样地，重构 `SystemDefaultNamespaceInitializer`，提取出一个 public 的 `initializeSystemDefaultNamespace()` 方法。
- **调用点**: 在 `InitWizardService.setup()` 成功执行时，首先调用 `LanguageInitializer.initializeLanguages()` 初始化语言，然后再调用 `SystemDefaultNamespaceInitializer.initializeSystemDefaultNamespace()` 初始化系统默认命名空间（由于系统默认命名空间需要用到 `en-US` 和 `zh-CN` 的语言数据，因此顺序应当是先初始化语言，后初始化系统默认命名空间）。
- **理由**: 这种设计最符合单一职责原则，且能复用已有的初始化逻辑，避免代码冗余。通过提取出 public 方法，不仅启动时可以调用，系统初始化向导运行时同样可以按需调用。

## Risks / Trade-offs

- **[Risk]** 数据库事务与并发冲突：在 `InitWizardService.setup` 事务中进行初始化可能与其他并发请求冲突。
  - **Mitigation**：由于初始化向导只有在系统未初始化（`systemSettingService.isInitialized() == false`）时才能调用，且调用时系统处于独占配置状态，因此并发冲突风险极低。同时所有的 `upsert` 方法都已经实现了防重防冲突的幂等保护。
