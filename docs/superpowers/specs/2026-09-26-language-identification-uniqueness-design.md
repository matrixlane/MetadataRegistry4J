# Language_Identification 唯一性约束与初始化防御设计

日期：2026-09-26
状态：待用户确认
关联变更：（待定，OpenSpec change 名建议 `enforce-language-identification-uniqueness`）

## 1. 背景与目标

### 1.1 故障链

2026-09-25 23:59 后端启动失败：

```
Caused by: org.hibernate.NonUniqueResultException: Query did not return a unique result: 2 results were returned
    at io.github.matrixlane.metadataregistry.web.seed.LanguageInitializer.upsertLanguage(LanguageInitializer.java:58)
    at io.github.matrixlane.metadataregistry.web.seed.LanguageInitializer.initializeLanguages(LanguageInitializer.java:52)
    at io.github.matrixlane.metadataregistry.web.seed.LanguageInitializer.run(LanguageInitializer.java:24)
```

`LanguageInitializer.upsertLanguage` 调用 `languageRepository.findByLanguageIdentifier("id-ID").orElseGet(...)`，期望返回 1 行但返回 2 行，触发 `NonUniqueResultException`。`LanguageInitializer` 是 `ApplicationRunner`（`@Order(1)`），其异常终止 Spring 上下文刷新 → Tomcat 优雅停机 → 后端 503。

### 1.2 根因

`mdr.language_identification` 表**只有 PK 约束 `PK__language__3213E83F`，没有 `UNIQUE(language_identifier)` 约束**。`LanguageInitialization` 用 `findByLanguageIdentifier` 隐含假设查找唯一——这条假设在表没约束时不成立。

事故前的脏数据（孤儿 `2AA28822-3900-4A1C-A647-1C095F753EE2`）由外部 API/手 SQL 写入，不是 `V20260919003__Seed_builtin_languages.sql` 引入的（seed 用 `WHERE NOT EXISTS` 幂等，只插 `f0ea7a50-18F4-4749-9D14-6F1B40ED5B08`）。也就是说**即使孤儿被清理，未来任何外部代码 INSERT 一行重复 `language_identifier`，下次启动仍会崩**——表上没唯一约束是真正的根因。

### 1.3 目标

- 在 DB 层施加 `UNIQUE(language_identifier)` 约束，从根本上拒绝外部脏数据产生。
- 改 `LanguageInitializer` 不再隐含假设查找唯一（防御式编程）。
- 现状已手清孤儿（仅 `id-ID` 一条孤儿，无 FK 引用），本次变更后其他环境应用迁移时应自带幂等 dedup，不依赖人工预处理。

### 1.4 范围

按项目长期原则"按用户'只修报告的'原则未批量处理，等报告再处理"（见 `.superpowers-memory/CURRENT_STATE.md` 第 33 行），本次变更**仅修 `mdr.language_identification`**。

同类隐患的 mdr 表（如 `namespace`、`account`、`organization` 等"按自然键 upsert 但只有 PK"的表）只入 `.superpowers-memory/KNOWN_FAILURES.md` 作后续 backlog，**不在本次范围内**。

### 1.5 标准依据

ISO/IEC 11179-3:2023 §6.3.7 定义 `Language_Identification` 类，其自然键为 `language_identifier`（BCP 47 tag，如 `zh-CN`、`en-US`、`id-ID`）。该值在同一注册机构作用域内业务上唯一——本次 UNIQUE 约束是该业务规则的 DB 层落地，不是新规则。

## 2. 用户已确认的决策

| # | 决策 | 结论 |
|---|---|---|
| 1 | 修复范围 | **A. 仅 Language_Identification**。其他"自然键 upsert 但只有 PK"的 mdr 表入 KNOWN_FAILURES，不动。 |
| 2 | `LanguageInitializer` 多行检测行为 | **A. 宽容 + WARN（推荐）**。`findAllByLanguageIdentifier` 取最早一条（按 id 字典序），多条时记 WARN 跳过创建。DB UNIQUE 约束兜底防止新脏数据，WARN 日志足以让运维/监控发现历史脏数据。备选 B（严格报错）/ C（沉默容忍）见 §3.5。 |
| 3 | 约束实施方式 | **A. `CREATE UNIQUE INDEX`**（三方言一致，不锁表过久、不需要重建表）。备选 `ALTER TABLE ADD CONSTRAINT`（方言差异大、SQLite 不支持）。 |
| 4 | 迁移自带 dedup | **A. 自带幂等 dedup**。同一迁移脚本里先 dedup 再加 UNIQUE INDEX，避免依赖人工预处理；dedup 用"按 `language_identifier` 分组保留 id 最小一行，其余删除"。 |
| 5 | spec 归属 | **A. 新增 `openspec/specs/language-identification-uniqueness/spec.md`**（独立 spec 承载"DB 唯一约束 + 启动器防御"两条核心规则），与既有 `language-identification-extension` spec 解耦（后者聚焦 EXT 卫星表/语言名/启用开关）。 |
| 6 | 测试覆盖范围 | TDD：单元测试覆盖 `LanguageInitializer` 多行行为（0/1/多行三种情况）+ 集成测试覆盖迁移脚本（先 dedup 再建索引）+ 现有 repository 单测保留。 |

