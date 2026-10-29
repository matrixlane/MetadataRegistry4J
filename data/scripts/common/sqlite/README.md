# SQLite 方言基准脚本

- `00-common.sql` 已就位：22 条内置语言标识 + 语言中文名称/地区名称扩展，与 `data/scripts/common/sqlserver/00-common.sql` 同源同 id（生成器 `scratch/baseline-rebuild/gen_scripts_sqlserver.py`，数据源 `scratch/baseline-rebuild/golden-new.db`）。
- WS 层脚本（10/20，标准数据本体与衍生数据）后续由 `data/scripts/data-standards/ws/sqlserver/` 版本转换产出，本迭代未提供。
