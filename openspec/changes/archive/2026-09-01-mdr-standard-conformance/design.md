# 设计:MDR 标准模型一致性恢复

## 总体策略

按"先枚举与数据安全项、再结构项、最后注释项"的顺序推进,每步编译+测试验证。继承全部 JOINED,改 abstract 不动表结构;新增列走 ddl-auto/Flyway 基线;多重性收紧前核库。

## 关键设计决策

### D1. RegistrationStatus 值集与读取兼容

标准值集(11179-3:2023 Table 30 / 11179-6:2023 Table 1):
`INCOMPLETE, CANDIDATE, RECORDED, QUALIFIED, STANDARD, PREFERRED_STANDARD, SUPERSEDED, RETIRED, HISTORICAL, APPLICATION`

- **持久化**:`@Enumerated(EnumType.STRING)` 落库 Java 枚举名(大写)。
- **历史数据兼容**:`CaseInsensitiveEnumJavaType` 已做大小写不敏感;本次增强 `relaxedValueOf`——先 `trim().toUpperCase()`,失败再 `replaceAll("\s+", "_")` 归一("Preferred Standard"→`PREFERRED_STANDARD`、"preferred_standard"→`PREFERRED_STANDARD`),覆盖标准书写格式含空格的取值。
- **状态机**:`RegistrationLifecycleService` 转移表与权限矩阵按新值集重排;`CANDIDATE` 作为 Incomplete 之后、Recorded 之前的提议态。
- **迁移脚本**:V20260517001(仅 sqlserver)是反向迁移(CANDIDATE→SUBMITTED、PREFERRED_STANDARD→PREFERRED、APPLICATION→SUBMITTED),新增 V2026xxxx 修正:SUBMITTED→CANDIDATE、PREFERRED→PREFERRED_STANDARD;现库仅 'Standard',实际 no-op,幂等 IF EXISTS 兜底。sqlite/dm 补对应脚本。

### D2. 抽象类(零表结构)

JOINED 策略下 abstract 父类照建父表。改动:
- `ValueDomain`、`ConceptualDomain`、`EnumeratedValueDomain`、`EnumeratedConceptualDomain` → `abstract class`
- `RegisteredItem` → `abstract class`
- `Concept` → 去掉 `abstract`(标准 6.4.2.1.1:Item 抽象、Concept 可实例化)

实例化点:生产代码走 `ValueDomainType`/`ConceptualDomainType` 枚举 switch(`ValueDomainService.instantiate()`、`ConceptualDomainService`),无需改;测试改为实例化叶子类(`DescribedValueDomain`、`LocalEnumeratedConceptualDomain` 等)。

### D3. 类型回归标准

| 项 | 现状 | 目标 | 说明 |
|---|---|---|---|
| RegistrySpecification.name | 可空 String | `Sign` @Embedded,nullable=false | 11179-3 Table 28 |
| Namespace.schemeReference | String | `Sign` @Embedded | Table 13(Sign=IRI) |
| DataTypeScheme.reference | String `reference` | `schemeReference` @ManyToOne Reference_Document | 11179-31 Table 11(Figure 5 与 Table 11 矛盾,以表格为准) |
| Datetime_Period.start/end | LocalDateTime | 本包 `Datetime`(OffsetDateTime) | 含时区 |
| Dimensionality.coordinateIndicator | 原生 boolean | `Boolean`(三态) | Table 22,NULL=Not applicable |
| email_address | `EmailAddress` @Embeddable | String | 表 1/2/3;删 EmailAddress 类,列类型不变 |
| NaturalRange | min 可空/max 恒存 | min 必备非负、max 可无且>min | 6.2.6 |
| defaultTimeZone | String(8) | String + `^[-+]\d{4}$` 校验 | Table 28,显式妥协声明 |

### D4. 多重性收紧(核库先行)

执行前对每项跑 `SELECT COUNT(*) ... WHERE <col> IS NULL` 核库;有 NULL 存量先补数据。
- `@Column(nullable=false)`/`@ManyToOne(optional=false)`:ValueDomain.datatype、ScopedIdentifier.version、RegistrationState.registrationStatus/effectiveDate、AdministeredItem.creationDateTime、Stewardship/Submission 的 organization+contact、RegistrationAuthority.identifier、RegistrySpecification.name/characterRepertoire。
- `Slot.value` 1..* → `@ElementCollection List<String>`(新表 `slot_value`)。
- `Role.email_address`/`phone_number` 0..* → `@ElementCollection List`(新表 role_email/role_phone)。
- `Item↔IdentifiedItem` 0..1 → OneToOne(去 List 变通)。
- `UnitOfMeasure→MeasureClass` 1..* → 服务层校验(关联表,非列)。
- 级联:7.3.2.4、9.4.3.5 → `@OneToMany(cascade=ALL, orphanRemoval=true)`。

### D5. 补标准缺失属性

| 属性 | 目标类 | 多重性 | 实现 |
|---|---|---|---|
| permitted_value | PermissibleValue | 1 | String 列 nullable=false(核库回填) |
| valid_period | ConceptualDomain | 1 | @Embedded Datetime_Period(继承体系改基类) |
| dimensionality | ConceptualDomain | 0..1 | @ManyToOne Dimensionality |
| expanded_on_datetime / locked_datetime | EnumeratedConceptualDomain | 0..1 | OffsetDateTime |
| annotation | DataType | 0..1 | Text |
| fullExpansion / shorthandExpansion | ScopedIdentifier | 0..1 derived | 派生 getter,不落库 |
| value_domain_meaning | ValueDomain 基类 | 0..1 CD | 基类 @ManyToOne ConceptualDomain;子类字段迁移后删;保留 §7.4.5.2.1 子类型一致校验 |

### D6. 条款号更正

按审计报告第 3.5 节对照表批量替换;`Entity` 技术基类的 `@StandardReference("…6.4.5…")` 删除(2023 版无此条);Designation/Definition/DesignationContext/DefinitionContext/Pairing/Acceptability 补 @StandardReference。

## 测试策略

- 每个修正点配/改单测(枚举值集、状态机、派生 getter、多重性 Bean Validation)。
- `CaseInsensitiveEnumJavaTypeTest` 增加 "Preferred Standard"→PREFERRED_STANDARD 用例。
- `RegistrationStatusMigrationTest` 重写为正向迁移断言。
- 端到端:`CaseInsensitiveEnumPersistenceTest` 模式跑内存 SQLite 引导,验证新枚举落库/读回。
- 全模块测试绿后打包连 SQL Server 库人工过数据元/值域/概念域/注册项页面。
