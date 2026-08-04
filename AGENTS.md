<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **MetadataRegistry4j** (19455 symbols, 46994 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/MetadataRegistry4j/context` | Codebase overview, check index freshness |
| `gitnexus://repo/MetadataRegistry4j/clusters` | All functional areas |
| `gitnexus://repo/MetadataRegistry4j/processes` | All execution flows |
| `gitnexus://repo/MetadataRegistry4j/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

<!-- db-portability:start -->
# 数据库可移植性规则（SQLite / SQL Server / 达梦）

- 表结构由 Hibernate `ddl-auto` 依据实体元数据管理；Flyway 迁移脚本仅承载索引、约束与数据迁移等 Hibernate 无法表达的内容。
- 迁移脚本按方言分目录：`db/migration/sqlserver/`、`db/migration/sqlite/`、`db/migration/dm/`，由 `DB_VENDOR` 环境变量（默认 `sqlserver`）选择加载目录。
- 新增迁移必须可移植（三种方言均可执行），或在每个方言目录各提供一份等价脚本。
- JPA 可表达的索引/约束一律写在实体注解（`@Table(indexes/uniqueConstraints)`、`@Check`），不写进迁移脚本。
- 业务代码禁止方言专有 SQL（`INFORMATION_SCHEMA`、`sys.*`、`DBCC`、`NOCHECK` 等）；确需 native SQL 时放入按方言命名的实现类，接口保持方言中立。
- 实体类不得硬编码 `@Table(schema = ...)`；schema 归属由 `DB_DEFAULT_SCHEMA` 配置决定。
 <!-- db-portability:end -->
 
 <!-- iso11179-mdr-requirements:start -->
 # MDR 需求依据 —— ISO/IEC 11179 标准文档
 
 本项目作为元数据注册系统（Metadata Registry, MDR），其功能与数据模型需求依据应通过查询 Milvus 中存储的 ISO/IEC 11179 八个标准文档获得。
 
 ## 查询范围
 
 ISO/IEC 11179 系列共八个部分，全部纳入需求溯源范围：
 
 1. ISO/IEC 11179-1: 框架
 2. ISO/IEC 11179-2: 分类
 3. ISO/IEC 11179-3: 注册系统元模型与基本属性
 4. ISO/IEC 11179-4: 数据定义的形成与规则
 5. ISO/IEC 11179-5: 命名与标识原则
 6. ISO/IEC 11179-6: 注册
 7. ISO/IEC 11179-7: 数据元元数据的分类与排序
 8. ISO/IEC 11179-8: 信息交换
 
 ## 使用方式
 
 - 当分析、设计或验证 MDR 功能时，优先通过 Milvus 向量检索查询上述标准文档，以获取权威依据。
   - 查询时优先使用 Milvus MCP 工具 `milvus_vector_search`；该工具不可用时再考虑其他 Milvus MCP 工具。
   - 向量集合信息可通过 `list_mcp_resources` / `read_mcp_resource` 获取。
 - 若需求依据与标准文档存在冲突，应以 ISO/IEC 11179 系列为准，并在变更记录中注明依据来源。
 
 ## 本地源文件
 
 这八个标准文档的 PDF 版本存放在本地目录：ISO/IEC 11179 标准原文目录。

 - **首选来源仍是 Milvus 向量检索**；仅当 Milvus 不可用，或经过 Milvus 查询后确认无法满足当前需求时，才回退到该目录下的 PDF 文件。
 - 如需向 Milvus 重新导入、补全缺失部分或校验向量来源，也优先使用该目录下的 PDF 文件。
 <!-- iso11179-mdr-requirements:end -->
