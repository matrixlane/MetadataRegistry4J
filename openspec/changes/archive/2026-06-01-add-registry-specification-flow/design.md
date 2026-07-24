## Context

目前，系统初始化（由 `InitWizardService` 处理）只初始化了注册机构（`RegistrationAuthority`）和默认命名空间（`Namespace`），没有创建注册规范（`RegistrySpecification`），并且没有相关的权限或强制门禁以拦截未配置规范时的元数据注册与治理操作。

为了满足 ISO/IEC 11179-3 规范并确保数据一致性，需要在初始化阶段引入注册规范步骤，同时当已有元数据产生时，锁定注册规范中的关键配置项。

## Goals / Non-Goals

**Goals:**
1. 实现 `RegistrySpecification`（注册规范）数据库实体、数据表结构、Repository 及增删改查 REST APIs。
2. 修改系统初始化向导，若超级管理员选择兼任注册管理员（`selfAsRegistrar = true`），则允许其选择立即创建或稍后创建注册规范。
3. 如果超级管理员选择立即创建，将新增一个步骤，允许配置注册规范的关键字段（名称、前缀、是否自动生成标识符、校验正则、版本策略、是否强制分类、默认语言）。
4. 识别并锁定对已有元数据有一致性影响的注册规范字段（`identifierPrefix`, `autoGenerateIdentifier`, `identifierPattern`, `versioningPolicy`, `mandatoryClassification`, `defaultLanguage`），并保证已存在元数据时这些字段在 API 校验中报错，在前端只读。
5. 拦截未创建注册规范时所有非初始化、非授权的写操作（如创建/更新命名空间、数据元素等），并更新就绪检查接口（`ReadinessController` / `DataGovernanceReadinessService`）以校验注册规范的存在性。

**Non-Goals:**
1. 本地多注册表/多租户规范隔离（目前系统仅支持单注册表 `SINGLE` 模式）。
2. 在已有元数据不合规时，自动进行清洗或强制迁移。

## Decisions

### 1. 新增 `RegistrySpecification` 领域实体
在 `metadata-registry-common-facilities` 的 `registration` 包中创建 `RegistrySpecification.java`。
- 继承 `Entity` 基础类（提供 UUID `id`）。
- 字段属性：
  - `registryName` (String, nullable = false) - 注册表名称
  - `identifierPrefix` (String) - 注册规范下的标识符前缀
  - `autoGenerateIdentifier` (Boolean) - 是否自动生成标识符
  - `identifierPattern` (String) - 手动输入标识符的校验正则表达式
  - `versioningPolicy` (String) - 版本化规则策略（例如 "SEMVER", "INTEGRAL"）
  - `mandatoryClassification` (Boolean) - 是否强制分类
  - `defaultLanguage` (String) - 默认语言（例如 "zh-CN"）
  - `description` (String) - 描述
- 添加 Repository 类 `RegistrySpecificationRepository`。

### 2. 数据库变更迁移
新建 Flyway 迁移脚本 `V20260601002__Create_registry_specification_table.sql`：
- 创建 `registry_specification` 表。
- 将 `id` 设为 `UNIQUEIDENTIFIER NOT NULL PRIMARY KEY`。

### 3. 初始化向导流程更新
#### 后端：
- 在 `InitWizardSetupRequest` 结构体中添加可选的 `RegistrySpecification` 字段：
  - `createRegistrySpecification` (Boolean)
  - `registrySpecification` (DTO/Record 封装配置参数)
- 在 `InitWizardService#setup` 中：
  - 若 `request.createRegistrySpecification` 为 `true`，使用 DTO 数据创建并保存 `RegistrySpecification`。
  - 若演示数据种子化（`DataInitializer`）触发且不存在 `RegistrySpecification`，由 `DataInitializer` 自动创建并保存一个默认的 `RegistrySpecification`。

#### 前端：
- 修改 `InitWizard.vue`：
  - 如果 Step 3 中的 `selfAsRegistrar` 被选中，则向导增加第四步（Step 4）："配置注册规范"。
  - Step 4 提供一个开关 "立即配置注册规范"。若开启，则展开输入表单，允许输入上述关键配置字段；若关闭（选择稍后配置），则在点击“完成初始化”时只提交系统初始化请求而不携带注册规范。
  - 对 `handleSubmit` 进行逻辑调整，当选择立即创建时，向 `/api/v1/init-wizard/setup` 传递注册规范数据。

### 4. 数据治理防篡改（锁定关键字段）
#### 后端校验：
- 在 `RegistrySpecificationService#update` 时，判断数据库中是否已存在非系统内置的元数据（可以通过 `administeredItemRepository.count() > 0` 判定）。
- 如果存在关联元数据，且修改了 `identifierPrefix`, `autoGenerateIdentifier`, `identifierPattern`, `versioningPolicy`, `mandatoryClassification`, `defaultLanguage` 中的任何一个，抛出 `BusinessException`。

#### 前端只读控制：
- 前端注册规范编辑页面（`RegistrySpecificationEdit.vue`）加载时，先请求后端接口（例如 `/api/v1/registry-specifications/has-metadata` 或通过详情 API 的元数据状态属性）。
- 如果已有元数据，将上述关键字段在表单中设为 `disabled`，仅允许修改注册表名称和描述等非关键字段。

### 5. 开展治理工作前置校验门禁
#### API 拦截门禁：
- 新增 `RegistrySpecificationInterceptor` 拦截器：
  - 拦截所有 `POST`、`PUT`、`DELETE`、`PATCH` 写请求。
  - 排除白名单（如 `/api/v1/init-wizard/**`, `/api/v1/auth/**`, `/api/v1/registry-specifications/**`）。
  - 若数据库中不存在任何 `RegistrySpecification`，直接返回 HTTP 400（或 403），错误信息为 `"必须先创建注册规范，才允许开展数据治理和项目注册"`。
- 在 `WebConfig.java` 中注册该拦截器，并在 `InitializationWizardInterceptor` 之后执行。

#### 就绪状态更新：
- 修改 `DataGovernanceReadinessService` 和 `ReadinessChecklist`，新增 `registrySpecificationExists` 就绪指标。
- 在 `checkReadiness` 和 `checkSystemReadiness` 中校验是否存在注册规范。若不存在，在 `missingPrerequisites` 中加入 `"注册规范未创建"`，并为前端提供引导建议。

## Risks / Trade-offs

- **[Risk]**：数据库已存在历史种子演示数据（`DataInitializer`），由于无 `RegistrySpecification` 会导致所有写 API 报错。
  - *Mitigation*：在 `DataInitializer` 中自动植入一条默认注册规范以匹配种子数据。
- **[Risk]**：拦截器拦截过宽，导致在没有注册规范时，用户也无法创建注册规范本身。
  - *Mitigation*：在拦截器中明确将 `/api/v1/registry-specifications/**` 排除在外。
