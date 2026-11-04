# 基准数据初始化体系重建 — 设计文档

日期：2026-10-23
状态：待确认
关联 OpenSpec 变更：`baseline-data-init-rebuild`（待创建）

## 1. 背景与目标

用户要求重建基准数据初始化脚本体系，前提假设：**不考虑任何存量数据库，所有现有数据库将被清空并按新脚本重新初始化**。六条需求：

1. 数据库结构由代码内建机制（Hibernate ddl-auto + Flyway 迁移）生成；**基准数据全部由数据初始化脚本生成**。
2. 第一步只产 SQL Server 数据脚本；SQLite / 达梦后续参考 SQL Server 脚本派生。
3. 基准数据分三层：
   - a. **通用基准数据**（语言标识、计量单位等）：必选，系统初始化时自动导入；
   - b. **卫生信息标准数据**（WS/T 363-2023、WS/T 364-2023）：根据最新标准文档重新解析生成，现有脚本内容需重新核对（所有元数据项目、数据类型、注册/提交/主管机构、命名空间等）；同码两义按 `scratch/restore-test/code_recoding_proposal.md` 修订后生成；
   - c. **衍生数据**：对卫生标准数据补充描述的管理元数据与操作元数据（条目来源标准文档及其 URI 等）。
4. SQL Server 脚本放入 `db-baseline` 对应目录，**`db-baseline` 目录更名为 `data/scripts`**。
5. SQLite 默认库文件改为 **用户目录 `~/.metadata-registry/data/metadata-registry.db`**（不再默认程序目录 `data/` 同名文件）；路径在系统初始化与系统设置中可修改；程序目录 `data/` 下的 db 文件转为**预置基准数据的数据库文件**，供用户拷贝使用，并在 `data/README` 中说明。
6. `data/` 下提供一个批处理文件，点击后按当前系统数据源配置选择对应方言，执行 `scripts/` 下全部基准脚本；配置缺失（未初始化）时不生效。

## 2. 现状盘点结论（勘察已完成）

### 2.1 代码内建种子（今天"谁在种数据"）

| 种子项 | 机制 | 触发时机 |
| --- | --- | --- |
| G20 语言 22 条 + 中文名 | Flyway `V20260919003`（228 行）+ `LanguagePresetSeeder` 跨目录重放 + `db/preset/*/localize-language-names.sql` | 迁移 / presetAll |
| `identifier_strategy_config` 全局默认行 | `StaticDataPresetService.ensureGlobalDefaultIdentifierStrategy()` 裸 JDBC | presetAll |
| 占位 admin | `ensurePlaceholderAdmin()` JPA | presetAll |
| 注册流转规则 20 条 + 10 审核点 | `RegistrationProcessService.ensureSeed()` 调和式 | presetAll / RA 创建 |
| Registry Context | `RegistryContextBootstrapService` | 向导 commit 后 / 启动补建 |
| 注册规范 / RA / Registrar / 命名空间 | 初始化向导 `InitWizardService.setup()` | 向导 |
| 计量单位 / 计量类 / 维度 | **无任何预置** | — |

### 2.2 WS 基准管线现状

