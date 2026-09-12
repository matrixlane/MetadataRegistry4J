# 变更提案:扩展剥离(ext-extraction)

## Why

阶段 1(mdr-standard-conformance)已把 MDR 标准领域模型恢复到与 ISO/IEC 11179-3:2023 / 11179-31:2023 一致。审计报告(`docs/iso11179-conformance-audit-2026-09-01.md` 第 2 节)确认仍有 **56 个多余扩展类/属性**混在标准模块中:标识符策略框架 35 类、自造字段的 Classification 实现、标准实体上的扩展属性(Contact.account、LanguageIdentification.enabled、NamingConvention.rulesJson、Namespace/ScopedIdentifier 扩展字段)、以及一批前端无功能的死代码(审批流、通知、数据质量、导出任务、FHIR、draft-generation、schema-extraction 整模块)。

**架构约束(用户决策)**:

1. MDR 标准领域模型与标准始终保持一致,扩展必须迁出标准模块;
2. 移出功能由另外模块承载、不同 schema 隔离;**前端无功能的模型先行移除**;
3. 标准模型 schema 用 MDR、扩展用 EXT(schema 物理拆分属后续 `mdr-ext-schema-split` 变更,本变更先做代码级剥离)。

## What Changes

### 1. 直接删除(前端无功能/仅内部设施)

- **整模块删除**:`metadata-registry-draft-generation`、`metadata-registry-schema-extraction`(根 pom 移除模块声明)。
- **web 模块内删除**:审批流(approval_* 5 表及相关 Service/Controller)、通知(notification_* + EmailNotificationService/NotificationRetryService)、数据质量(data_quality_task + DataQuality*)、导出任务(export_task)、FHIR 实体(web/entity/fhir 4 表及 FhirComplianceService 等引用)、definition_audit_log(实体+服务+端点)。
- **common-facilities 内删除**:`Value` 类(含 datatypeUri,未接线)、`DataTimePeriod`(4 处 @Embedded 收敛到 `Datetime_Period` 后删别名)、`Designation.contexts`/`Definition.contexts` 裸 @ManyToMany(表 designation_context/definition_context)、`Definition.namespaces`(表 definition_namespace;designation_namespace 在用,勿误删)。
- **同步修复**:`MetadataSearchService.java:222-227` 原生 SQL 改 JOIN `*_context_relation`,顺带修复搜索过滤恒空的既存 bug。
- **Slot 对齐标准**:恢复标准属性名 `type`(列 value_datatype→type 迁移),删废弃双字段;Slot 生产未接线,改动限于 common-facilities。

### 2. 迁新模块 `metadata-registry-ext`(领域型扩展)

新建 Maven 模块,包名 `io.github.matrixlane.metadataregistry.ext`,web 模块依赖之。

- **标识符策略框架 35 类**:identification 包下策略/模板/序列/版本治理全部(IdentifierGenerationStrategy 族、segment/ 19 类、IdentifierStrategyConfig/SequenceRegistry/IdentifierVersionHistory 实体、VersionStatus/VersioningPolicy/Versions/SemVerValidator、IdentifierCreationMode/IdentifierInputMode)。
- **Namespace/ScopedIdentifier 扩展字段剥离**:标准实体只留标准属性;扩展字段迁入 EXT 卫星实体 `NamespaceIdentifierPolicy`(namespace_id 一对一:versioningPolicy/identifierInputMode/versionInputMode/manualIdentifierPattern)与 `ScopedIdentifierLifecycle`(scoped_identifier_id 一对一:creationMode/versionStatus/4 时间戳)。web 服务(NamespaceService、ScopedIdentifierService、IdentifierInputPolicyService、InitWizardService、联邦包)改为读写卫星实体;DTO/前端字段保持不变(组装层适配)。
- **ClassificationScheme/ClassificationNode**:当前实现非标准(自造字段),先整体迁入 ext 模块(保留现有前后端功能),javadoc 显式声明妥协:"11179-3 第 10 章标准模型重建"另立后续 change。
- **11179-3 第 11 章审计**:Item_Mapping/Subject_Mapping/Object_Mapping/Item_Mapping_Degree 对照 item_mapping* 三表逐字段审计,结论决定 item-mapping 模块归 MDR 还是 EXT(审计产出让用户决策,本变更只出报告不动模块)。
- item-mapping 对 common-facilities 的 `Versions`/`SemVerValidator` 依赖随版本治理迁移改指 ext 模块。

### 3. 留 web 模块(应用型扩展)

- Account/SystemRole/auth 包(活功能)、version_history、federation 包(含 item_link)原地保留,待阶段 3 归 EXT schema。
- **标准实体扩展属性卫星实体化**:Contact.account→`ContactAccountBinding`、LanguageIdentification.enabled→`LanguageGovernance`、NamingConvention.rulesJson→`NamingConventionRules`;web 服务层适配,DTO/前端不变。
- association/ 子包 11 类(纯服务、无实体):从 data-specification 迁 web/service/association,无 schema 影响。

## Capabilities

### Modified Capabilities
- `identifier-auto-generation`:实现从 common-facilities 迁 metadata-registry-ext,行为不变。
- `identifier-template-designer`:segment 模板引擎随迁,行为不变。
- `namespace-identifier-policy`:扩展字段改由卫星实体 NamespaceIdentifierPolicy 承载,DTO/前端不变。
- `metadata-object-association`:association 11 类迁 web/service/association,行为不变。

### Removed Capabilities
- 无 spec 级能力删除(被删功能均无前端、无对应 capability spec);审批流/通知/数据质量/导出/FHIR/definition_audit_log/draft-generation/schema-extraction 为无前端死代码移除。

## Impact

- **受影响模块**:全部 Maven 模块。根 pom 增删模块;common-facilities 减重(35+ 类迁出、若干删除);data-specification 迁出 Classification 与 association;web 服务层适配卫星实体;新建 ext 模块。
- **数据库**:本变更不物理搬表(schema 拆分在阶段 3)。卫星实体新表由 ddl-auto=update 自动建;被删实体的表暂留库中,阶段 3 统一清理;Slot 列改名 value_datatype→type 需迁移脚本;卫星实体回填(Contact.account 等)需迁移脚本。
- **前端**:零改动目标(DTO 不变);删除功能确认无残留路由/菜单/i18n。
- **风险**:标识符策略框架 35 类迁移影响面大(NamespaceService、InitWizardService、联邦包均依赖),迁移后需全量测试+前端走查。

## 非目标(Non-goals)

- Schema 物理拆分(MDR/EXT)、ALTER SCHEMA TRANSFER —— 属后续 `mdr-ext-schema-split` 变更。
- 11179-3 第 10 章 Classification 标准模型重建 —— 另立变更(本变更仅迁出现有实现并声明妥协)。
- 11179-31 §7.7/§7.8/§7.9 类级缺失补齐 —— 待用户决策后另立变更。
- item-mapping 模块的搬迁 —— 待第 11 章审计结论由用户决策。
