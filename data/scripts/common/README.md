# 通用基准数据

本数据包承载系统运行必需、且不依附于特定业务主题的基准数据。当前内容为 G20 语言标识及其中文语言名称、地区名称扩展。

## 数据内容

`00-common.sql` 包含：

- 22 条语言标识，写入 `mdr.language_identification`；
- 22 条语言中文名称和地区名称，写入扩展卫星表；
- 三种数据库方言使用相同的数据来源和条目 id。

脚本为纯 DML，按业务键或关联 id 判重，可重复执行。

## 方言目录

| 方言 | 脚本 |
| --- | --- |
| SQL Server | `sqlserver/00-common.sql` |
| SQLite | `sqlite/00-common.sql` |
| 达梦 DM8 | `dm/00-common.sql` |

各方言目录中的补充说明只记录方言差异及生成状态；数据包的整体说明以本文件为准。

## 导入方式

通用基准是**必选数据**。应用启动期静态预置、初始化向导 setup/abort 和恢复出厂流程会根据当前数据源方言自动执行对应脚本。

脚本目录由 `app.baseline-scripts-dir` 配置，默认为应用工作目录下的 `data/scripts`。目录或脚本缺失时应用记录警告并跳过，可停止应用后运行 `data/init-baseline.bat` 补入。

目标表结构必须先由应用通过 Hibernate + Flyway 建立。

## 再生成

- 数据源：`scratch/baseline-rebuild/golden-new.db`
- 生成器：`scratch/baseline-rebuild/gen_scripts_sqlserver.py`

生成器同时产出三种方言的 `00-common.sql`，并验证 SQLite 脚本重复执行后的语言数据数量与幂等性。