## 3. 总体设计

### 3.1 实施切入点

```
        ┌───────────────────────────────────────────────┐
        │ mdr.language_identification 表                  │
        │ 当前：只有 PK 约束（无 UNIQUE）                   │
        └─────────────────┬─────────────────────────────┘
                          │
                          ▼ 迁移 V20260926001（dedup + UNIQUE INDEX）
        ┌───────────────────────────────────────────────┐
        │ mdr.language_identification 表                  │
        │ 改后：UNIQUE INDEX uq_language_identifier      │
        │       （拒绝未来 INSERT 重复行）                  │
        └─────────────────┬─────────────────────────────┘
                          │
                          ▼ 应用启动
        ┌───────────────────────────────────────────────┐
        │ LanguageInitializer.initializeLanguages()        │
        │   ↓ for each (id, script, territory)           │
        │ upsertLanguage(id, ...)                        │
        │   ↓ findAllByLanguageIdentifier(id)            │
        │     取最早一条（按 id 字典序）                   │
        │     列表空 → createLanguage                    │
        │     列表多条 → WARN 跳过                       │
        └───────────────────────────────────────────────┘
```

### 3.2 现状代码定位

- `LanguageInitializer.java:57-60`（`upsertLanguage`）—— 用 `findByLanguageIdentifier().orElseGet(...)`，隐含唯一假设。
- `LanguageInitializer.java:27-55`（`initializeLanguages`）—— 22 次 `upsertLanguage` 调用（G20 国家语言）。
- `LanguageIdentificationRepository.java:14`（`findByLanguageIdentifier`）—— Spring Data JPA 派生方法，默认走 `getSingleResult`。
- `LanguageIdentification.java:32-49`（实体类，ISO 11179-3:2023 §6.3.7）—— `@StandardReference` 引用。

### 3.3 详细变更

#### 3.3.1 迁移脚本 V20260926001（三方言）

**新文件**：
- `metadata-registry-web/src/main/resources/db/migration/sqlite/V20260926001__Enforce_language_identifier_unique.sql`
- `metadata-registry-web/src/main/resources/db/migration/sqlserver/V20260926001__Enforce_language_identifier_unique.sql`
- `metadata-registry-web/src/main/resources/db/migration/dm/V20260926001__Enforce_language_identifier_unique.sql`

**语义**（三方言一致；先 dedup 再加 UNIQUE INDEX；幂等）：

1. **dedup 阶段**：按 `language_identifier` 分组，保留 id 最小的一行，把其余行的 FK 引用统一指向保留行，然后删除多余行。
   - FK 引用表：`reference_document_language`、`registration_authority_doc_language`、`registry_specification_alternative_language`、`registry_specification`（列 `default_language_id`）、`definition`、`designation`、`language_extension`、`language_variant`、`ext.language_identification`（列 `language_id`）—— 共 9 张表引用。
2. **约束阶段**：`CREATE UNIQUE INDEX uq_language_identification_identifier ON mdr.language_identification(language_identifier)`。

**SQLite（最简单，因为不支持 `MERGE` / 高级 upsert）**：

```sql
-- 1) dedup：将多余行的 FK 引用指向保留行（id 最小）
--    reference_document_language
UPDATE reference_document_language SET language_id = (
    SELECT MIN(id) FROM language_identification WHERE language_identifier = (
        SELECT language_identifier FROM language_identification WHERE id = language_id
    )
)
WHERE language_id IN (
    SELECT id FROM language_identification
    WHERE id NOT IN (SELECT MIN(id) FROM language_identification GROUP BY language_identifier)
);
--    同理对其它 8 张引用表

-- 2) 删除多余行
DELETE FROM language_identification
WHERE id NOT IN (SELECT MIN(id) FROM language_identification GROUP BY language_identifier);

-- 3) 建 UNIQUE INDEX
CREATE UNIQUE INDEX uq_language_identification_identifier
    ON language_identification(language_identifier);
```

**SQL Server（支持 CTE）**：

