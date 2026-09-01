# ISO/IEC 11179 一致性审计报告

- 审计日期:2026-09-01
- 审计范围:`metadata-registry-common-facilities`(对应 ISO/IEC 11179-3:2023)与 `metadata-registry-data-specification`(对应 ISO/IEC 11179-31:2023)两个模块的全部领域模型
- 审计基准:标准 PDF 原文(`ISO/IEC 11179 标准原文目录\ISO_IEC 11179-3-2023.pdf`、`ISO_IEC 11179-31-2023.pdf`、`ISO_IEC 11179-6-2023.pdf`),条款号均以 2023 版正文实际文本为准
- 审计方式:三个并行审计代理逐类逐字段比对,关键结论由主会话从 PDF 原文二次核验

---

## 处置状态(2026-09-01 更新)

阶段 1(`openspec/changes/2026-09-01-mdr-standard-conformance`)已处置完毕,按提交对应:

| 报告条目 | 处置 | 提交 |
|---|---|---|
| 0.1 RegistrationStatus 枚举相悖 | ✅ 已修正(SUBMITTED→CANDIDATE、PREFERRED→PREFERRED_STANDARD、迁移纠正、大小写不敏感读取) | e7f01d8、05fd236 |
| 3.1 枚举/取值级 #1 | ✅ 同上 | e7f01d8 |
| 3.2 类型级 #3–#9 | ✅ 已修正(含 defaultTimeZone 格式校验+显式妥协声明) | bafe69a |
| 3.3 多重性级 #10–#16 | ✅ 已收紧(核库无 NULL 存量后落约束;Slot.value→List、Role 集合、Item↔IdentifiedItem OneToOne、级联删除) | 26abaf7 |
| 3.4 结构级 #17–#19 | ✅ 抽象类修正(Concept 恢复可实例化) | f703311 |
| 3.4 结构级 #20(value_domain_meaning 归基类) | ✅ 提升到 ValueDomain 基类,子类改类型化视图,存量 16 行无损迁移 | dc5a3da(5.6) |
| 3.4 结构级 #21(NaturalRange) | ✅ 已修正 | bafe69a |
| 3.5 条款号错位 | ✅ 14 处错位更正 + 6 类补注 + Entity 虚假引用删除 | 6f108f9 |
| 4.1 属性级缺失 1–6 | ✅ 全部补齐(permitted_value 必填回填、valid_period/dimensionality、两个 datetime、annotation、派生 getter) | dc5a3da |

阶段 2(`openspec/changes/2026-09-01-ext-extraction`)已处置完毕,第 2 节多余扩展项按提交对应:

| 报告条目 | 处置 | 提交 |
|---|---|---|
| 2.1 `Contact.account` | ✅ 剥为 `ContactAccountBinding` 卫星实体(EXT,纯值键;读路径统一经 ContactAccountBindingService) | b991cdf(3.1) |
| 2.1 `LanguageIdentification.enabled` | ✅ 剥为 `LanguageGovernance` 卫星实体(缺行等同启用) | 75a2330(3.2) |
| 2.1 `DataTimePeriod`、`EmailAddress`、`Value`(含 datatypeUri) | ✅ 已删除 | 489a8ef |
| 2.1 `Slot.value_datatype` | ✅ 回归标准属性名 `type` | 489a8ef |
| 2.1 `Entity` 虚假标准引用 | ✅ 已清除(阶段 1) | 6f108f9 |
| 2.2 `Designation.contexts`/`Definition.contexts`、`Definition.namespaces` | ✅ 已删除(走标准关联类) | 489a8ef |
| 2.2 `NamingConvention.rulesJson` | ✅ 剥为 `NamingConventionRules` 卫星实体(校验引擎经卫星服务/显式重载读取) | 24b0aa5(3.3) |
| 2.3 标识符生成策略框架(11)+ 分段模板引擎(19)+ 版本治理(5)+ 创建模式治理(2) | ✅ 迁 `metadata-registry-ext`(ext.identification) | 536221d(2.2/2.4) |
| 2.3 `Namespace` 4 个治理属性 | ✅ 剥为 `NamespaceIdentifierSettings` 卫星实体(resolver 单点读取) | 41dd8ab(2.3/2.8) |
| 2.3 `ScopedIdentifier` 7 个扩展属性 | ✅ 剥为 `ScopedIdentifierLifecycle` 卫星实体 | 536221d(2.2/2.4) |
| 2.4 `ClassificationScheme`/`ClassificationNode` | ✅ 迁 `metadata-registry-ext`(ext.classification,附妥协声明;第 10 章标准模型重建另立变更) | 241e0c2(2.5) |
| 2.4 `association/` 10 类 | ✅ 迁 `web.service.association`(纯服务无实体,无表) | a976cf3(3.4) |
| web 审批流/通知/数据质量/导出任务/FHIR 实体/definition_audit_log、draft-generation/schema-extraction 模块 | ✅ 已删除(无前端死代码);残留清理:`APPROVAL_TIMEOUT_NOT_CONFIGURED` 死码、`@Async("dataQualityTaskExecutor")` 失效限定符 | 489a8ef 及以前;残留 (4.3) |

