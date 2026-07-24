## 1. 重构数据初始化类

- [x] 1.1 在 `LanguageInitializer` 中重构 G20 常用语言的初始化逻辑，提取出公共的 `initializeLanguages` 方法，使其能被外部调用。
- [x] 1.2 在 `SystemDefaultNamespaceInitializer` 中重构系统默认命名空间的初始化逻辑，提取出公共的 `initializeSystemDefaultNamespace` 方法，并修复其对 `LanguageIdentificationRepository` 的直接保存逻辑以防主键/冲突问题。

## 2. 修改系统初始化向导

- [x] 2.1 在 `InitWizardService` 中注入 `LanguageInitializer` 和 `SystemDefaultNamespaceInitializer`。
- [x] 2.2 在 `InitWizardService.setup` 方法逻辑中，在完成 `systemSettingService` 的配置和启用前，顺序调用重构后的 `LanguageInitializer.initializeLanguages()` 和 `SystemDefaultNamespaceInitializer.initializeSystemDefaultNamespace()` 方法。

## 3. 测试与验证

- [x] 3.1 编写或更新 `InitWizardServiceIntegrationTest` 相关的集成测试，确保在执行 `setup` 后，数据库中存在包含 `zh-CN`、`en-US` 在内的语言标识列表，以及系统默认命名空间。
- [x] 3.2 运行 `mvn clean test` 或相关测试套件确保所有相关测试全部通过。
