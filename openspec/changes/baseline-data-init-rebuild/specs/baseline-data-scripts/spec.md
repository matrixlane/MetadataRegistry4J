## Purpose

基准数据脚本体系定义「表结构由代码内建、基准数据由脚本生成」原则下的数据层契约：基准数据的三层内容（通用基准 / 卫生信息标准数据 / 衍生数据）、`data/scripts` 目录布局、脚本幂等与执行顺序、一键初始化批处理、预置 SQLite 库的用途与生成方式。

## ADDED Requirements

### Requirement: 基准数据脚本分层与目录布局

基准数据脚本 MUST 以可独立说明和维护的数据包为主分类、数据库方言为次分类，存放于程序目录 `data/scripts/`。通用基准 MUST 位于 `common/<方言>/00-common.sql`；卫生信息标准数据 MUST 位于 `data-standards/ws/<方言>/`，其中 SQL Server 脚本为 `10-main.sql`（标准数据本体）与 `20-derived.sql`（衍生数据）。方言目录为 `sqlserver`、`sqlite`、`dm`。每个数据包 MUST 提供主题级 README；WS 的 SQLite/达梦脚本产出前，MUST 在 WS 主题 README 说明其支持状态及将以 SQL Server 版为基准转换生成。脚本、说明文件、标准原文与批处理 MUST 被 git 跟踪；数据库文件 MUST NOT 被 git 跟踪。

#### Scenario: 目录结构完整

- **WHEN** 检出仓库后查看 `data/scripts/` 目录
- **THEN** 存在 `common` 与 `data-standards/ws` 两个数据包；`common` 下含三方言 `00-common.sql`，`data-standards/ws` 下含主题 README 与 SQL Server 的 `10-main.sql`、`20-derived.sql`

#### Scenario: 脚本入库跟踪

- **WHEN** 执行 `git check-ignore` 检查 `data/scripts/common/sqlserver/00-common.sql`
- **THEN** 该文件未被忽略，可正常签入；而 `data/metadata-registry.db` 被忽略

### Requirement: 脚本幂等与执行安全

所有基准数据脚本 MUST 幂等：全量重跑不新增重复行、不产生主键冲突、可中途失败重跑补齐。脚本 MUST 采用集合式操作与存在性守卫（如 `NOT EXISTS`），MUST NOT 硬编码行数作为执行前提。每个脚本 MUST 在事务内执行，任一语句失败整体回滚。

#### Scenario: 重复执行零副作用

- **WHEN** 对已完成初始化的库再次执行同一方言全部三层脚本
- **THEN** 各表行数与首次执行后完全一致，无错误

#### Scenario: 中途失败重跑补齐

- **WHEN** 脚本执行中途失败，修正环境后重新执行
- **THEN** 缺失数据被补齐且已存在数据不受影响

### Requirement: 通用基准数据内容

通用基准脚本 MUST 承载系统运转必需的通用基准数据：G20 语言标识记录（22 种语言，含 zh-CN、en-US）及其中文语言名称（zh-CN 为「简体中文」）。通用基准数据 MUST 标记为系统初始化时自动导入（见 `system-initialization` 能力）。

#### Scenario: 通用基准导入后语言完整

- **WHEN** 在已建表的空库上执行 `00-common` 脚本
- **THEN** 语言标识 22 条在位，zh-CN 语言名称为「简体中文」，重复执行行数不变

### Requirement: 卫生信息标准数据内容核对基准

WS 标准数据脚本 MUST 以 34 份最新重印版 WS/T 363-2023 / WS/T 364-2023 标准文档为唯一内容来源重新生成，并包含：全部数据元及其名称/定义、CV 值域与数据元自有值域、允许值全链路（PV/PVM/VM/concept/PVS 对称表）、8 个数据类型（S1/S2/S3/N/D/DT/T/L，名称与注记忠实还原 WS/T 363.1—2023 §6.2.4 表 3 原文）、命名空间、注册机构/提交机构/主管机构等机构记录。同码两义 MUST 按既定更正方案修订：组 1 `CV03.00.203` 加速器→`4004`；组 2 `CV04.30.002` 左侧腕关节整块五码 `BP0K…`→`BP0M…`；组 3 `BQ2FZUZZZ`→`BQ2F10ZZZ`；组 4 `CV05.01.016` 多趾—右侧→`1702`；组 5 纯净水=`0205`（标准已修复，导入结果须断言）。历史裁决 MUST 沿用：`CV04.30.002` 整域移除且其唯一引用数据元的值域关联置空、5 个 KNOWN_GAP 空壳 CV 保留、`CV08.50.102` 保留 OBSOLETE 状态、活库独有 315 空壳条目不进入基准。

#### Scenario: 数据类型名称忠实标准原文

- **WHEN** 查询导入后的 8 个数据类型
- **THEN** 名称与注记与 WS/T 363.1—2023 §6.2.4 表 3 原文一致（如 S2 为「S2 字符型（枚举型）」），无自行发挥的映射注记

#### Scenario: 同码两义更正落地

- **WHEN** 查询 `CV03.00.203` 的允许值
- **THEN** 「加速器产生的电离辐射」码值为 `4004`，码值 `4003` 在表内唯一（X 射线装置）

### Requirement: 衍生数据内容

