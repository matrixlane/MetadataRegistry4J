## Why

目前注册表标识符的唯一标识必须由用户手动输入，系统虽然预留了标识符生成策略的配置，但并未在元数据项创建/更新流程中集成该自动生成功能。对于本地元数据，自动生成可以显著提高注册效率，避免手动输入带来的编码冲突；而对于外部标准元数据，则依然支持手动录入以维持其原有的外部标识。

## What Changes

- 在元数据项创建/更新时支持“自动生成”与“手动录入”双重模式。
- 后端 `ItemAdministrationService` 集成基于数据库策略配置的 `IdentifierStrategyConfigService` 以及悲观锁保护的序列分配器，在用户选择自动生成（即 identifier 传入空值时）自动生成对应的命名空间标识符。
- 前端 `ItemFormDialog` 提供“生成方式”选择组件，允许用户在创建项时切换“自动生成”与“手动输入”。
- 在“自动生成”模式下，前端标识符输入框禁用并显示提示占位符，且绕过“标识符必填”校验。
- 编辑元数据项时，标识符显示为只读，不允许变更。

## Capabilities

### New Capabilities
- `identifier-auto-generation`: 支持基于命名空间与标识符类型的灵活生成策略自增或生成标识符，集成悲观锁保证并发安全。

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use existing spec names from openspec/specs/. Leave empty if no requirement changes. -->

## Impact

- 接口变动：`ItemRegistrationRequest` 中 `identifier` 字段不再是物理强校验必填，后端 `ItemAdministrationService` 会在 `identifier` 为空时执行策略生成。
- 新增依赖：`ItemAdministrationService` 依赖 `IdentifierStrategyConfigService`（打通其与 `SequenceRegistryService` 行锁实现的回调绑定）。
- 数据库扩展：使用已有的 `identifier_strategy_config` 和 `identifier_sequence_registry` 结构，提供数据层支撑。
