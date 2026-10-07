# Design: move-ra-primary-to-ext

## Context

is_primary 为应用内部隐式标志（ISO/IEC 11179-3 无 Primary 注册机构概念，§9.4.6 只有 registration_authority_identifier），按分层原则属 ext 扩展层。

## Decisions

- **卫星表**：`ext.registration_authority(id PK, is_primary 非空默认 false)`，与 mdr.registration_authority 1:1；有意不建 JPA 关联（与 ContactAccountBinding 同范式）。
- **方言映射**：实体 `@Table(name="registration_authority")` 裸写；orm-team.xml `<table schema="ext"/>`；orm-sqlite.xml `<table name="ext_registration_authority"/>`（SQLite 无 schema、同名冲突加前缀，同 ext_language_identification 判例）。
- **迁移**：数据搬迁（INSERT SELECT）后 DROP mdr.is_primary；三方言守卫惯例（SQL Server COL_LENGTH+schema 兜底；DM 大写全限定；SQLite 裸名）。列删除后实体同步删字段，ddl-auto 库由实体驱动不再建列。
- **级联删除**：RegistrationAuthorityDeletionService 末段补删卫星行（按 RA id）。

## Risks / Trade-offs

- [存量 SQL Server 库已应用 V20261005001] → 本次迁移幂等搬运（列存在才搬并删），既有行不丢。
