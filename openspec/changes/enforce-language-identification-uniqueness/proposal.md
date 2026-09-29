# Proposal: enforce-language-identification-uniqueness

## Why

2026-09-25 23:59 后端启动失败：Spring `ApplicationRunner` `LanguageInitializer.upsertLanguage` 调用 `languageRepository.findByLanguageIdentifier("id-ID")` 抛 `NonUniqueResultException`（返回 2 行），Spring 上下文销毁、TOMCAT 优雅停机、业务 503。根因是 `mdr.language_identification` 表只有 PK 约束、缺 `UNIQUE(language_identifier)`，且 `LanguageInitializer` 用 `findByLanguageIdentifier().orElseGet(...)` 隐含假设查找唯一——任一外部代码 INSERT 重复 `language_identifier` 都会让下次启动崩。本次要给该列施加 DB 唯一约束作为终极防线，并把 `LanguageInitializer` 改成不依赖隐含唯一假设的显式防御式写法；同时迁移脚本自带幂等 dedup（按 id 最小保留行 + 9 张 FK 引用表的引用合并），不依赖人工预处理。

## What Changes

- **DB 唯一约束**：新增三方言迁移 `V20260926001__Enforce_language_identifier_unique.sql`（SQLite / SQL Server / 达梦），先按 `language_identifier` 分组保留 id 最小行并把其余 9 张 FK 引用表的 `language_id` 重定向到保留行，再删除多余行，最后 `CREATE UNIQUE INDEX uq_language_identification_identifier ON mdr.language_identification(language_identifier)`。三方言语义一致、幂等。
- **`LanguageInitializer` 防御重写**：改用 `languageRepository.findAllByLanguageIdentifier(identifier)`（新派生方法，返回 List）；空列表 → 创建，单元素 → 跳过，多元素 → 按 `MIN(id)` 取保留行并记 WARN（含 identifier / 计数 / 保留行 id），不阻塞启动。
- **`LanguageIdentificationRepository` 派生方法**：新增 `List<LanguageIdentification> findAllByLanguageIdentifier(String)`。
- **`KNOWN_FAILURES.md` 同步**：新增同类隐患条目（mdr.namespace、mdr.account 等"自然键 upsert 但只有 PK"），记录触发条件、缓解、后续 backlog。
- **新增 spec**：`openspec/specs/language-identification-uniqueness/spec.md`（独立承载"DB 唯一约束 + 启动期多行防御 + FK 引用合并"三条核心规则）；既有 `language-identification-edit` spec 不修改。
- **范围限定**：按项目既定"只修报告的"原则，本次**仅修 Language_Identification**；其他同类隐患的 mdr 表本次不动，留作后续 backlog。

## Capabilities

### New Capabilities

- `language-identification-uniqueness`: mdr.language_identification 表 `language_identifier` 列的 DB 唯一约束、启动期 `LanguageInitializer` 的多行防御行为、迁移期 FK 引用合并策略。

### Modified Capabilities

（无既有能力的需求语义变更。`language-identification-edit` 的"数据库语言标识初始化"需求在语义上保持"如不存在则自动插入"，新增的"启动期多行行为"由新独立 spec `language-identification-uniqueness` 承载，互不重复；既有 spec 不修改以保持职责单一。）

## Impact

- `metadata-registry-web`：
  - 三份新迁移 `db/migration/{sqlite,sqlserver,dm}/V20260926001__Enforce_language_identifier_unique.sql`。
  - `LanguageIdentificationRepository` 新增 `findAllByLanguageIdentifier` 派生方法。
  - `LanguageInitializer.upsertLanguage` 重写为多行防御写法。
  - 新增 `LanguageInitializerTest` 覆盖 0/1/多行三种情况（先红后绿）。
- `metadata-registry-common-facilities`：无变更。
- `metadata-registry-ui`：无变更。
- `.superpowers-memory/KNOWN_FAILURES.md`：新增同类隐患条目。
- 多方言要求：三方言迁移语义一致；启动期与运行时与方言无关。
- 第三方依赖：不变。
- 兼容性：纯向后兼容；现状已手清孤儿（仅 `id-ID` 一条孤儿，无 FK 引用），新装库/老库应用迁移后行为一致。
- 标准依据：ISO/IEC 11179-3:2023 §6.3.7 `Language_Identification`，自然键为 `language_identifier`（BCP 47 tag），本次 UNIQUE 约束为该业务规则的 DB 层落地。