- 管线全在 `scratch/ws-verify/`（PDF→CSV→金库→导出），金库 `data/metadata-registry.db` 为唯一事实源但**停留在旧 schema**（33 个已废弃分册命名空间、`scoped_identifier.namespace_id` 全 NULL、机构 0 行、`designation/definition.source_id` 全 NULL、配对 0 行、`slot.value` 内联）。
- SQL Server 活库已经 `restore_v2.py` P1–P6 全部处理（单一 `ws-` 命名空间、34 份参考文档 + 10,706 关联、RA「WJW」、3 机构、受管链闭环 10,708、source_id 18,225/2,624、配对 2,199），仅多 315 个已裁决不进基线的空壳条目。
- `db-baseline/sqlite/`、`db-baseline/dm/` 为空；`db-baseline/` 被 `.gitignore:20` 整体忽略（与 README「本目录被 git 跟踪」自相矛盾， backlog BL-2026-10-09-06）。
- 权威标准 PDF：`WS 卫生信息标准原文目录\{363 卫生信息数据元目录\WST 363-2023, 364 卫生信息数据元值域代码\WST 364-2023}\` 共 34 份（2026-10-12 换入的最新重印版，含前言勘误）。
- 同码两义（`code_recoding_proposal.md`，对照最新重印版复核）：组 1 `CV03.00.203` 4003→**4004**；组 2 `CV04.30.002` 左侧腕关节整块 5 码 `BP0K…`→**`BP0M…`**；组 3 `BQ2FZUZZZ`→**`BQ2F10ZZZ`**；组 4 `CV05.01.016` 1701→**1702**；组 5 纯净水 0205 标准已修复，无需操作。

### 2.3 SQLite 路径现状

- 实际默认值：`application-personal.yml:8` → `${DB_URL:jdbc:sqlite:./data/metadata-registry.db}`（相对 JVM 工作目录）。
- 数据源设置服务/页面已存在（`DatasourceConfigService` + `DatasourceSettingsPage.vue` + `GET/PUT /api/v1/datasource/config`），本地配置落 `~/.metadata-registry/application-local.yml` 且优先级高于 profile 默认。
- 初始化向导第 2 步默认值**已是** `~/.metadata-registry/data/metadata-registry.db`（前端 `InitWizard.vue:550`），profile 默认值未跟上。
- `~/.metadata-registry/` 约定已存在（application-local.yml、ai-assist.yml 等 3 处代码引用）。

## 3. 关键决策

### 决策 1 — 数据生成路径：PDF 重解析入新金库（推荐）

| 方案 | 说明 | 取舍 |
| --- | --- | --- |
| **A（推荐）** | 用既有抽取器从 34 份最新 PDF 重新解析 → 写入**当前 schema 的全新金库**（应用先行建表）→ 管线内固化同码两义裁决 → 补齐机构/命名空间/参考文档/受管链 → 与活库交叉核验 → 导出 SQL Server 脚本 | 满足"根据最新标准文档解析"的出处要求；消除金库旧 schema 落差；产出可重复管线；活库作独立对照降低重解析风险 |
| B | 以活库为源导出修补（剔除 315 空壳） | 活库内容本就源于 PDF 且经多轮人工核验，最快；但不满足"从标准文档解析生成脚本"的出处要求，管线不可重复 |
| C | 旧金库经适配器升级 | 旧金库缺 P2/P4/P5 全部成果，适配器只解决列层不解决语义层，落差最大 |

选 A。既有抽取器（`extract_ws363.py`/`extract_ws364.py`，处理跨页断行、前言误命中等已知坑）与验证器（`verify.py`）原样复用；入库段（`build_associations.py` 写旧 schema）重写为当前 schema 版本。

### 决策 2 — 脚本组织：三层分文件，字典序执行（推荐）

每方言目录下三个文件，与需求 3a/3b/3c 一一对应：

```
data/scripts/sqlserver/
  00-common.sql              # 通用基准：语言标识 22 条（含中文名）
  10-ws363-364.sql           # WS 标准数据本体：DE/VD/CD/PV/VM/concept/DT/命名空间/机构/标识
  20-ws363-364-derived.sql   # 衍生：受管链+注册状态、来源参考文档+URI、条目-文档关联、
                             #       designation/definition 来源、指称-定义配对、溯源 origin