**尚未处置(后续阶段/变更):**

- 2.1 `Account`/`SystemRole`(IAM 设施)→ 留待阶段 3 schema 拆分时归 EXT(或另立 IAM 模块决策)
- 0.3 Item_Mapping 第 11 章一致性审计 → 报告已产出(`docs/iso11179-clause11-itemmapping-audit-2026-09-01.md`);**用户已决策(2026-09-01):归 MDR(标准第 11 章内容),按第 11 章元模型重建另立 change**,与第 10 章 Classification 重建一并规划;阶段 3 中 item-mapping 表留 MDR schema
- 第 2 节已剥离项的 schema 落地 → 阶段 3(`mdr-ext-schema-split`)归 EXT schema
- 4.2 类/关联级缺失(11179-31 §7.7/§7.8/§7.9 区域、9 个 Constraint_Set 子类等)→ 待用户决策后另立变更

---

## 0. 重大发现(最高优先级)

### 0.1 RegistrationStatus 枚举与标准相悖【最严重】

标准原文(已从两份 PDF 逐字核验):

- **ISO/IEC 11179-3:2023 Table 30(9.6.2)**:Incomplete、**Candidate**、Recorded、Qualified、Standard、**Preferred Standard**、Superseded、Retired、Historical、Application
- **ISO/IEC 11179-6:2023 Table 1(4.3.2)**:同一集合(Lifecycle 八级 + Documentation 两级),**不存在 "Submitted"**

代码现状(`commonfacilities/registration/RegistrationStatus.java`):`SUBMITTED` 是标准外取值;缺 `CANDIDATE`;`PREFERRED` 对应的标准名为 "Preferred Standard"。代码 javadoc 中 "WI-019 变更:移除非标准状态 CANDIDATE,新增标准状态 SUBMITTED" 的说法**与原文恰好相反**;迁移测试 `RegistrationStatusMigrationTest` 及迁移脚本 V20260517001 把 `CANDIDATE→SUBMITTED`、`PREFERRED_STANDARD→PREFERRED`,是反向迁移,需纠正。

> 注:2026-08-31 会话曾基于 Milvus 向量库检索得出"含 Submitted"的结论,本次以 PDF 原文核验证伪。**标准内容一切以 PDF 原文为最终裁决。**

### 0.2 ClassificationScheme/ClassificationNode 放错模块

11179-3:2023 **第 10 章 Classification package** 定义了 Classification_Scheme(10.3.1)、Classification_Scheme_Item(10.3.2)、Classification_Scheme_Item_Relationship(10.3.3)、Classification_Scheme_Item_Relationship_Type(10.3.4)。这两个类属于 11179-3(应归 MDR_RCF),而不是 11179-31;且现有实现是自造字段(schemeName/version/status/sortOrder/createdAt/updatedAt),与第 10 章模型不一致,需要按标准重建,而非简单移动。

### 0.3 Item_Mapping 是 11179-3 第 11 章的标准模型

