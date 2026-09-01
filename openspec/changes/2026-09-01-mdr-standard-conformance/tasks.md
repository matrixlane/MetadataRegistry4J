# 任务清单:MDR 标准模型一致性恢复

## 1. RegistrationStatus 枚举修正

- [x] 1.1 修改 `RegistrationStatus.java`:SUBMITTED→CANDIDATE、PREFERRED→PREFERRED_STANDARD,javadoc 改为引用 11179-3:2023 Table 30 + 11179-6:2023 Table 1,删除 WI-019 错误表述
- [x] 1.2 增强 `CaseInsensitiveEnumJavaType.relaxedValueOf`:空白/下划线归一("Preferred Standard"→PREFERRED_STANDARD),补单测
- [x] 1.3 修改 `RegistrationLifecycleService` 状态机转移表与权限矩阵
- [x] 1.4 新增修正迁移脚本(sqlserver V2026xxxx + sqlite/dm 对应),重写 `RegistrationStatusMigrationTest`
- [x] 1.5 前端 `types/common.ts:45` union type + 17 处视图/i18n 标签同步
- [x] 1.6 更新 openspec `registration-status` spec 值集

## 2. 抽象类修正

- [x] 2.1 ValueDomain/ConceptualDomain/EnumeratedValueDomain/EnumeratedConceptualDomain 改 abstract
- [x] 2.2 RegisteredItem 改 abstract
- [x] 2.3 Concept 取消 abstract
- [x] 2.4 修测试实例化点(association/*Test、DataDictionaryExportServiceTest、DataElementServiceTest、RegisteredItemTest、SubmissionRecordTest 等)

## 3. 类型修正

- [x] 3.1 RegistrySpecification.name → Sign @Embedded nullable=false
- [x] 3.2 Namespace.schemeReference → Sign @Embedded
- [x] 3.3 DataTypeScheme.reference → schemeReference @ManyToOne Reference_Document
- [x] 3.4 Datetime_Period.start/end → Datetime(OffsetDateTime 含时区)
- [x] 3.5 Dimensionality.coordinateIndicator → Boolean 三态
- [x] 3.6 email_address → String(Individual/Organization/Role),删 EmailAddress 类
- [x] 3.7 NaturalRange 语义修正(min 必备非负、max 可无且>min)+校验
- [x] 3.8 defaultTimeZone 加 [-+]HHMM 格式校验 + javadoc 妥协声明

## 4. 多重性修正(每项先核库)

- [x] 4.1 核库:对每项收紧跑 NULL 计数,有存量先补数据
- [x] 4.2 ValueDomain.datatype / ScopedIdentifier.version / RegistrationState 必填项 / AdministeredItem.creationDateTime / Stewardship/Submission organization+contact / RegistrationAuthority.identifier / RegistrySpecification.name+characterRepertoire 补非空
- [x] 4.3 Slot.value → @ElementCollection List(新表 slot_value)
- [x] 4.4 Role.email_address/phone_number → @ElementCollection List
- [x] 4.5 Item↔IdentifiedItem → OneToOne(去 List 变通)
- [x] 4.6 UnitOfMeasure→MeasureClass 1..* 服务层校验
- [x] 4.7 级联删除:7.3.2.4(Namespace→ScopedIdentifier)、9.4.3.5(AdministeredItem→AttachedItem)

## 5. 补标准缺失属性

- [x] 5.1 PermissibleValue.permitted_value(必填,核库回填)
- [x] 5.2 ConceptualDomain.valid_period(@Embedded)+ dimensionality(0..1)
- [x] 5.3 EnumeratedConceptualDomain.expanded_on_datetime / locked_datetime
- [x] 5.4 DataType.annotation
- [x] 5.5 ScopedIdentifier.fullExpansion / shorthandExpansion 派生 getter(不落库)+单测
- [x] 5.6 value_domain_meaning 提升到 ValueDomain 基类,子类字段迁移后删,保留 §7.4.5.2.1 校验

## 6. 条款号更正

- [x] 6.1 按审计报告 3.5 节批量更正 16 处错位引用
- [x] 6.2 Entity 删除虚假 "6.4.5" 引用
- [x] 6.3 Designation/Definition/DesignationContext/DefinitionContext/Pairing/Acceptability 补 @StandardReference

## 7. 验证与提交

- [ ] 7.1 `./mvnw.cmd -pl metadata-registry-common-facilities,metadata-registry-data-specification,metadata-registry-web test` 全绿
- [ ] 7.2 打包重启连 SQL Server 库,数据元/值域/概念域/注册项页面人工过一遍
- [ ] 7.3 GitNexus detect_changes 无 HIGH/CRITICAL
- [ ] 7.4 openspec validate 2026-09-01-mdr-standard-conformance --strict 通过
- [ ] 7.5 提交(commit message 遵循约定)
