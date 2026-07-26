**Note (2026-07-27)**: `IdentifierType` 概念已在本变更 `cleanup-identifier-type-and-simplify-strategy-routing` 中清理。LOINC/SNOMED CT 等代码集应以 `Namespace` 表达，而非 `IdentifierType` 枚举值。

## 1. 后端基础设施实现 (Backend Infrastructure)

- [x] 1.1 创建 `IdentifierStrategyConfigRepository` 以从数据库查询有效的策略配置
- [x] 1.2 创建 `SequenceRegistryRepository` 并声明基于悲观排他锁（PESSIMISTIC_WRITE）的行锁查询接口
- [x] 1.3 在 `common-facilities` 模块中声明 `SequenceProvider` 解耦接口
- [x] 1.4 在 `web` 模块中创建 `SequenceRegistryService` 并实现 `SequenceProvider` 接口
- [x] 1.5 重构 `IdentifierStrategyConfigService` 以在自增序列生成时调用 `SequenceProvider` 回调
- [x] 1.6 在 `IdentifierGenerationStrategyRegistry` 的内置策略池中注册 `SEQUENCE` 和 `TEMPLATE` 策略
- [x] 1.7 实现 `IdentifierStrategyLoader` CommandLineRunner，在系统启动时将已发布且生效的生成策略配置加载至内存缓存中

## 2. 核心注册服务集成 (Core Registration Integration)

- [x] 2.1 将 `IdentifierStrategyConfigService` 注入 `ItemAdministrationService`
- [x] 2.2 修改 `ItemAdministrationService.ensureAdministeredItem` 的创建分支：在请求中的 identifier 为空/空字符串时，利用 `IdentifierStrategyConfigService` 依据当前命名空间和类型策略自动生成标识符
- [x] 2.3 在 `ItemAdministrationServiceTest` 中增加自增序列生成和 URN 生成的集成测试验证

## 3. 前端界面与动态校验 (Frontend Components & Validation)

- [x] 3.1 修改 `ItemFormDialog.vue` 的 `form` 结构，为注册信息新增 `autoGenerate` 生成模式标记（默认值为 `true`）
- [x] 3.2 在前端 `注册标识` 标签页渲染单选开关“生成方式”（仅在创建模式下显示），在自动生成和手动输入之间切换
- [x] 3.3 调整标识符输入框的禁用逻辑：当 `autoGenerate = true` 或编辑已有元数据时，输入框禁用并显示对应的 placeholder 提示
- [x] 3.4 修改 `registration.identifier` 的校验规则：若为自动生成模式，跳过非空校验；否则强制要求手动输入
- [x] 3.5 调整 `handleSubmit` 提交的 payload 逻辑：若选用自动生成模式，向后端发送的 `identifier` 清空为 `null`