11179-3:2023 **第 11 章** 定义 Item_Mapping(11.3.1)、Subject_Mapping(11.4.1)、Object_Mapping(11.4.2)、Item_Mapping_Degree 枚举(11.5/Table 35)。`metadata-registry-item-mapping` 模块的 `item_mapping`、`item_mapping_subject`、`item_mapping_object` 表很可能对应标准模型(归 MDR_RCF),而非功能扩展。**该模块不在本次审计范围内,schema 归属前需补做第 11 章一致性审计。**(`mapping_scheme`、`mapping_subscription` 等大概率仍是扩展。)

---

## 1. 统计总览

| 模块/包 | 代码类数 | 标准对应类 | 完全一致 | 有多余扩展 | 有缺失 | 有不一致 | 多余扩展类 | 标准缺失类 |
|---|---|---|---|---|---|---|---|---|
| common-facilities/basicandcore | 32 | 25(全覆盖) | 14 | 4 类 | 2 类 | 8 类 | 7 | 0 |
| common-facilities/designationanddefinition | 8 | 7(全覆盖) | 4 | 3 类 | 0 | 0 | 1(技术类) | 0 |
| common-facilities/identification | 38 | 3 | 1 | 3 类 | 2 类 | 2 类 | 35 | 0 |
| common-facilities/registration | 12 | 12(全覆盖) | 5 | 0 | 0 | 6 类 | 0 | 0 |
| data-specification(主包) | 26 | 24 | 13 | 0 | 6 类 | 5 类 | 2(错放模块) | 23 项 |
| data-specification/association | 11 | 0 | — | 11 | — | — | 11 | — |
| **合计** | **127** | **71** | | | | | **56** | |

---

## 2. 多余扩展项清单(标准中不存在,需迁出 MDR_* schema 或删除)

### 2.1 common-facilities/basicandcore

| 项 | 类型 | 推断用途 | 处置建议 |
|---|---|---|---|
| `Account`(10 字段,含 activeRegistrarId 等) | 扩展类 | 登录认证、强制改密、当前 Registrar 身份 | 迁 X_IAM |
| `SystemRole` 枚举(SUPER_ADMIN) | 扩展类 | Spring Security 权限 | 迁 X_IAM |
| `Contact.account` | 扩展属性 | 联系人与登录账户绑定 | 删除,关联在 X_IAM 侧维护 |
| `LanguageIdentification.enabled` | 扩展属性 | 语言启用/停用(UI) | 删除或迁扩展表 |
| `DataTimePeriod` | 扩展类 | Datetime_Period 改名前的 @Deprecated 兼容别名 | 删除 |
| `EmailAddress` 类 | 扩展类 | 邮件专用类型(标准 6.3 表 1/2/3 中 email_address 类型为 String) | 删除,字段回归 String |
| `Value.datatypeUri` | 扩展属性 | 值的 datatype URI 提示 | 删除 |
| `Slot.value_datatype` | 改名扩展 | 标准表 11 属性名是 `type`(0..1 String);代码改名且与废弃的 `type` 字段并存,形成双字段 | 回归 `type`,删除 value_datatype |
| `Entity` | 技术基类 | JPA MappedSuperclass;javadoc 引用的"6.4.5 Entity"在 2023 版不存在 | 保留但清除虚假标准引用(纯技术类,不进 MDR 语义) |
| `IValueType`、`StandardReference` | 技术设施 | 接口/注解 | 保留(非持久化) |

### 2.2 common-facilities/designationanddefinition

| 项 | 类型 | 推断用途 | 处置建议 |
|---|---|---|---|
| `Designation.contexts` / `Definition.contexts`(裸 @ManyToMany) | 扩展关联 | 与 Designation_Context/Definition_Context 关联类冗余的直查通道 | 删除,走标准关联类 |
| `Definition.namespaces`(表 definition_namespace) | 扩展关联 | 让 Definition 挂接命名空间;标准 8.6 仅有 designation_namespace(8.6.2) | 删除或迁扩展 schema |
| `NamingConvention.rulesJson` | 扩展属性 | 机读命名规则(规则引擎) | 迁扩展表(X_SYS 命名规则配置) |
| `AcceptabilityConverter` | 技术类 | 持久化大小写转换 | 保留(非领域模型) |

