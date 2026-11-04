# Proposal: baseline-data-init-rebuild

## Why

现有基准数据体系存在结构性缺陷：WS 363/364 基准脚本基于旧 schema 生成且需适配器二次转换、SQLite/达梦方言脚本空缺、`db-baseline/` 被 .gitignore 忽略导致无法跨机复现、通用基准数据（语言标识）寄生在 Flyway 迁移与 Java 播种器中而非数据脚本。同时 SQLite 默认库文件位于程序目录，不符合用户数据应存用户目录的部署形态。在"存量数据库全部清空重建"的前提下，需要按「结构由代码内建、基准数据由脚本生成」的原则重建整套基准数据初始化体系。

## What Changes

- **重建基准数据生成管线**：从 34 份最新 WS/T 363/364-2023 重印版 PDF 重新解析，写入当前 schema 的全新金库；同码两义按 `scratch/restore-test/code_recoding_proposal.md` 本地更正（组 1–4，组 2 整块五码联动）；补齐注册/提交/主管机构、单一 `ws-` 命名空间、参考文档 URI 等现行模型语义；与 SQL Server 活库交叉核验（剔除已裁决的 315 空壳条目）。
- **基准数据三层脚本化**：通用基准（语言标识，必选，初始化自动导入）/ WS 标准数据本体 / WS 衍生数据（受管链、来源参考文档与 URI、条目-文档关联、指称定义来源与配对），按数据包组织并在包内按文件名字典序执行。
- **目录重构**：`db-baseline/` 调整为按主题分类的 `data/scripts/common/<方言>/` 与 `data/scripts/data-standards/ws/<方言>/`；WS 标准原文位于 `data/docs/data-standards/ws/WS363|WS364/`；`.gitignore` 收窄使脚本、README 与标准原文入库跟踪；`data/metadata-registry.db` 转为预置基准数据库文件；`data/README.md` 与各主题 README 说明用法；`data/init-baseline.bat` 提供一键初始化（按当前数据源配置选方言，配置缺失时无效退出）。
- **通用基准自动导入机制**：应用初始化链路（启动 presetAll / 向导 setup/abort / 恢复出厂）执行 `data/scripts/common/<方言>/00-common.sql`（幂等），语言播种从 Java/Flyway 寄生收敛为脚本单一事实源。
- **SQLite 默认路径迁移**：默认库文件改为 `~/.metadata-registry/data/metadata-registry.db`，profile 默认值与系统设置页默认值同步更新；不做旧库自动迁移（存量库清空前提）。**BREAKING**（对依赖旧默认路径的部署脚本/文档而言）。
- **达梦与 SQLite 的 WS 方言脚本**本次不产出（第一步只产 SQL Server），在 WS 主题 README 说明支持状态。

## Capabilities

### New Capabilities

- `baseline-data-scripts`: 基准数据脚本体系——三层数据内容契约（通用/WS 标准/衍生）、`data/scripts` 目录布局与方言组织、脚本幂等与执行顺序、一键初始化批处理行为、预置 SQLite 库的生成与用途、SQL Server 脚本的内容核对基准（数据元/值域/允许值/数据类型/机构/命名空间/参考文档）。

### Modified Capabilities

- `datasource-configuration`: SQLite 默认文件路径由程序目录 `data/` 改为用户目录 `~/.metadata-registry/data/`，路径仍可在初始化向导与系统设置中修改。
- `system-initialization`: 通用基准数据（语言标识）改由数据脚本在初始化链路自动导入，替代 Java 语言播种器的承载角色。

## Impact

- **代码**：`metadata-registry-web` seed 包（新增 `BaselineScriptPresetService`、`LanguagePresetSeeder` 退役）、`application*.yml`（4 处默认 URL）、前端 `DatasourceSettingsPage.vue` 默认值。
- **数据/脚本**：`scratch/baseline-rebuild/` 新管线脚本；`data/scripts/common/` 三方言通用脚本；`data/scripts/data-standards/ws/` 主题说明与 SQL Server 的 `10-main.sql`、`20-derived.sql`；`data/docs/data-standards/ws/` 标准原文；`db-baseline/` 删除。
- **配置**：`.gitignore`（`db-baseline/` 行删除、`data/` 收窄为 `data/*.db*`）。
- **文档**：`data/README.md` 新建、根 `README.md` 数据初始化章节同步。
- **验证依赖**：SQL Server 本机实例（空库端到端）、34 份标准 PDF（`WS 卫生信息标准原文目录\`）、活库交叉对账。
- **历史裁决沿用**：315 空壳不进基线、`CV04.30.002` 整域移除、KNOWN_GAP 空壳保留、标识冲突 SI 保留 NULL 归属。