衍生数据脚本 MUST 承载对 WS 标准数据的补充描述：① 受管链实例（registered_item / administered_item / registration_state），注册状态为 `PREFERRED_STANDARD`、生效日期 `2024-04-01`、`origin` 标记可追溯、不伪造状态历史链与运行时归属；② 34 份来源参考文档（reference_document），其 URI MUST 为仓库内相对路径（`data/docs/data-standards/ws/WS363|WS364/` 下的标准文档，见「标准文档随仓库分发」）；③ 条目与来源文档的唯一来源关联，关联规则为：`DE*` 与 `VD*`/`CD-VD*` → 所属部分的 WS/T 363.x；`CV*`/`CD-CV*` → 所属部分的 WS/T 364.x；`DT-*` → WS/T 363.1；每个条目 SHALL 恰好一份来源文档；④ 指称/定义的来源关联与指称-定义配对。衍生数据导入后受管链 MUST 闭环：所有已标识条目均落到 Administered_Item 或 Attached_Item（ISO/IEC 11179-3:2023 §9.4.1.2），不停留在 Identified_Item 中间层。

#### Scenario: 受管链闭环

- **WHEN** 执行缺环校验查询（已标识条目左连 registered_item/administered_item/attached_item）
- **THEN** 缺环条目数为 0，全部 WS 条目注册状态为 `PREFERRED_STANDARD`

#### Scenario: 唯一来源关联

- **WHEN** 统计每条目的来源文档关联数
- **THEN** 每个 WS 条目恰好关联 1 份参考文档，且前缀分流规则抽查全部命中（如 DE02 类目条目来源为 WS/T 363.3，CV03 类目值域来源为 WS/T 364.5）

#### Scenario: 参考文档 URI 有效

- **WHEN** 读取 34 份参考文档的 URI
- **THEN** 每份 URI 为仓库内相对路径（`data/docs/data-standards/ws/WS363|WS364/` 下），按仓库根解析后文件实际存在

### Requirement: 标准文档随仓库分发

34 份 WS/T 363-2023 / WS/T 364-2023 标准原文 PDF（2026-10-12 重印版）MUST 随仓库分发：存放于 `data/docs/data-standards/ws/WS363/`（17 份，数据元目录）与 `data/docs/data-standards/ws/WS364/`（17 份，值域代码），文件名与权威来源目录一致，内容与权威来源逐字节一致。基准数据的参考文档 URI MUST 为指向这些文件的仓库根相对路径（POSIX 分隔符、不含盘符），使其他用户克隆仓库后即可直接经 URI 打开标准原文，无需任何本机绝对路径依赖。

#### Scenario: 标准文档在位且与权威源一致

- **WHEN** 检出仓库后查看 `data/docs/data-standards/ws/` 并对照权威来源目录
- **THEN** WS363/WS364 两目录各 17 份 PDF 在位，逐份内容哈希一致

#### Scenario: 克隆后 URI 直接可开

- **WHEN** 在他机克隆仓库并导入基准数据后读取任一参考文档 URI
- **THEN** URI 不含盘符、按仓库根解析后文件存在，可直接打开对应标准 PDF

### Requirement: 一键初始化批处理

程序目录 `data/` 下 MUST 提供批处理文件，双击执行时：读取当前系统数据源配置（用户目录配置文件优先）；识别配置的数据库方言；按 `common` → `data-standards/ws` 的数据包顺序收集该方言的可用 `.sql` 脚本，包内按文件名字典序执行；任一脚本失败 MUST 中止并报告。数据源配置缺失（系统尚未初始化）时 MUST 明确提示并退出，不产生任何副作用。目标库 schema 未建立时 MUST 提示先启动应用建表后退出。SQLite 执行环境缺少 `sqlite3` 命令时 SHALL 回退使用 Python 执行。

#### Scenario: 配置缺失时无效退出

- **WHEN** 系统从未初始化（无用户目录数据源配置），双击批处理
- **THEN** 提示系统尚未初始化数据源配置并退出，数据库无任何变更

#### Scenario: 按当前配置选方言执行

- **WHEN** 当前配置为 SQL Server 数据源，双击批处理
- **THEN** 依次执行 `scripts/common/sqlserver/00-common.sql`、`scripts/data-standards/ws/sqlserver/10-main.sql` 与 `20-derived.sql`，完成后基准数据可用

#### Scenario: 恢复出厂后经批处理重新导入基准

- **GIVEN** 已执行恢复出厂设置（全部数据含基准被清除，通用基准经初始化链路自动重放）
- **WHEN** 用户双击批处理
- **THEN** WS 基准数据（`10-main` 与 `20-derived`）重新导入，脚本幂等不产生重复行

### Requirement: 预置基准数据库

程序目录 `data/metadata-registry.db` SHALL 作为预置了全部基准数据的 SQLite 数据库文件分发，用户可将其拷贝为实际使用的库文件直接使用。`data/README.md` MUST 说明：预置库的用途与拷贝用法、脚本体系分层、批处理用法、以及 SQLite 默认库文件位置（用户目录）与修改方式。预置库文件 MUST NOT 被 git 跟踪。

#### Scenario: 拷贝预置库即用

- **WHEN** 用户将程序目录 `data/metadata-registry.db` 拷贝到实际 SQLite 库文件位置后启动应用
- **THEN** 应用直接读取其中全部基准数据，无需再执行脚本