```sql
WITH ranked AS (
    SELECT id, language_identifier,
           ROW_NUMBER() OVER (PARTITION BY language_identifier ORDER BY id) AS rn
    FROM mdr.language_identification
),
keepers AS (SELECT id, language_identifier FROM ranked WHERE rn = 1),
losers AS (SELECT id, language_identifier FROM ranked WHERE rn > 1)
-- 1) dedup FK 引用（同上 9 张表，UPDATE … FROM losers/keepers）
UPDATE rdl SET rdl.language_id = k.id
FROM mdr.reference_document_language rdl
JOIN losers l ON rdl.language_id = l.id
JOIN keepers k ON k.language_identifier = l.language_identifier;
-- 同理对其它 8 张表

-- 2) 删除多余行
DELETE vh FROM mdr.language_identification vh
JOIN losers l ON vh.id = l.id;

-- 3) 建 UNIQUE INDEX
CREATE UNIQUE INDEX uq_language_identification_identifier
    ON mdr.language_identification(language_identifier);
```

**达梦（DM，语法对齐 SQL Server 大体）**：仿 SQL Server 写法（DM 支持 CTE / 窗口函数），仅需把 catalog/schema 限定统一为 `mdr.`。

> **实施时细化**：具体每一张 FK 引用表的 UPDATE 语句在 tasks.md 中逐条列出，避免设计文档过于庞大。

#### 3.3.2 `LanguageInitializer` 防御性重写

**变更点 1**：`LanguageInitializer.upsertLanguage`

```java
private void upsertLanguage(String identifier, String script, String territory) {
    List<LanguageIdentification> existing = languageRepository.findAllByLanguageIdentifier(identifier);
    if (existing.isEmpty()) {
        createLanguage(identifier, script, territory);
    } else if (existing.size() > 1) {
        // DB 唯一约束是终极防线；此处宽容处理历史脏数据，
        // WARN 让运维/监控发现，不阻断启动。
        log.warn("language_identifier={} 在 mdr.language_identification 表中匹配到 {} 行，"
                + "保留最早一行（id={}），其余需人工清理。"
                + "本次启动不再创建新行；新约束应阻止此类脏数据产生。",
                identifier, existing.size(),
                existing.stream().min(Comparator.comparing(LanguageIdentification::getId)).orElseThrow().getId());
    }
    // size == 1：正常路径，直接返回
}
```

要点：
- 用 `findAllByLanguageIdentifier` 替代 `findByLanguageIdentifier`，显式接收多行。
- 多行时取 `MIN(id)`（最小 UUID），不依赖插入顺序（UUID 不连续）。
- WARN 包含 `identifier`、`count`、保留行的 `id`，便于运维定位。
- 0 行 / 1 行：行为不变（前者创建，后者跳过）。

**变更点 2**：`LanguageIdentificationRepository` 新增 `findAllByLanguageIdentifier`

```java
List<LanguageIdentification> findAllByLanguageIdentifier(String languageIdentifier);
```

（Spring Data JPA 派生方法，按方法名生成 `WHERE language_identifier = ?` 查询，返回 List 而不是 Optional。）

#### 3.3.3 新 spec（决策 5 = A）

**变更点 3**：新增 `openspec/specs/language-identification-uniqueness/spec.md`。

需求分节：

1. **DB 唯一约束**：`mdr.language_identification` 表 MUST 有 `UNIQUE(language_identifier)` 约束，由 `V20260926001` 迁移在三方言（SQLite / SQL Server / 达梦）一致施加。约束在已存在重复数据的实例上 MUST 借助幂等 dedup 阶段（保留 id 最小行 + FK 引用合并 + 删多余行）保证可应用。
2. **启动器多行防御**：`LanguageInitializer.upsertLanguage` MUST 不假设 `findAllByLanguageIdentifier` 返回唯一结果：列表为空则创建、列表为一条则跳过、列表为多条则按 id 最小取保留行并记 WARN 不阻塞启动。
3. **外键引用合并策略**：dedup 阶段必须把多余行（被丢弃的）所引用的 9 张外键引用表的 FK 指向保留行（id 最小），不留下悬挂引用。

引用关系：
- `language-identification-extension` spec 不变（聚焦 EXT 卫星/语言名/启用开关）。
- `system-initialization` spec 不变（已包含 `LanguageInitializer` 作为启动期补建路径，本 spec 不重复"何时跑"的规则）。

#### 3.3.4 KNOWN_FAILURES 同步

**变更点 4**：`.superpowers-memory/KNOWN_FAILURES.md` 新增一条"Failure Pattern: mdr.* 自然键列缺 UNIQUE 约束 + JPA findBy* 隐含唯一假设"。

记录：
- 同类潜在隐患表：`mdr.namespace`、`mdr.account`、`mdr.organization`、`mdr.registration_authority`、`mdr.admins` 等"业务上按自然键 upsert 但表上只有 PK"。
- 触发条件：外部代码 INSERT 重复自然键 → 应用启动时 `findBy*` 抛 NonUniqueResultException → AppRunner 失败 → Spring 上下文销毁。
- 缓解（本次应用）：见本次变更 spec。
- 后续 backlog：逐一审计同类表并按本次模式加 UNIQUE + 启动器防御。

