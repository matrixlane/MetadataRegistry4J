# SQLite 方言基准脚本

- `00-common.sql` 已就位：22 条内置语言标识 + 语言中文名称/地区名称扩展，与 `data/scripts/common/sqlserver/00-common.sql` 同源同 id。
- WS 层脚本已就位：`data/scripts/data-standards/ws/sqlite/10-main.sql`（标准数据本体）与 `20-derived.sql`（衍生数据），与 sqlserver 版同源同行序（生成器 `scratch/baseline-rebuild/gen_scripts_sqlserver.py`，数据源 `scratch/baseline-rebuild/golden-new.db`）。