注:Designation.acceptability / Definition.acceptability 在 8.7.1.1 文字中存在("This enumeration is used as a datatype for the attributes Designation.acceptability (8.4.1.3) and Definition.acceptability (8.4.2.3)")但表 14/15 未列——**标准自身文字与属性表矛盾**,代码实现该属性属合理选择,建议保留并加注说明矛盾。

### 2.3 common-facilities/identification(35 个扩展类,标准第 7 章仅有 3 个类)

| 扩展族 | 类 | 推断用途 | 处置建议 |
|---|---|---|---|
| 标识符生成策略框架(11) | IdentifierGenerationStrategy、UuidGenerationStrategy、UrnGenerationStrategy、SequenceGenerationStrategy、TemplateGenerationStrategy、IdentifierGenerationStrategyRegistry、IdentifierStrategyConfig(JPA)、IdentifierStrategyConfigService、SequenceProvider、SequenceRegistry(JPA) | 可配置标识符自动生成(UUID/URN/顺序号/模板),命名空间级配置、全局兜底、序列分桶计数 | 迁 X_SYS(或单立 X_ID) |
| 分段模板引擎(19) | segment/ 与 segment/types/ 全部:SegmentTemplate、SegmentDefinition、SegmentType、SegmentTypeHandler、SegmentTypeRegistry、SegmentTemplateEngine、SegmentContext、SequenceAllocator、ObjectAbbreviationCatalog、SegmentTemplateException、SequenceOverflowException、8 个 Handler | 占位符/分段模板生成、缩写目录、校验位、反向解析 | 迁 X_SYS(或 X_ID) |
| 版本治理(5) | VersionStatus、IdentifierVersionHistory(JPA)、VersioningPolicy、Versions、SemVerValidator | 自建版本生命周期 DRAFT→PUBLISHED→DEPRECATED→OBSOLETE 及历史审计;标准第 7 章无版本状态概念 | 迁 X_AUD/X_SYS |
| 创建模式治理(2) | IdentifierCreationMode、IdentifierInputMode | 自动/手动录入准入控制 | 迁 X_SYS(或 X_ID) |
| `Namespace.versioningPolicy/identifierInputMode/versionInputMode/manualIdentifierPattern` | 扩展属性 | 命名空间级治理策略 | 迁出(另:已有对应枚举但字段声明为 String,枚举未实际使用——代码内部一致性问题) |
| `ScopedIdentifier.identifierCreationMode/versionCreationMode/versionStatus/createdAt/publishedAt/deprecatedAt/obsoleteAt` | 扩展属性 | 创建模式记录、版本状态、生命周期时间戳 | 迁出 |

### 2.4 data-specification

| 项 | 类型 | 说明 | 处置建议 |
|---|---|---|---|
| `ClassificationScheme`、`ClassificationNode` | 错放模块 | 属 11179-3 第 10 章;现有自造字段(schemeName/version/status/sortOrder/createdAt 等)与 10.3.1/10.3.2 模型不符 | 迁 common-facilities(MDR_RCF)并按第 10 章重建 |
| `association/` 子包全部 11 类(AssociationType 枚举、3 校验器、MetadataObjectAssociationService、ValueDomainConceptualDomains、3 异常) | 功能扩展 | "四实体标准关联管理界面"(commit 9fbe462)的应用服务设施 | 非 JPA 实体的留应用层;有表则归扩展 schema |

主包 24 个标准对应类中**未发现**为功能后加的扩展字段(DataElement.precision 经核为 11179-31 Table 23 标准属性)。

---

## 3. 不一致项清单(需修正)

### 3.1 枚举/取值级

