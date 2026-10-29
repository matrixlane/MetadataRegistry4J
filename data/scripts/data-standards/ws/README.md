# 卫生信息标准数据（WS）

本数据包承载 WS/T 363-2023《卫生健康信息数据元目录》与 WS/T 364-2023《卫生健康信息数据元值域代码》的基准数据及其来源文档关联。当前收录标准为 2026-10-12 重印版，2023-10-07 发布、2024-04-01 实施。

## 数据包组成

| 脚本 | 内容 | 依赖 |
| --- | --- | --- |
| `sqlserver/10-main.sql` | 标准数据本体：数据元、值域、允许值、数据类型、命名空间，以及注册、提交和主管机构等 | 先建立应用表结构，并具备通用基准 |
| `sqlserver/20-derived.sql` | 衍生管理与操作元数据：受管链与注册状态、来源参考文档及 URI、条目与文档关联、指称和定义来源及配对 | 先执行 `10-main.sql` |

通用基准位于 `data/scripts/common/`，不属于 WS 主题，但 WS 数据导入前必须具备。

## 方言支持状态

当前仅提供 SQL Server 的 `10-main.sql` 与 `20-derived.sql`。SQLite、达梦 DM8 的 WS 层脚本尚未提供，后续以 SQL Server 版本为基准转换生成；通用基准不受此限制。

## 导入方式

1. 先启动应用，由 Hibernate + Flyway 建立目标库表结构；
2. 停止应用；
3. 运行 `data/init-baseline.bat`，批处理按当前数据源配置选择方言，并按通用基准、WS 数据本体、WS 衍生数据的顺序执行可用脚本。

脚本采用存在性守卫，可重复执行；中途失败修正后可重跑补齐。手工执行 SQL Server 脚本时，也必须保持 `00-common.sql` → `10-main.sql` → `20-derived.sql` 的顺序。

## 标准原文与参考文档 URI

34 份标准原文随仓库分发：

- `data/docs/data-standards/ws/WS363/`：WS/T 363-2023 第 1–17 部分，共 17 份；
- `data/docs/data-standards/ws/WS364/`：WS/T 364-2023 第 1–17 部分，共 17 份。

`20-derived.sql` 中的 34 条 `reference_document.uri` 使用仓库根相对 POSIX 路径，例如：

```text
data/docs/data-standards/ws/WS363/卫生健康信息数据元目录 第1部分：总则.pdf
```

后端按应用工作目录解析相对路径，前端通过参考文档内容端点打开本地文件。URI 不含本机盘符，克隆仓库并保留目录结构后即可使用。

## 数据验证

导入或重新生成后至少核验：

- WS363、WS364 各有 17 份原文，文件名与内容未因目录迁移改变；
- 34 条参考文档 URI 均指向实际存在的文件；
- 已标识条目的受管链闭环，不停留在 `Identified_Item` 中间层；
- 每个 WS 条目恰好关联一份来源文档；
- 全量重跑不产生重复数据。

## 再生成

- 权威内容来源：上述 34 份标准原文；
- 金库变换：`scratch/baseline-rebuild/transform_golden_v3.py`；
- 当前金库：`scratch/baseline-rebuild/golden-new.db`；
- 脚本导出：`scratch/baseline-rebuild/gen_scripts_sqlserver.py`。

生成管线使用同一原文目录生成 URI 并断言文件存在，避免脚本路径与随包文档位置漂移。
