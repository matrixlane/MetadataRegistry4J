# language-identification-uniqueness — 语言标识表自然键唯一约束与启动期防御

## Purpose

为 `mdr.language_identification` 表的 `language_identifier` 列施加 DB 唯一约束作为终极防线，并在 `LanguageInitializer` 启动期播种内置语言时改用防御式查询——历史脏数据存在时按 id 最小保留行 + WARN 跳过，不阻塞启动；外部 INSERT 重复行在 DB 层面直接被拦截，避免类似 2026-09-25 23:59 `NonUniqueResultException` 启动崩溃再次发生。

## ADDED Requirements

### Requirement: 语言标识表自然键唯一约束

`mdr.language_identification` 表 MUST 有 `UNIQUE(language_identifier)` 约束。该约束 MUST 在三方言（SQLite / SQL Server / 达梦）上一致施加，由 `V20260926001__Enforce_language_identifier_unique.sql` 迁移承载，索引名 `uq_language_identification_identifier`。已存在重复行的实例 MUST 由该迁移自带的幂等 dedup 阶段处理后再建立约束——按 `language_identifier` 分组保留 id 最小一行、其余 9 张外键引用表的 `language_id` 字段全部重定向到保留行、再删除多余行，最后 `CREATE UNIQUE INDEX`。本次迁移 MUST NOT 引入 `NOT NULL` 约束。

#### Scenario: 干净库应用迁移

- **WHEN** 在任一支持方言执行 `V20260926001` 迁移，且 `mdr.language_identification` 表无重复行
- **THEN** 迁移成功应用，dedup 阶段无操作；表存在 `uq_language_identification_identifier` UNIQUE INDEX

#### Scenario: 脏数据库应用迁移

- **WHEN** 在任一支持方言执行 `V20260926001` 迁移，且 `mdr.language_identification` 表存在 `language_identifier` 重复的多行
- **THEN** 迁移成功应用；dedup 阶段保留每组 `language_identifier` 中 id 最小的一行；其余 9 张引用表（`reference_document_language`、`registration_authority_doc_language`、`registry_specification_alternative_language`、`registry_specification`、`definition`、`designation`、`language_extension`、`language_variant`、`ext.language_identification`）的 `language_id` 字段全部指向保留行；多余行被删除；`uq_language_identification_identifier` UNIQUE INDEX 成功建立

#### Scenario: 外部 INSERT 重复自然键被 DB 拒绝

- **WHEN** 迁移已应用后，任何外部代码尝试 INSERT 一行与既有行 `language_identifier` 重复的新行
- **THEN** DB 层面因 `uq_language_identification_identifier` 唯一索引抛唯一约束违例错误，INSERT 失败；系统中不会产生新的重复行

#### Scenario: 不引入 NOT NULL 约束

- **WHEN** 应用迁移前后
- **THEN** `mdr.language_identification.language_identifier` 列的可空性保持不变（不加 `NOT NULL`）

### Requirement: 启动期多行防御

`LanguageInitializer.upsertLanguage` MUST 不假设 `language_identifier` 查询返回唯一结果。其查询 MUST 改用返回列表的方法（`findAllByLanguageIdentifier`）；列表为空时 MUST 调用创建逻辑；列表为单元素时 MUST 跳过创建；列表为多元素时 MUST 按 id（UUID）字典序最小取保留行，记 WARN 日志（含 `language_identifier` 值、命中行数、保留行 id），MUST NOT 调用创建逻辑，MUST NOT 抛出未捕获异常——启动期播种不应被历史脏数据阻塞业务。DB 唯一约束是该规则的终极防线；WARN 让运维/监控发现历史脏数据并安排清理。

#### Scenario: 启动期发现某语言已有唯一行

- **WHEN** 系统启动，`LanguageInitializer` 处理某内置语言标识
- **THEN** 数据库中存在该 `language_identifier` 的唯一一行；`upsertLanguage` 跳过创建，不写日志

#### Scenario: 启动期发现某语言在数据库中无对应行

- **WHEN** 系统启动，`LanguageInitializer` 处理某内置语言标识
- **THEN** 数据库中无该 `language_identifier` 对应行；`upsertLanguage` 创建新行并持久化

#### Scenario: 启动期发现某语言在数据库中有多行

- **WHEN** 系统启动，`LanguageInitializer` 处理某内置语言标识，且数据库中匹配 `findAllByLanguageIdentifier` 返回多行
- **THEN** `upsertLanguage` 不创建新行；在 WARN 级别记录一条日志，包含 `language_identifier` 值、命中行数、按 id 字典序最小保留行的 id；启动期播种继续推进后续语言标识，不抛异常，不影响 Spring 上下文刷新与 TOMCAT 启动

#### Scenario: 多行时不影响其他语言的处理

- **WHEN** 启动期发现某语言在数据库中有多行，记 WARN 后
- **THEN** 后续其他内置语言标识的 `upsertLanguage` 调用照常推进，不被中断

### Requirement: 启动期不再依赖隐含唯一假设

`LanguageIdentificationRepository` MUST 提供 `findAllByLanguageIdentifier(String languageIdentifier)` 方法（Spring Data JPA 派生方法，返回 `List<LanguageIdentification>`），与既有 `findByLanguageIdentifier`（返回 `Optional<LanguageIdentification>`）并存。`LanguageInitializer` MUST NOT 再使用 `findByLanguageIdentifier().orElseGet(...)` 模式——该模式隐含假设查询唯一，与本能力承载的多行防御语义冲突。

#### Scenario: 仓库派生方法存在

- **WHEN** 编译期或运行期检查 `LanguageIdentificationRepository`
- **THEN** 该接口声明 `List<LanguageIdentification> findAllByLanguageIdentifier(String languageIdentifier)` 方法

#### Scenario: 启动器不再使用隐含唯一假设

- **WHEN** 阅读 `LanguageInitializer.upsertLanguage` 实现
- **THEN** 不再调用 `findByLanguageIdentifier(...).orElseGet(...)` 模式；改用 `findAllByLanguageIdentifier(...)` 并按"启动期多行防御"需求语义分支处理