| # | 位置 | 问题 | 标准依据 |
|---|---|---|---|
| 1 | RegistrationStatus | 多 SUBMITTED、缺 CANDIDATE;PREFERRED 应作 "Preferred Standard";javadoc 的 WI-019 理由与原文相反;迁移 V20260517001 为反向迁移 | 11179-3 Table 30;11179-6 Table 1 |
| 2 | (正确示例)Acceptability、DegreeOfConformance | 与标准一致 | 11179-3 表 20/Table 29 |

### 3.2 类型级

| # | 位置 | 标准规定 | 代码实现 | 依据 |
|---|---|---|---|---|
| 3 | RegistrySpecification.name | 1, **Sign (6.2.10)** | 可空 String | 11179-3 Table 28 |
| 4 | Namespace.schemeReference | 0..1, **Sign**(IRI) | String | Table 13 |
| 5 | DataTypeScheme.scheme_reference | 0..1, **Reference_Document**;属性名 scheme_reference | 字段名 `reference`,类型 String | 11179-31 Table 11(Figure 5 与 Table 11 自相矛盾,以表格为准) |
| 6 | Datetime_Period.start/end_datetime | Datetime(6.2.3,含时区) | LocalDateTime(无时区),与本包自己的 Datetime(OffsetDateTime)不一致 | 11179-3 表 9 |
| 7 | Dimensionality.coordinate_indicator | 0..1 **三态** Boolean(NULL=Not applicable) | 原生 boolean,无法表达 NULL | 11179-31 Table 22 |
| 8 | Individual/Organization/Role.email_address | String(6.2.11) | 扩展类 EmailAddress | 11179-3 表 1/2/3 |
| 9 | RegistrySpecification.defaultTimeZone | Datetime(仅时分,"-0230" 格式) | String(8),无格式校验 | 11179-3 Table 28 |

### 3.3 多重性级

| # | 位置 | 标准 | 代码 | 依据 |
|---|---|---|---|---|
| 10 | ValueDomain.datatype | **1**(必填) | @ManyToOne 可空 | 11179-31 Table 5 |
| 11 | Slot.value | **1..*** | 单值 String | 11179-3 表 11 |
| 12 | Role.email_address / phone_number | **0..*** | 单值 0..1 | 11179-3 表 3 |
| 13 | ScopedIdentifier.version | **1** | 可空 | Table 12 |
| 14 | UnitOfMeasure→MeasureClass(unit_of_measure_class) | UoM 端 **1..*** | @ManyToMany 允许 0 | 11179-31 §7.5.3.2 |
| 15 | Item↔Identified_Item(item_identification) | **0..1** | List(0..*),getIdentifiedItem() 取首个变通 | 11179-3 7.4.1 |
| 16 | 普遍性问题:标准中多重性 1 / 1..* 的属性(creation_datetime、registration_status、effective_date、stewardship/submission 的 organization+contact、RA 的 identifier+documentation_language 等)JPA 全部可空、无 Bean Validation | | | Tables 12/13/21/22/24/26/27/28(11179-3) |

### 3.4 结构级

| # | 位置 | 问题 | 依据 |
|---|---|---|---|
| 17 | ConceptualDomain、EnumeratedConceptualDomain、ValueDomain、EnumeratedValueDomain | 标准为**抽象类**,代码为可实例化具体 @Entity | 11179-31 §7.4.2.1.2/§7.4.2.3.2/§7.4.2.8.2/§7.4.2.10.2 |
| 18 | RegisteredItem | 标准要求 abstract(9.4.1.2),代码为具体类 | 11179-3 9.4.1.2 |
| 19 | Concept | 代码声明 abstract;标准中 Item 才是抽象类,Concept 可实例化 | 11179-3 6.4.2.1.1 |
| 20 | ValueDomain.value_domain_meaning | 标准定义在基类(VD 0..* ↔ CD 0..1),代码下移到三个子类 + association 工具类补偿 | 11179-31 §7.4.4.1 |
| 21 | NaturalRange | min/max 可空性与标准语义颠倒(标准 min 必备、max 可无界),无"非负""max>min"约束 | 11179-3 6.2.6 |

### 3.5 @StandardReference 条款号系统性错位(疑沿用 2013 旧版编号)

