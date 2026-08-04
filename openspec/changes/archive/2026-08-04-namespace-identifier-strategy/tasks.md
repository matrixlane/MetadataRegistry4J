# Tasks

## 1. 领域模型与策略解析

- [x] 1.1 新增 `IdentifierInputMode` 枚举（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY）与 `VersioningPolicy` 枚举（SEMVER / SEQUENTIAL / DATE）
- [x] 1.2 `Namespace` 实体新增 `identifierInputMode` / `versionInputMode` / `manualIdentifierPattern` 三列（JPA 注解、方言中立、默认值兼容）
- [x] 1.3 新增 `NamespaceIdentifierPolicy`（强类型策略视图）与 `NamespaceIdentifierPolicyResolver`（脏数据回退默认、空白正则归一 null）

## 2. 统一策略执行入口（后端）

- [x] 2.1 新增 `IdentifierInputPolicyService`：`resolveIdentifier`（模式裁决 + 正则校验）、`resolveVersion`（初始版本生成 + 格式校验）、`validateManualVersion`（版本变更复用校验）
- [x] 2.2 `ItemAdministrationService` 注册入口改走策略服务，携带注册项类型供 `{type}` 占位符
- [x] 2.3 `ScopedIdentifierService` 创建/版本变更改走策略服务，移除硬编码 `1.0.0` 与 SemVer 单一校验
- [x] 2.4 删除 `IdentifierGenerationService` 及其测试

## 3. 生成策略占位符扩展

- [x] 3.1 `TemplateGenerationStrategy` 新增 `{ns}` / `{type}` 占位符，白名单放行 `.` 分隔符
- [x] 3.2 `IdentifierStrategyConfigService`：prefix 令牌替换（`resolvePrefixTokens`）、项类型大写缩写（`itemTypeAbbreviation`）、模板含 `{seq}` 且上下文无序列号时悲观锁自分配
- [x] 3.3 `GenerationContext` 增加 `itemType` 载波

## 4. 命名空间设置页策略自管理（后端）

- [x] 4.1 `NamespaceRequest` / `NamespaceResponse` / `NamespaceDetailResponse` 扩展策略字段（输入模式、版本化策略、正则、策略类型与参数）
- [x] 4.2 `NamespaceService.create/update`：策略字段省略不动既有设置；携带 `identifierStrategy` 时版本化 upsert（禁用旧 PUBLISHED、新版本 PUBLISHED、UUID 仅禁用不新增）并 `refreshRegistry()`
- [x] 4.3 响应映射回读当前生效策略配置（容忍脏 JSON 只暴露类型）
- [x] 4.4 `IdentifierStrategyConfigRepository` 新增 `findByNamespaceIdOrderByVersionDesc`

## 5. 初始化向导（后端）

- [x] 5.1 `InitWizardSetupRequest` 新增 `identifierInputMode` / `versionInputMode` / `strategySeqStart` / `strategySeqPad`
- [x] 5.2 `InitWizardService`：输入模式归一化（非法值回退 AUTO_WITH_OVERRIDE）并写入命名空间；MANUAL_ONLY 跳过策略配置创建；前缀缺省为空；SEQUENCE/TEMPLATE 配置携带 start/pad 参数

## 6. 前端

- [x] 6.1 `NamespaceList.vue`：新增"标识生成策略"与"版本生成策略"页签（输入模式单选、正则、策略类型与参数联动、MANUAL_ONLY 禁用策略控件并提示）
- [x] 6.2 `InitWizard.vue`：命名空间步骤扩展输入模式控件与序列参数，MANUAL_ONLY 禁用策略类型
- [x] 6.3 `ItemFormDialog.vue`：按命名空间输入模式联动生成方式单选、禁用态、占位提示与预校验；编辑态标识符恒禁用；自动生效时提交空标识/版本
- [x] 6.4 `common.ts` 类型扩展与 `zh-CN.json` 文案

## 7. 验证

- [x] 7.1 后端编译通过（`mvn compile`，common-facilities 与 web 模块）
- [x] 7.2 前端构建通过（`npm run build`）
- [x] 7.3 后端定向测试：策略裁决（`IdentifierInputPolicyServiceTest`：AUTO_ONLY 拒绝手动、MANUAL_ONLY 拒绝空、初始版本与版本格式校验、非法正则跳过）、模板 `{ns}`/`{type}` 渲染与前缀令牌（`TemplateGenerationStrategyTest`、`IdentifierStrategyConfigServiceTest`）、既有套件回归（`ScopedIdentifierServiceTest` 等按新协作者更新）
- [x] 7.4 前端测试：`ItemFormDialog.spec.ts` 按输入模式联动（AUTO_WITH_OVERRIDE 显示单选、AUTO_ONLY/MANUAL_ONLY 禁用/启用输入框、占位提示）