```

- 批处理与应用初始化均按字典序执行；`00-common` 同时是"系统初始化自动导入"的载体。
- 备选（单一大文件）否决：无法区分必选/可选，也无法被初始化单独引用 common 层。

### 决策 3 — 通用基准自动导入：应用执行 `data/scripts/<方言>/00-common.sql`（推荐）

- 新增 `BaselineScriptPresetService`（或改造 `LanguagePresetSeeder` 为通用脚本执行器）：在 `presetAll()` 链路（启动 `@Order(10)`、向导 setup/abort、恢复出厂）按当前方言执行 `data/scripts/<dialect>/00-common.sql`。
- 脚本目录解析：默认 `<工作目录>/data/scripts`，可用 `app.baseline-scripts-dir` 覆盖；`start.bat` 已 `cd /d "%~dp0"`，相对路径可靠。目录缺失时 **WARN + 跳过**（不阻断启动），由批处理兜底。
- 语言播种从「Flyway 迁移寄生 + SCRIPT_TEMPLATES 跨目录引用」收敛到脚本文件单一事实源；`V20260919003` 是历史迁移不可删（既有库重放链），但 FRESH 库 baseline-version=20261009012 本就跳过它，无冲突。
- 脚本必须幂等（`NOT EXISTS` 守卫），重复执行零副作用。

### 决策 4 — SQLite 默认路径：改默认值，不做旧库迁移

- 默认值改为 `${DB_URL:jdbc:sqlite:${user.home}/.metadata-registry/data/metadata-registry.db}`（嵌套占位符需实测，兜底方案为 `PathExpansion` 预处理或直接在 `DatasourceConfigService` 常量层处理）。
- 同步点：web 模块 `application-personal.yml:8`、web `application.yml:11`、仓库根 `application.yml:9`、仓库根 `application-local.yml:3`、`DatasourceSettingsPage.vue:134` 默认值。（向导默认值已是新路径，无需改。）
- **不做旧库自动迁移**：用户明确存量库全部清空重建；`FreshDatabaseInitializer` 按 URL 解析路径、自动建父目录的逻辑不变，天然兼容新默认。
- 程序目录 `data/metadata-registry.db` 转为**预置库**（见决策 9）。

### 决策 5 — 同码两义：按 proposal 对组 1–4 本地更正，组 2 采纳整块 5 码联动

- 用户指令"根据 code_recoding_proposal.md 中的解决方案对数据进行修订后再生成数据脚本"，proposal §4 组 2 建议整块联动（`BP0KZZZZ1/A/7/2/M1` → `BP0MZZZZ1/A/7/2/M1`），采纳。
- 组 5 已由标准修复，仅断言核验。
- 更正在管线内固化为独立 adjudication 步骤（参照 `apply_local_adjudication.py` 语义重写），脚本内以 `origin` 标记可追溯。

### 决策 6 — 历史裁决沿用

- 315 个活库独有空壳条目**不进基线**（2026-10-22 用户裁决，`dropped-live-only-items-20261022.md`）。
- `CV04.30.002` 整域移除 + `DE04.30.036.00.value_domain_id` 置空，沿用既有裁决。
- `CV08.50.102` 保留 OBSOLETE；5 个 KNOWN_GAP 空壳 CV 保留。
- 标识冲突的 `scoped_identifier` 归属沿用 restore_v2 P2 规则（冲突保留 NULL namespace）。

### 决策 7 — 溯源表达：不复活 `ws_import_run` 表

正式 schema 已无该表（`V20261009009` 删除，且其非 11179 标准表）。溯源信息经两类标准机制表达：① `registered_item.origin = 'WS 363-2023/WS 364-2023 基准导入'`；② 来源参考文档（§9.5.2 reference_document + 关联）。脚本头注记载生成日期/标准版本/裁决清单。

### 决策 8 — SQLite / 达梦脚本本迭代不产出

按需求 2，本次只产 SQL Server 脚本；SQLite/达梦后续以 SQL Server 脚本为基准转换。`data/scripts/sqlite/`、`data/scripts/dm/` 建目录留占位 README。

### 决策 9 — 目录重构与 git 跟踪

```
data/
  README.md                  # 体系说明：脚本分层、预置库用法、批处理用法
  init-baseline.bat          # 一键初始化批处理（需求 6）
  metadata-registry.db       # 预置基准数据的 SQLite 库（git 忽略，随产物分发）
  scripts/
    sqlserver/  00-common.sql / 10-ws363-364.sql / 20-ws363-364-derived.sql
    sqlite/     README.md（占位：由 sqlserver 版转换，待产出）
    dm/         README.md（占位：同上）
