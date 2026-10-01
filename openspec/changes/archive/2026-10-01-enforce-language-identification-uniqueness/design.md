# Design: enforce-language-identification-uniqueness

## Context

参见 `proposal.md` 与 `docs/superpowers/specs/2026-09-26-language-identification-uniqueness-design.md`。

关键约束：

- `mdr.language_identification` 表当前只有 PK 约束，无 `UNIQUE(language_identifier)`。
- 该表被 9 张表通过外键引用 `language_id`：`reference_document_language`、`registration_authority_doc_language`、`registry_specification_alternative_language`、`registry_specification`（列 `default_language_id`）、`definition`、`designation`、`language_extension`、`language_variant`、`ext.language_identification`（列 `language_id`）。
- `LanguageInitializer`（`@Order(1)` `ApplicationRunner`）抛异常会终止 Spring 上下文刷新 → Tomcat 优雅停机 → 后端 503。
- 项目多方言：SQLite / SQL Server / 达梦（DM）。SQLite 不支持 `ALTER TABLE ... ADD CONSTRAINT`，需要走 `CREATE UNIQUE INDEX` 路线才能三方言一致。
- 项目既定原则："按用户'只修报告的'原则未批量处理，等报告再处理"——本次仅修 Language_Identification，同类隐患的 mdr 表留 backlog。

## Goals / Non-Goals

**Goals:**

- 在 DB 层施加 `UNIQUE(language_identifier)` 约束作为终极防线，从根本上拒绝外部脏数据产生。
- 改 `LanguageInitializer` 不再隐含假设查找唯一（防御式编程），脏数据存在时取保留行 + WARN 不阻塞启动。
- 迁移自带幂等 dedup，不依赖人工预处理。
- 多方言语义一致；现状库（22 行干净状态）与带脏数据库应用迁移后行为一致。

**Non-Goals:**

- 不给 `language_identifier` 列加 `NOT NULL` 约束（超出本次范围）。
- 不修改 `language-identification-edit` spec（保持职责单一，新行为由独立 spec 承载）。
- 不修复其他"自然键 upsert 但只有 PK"的 mdr 表（namespace、account、organization、registration_authority 等）；这些只入 KNOWN_FAILURES 作 backlog。
- 不为 `mdr.language_identification` 加 NOT NULL 或 CHECK 约束。
- 不修改既有 `V20260919003__Seed_builtin_languages.sql`（seed 脚本本身幂等，本次不动）。

## Decisions

### D1. 约束实施走 `CREATE UNIQUE INDEX`，不走 `ALTER TABLE ADD CONSTRAINT`

**理由**：SQLite 不支持 `ALTER TABLE ... ADD CONSTRAINT`，三方言必须走索引路线才能保持 SQL 形态一致；`CREATE UNIQUE INDEX` 在三方言都支持、不锁表过久、不需要重建表。

**备选**：`ALTER TABLE ADD CONSTRAINT UNIQUE`（SQL Server / DM 支持，SQLite 不支持，方言不一致）。

### D2. 迁移自带幂等 dedup，不依赖人工预处理

**理由**：同类环境（多实例、跨方言副本、CI 烟测库）不应各自依赖人工删孤儿；脚本需自带"按 `language_identifier` 分组保留 id 最小行 + 9 张 FK 引用表重定向 + 删多余行"流程后才能建 UNIQUE INDEX。

**备选**：依赖人工预处理（脆弱，本次事故就是依赖人工的代价）。

### D3. `LanguageInitializer` 多行防御走宽容 + WARN

**理由**：启动期播种内置数据不应被历史脏数据阻塞业务；DB UNIQUE 约束兜底防止新脏数据产生；WARN 日志（含 identifier / 计数 / 保留行 id）足以让运维/监控发现历史脏数据并安排清理。

**备选**：
- 严格报错（`count > 1` 抛异常）：跟今天故障行为一致，业务完全不可用——脏数据已发生，强制清理不应由启动路径承担。
- 沉默容忍（`findFirst()` 不记日志）：脏数据被埋掉，运维不知道——否决。

### D4. `LanguageIdentificationRepository` 派生方法名 `findAllByLanguageIdentifier`

**理由**：Spring Data JPA 派生方法按方法名生成 `WHERE language_identifier = ?`，返回 List；命名清晰、与既有 `findByLanguageIdentifier`（返回 Optional，单行场景）并存，便于单测覆盖两种查询路径。

**备选**：自定义 `@Query` 显式写 SQL——杀鸡用牛刀，派生方法更简洁。

### D5. 新增独立 spec `language-identification-uniqueness`，不修改 `language-identification-edit`

**理由**：职责单一原则——既有 `language-identification-edit` 聚焦"语言标识状态管理"（初始化、UI 开关、编辑），新 spec 聚焦"唯一性约束"（DB 约束 + 启动期多行防御 + FK 合并）；两者场景不同、未来扩展方向不同，分开更利于维护。引用关系在 design.md §3.4 描述，不修改既有 spec 的需求文本。

**备选**：把"启动期多行行为"作为 ADDED Requirement 写入 `language-identification-edit` 的 delta spec——会污染既有 spec 的职责边界。

## Risks / Trade-offs

- [迁移对 9 张 FK 引用表 UPDATE 的写放大] → 单次迁移、非频繁操作；规模小（最多 22 行内 dedup），实测毫秒级可完成。
- [Flyway 迁移期并发 INSERT] → Flyway 在 `EntityManagerFactory` 初始化前运行，应用 `LanguageInitializer` 此时尚未启动；SQL Server 默认 READ COMMITTED 下 ROW_NUMBER 窗口函数足够，无显式锁。
- [达梦方言窗口函数兼容性] → DM8 支持 `ROW_NUMBER() OVER`；若环境受限则在任务细化时回退到 `MIN(id) GROUP BY` + 自连接等价写法。
- [WARN 日志噪音] → 历史脏数据清理前每次启动都会有 22 行中的 WARN；首次清理后噪音消失。可接受——比启动崩溃好得多。
- [新 spec 与既有 `language-identification-edit` 引用不清] → 在 design.md §3.4 明确"前者承载 DB 唯一约束 + 多行防御，后者承载初始化/编辑 UI"，并在 tasks.md 加交叉引用注释；不修改既有 spec 文本。
- [本次不动同类隐患的 mdr 表] → 在 `.superpowers-memory/KNOWN_FAILURES.md` 新增条目记录 backlog，便于未来检索。

## Migration Plan

1. **应用阶段**：随下次发布部署；Flyway 自动在三方言数据库依次执行 `V20260926001__Enforce_language_identifier_unique.sql`。
   - 干净库（22 行）：dedup 阶段无操作 → UNIQUE INDEX 直接建立。
   - 脏数据库（如本次事故现场）：dedup 阶段合并 FK 引用并删除多余行 → UNIQUE INDEX 成功建立。
2. **回滚策略**：本次不提供 DOWN 迁移（标准做法，详见项目历史变更）；若必须回滚，需手工 `DROP INDEX uq_language_identification_identifier` 并视情况恢复数据。
3. **运行时变更**：`LanguageInitializer` 改防御写法（与迁移独立运行；新库先跑迁移再启动应用，老库同序）。
4. **既有 seed 脚本 `V20260919003__Seed_builtin_languages.sql`**：不动（本身幂等，重复插入被 `WHERE NOT EXISTS` 拦截）。

## Open Questions

（无。本次变更设计已收敛，所有关键决策点已在 design 文档中列明并由用户确认。）