### 3.4 边界与异常

- **FK 引用表里的 `language_id` 可能为 NULL**：本次 dedup 只更新 `language_id IS NOT NULL` 的行；NULL 行不受影响。
- **`ext.language_identification` 的 `language_id` 也是 FK**：按相同模式处理（保留行有 ext 卫星时优先保留；本次事故里孤儿 `2AA28822` 没 ext 卫星、`f0ea7a50` 有，所以保留 `f0ea7a50` 自然合理）。
- **迁移应用时的并发**：本次 dedup 在 Flyway 迁移中运行，应用启动尚未进入 `LanguageInitializer`（Flyway 在 `EntityManagerFactory` 之前），不会有并发写。SQL Server 上不显式加锁——ROW_NUMBER 窗口函数在 READ COMMITTED 下足够。
- **达梦方言窗口函数支持**：DM8 支持 `ROW_NUMBER() OVER`，与 SQL Server 写法对齐；如不支持则回退到 `MIN(id) GROUP BY` + 自连接的等价写法（在任务细化时确认）。
- **null `language_identifier`**：当前表没有 NOT NULL 约束，理论上可有 NULL 行。dedup 时 `GROUP BY language_identifier` 会把所有 NULL 分到一组，保留 id 最小一行。这与"自然键唯一"语义一致（NULL 在 SQL 中不互相相等，多个 NULL 不违反 UNIQUE INDEX 语义——但 SQLite/SQL Server 默认 UNIQUE INDEX 允许多个 NULL）。**决策**：不在本次加 `NOT NULL` 约束（超出范围），但 dedup 把 NULL 也视作一组去重。

### 3.5 备选方案回顾

**决策 2 的备选**：

- **B 严格报错**：`count > 1` 时 `throw new IllegalStateException`，启动失败。优点：强制清理；缺点：跟今天故障一致，业务完全不可用。本次不推荐——重复行已经发生，强制清理不应由"系统启动"路径承担。
- **C 沉默容忍**：`findFirst()` 不记日志。优点：噪音最小；缺点：脏数据被埋掉，运维不知道。否决。

**决策 3 的备选**：

- **`ALTER TABLE ADD CONSTRAINT UNIQUE`**：SQL Server / DM 支持，但 SQLite 不支持；不一致。否决。
- **`NOT NULL + UNIQUE` 同时加**：超出本次范围（应单独提变更审计所有自然键 NOT NULL 现状）。否决。

## 4. 待确认事项

1. 决策 1/2/3/4/5/6 等待用户确认（推荐 A/A/A/A/A/A）。
2. 迁移脚本具体每张 FK 引用表的 UPDATE 语句是否需要在 design.md 全部展开——我倾向在 tasks.md 里逐条列，design.md 仅描述策略与示例；如果你倾向展开，告诉我。
3. 是否需要额外的 `KNOWN_FAILURES` 条目同步列出后续 backlog 表清单——我倾向同步列出（即使本次不动），方便未来检索。

## 5. 验证标准

- **后端 TDD**：
  - 新增 `LanguageInitializerTest` 覆盖三种情况（0/1/多行）。先写红测试。
  - 新增 `LanguageIdentificationRepositoryTest` 覆盖 `findAllByLanguageIdentifier` 派生方法。
  - `./mvnw -pl metadata-registry-web -am test -Dtest='LanguageInitializerTest,LanguageIdentificationRepositoryTest' -DfailIfNoTests=false` → 0 failures。
- **后端全量**：`./mvnw -pl metadata-registry-web -am test` → 0 failures（既有 baseline 1971 tests，本变更不破坏）。
- **前端**：本次无前端变更，不跑 vitest。
- **多方言迁移实测**：
  - SQL Server 真实库（已有 22 行干净状态）：应用 V20260926001 成功，事后 `UNIQUE INDEX uq_language_identification_identifier` 存在。
  - SQLite 副本库（人工 INSERT 2 行重复 `id-ID` 模拟脏数据）：应用 V20260926001 成功，dedup 后剩 22 行，`UNIQUE INDEX` 存在。
  - 达梦副本库（如可达）：同上。
- **DB 兜底验证**：迁移后人为 INSERT 一行重复 `language_identifier`，预期 DB 层面报错（不依赖应用层）。
- **应用启动冒烟**（SQL Server 真实库 + SQLite 副本库）：重启后端，`actuator/health` 返回 200；日志中出现 `内置语言标识数据初始化完成`，无 `NonUniqueResultException`。
- **前端 build**：`cd metadata-registry-ui && npm run build` → 通过。
- **OpenSpec valid**：`openspec status --change "enforce-language-identification-uniqueness" --json` → all artifacts ready。
- **GitNexus detect_changes**：`bunx gitnexus@latest detect-changes --scope all --repo .` → 无 partial / truncated。