错误引用(→2023 版正确条款):DocumentType 6.3.5→**6.3.6**;Contact 6.3.1→**6.3.5**;LanguageIdentification 6.3.6→**6.3.7**;ReferenceDocument 6.3.7→**6.3.8**;RegistrationAuthorityIdentifier 6.3.8→**6.3.9**;Datetime_Period 6.3.9→**6.3.10**;BooleanType 6.2.1→**6.2.2**;Date 6.2.2→**6.2.4**;Sign 6.2.11→**6.2.10**;StringType 6.2.10→**6.2.11**;Text 6.2.11→**6.2.12**;Value 6.2.12→**6.2.13**;Item 6.4.1→**6.4.2.1**;Concept 6.4.2→**6.4.2.2**;Context 6.4.3→**6.4.2.3**;Slot 6.4.6→**6.4.2.4**;Entity "6.4.5"→**2023 版无此条**。Designation/Definition/两个 Context 关联类/Pairing/Acceptability 缺 @StandardReference。正确的仅:Individual 6.3.2、Organization 6.3.3、Role 6.3.4、Datetime 6.2.3、IntegerType 6.2.5、NaturalRange 6.2.6、Notation 6.2.7、PhoneNumber 6.2.8、PostalAddress 6.2.9、NamingConvention 8.4.4。

---

## 4. 标准缺失项清单(标准有、代码未实现)

### 4.1 属性级缺失(优先)

1. **PermissibleValue.permitted_value** —— 11179-31 Table 6,多重性 **1 必填**(NOTE:即使有 Designation.sign 也必须单独指定值),代码完全没有
2. ConceptualDomain.valid_period(Table 2,多重性 1,含 §7.4.2.1.5.1 start_datetime 约束)
3. ConceptualDomain.dimensionality(Table 2,0..1)
4. EnumeratedConceptualDomain.expanded_on_datetime / locked_datetime(Table 3,各 0..1)
5. DataType.annotation(Table 8,0..1 Text)
6. ScopedIdentifier.fullExpansion / shorthandExpansion(11179-3 Table 12,0..1 条件派生;"shall exist if and only if the corresponding shorthand_prefix exists")

### 4.2 类/关联级缺失

- 11179-31:Enumerated_Conceptual_Domain_Definition(§7.4.2.7)+ domain_definition 关联;Data_Element_Example(§7.6.2.4);Data_Element_Derivation_Rule(§7.6.2.5);Data_Element_Derivation(§7.6.2.6)+ 三条关联;**§7.7 值域子集区域整体**(含 Subset_Values);**§7.8 概念域子集区域整体**;**§7.9 复合数据元区域整体**(Data_Element_Collection 等);**9 个 Constraint_Set 子类**(§7.3.2.5、§7.4.2.16~7.4.2.22 等)
- 11179-3:无类级缺失;约束条款未实现若干(7.3.2.4 级联删除、9.4.3.5 级联删除、Table 13 mandatory_naming_convention_indicator 约束、Contact 6.3.5.3、DocumentType 6.3.6.3、Acceptability 8.7.1.3 唯一 preferred、language 条件回退等)

---

## 5. Schema 重组方案

### 5.1 命名规则(已定 + 建议)

已定规则:
- `MDR_RCF` —— ISO/IEC 11179-3 元模型(registry common facilities)
- `MDR_DSR` —— ISO/IEC 11179-31 元模型(data specification registration)
- `MDR_` 前缀 = 该 schema 下所有领域模型/数据表必须逐字段可回溯到标准条款

建议的扩展命名规则(与 MDR_ 镜像):

> **`X_` 前缀 = 标准之外的功能扩展**,格式 `X_<功能域>`,大写;新增扩展功能先选既有功能域,无合适域才新立;备份/临时表(`*_bak_*`)不入任何正式 schema,用完即删。

