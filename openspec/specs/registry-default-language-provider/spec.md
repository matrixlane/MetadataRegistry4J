
## Purpose

为所有需要创建或编辑指称（Designation）与定义（Definition）的 UI 组件和后台服务提供当前注册表的默认语言标识，使语言标识与来源文档成为可选项；当用户未显式指定语言时，系统自动使用 `RegistrySpecification.defaultLanguage` 补全，减少重复选择并符合 ISO/IEC 11179-3:2023 的元模型语义。

## Requirements

### Requirement: 注册表默认语言全局可获取
系统 SHALL 向所有需要创建或编辑指称/定义的 UI 组件提供当前注册表的默认语言标识。

#### Scenario: 应用启动时加载默认语言
- **WHEN** 应用 shell（如 `AppLayout.vue`）挂载
- **THEN** 系统 SHALL 调用 `registrySpecificationApi.list({ size: 1 })`（或等价的“当前注册规范”接口）并缓存返回的 `defaultLanguageId` 与 `defaultLanguageIdentifier`

#### Scenario: 通过组合式函数读取默认语言
- **WHEN** 任意编辑器组件调用 `useRegistryDefaultLanguage()`
- **THEN** 该函数 SHALL 返回 `{ defaultLanguageId, defaultLanguageIdentifier, loading, error }`，不触发重复请求

### Requirement: 后端暴露稳定的默认语言读取能力
后端服务 SHALL 能够通过 `RegistrySpecificationRepository` 获取当前注册规范及其 `defaultLanguage`。

#### Scenario: 唯一注册规范存在时返回默认语言
- **WHEN** 业务服务调用 `RegistrySpecificationRepository.findFirst()`（或等效查询）
- **THEN** 若存在记录且 `defaultLanguage` 非空，则返回该 `LanguageIdentification` 的 ID 与标识符

#### Scenario: 无注册规范时返回空
- **WHEN** 数据库中不存在 `RegistrySpecification` 记录
- **THEN** 查询 SHALL 返回空，调用方据此抛出业务错误提示用户先完成系统初始化