```

- `.gitignore` 修订：删除 `db-baseline/` 忽略行与旧注释；`data/` 整目录忽略改为精确忽略 `data/*.db*`（脚本、README、批处理全部入库跟踪）。同步解决 backlog BL-2026-10-09-06。
- `db-baseline/` 目录删除（内容已被新体系取代；git 中本无跟踪，无历史损失）。
- **预置库来源**：新金库本身就是「当前 schema + 全量基准」的 SQLite 库，构建完成后清除临时表/产物即得预置库，拷贝为 `data/metadata-registry.db`。

### 决策 10 — 批处理 `init-baseline.bat` 行为

1. 解析数据源配置：读 `~/.metadata-registry/application-local.yml` 的 `spring.datasource.url`（含 `${DB_URL:...}` 默认值展开）；缺失/无 url → 提示「系统尚未初始化数据源配置」并退出（需求 6 的"无效"语义）。
2. 按 URL 前缀选方言目录：`jdbc:sqlserver:` → `scripts\sqlserver`（调 `sqlcmd`；`integratedSecurity=true` 用 `-E`，否则从配置取用户名/口令）；`jdbc:sqlite:` → `scripts\sqlite`（`sqlite3` 缺失时回退内嵌 Python 调用）；`jdbc:dm:` → 提示达梦脚本待产出。
3. 前置检查：目标库 schema 已建立（SQL Server 查 `mdr` schema 关键表；SQLite 查文件存在且有用户表），否则提示"先启动一次应用建表"。
4. 按字典序执行该方言目录全部 `.sql`，任一失败即中止并报告。
5. 全程中文提示，窗口暂停等待按键（双击场景）。

### 决策 11 — WS 数据的机构与归属建模（内容核对基准）

以活库 P1–P6 终态为参照、以 PDF 扉页为权威核对来源：

- 机构（`organization` + `organization_name`）：国家卫生健康标准委员会卫生健康信息标准专业委员会（RA 主体）、国家卫生健康委统计信息中心（主管/提交方）等，以各分册扉页「提出/归口/发布」信息核对。
- 注册机构：`registration_authority`（international_code_designator + organization_identifier='WJW'，参照活库既有行）+ 单一命名空间 `ws-`。
- 命名空间：废弃 33 个分册命名空间，单一 `ws-` 命名空间承载全部 WS 标识符（restore_v2 P2 终态语义）。
- 每个条目唯一来源文档（2026-10-13 前缀分流裁决）：`DE*`→363.x；`VD*`/`CD-VD*`→363.x；`CV*`/`CD-CV*`→364.x；`DT-*`→363.1。
- 参考文档 URI：以 PDF 权威目录的**实际文件名**（「卫生健康信息数据元目录 第N部分：….pdf」命名）重新生成，修复 `find_pdf()` 正则与目录命名不匹配的历史缺陷。
- 受管链：`registration_status=PREFERRED_STANDARD`、`effective_date=2024-04-01`、`previous_state_id=NULL`、`registration_authority_id=NULL`（运行时归属不伪造）、`origin` 标记，集合式幂等。

## 4. 生成管线（目标态）

```
标准原文目录\…\WST 36{3,4}-2023\*.pdf（34 份最新重印版）
  │ extract_ws363.py / extract_ws364.py（复用，PyMuPDF）
  ▼
ws363_pdf.csv / ws364_pdf.csv
  │ build_golden_v3.py（新写：当前 schema；单一 ws- 命名空间；机构；
  │   slot_value 集合表；ext 卫星表；受管链；参考文档+URI；来源关联；配对）
  │ adjudication 步骤内置（组 1–4 更正 + CV04.30.002 整域移除 + KNOWN_GAP 保留）
  ▼
scratch/baseline-rebuild/golden.db（新金库，当前 schema）
  │ 三重核验：① 行数闸口（2212 DE / 2160 VD / 4171±更正 PV / 8 DT / 34 文档）
  │           ② verify.py PDF↔DB 内容比对
  │           ③ 与活库对账（剔除 315 空壳后应零差异）
  ▼
gen_scripts_sqlserver.py（新写：直接从当前 schema 金库导出，无需适配器）
  ▼
data/scripts/sqlserver/00-common.sql / 10-ws363-364.sql / 20-ws363-364-derived.sql
  │
  ▼ 拷贝新金库（清理临时产物）→ data/metadata-registry.db（预置库）
```

## 5. 代码改动点清单

| 改动 | 文件 |
| --- | --- |
| SQLite 默认 URL | web `application-personal.yml:8`、web `application.yml:11`、根 `application.yml:9`、根 `application-local.yml:3` |
| 设置页默认路径 | `metadata-registry-ui/src/views/system/settings/DatasourceSettingsPage.vue:134` |
| 通用基准脚本执行器 | 新增 `BaselineScriptPresetService`（web seed 包），接入 `StaticDataPresetService.presetAll()`；`LanguagePresetSeeder` 退役（语言改由 00-common.sql 承载） |
| `.gitignore` | `db-baseline/` 行删除；`data/` 改为 `data/*.db*` 精确忽略 |
| 目录迁移 | `db-baseline/` 删除；`data/scripts/{sqlserver,sqlite,dm}/` 建立 |
| README | `data/README.md` 新建；根 `README.md` 同步（数据初始化章节） |
| 文档同步 | `docs/architecture/ARCHITECTURE.md` 若涉模块描述则最小同步 |

## 6. 验证方案

1. **管线核验**：行数闸口 + `verify.py` 内容比对 + 活库对账（剔 315 空壳）三方一致。
2. **语义不变式**：受管链闭环 remaining=0；每条目唯一来源文档；8 数据类型名称/注记 = WS/T 363.1 表 3 原文；组 1–4 更正落地断言；组 5 断言纯净水=0205。
3. **SQL Server 端到端**：空库 → 应用启动建表 → 执行三脚本 → 应用再启动 → 数据元/值域列表/参考文档页可见、注册状态=PREFERRED_STANDARD、计数与金库一致。
4. **通用基准自动导入**：全新 SQLite 库启动后语言 22 条在位（含「简体中文」）；向导 setup/abort/出厂恢复三路径幂等。
5. **SQLite 路径**：无配置全新启动 → 库文件落 `~/.metadata-registry/data/metadata-registry.db`；设置页改路径 → 重启生效。
6. **批处理**：scratch 沙箱内分别模拟 sqlserver 配置/缺失配置/sqlite 配置三场景。
7. 守护测试：`AdministeredItemClosureConformanceTest` 绿；web 全量测试零新增回归；`detect_changes` 无 partial/truncated。

## 7. 开放问题（不阻塞，实现期按记录值执行）

| # | 问题 | 记录值（默认） |
| --- | --- | --- |
| 1 | 计量单位通用基准无权威目录来源 | 本次 common 仅含语言标识；脚本留扩展位，待用户提供计量单位目录来源后补充 |
| 2 | 组 2 五码联动 | 采纳（用户指令引用 proposal 解决方案） |
| 3 | 通用基准执行时机 | 每次启动调和式执行（幂等），与 presetAll 既有语义一致 |
| 4 | 306 条标识冲突 SI 的命名空间归属 | 沿用 P2 规则（保留 NULL），与活库一致 |
| 5 | 预置 SQLite 库是否随 git 分发 | 不分发（.db 忽略），由构建产物/发布渠道携带；README 说明如何自行生成 |