| 建议 schema | 收纳功能 | 对应表现有表 |
|---|---|---|
| X_IAM | 账号、认证、命名空间权限 | account、namespace_permission |
| X_WF | 审批工作流 | approval_template、approval_process_instance、approval_step、approval_step_record、approval_record |
| X_MAP | 映射扩展(第 11 章之外的部分) | mapping_scheme、mapping_subscription(待补审计后定) |
| X_FHIR | FHIR/互操作 | fhir_code_system、fhir_element、fhir_element_mapping、fhir_resource、fhir_value_set、interop_data_element、interop_data_set、interop_mapping |
| X_NTF | 通知 | notification_preference、notification_record |
| X_FED | 联邦/合并 | merge_request、merge_request_item、registry_node、sync_watermark |
| X_DQ | 数据质量 | data_quality_task |
| X_SYS | 系统配置、标识符策略、导出 | system_setting、identifier_strategy_config、identifier_sequence_registry、export_task |
| X_AUD | 审计/版本历史 | definition_audit_log、version_history、identifier_version_history |

### 5.2 现有 119 张表的归属草案(mdr → 目标 schema)

**MDR_RCF(11179-3)**:administered_item、attached_item、concept、constraint_set、contact、context、definition、definition_context_relation、designation、designation_context_relation、designation_definition_pairing、designation_naming_convention、document_type、identified_item、individual、individual_email、individual_phone、item、language_identification、language_extension、language_variant、naming_convention、namespace、namespace_naming_convention、organization、organization_email、organization_name、organization_phone、reference_document、reference_document_language、reference_document_provider、registered_item、registered_item_reference_document、registrar、registration_authority、registration_authority_doc_language、registration_state、registry_specification、registry_specification_alternative_language、registry_specification_reference_doc_id_kind、role、scoped_identifier、slot、stewardship_record、submission_record、submission_record_registered_item、classification_scheme、classification_node(按第 10 章重建后)、item_classification、item_mapping、item_mapping_subject、item_mapping_object(第 11 章,待补审计确认)

**MDR_DSR(11179-31)**:conceptual_domain、conceptual_domain_dec、data_element、data_element_concept、data_type、data_type_scheme、described_conceptual_domain、described_value_domain、dimensionality、dimensionality_measure_class、enumerated_conceptual_domain、enumerated_value_domain、local_enumerated_conceptual_domain、local_enumerated_value_domain、measure_class、object_class、permissible_value、permissible_value_meaning、permissible_value_pvm、permissible_value_set、property、reference_enumerated_conceptual_domain、reference_enumerated_value_domain、unit_of_measure、unit_of_measure_measure_class、value_domain、value_meaning、value_meaning_permissible_value_meaning、value_meaning_set

**扩展(X_*)**:见 5.1 表;另有 designation_context、definition_context(裸关联,建议删)、definition_namespace(建议删或迁)

**删除**:designation_bak_20260901、definition_bak_20260901(临时备份)

**待查**:item_link(来源未确认)

### 5.3 技术注意点

- 现状是全局单一 `hibernate.default_schema=mdr`,实体无 per-table schema;拆分需逐实体显式 `@Table(schema=...)`(建议每模块定义常量,如 `Schemas.MDR_RCF`)。
- **SQLite(个人版)无 schema 概念**,`MDR_RCF.table` 会报错;需方言差异化方案(启动时 ATTACH 分库文件,或 SQLite 下落表名前缀),打包前必须解决。

---

## 6. 建议的清理顺序

1. 先修**枚举与数据**:RegistrationStatus(补 CANDIDATE、去 SUBMITTED、PREFERRED→Preferred Standard 语义),纠正 V20260517001 迁移方向与 javadoc
2. 修**不一致项**(第 3 节 21 项,多重性/类型/抽象性)
3. 补**属性级缺失**(第 4.1 节 6 项,尤其 PermissibleValue.permitted_value)
4. 迁出**多余扩展**(第 2 节)到 X_* schema / 新模块
5. 决策**类/关联级缺失**(第 4.2 节:11179-31 §7.7/§7.8/§7.9 与 Constraint_Set 子类是否本期实现)
6. 执行 schema 拆分(MDR_RCF/MDR_DSR/X_*)与 SQLite 兼容方案
7. 系统性修正 @StandardReference 条款号(第 3.5 节)
