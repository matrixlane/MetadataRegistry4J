# Design: baseline-data-init-rebuild

## Context

完整现状勘察与逐项依据见 `docs/superpowers/specs/2026-10-23-baseline-data-init-rebuild-design.md`（§2 现状盘点）。关键约束：

- 前提：存量数据库全部清空重建，不考虑任何存量库兼容/迁移。
- 表结构由 Hibernate ddl-auto + Flyway 迁移生成；本变更只产**数据脚本**。
- 金库（`data/metadata-registry.db`）内容正确但模型落后；SQL Server 活库已过 restore_v2 P1–P6 全流程（模型正确），两库可用性见勘察报告。
- 权威标准源：`WS 卫生信息标准原文目录\` 下 34 份最新重印版 PDF。
- 既有裁决：同码两义 proposal、315 空壳不进基线、`CV04.30.002` 整域移除、前缀分流唯一来源。

## Goals / Non-Goals

**Goals:**

- 三层基准脚本（三方言 `data/scripts/common/<方言>/00-common.sql`，以及 SQL Server `data/scripts/data-standards/ws/sqlserver/10-main.sql`、`20-derived.sql`），从最新标准 PDF 可重复生成。
- 通用基准经应用初始化链路自动导入（脚本为单一事实源）。
- SQLite 默认库路径迁至 `~/.metadata-registry/data/metadata-registry.db`。
- `data/init-baseline.bat` 一键初始化；`data/README.md` 说明；预置 SQLite 库可供拷贝使用。
- 脚本与文档入库跟踪（解决 backlog BL-2026-10-09-06）。

**Non-Goals:**

- SQLite / 达梦方言数据脚本（后续以 SQL Server 版为基准派生，目录留占位）。
- 计量单位/计量类/维度通用基准内容（无权威目录来源，留扩展位）。
- 旧库自动迁移、存量库数据订正。
- `ws_import_run` 溯源表（正式 schema 已删除，溯源经 `origin` 标记 + 参考文档表达）。

## Decisions

### D1 管线：PDF 重解析 → 新金库（当前 schema）→ 导出

用既有抽取器（`extract_ws363.py`/`extract_ws364.py`，PyMuPDF，已处理跨页断行/前言误命中）重解析 34 份 PDF；新写入库器把数据直接写入**当前实体模型 schema 的全新 SQLite 金库**（应用先行建表）；同码两义裁决固化为管线内独立步骤。备选「活库导出修补」（出处不可重复）与「旧金库适配升级」（缺 P2/P4/P5 语义层）均否决。活库降级为**交叉核验对照**（剔除 315 空壳后应零差异）。

### D2 三层脚本文件 + 数据包顺序执行

与需求 3a/3b/3c 一一对应：`common` 数据包的 `00-common`（必选，初始化自动导入）、`data-standards/ws` 数据包的 `10-main`（标准数据本体）与 `20-derived`（衍生管理/操作元数据）。批处理按 `common` → `data-standards/ws` 的顺序收集当前方言脚本，包内按文件名字典序执行；应用初始化只自动执行 `common`。单一大文件方案否决（无法区分必选/可选）。

### D3 通用基准自动导入 = 应用执行 `00-common.sql`

新增 `BaselineScriptPresetService`：在 `presetAll()` 链路（启动 @Order(10)、向导 setup/abort、恢复出厂）按当前方言执行 `data/scripts/common/<dialect>/00-common.sql`（幂等 NOT EXISTS 守卫）；脚本根目录经 `app.baseline-scripts-dir` 可配，默认 `<工作目录>/data/scripts`；目录缺失 WARN 跳过不阻断启动。`LanguagePresetSeeder` 退役，语言播种收敛为脚本单一事实源（`V20260919003` 属历史迁移保留不动，FRESH 库基线版本已跳过它）。

### D4 SQLite 默认路径改默认值，不做旧库迁移

`${DB_URL:jdbc:sqlite:${user.home}/.metadata-registry/data/metadata-registry.db}`（嵌套占位符实测兜底）。同步 web `application-personal.yml` / web `application.yml` / 根 `application.yml` / 根 `application-local.yml` / `DatasourceSettingsPage.vue` 默认值。向导默认值已是新路径。`FreshDatabaseInitializer` 按 URL 解析路径不变，天然兼容。

### D5 同码两义按 proposal 更正（组 2 整块联动）

组 1 `4003→4004`；组 2 左侧腕关节整块 5 码 `BP0K…→BP0M…`；组 3 `BQ2FZUZZZ→BQ2F10ZZZ`；组 4 `1701→1702`；组 5 标准已修复仅断言。用户指令引用 proposal 解决方案，采纳其整块联动建议。

### D6 批处理行为

解析 `~/.metadata-registry/application-local.yml` 的 `spring.datasource.url` → 识别方言 → 前置检查 schema 已建 → 按 `common` → `data-standards/ws` 的包顺序执行当前方言全部可用 `.sql`（包内字典序）→ 失败即中止。配置缺失提示"系统尚未初始化数据源配置"退出（需求 6"无效"语义）。SQLite 侧 `sqlite3` 缺失回退 Python。

### D7 目录与 git 跟踪

`db-baseline/` 删除；脚本按 `data/scripts/common/<方言>/` 与 `data/scripts/data-standards/ws/<方言>/` 组织，主题级 README 与数据包同目录；34 份标准原文移至 `data/docs/data-standards/ws/WS363|WS364/`。`.gitignore` 删 `db-baseline/` 行、`data/` 收窄为 `data/*.db*`；脚本、说明与标准原文入库，预置库不入 git（构建/发布渠道携带），`data/README.md` 说明生成方式。

## Risks / Trade-offs

- [PDF 重解析回归风险（跨页表格/前言误命中等旧坑）] → 复用已维护的抽取器；三重核验（行数闸口 + verify.py 内容比对 + 活库对账）把关。
- [新金库写入器重写范围大（受管链/卫星表/集合表）] → 以活库 P1–P6 终态为语义参照物逐表对齐；`AdministeredItemClosureConformanceTest` 不变式纳入脚本验收。
- [`00-common.sql` 与应用启动顺序耦合（表须先存在）] → 执行点保持在 presetAll（Hibernate/Flyway 之后）；脚本幂等，失败 WARN 不阻断启动，批处理兜底。
- [批处理解析 yml 的健壮性（`${DB_URL:...}` 占位、注释、引号）] → 只支持项目自产的两种已知形态并明确报错；实测覆盖三场景。
- [嵌套占位符 `${user.home}` 在 Spring 默认值中的展开] → 实现期实测；不展开则在 `DatasourceConfigService.resolveSqliteFilePath` 层统一预处理 `~` 与 `${user.home}`。
- [根 `application.yml`（file:./）与 classpath 同名文件优先级] → 两处同步改，验证以实测生效值为准。

## Migration Plan

1. 管线与脚本生成（scratch/baseline-rebuild/）。
2. SQL Server 空库端到端验证（应用建表 → 三脚本 → 应用可用）。
3. 代码改动（yml 默认值、seed 执行器、前端默认值）+ `.gitignore` + 目录迁移。
4. 预置库产出（新金库清理临时产物后拷贝为 `data/metadata-registry.db`）。
5. 文档（data/README.md、根 README 同步）。
6. 回滚：git 还原代码改动；数据层无存量库迁移负担（前提即清空重建）。

## Open Questions

| # | 问题 | 记录值 |
| --- | --- | --- |
| 1 | 计量单位通用基准目录来源 | 本次不预置，留扩展位 |
| 2 | 306 条标识冲突 SI 归属 | 沿用 P2 规则保留 NULL |
| 3 | 预置库分发渠道 | 不入 git，README 说明自行生成/拷贝 |
