# 任务清单:扩展剥离(ext-extraction)

## 1. 直接删除(无前端功能)

- [x] 1.1 整模块删除 draft-generation、schema-extraction(根 pom 移除模块声明,确认无残留依赖)
- [x] 1.2 web 内删除审批流(approval_* 5 表实体、ApprovalService/MultiLevelApprovalService/ApprovalController 等)
- [x] 1.3 web 内删除通知(notification_* 实体、EmailNotificationService/NotificationRetryService)
- [x] 1.4 web 内删除数据质量(data_quality_task + DataQuality*)、导出任务(export_task)
- [x] 1.5 web 内删除 FHIR(web/entity/fhir 4 表、FhirComplianceService 等引用)
- [x] 1.6 删除 definition_audit_log(实体+服务+端点)
- [x] 1.7 common-facilities 删除 Value 类(含 datatypeUri)、DataTimePeriod 别名(4 处 @Embedded 先收敛到 Datetime_Period)
- [x] 1.8 删除 Designation.contexts/Definition.contexts 裸 @ManyToMany + Definition.namespaces;同步修 MetadataSearchService.java:222-227 原生 SQL 改 JOIN *_context_relation(顺带修搜索过滤恒空既存 bug);designation_namespace 在用勿误删
- [x] 1.9 Slot 恢复标准属性名 type(迁移脚本列 value_datatype→type,删废弃双字段)

## 2. 新建 metadata-registry-ext 模块

- [x] 2.1 建模块骨架(pom、包 io.github.matrixlane.metadataregistry.ext、根 pom 注册、web 依赖之)
- [x] 2.2 标识符策略框架 35 类迁入(策略族、segment/ 19 类、IdentifierStrategyConfig/SequenceRegistry/IdentifierVersionHistory 实体、VersionStatus/VersioningPolicy/Versions/SemVerValidator、IdentifierCreationMode/IdentifierInputMode)
- [x] 2.3 Namespace 扩展字段剥为 NamespaceIdentifierSettings 卫星实体(命名避开 web 既有同名 policy record)(versioningPolicy/identifierInputMode/versionInputMode/manualIdentifierPattern);NamespaceService/IdentifierInputPolicyService/InitWizardService/联邦包改读写卫星实体,DTO/前端不变
- [x] 2.4 ScopedIdentifier 扩展字段剥为 ScopedIdentifierLifecycle 卫星实体(creationMode/versionStatus/4 时间戳);ScopedIdentifierService 适配
- [x] 2.5 ClassificationScheme/ClassificationNode 整体迁入 ext(javadoc 显式声明妥协:第 10 章标准模型重建另立 change)
- [x] 2.6 item-mapping 对 Versions/SemVerValidator 依赖改指 ext 模块(核实为 no-op:item-mapping 对二者无真实引用,无需改动)
- [x] 2.7 11179-3 第 11 章审计(Milvus 核实标准原文):报告 docs/iso11179-clause11-itemmapping-audit-2026-09-01.md 已产出,建议归 EXT,归属决策待用户
- [x] 2.8 卫星实体迁移脚本(V20260901009 SI 卫星、V20260901010 NS 卫星;新表建表+回填+旧列删除;三方言)

## 3. web 内扩展属性卫星实体化

- [x] 3.1 Contact.account → ContactAccountBinding 卫星实体,服务层适配,DTO/前端不变
- [x] 3.2 LanguageIdentification.enabled → LanguageGovernance 卫星实体(编辑器过滤链路适配)
- [x] 3.3 NamingConvention.rulesJson → NamingConventionRules 卫星实体(校验引擎适配)
- [ ] 3.4 association/ 11 类从 data-specification 迁 web/service/association(纯服务无实体)
- [ ] 3.5 上述三卫星实体迁移脚本(建表+回填+删旧列;三方言)

## 4. 验证与提交

- [ ] 4.1 `./mvnw.cmd` 全模块编译 + 全量测试绿
- [ ] 4.2 前端有页面功能逐一过一遍:分类、标识符策略、命名空间表单、关联 tab、版本历史、联邦、映射、互操作
- [ ] 4.3 删除功能确认无残留路由/菜单/菜单 i18n
- [ ] 4.4 GitNexus detect_changes 无 HIGH/CRITICAL(partial/truncated 需重跑)
- [ ] 4.5 openspec validate 2026-09-01-ext-extraction --strict 通过
- [ ] 4.6 提交(commit message 遵循约定;审计报告第 2 节标记已处置项)
