# language-identification-uniqueness Specification

## Purpose
为 `mdr.language_identification` 表的 `language_identifier` 列施加 DB 唯一约束作为终极防线，并消除应用层所有 `language_identifier` 查找的隐含唯一假设——历史脏数据存在时按 id 最小保留行 + WARN 继续业务，不抛 `NonUniqueResultException`、不阻塞启动；外部 INSERT 重复行在 DB 层面直接被拦截，避免类似 2026-09-25 23:59 启动崩溃再次发生。

## Requirements

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

### Requirement: 语言标识解析多行防御

应用层按 `language_identifier` 解析 `Language_Identification` 时 MUST NOT 假设查询返回唯一结果。解析 MUST 经统一防御式入口（`LanguageIdentifierResolver`）完成：列表为空时 MUST 返回空结果交由调用方按既有逻辑处理；列表为单元素时 MUST 返回该行；列表为多元素时 MUST 按 id（UUID）字典序最小取保留行，记 WARN 日志（含 `language_identifier` 值、命中行数、保留行 id），MUST NOT 抛出未捕获异常——业务处理不应被历史脏数据阻塞。DB 唯一约束是该规则的终极防线；WARN 让运维/监控发现历史脏数据并安排清理。

#### Scenario: 解析命中唯一行

- **WHEN** 业务代码经防御式入口解析某 `language_identifier`，数据库中存在唯一一行
- **THEN** 返回该行，不写 WARN 日志

#### Scenario: 解析无命中行

- **WHEN** 业务代码经防御式入口解析某 `language_identifier`，数据库中无对应行
- **THEN** 返回空结果，调用方按既有"不存在"分支处理（创建或按域规则报错）

#### Scenario: 解析命中多行

- **WHEN** 业务代码经防御式入口解析某 `language_identifier`，数据库中匹配多行
- **THEN** 返回按 id 字典序最小的保留行；在 WARN 级别记录一条日志，包含 `language_identifier` 值、命中行数、保留行 id；不抛异常，本次业务调用继续完成，不影响后续处理

#### Scenario: 多行时不影响后续解析

- **WHEN** 某次解析命中多行并记 WARN 后
- **THEN** 后续其他 `language_identifier` 的解析调用照常推进，不被中断

### Requirement: 语言标识解析不依赖隐含唯一假设

`LanguageIdentificationRepository` MUST 提供 `findAllByLanguageIdentifier(String languageIdentifier)` 方法（Spring Data JPA 派生方法，返回 `List<LanguageIdentification>`），与既有 `findByLanguageIdentifier`（返回 `Optional<LanguageIdentification>`）并存。全部按 `language_identifier` 的业务解析调用点 MUST 经 `LanguageIdentifierResolver` 完成——MUST NOT 再使用 `findByLanguageIdentifier().orElseGet(...)` 等隐含唯一假设的模式；该模式与多行防御语义冲突，重复行存在时会抛 `NonUniqueResultException`。

#### Scenario: 仓库派生方法存在

- **WHEN** 编译期或运行期检查 `LanguageIdentificationRepository`
- **THEN** 该接口声明 `List<LanguageIdentification> findAllByLanguageIdentifier(String languageIdentifier)` 方法

#### Scenario: 主源码无隐含唯一假设调用

- **WHEN** 全库检索主源码（`src/main/java`）中 `findByLanguageIdentifier` 的直接业务调用
- **THEN** 除 `LanguageIdentifierResolver` 内部实现外，无直接调用；全部经 `LanguageIdentifierResolver` 解析
