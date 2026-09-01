# 变更提案:MDR 标准模型一致性恢复(mdr-standard-conformance)

## Why

2026-09-01 完成的全量审计(`docs/iso11179-conformance-audit-2026-09-01.md`)确认:`metadata-registry-common-facilities`(对应 ISO/IEC 11179-3:2023)与 `metadata-registry-data-specification`(对应 ISO/IEC 11179-31:2023)两个标准模块存在 **21 处不一致**、若干**标准缺失属性**。其中最关键的问题:

1. **RegistrationStatus 枚举与标准相悖**——代码的 `SUBMITTED` 在标准中不存在,标准的 `Candidate` 反被早期 WI-019 当作"非标准"移除;`PREFERRED` 对应的标准名是 "Preferred Standard"。两份标准 PDF(11179-3:2023 Table 30、11179-6:2023 Table 1)已逐字核验。
2. **标准要求的抽象类被实现为可实例化具体类**(ValueDomain、ConceptualDomain、EnumeratedValueDomain、EnumeratedConceptualDomain、RegisteredItem),而 Concept 反被错误标记为 abstract。
3. **PermissibleValue 缺必填属性 `permitted_value`**(11179-31 Table 6,多重性 1)——数据模型级实质缺失。
4. **类型/多重性/条款号引用系统性偏差**(Sign/Reference_Document 用裸 String、多重性 1/1..* 未强制、@StandardReference 沿用 2013 旧版编号)。

**架构约束(用户决策)**:MDR 标准领域模型与 ISO/IEC 11179 标准始终保持一致,仅允许显式声明的妥协。本变更在不触碰扩展迁移的前提下,把标准模块与标准的偏差全部修正。

## What Changes

### 1. RegistrationStatus 枚举修正(最高优先级)
- `SUBMITTED` → `CANDIDATE`,`PREFERRED` → `PREFERRED_STANDARD`,javadoc 改为引用 11179-3:2023 Table 30 + 11179-6:2023 Table 1。
- 状态机(`RegistrationLifecycleService`)、前端 union type/标签、迁移脚本(V20260517001 反向迁移纠正)同步。
- `CaseInsensitiveEnumJavaType` 兜底匹配增强:空白/下划线归一,兼容标准书写格式("Preferred Standard")入库的历史数据。

### 2. 抽象类修正(零表结构影响)
- 改 abstract:ValueDomain、ConceptualDomain、EnumeratedValueDomain、EnumeratedConceptualDomain、RegisteredItem。
- 取消 abstract:Concept(标准中 Item 才是抽象类)。
- 仅测试实例化点需改;生产代码走类型枚举 switch 不受影响。

### 3. 类型修正(8 项)
RegistrySpecification.name→Sign、Namespace.schemeReference→Sign、DataTypeScheme.reference→schemeReference(Reference_Document)、Datetime_Period→含时区 Datetime、Dimensionality.coordinateIndicator→三态 Boolean、email_address→String、NaturalRange 语义、defaultTimeZone 格式校验+显式妥协声明。

### 4. 多重性修正(执行前核库)
ValueDomain.datatype 必填、ScopedIdentifier.version 必填、Slot.value 1..*、Role email/phone 0..*、Item↔IdentifiedItem 0..1、UoM→MeasureClass 1..*、标准必填项统一非空、级联删除约束。

### 5. 补标准缺失属性(7 项)
PermissibleValue.permitted_value(必填)、ConceptualDomain.valid_period/dimensionality、EnumeratedConceptualDomain 两个 datetime、DataType.annotation、ScopedIdentifier 派生属性(fullExpansion/shorthandExpansion)、value_domain_meaning 提升到 ValueDomain 基类。

### 6. @StandardReference 条款号批量更正
16 处错位更正 + Designation/Definition 等 6 类补注 + Entity 虚假引用删除;统一以 2023 版条款号为准。

## Capabilities

### Modified Capabilities
- `registration-status`: 注册状态值集与 11179-3/11179-6 对齐(补 CANDIDATE、去 SUBMITTED、PREFERRED→PREFERRED_STANDARD)。
- `data-element-precision`: DataElement.precision 所在模块的标准符合性(本变更不改该 spec,但 data-specification 模块整体对齐)。
- `identification`: ScopedIdentifier 多重性与派生属性对齐。
- 其他:无新 capability;本变更是对既有标准模块的符合性修正,不引入新功能。

## Impact

- **受影响模块**:`metadata-registry-common-facilities`(领域模型)、`metadata-registry-data-specification`(领域模型)、`metadata-registry-web`(状态机、迁移脚本、CaseInsensitiveEnumJavaType、测试)、`metadata-registry-ui`(RegistrationStatus union type/标签)。
- **数据库**:JOINED 继承改 abstract 零表结构影响;新增列(permitted_value、valid_period、dimensionality 等)由 ddl-auto=update 自动加列(SQL Server 团队版)或 Flyway 基线(SQLite 个人版);现库 registration_state 6543 行全部 'Standard',枚举修正零数据负担。
- **前端**:RegistrationStatus 值集/标签同步;其余模型变更对前端表单透明。
- **向后兼容**:大小写不敏感枚举读取(已上线)保证新旧书写格式数据均可读;修正迁移脚本为幂等 no-op 兜底。
- **风险**:多重性收紧(必填)前必须核库确认无 NULL 存量,否则先补数据再收约束。

## 非目标(Non-goals)

- 扩展类迁移(标识符策略、分类、版本治理等)——属后续 `ext-extraction` 变更。
- Schema 拆分(MDR/EXT)——属后续 `mdr-ext-schema-split` 变更。
- 11179-3 第 10/11 章(Classification/Item_Mapping)审计与建模——另立变更。
- 11179-31 §7.7/§7.8/§7.9 子集与复合数据元区域的**类级**缺失——本期只补属性级缺失,类级缺失待用户决策后另立变更。
