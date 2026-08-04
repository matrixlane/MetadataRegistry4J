# Tasks

## 1. 后端 setup 语义调整

- [x] 1.1 `InitWizardService.setup`：`PERSONAL` 分支去除对 `selfAsRegistrar`/`createNamespace`/`createRegistrySpecification` 的强制 `true`，改为"缺省（null）为 `true`、显式 `false` 生效"
- [x] 1.2 `PERSONAL` 下显式 `createRegistrySpecification=false` 时返回业务校验错误（注册规范必选），`TEAM` 保持既有校验
- [x] 1.3 保持名称类字段缺省默认值（注册机构名、规范名联动）不变

## 2. 命名空间准入门槛（后端）

- [x] 2.1 `NamespaceRepository` 增加"存在关联注册机构的命名空间"查询（排除外部命名空间与 system-default）
- [x] 2.2 在注册受管项/生成 `Scoped_Identifier` 入口（`ItemAdministrationService` 等）接入校验：无注册机构关联命名空间时返回 400 业务错误，错误信息引导先创建命名空间
- [x] 2.3 确认导入外部数据标准（外部命名空间）路径不受该校验影响

## 3. 前端初始化向导

- [x] 3.1 `InitWizard.vue`：个人版步骤指示器与导航纳入 注册机构 → 命名空间 → 注册规范 步骤，治理字段预填默认值
- [x] 3.2 命名空间步骤（两个版本）增加显式"跳过"选项，跳过时提交 `createNamespace=false`
- [x] 3.3 个人版 `prevStep`/`nextStep`/`displayActive` 去除治理步骤裁剪逻辑

## 4. 前端准入引导

- [x] 4.1 数据标准创建入口在无注册机构关联命名空间时禁用并展示引导（跳转命名空间创建页）

## 5. 测试

- [x] 5.1 更新 `InitWizardServiceIntegrationTest` / `InitWizardControllerTest`：个人版显式跳过命名空间、显式关闭注册规范报错、缺省兼容行为
- [x] 5.2 新增门槛测试：无命名空间创建自有数据标准被阻止、补建后放行、外部标准导入不受限（SQLite 与 SQL Server 各跑一遍）
- [x] 5.3 前端向导交互测试更新

## 6. 命名空间策略配置（后端）

- [x] 6.1 `Namespace` 实体新增 `versioningPolicy` 字段（默认 `SEMVER`，仅 JPA 注解，方言中立）
- [x] 6.2 `InitWizardSetupRequest` 以命名空间作用域字段替换死字段：`namespaceName`、`identifierStrategy`（UUID/SEQUENCE/TEMPLATE）、`identifierPrefix`、`identifierTemplate`、`versionPolicy`
- [x] 6.3 `InitWizardService.setup`：创建命名空间时使用请求名称与 `versionPolicy`，并创建命名空间级 `IdentifierStrategyConfig`（PUBLISHED、version=1、即生效），随后 `IdentifierStrategyLoader.refreshRegistry()`
- [x] 6.4 `RegistrySpecificationService.createDefault` 移除 identifierPrefix/autoGenerateIdentifier/identifierPattern/versioningPolicy/mandatoryClassification 死参数

## 7. 向导步骤拆分与自适应滚动（前端）

- [x] 7.1 命名空间独立成步骤：跳过开关 + 命名空间名称 + 标识生成策略（类型与参数联动）+ 版本生成策略；角色分配开关并入注册机构步骤
- [x] 7.2 注册规范步骤移除错位字段（identifierPrefix、autoGenerateIdentifier、identifierPattern、versioningPolicy、mandatoryClassification），保留名称、默认语言、描述
- [x] 7.3 向导/登录共享布局与 `AppLayout` 主内容区支持内容超过一屏时滚动

## 8. 测试（第二批）

- [x] 8.1 后端：setup 创建命名空间时生成策略配置与 `versioningPolicy` 落库的集成断言；跳过命名空间时不创建策略配置
- [x] 8.2 前端 E2E：命名空间步骤策略控件联动、payload 携带新字段、注册规范步骤不再含死字段
- [x] 8.3 全量回归（后端定向套件 + 前端 build/vitest/e2e）
