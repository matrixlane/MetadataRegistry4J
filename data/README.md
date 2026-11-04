# data/ — 基准数据与数据库文件

本目录承载元数据注册系统的**基准数据脚本体系**与**预置数据库文件**。系统原则：**数据库表结构由应用内建机制生成（Hibernate + Flyway），基准数据由本目录的 SQL 脚本生成**。

## 目录内容

```
data/
  README.md                  本文件
  init-baseline.bat          一键初始化批处理（双击运行）
  metadata-registry.db       预置基准数据的 SQLite 数据库文件（见下文）
  scripts/
    common/                  通用基准数据包（必选）
      README.md                通用数据说明
      sqlserver/00-common.sql  SQL Server 方言
      sqlite/00-common.sql     SQLite 方言
      dm/00-common.sql         达梦方言
    data-standards/          数据标准主题
      ws/                    卫生信息标准数据包
        README.md              WS 数据说明
        sqlserver/
          10-main.sql          标准数据本体（WS/T 363/364-2023）
          20-derived.sql       受管链/注册状态/来源参考文档与 URI/关联
        sqlite/
          10-main.sql          同上，SQLite 方言
          20-derived.sql       同上，SQLite 方言
    governance/              注册治理数据包
      README.md              治理数据说明
      sqlserver/30-governance.sql  注册规范/默认注册机构/CTX 命名空间/注册人员
      sqlite/30-governance.sql     同上，SQLite 方言
  docs/
    data-standards/
      ws/
        WS363/                 卫生健康信息数据元目录原文（17 份）
        WS364/                 卫生健康信息数据元值域代码原文（17 份）
```

## 基准数据分层

| 层 | 内容 | 导入方式 |
| --- | --- | --- |
| `00-common` | 通用基准（G20 语言标识 22 条及中文名称） | **必选**：系统初始化链路（启动/向导/恢复出厂）自动导入 |
| `10-main` | WS/T 363-2023、WS/T 364-2023 标准数据本体（数据元、值域、允许值、数据类型、命名空间、注册/提交/主管机构等） | 可选：批处理或手工执行 |
| `20-derived` | 衍生管理/操作元数据（受管链与注册状态、来源参考文档及 URI、条目-文档关联、指称/定义来源、指称-定义配对） | 可选：随 10 之后执行 |
| `30-governance` | 注册治理基准（注册规范、默认注册机构、CTX 命名空间、默认注册人员/角色/联系人） | 可选：须在 20 之后执行（依赖 Registry Context）；缺此层注册模块不可用 |

脚本均**幂等**（存在性守卫，重复执行不产生重复数据，中途失败重跑可补齐）。批处理按 `common` → `data-standards/ws` → `governance` 的数据包顺序执行，每个包内按文件名字典序执行。

## 一键初始化（init-baseline.bat）

双击 `init-baseline.bat`：

1. 读取当前系统数据源配置（用户目录 `.metadata-registry\application-local.yml`）；
2. 按实际配置的数据库方言，依次收集 `common/<方言>/` 与 `data-standards/ws/<方言>/` 下的可用脚本并执行；
3. **配置缺失（系统尚未初始化）时不生效**，提示后退出，不产生任何副作用；
4. 目标库表结构未建立（应用从未启动过）时提示先启动应用并退出。

**执行前请停止应用运行。** SQL Server 依赖 `sqlcmd`；SQLite 优先用 `sqlite3`，缺失时自动回退 Python。

## 初始化向导 / 恢复出厂与基准数据

- **初始化向导**（setup/abort 的清理）保留基准数据：内容链/受管链/衍生表整表不动，治理表仅清除非基准行（基准的 `ws-` 命名空间、WJW 标委会/统计信息中心机构、WJW 注册机构为保留常量）。预置库上跑向导不会摧毁基准。
- **恢复出厂设置会清除全部数据（含基准数据）**；之后自动重新导入通用基准（`00-common`）。如需 WS 基准数据（10/20 层），恢复出厂后请重新执行 `init-baseline.bat`（脚本幂等，重复导入无副作用）。

## 预置数据库文件（metadata-registry.db）

本目录下的 `metadata-registry.db` 是**已预置全部基准数据**的 SQLite 数据库文件。当系统使用 SQLite 时（个人版默认），可将该文件直接拷贝到实际库文件位置使用，拷贝后即含全部基准数据，无需再执行脚本：

- 默认实际位置：用户目录 `.metadata-registry\data\metadata-registry.db`（如 `C:\Users\<你>\.metadata-registry\data\metadata-registry.db`）；
- 该默认路径可在**系统初始化向导**与**系统设置 → 数据源**中修改；若已将库文件配置到其他位置，拷贝到相应位置即可。

> 注意：程序目录 `data\` 下的这个 db 文件只是预置副本，系统默认**不直接读写**它（自本版本起 SQLite 默认库文件位于用户目录）。

## 标准文档随包

34 份 WS/T 363-2023 / WS/T 364-2023 标准原文 PDF（2026-10-12 重印版）随仓库分发：

```
data/docs/data-standards/ws/WS363/   卫生健康信息数据元目录 第1–17部分（17 份）
data/docs/data-standards/ws/WS364/   卫生健康信息数据元值域代码 第1–17部分（17 份）
```

基准数据中 34 条参考文档记录（reference_document）的 URI 为**仓库根相对路径**（如 `data/docs/data-standards/ws/WS363/卫生健康信息数据元目录 第1部分：总则.pdf`，POSIX 分隔符、不含盘符）；后端按应用工作目录相对解析，前端经后端代理打开。克隆仓库后参考文档链接即可直接打开标准原文，无需另行配置文档目录。

## 手工执行（替代批处理）

```bat
rem SQL Server（Windows 集成认证示例）
sqlcmd -E -S localhost,1433 -d metadata_registry -l 30 -t 0 -f 65001 -b -I -i data\scripts\common\sqlserver\00-common.sql
sqlcmd -E -S localhost,1433 -d metadata_registry -l 30 -t 0 -f 65001 -b -I -i data\scripts\data-standards\ws\sqlserver\10-main.sql
sqlcmd -E -S localhost,1433 -d metadata_registry -l 30 -t 0 -f 65001 -b -I -i data\scripts\data-standards\ws\sqlserver\20-derived.sql
sqlcmd -E -S localhost,1433 -d metadata_registry -l 30 -t 0 -f 65001 -b -I -i data\scripts\governance\sqlserver\30-governance.sql

rem SQLite
sqlite3 "<实际库文件路径>" ".read data/scripts/common/sqlite/00-common.sql"
```

手工执行也必须保持 `00` → `10` → `20` → `30` 的顺序（30 依赖 20 产出的 Registry Context）。

## 脚本再生成

基准脚本由生成管线产出（权威来源：34 份 WS/T 363/364-2023 最新重印版标准文档）：

- 变换：`scratch/baseline-rebuild/transform_golden_v3.py`（旧金库 → 当前 schema 新金库）
- 治理包注入：`scratch/baseline-rebuild/seed_governance.py`（线上治理配置 → 金库，一次性）
- 导出：`scratch/baseline-rebuild/gen_scripts_sqlserver.py`（新金库 → 本目录脚本）
- 核验：`scratch/baseline-rebuild/verification-report.md`

生成脚本目录 `scratch/` 不入库；本目录脚本、主题说明、标准原文与本文档入库跟踪，数据库文件（`*.db`）不入库。
