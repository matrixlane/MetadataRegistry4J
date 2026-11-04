# 实现计划：baseline-data-init-rebuild

日期：2026-10-23
依据：`openspec/changes/baseline-data-init-rebuild/`（proposal / design / specs / tasks）、`docs/superpowers/specs/2026-10-23-baseline-data-init-rebuild-design.md`

## 执行策略

- 主树直接实施（不建 worktree）：管线产物在 `scratch/`（git 忽略）、代码改动面小（4 个 yml + 1 个 Vue + seed 包 + .gitignore），DB 验证依赖本机绝对路径资源。
- 管线复用最大化：抽取器原样复用；现有金库内容本就是 2026-10-12 同批最新 PDF 的解析结果——以「重跑抽取器 + 新旧 CSV 逐字节 diff」作为重新核对证据，diff 为零则免重导，直接进 schema 升级变换。
- 每个阶段先验证再推进；SQL Server 端到端用 scratch 空库（`metadata_registry_baseline_test`）+ 旁路端口 8082。

## 阶段

1. **管线核验**：重跑 extract_ws363/364 → 与既有 CSV diff；diff 为零即以现有金库为内容事实源进入变换，非零则重导入。
2. **金库升级变换** `scratch/baseline-rebuild/transform_golden_v3.py`：旧 schema 金库副本 → 当前 schema 新金库（应用建表的空库容器）：ext 卫星表拆分、slot_value 集合化、单一 ws- 命名空间 + SI 归属、机构/RA、34 参考文档（URI 修正为权威目录实际文件名）、来源关联、source_id、配对、受管链闭环。语义参照 restore_v2 P1–P6 终态与活库。
3. **三重核验**：PDF↔CSV↔库内容比对、活库对账（剔 315 空壳）、不变式断言 → verification-report.md。
4. **脚本导出** `gen_scripts_sqlserver.py`：新金库 → `data/scripts/sqlserver/` 三层脚本（含 00-common 语言基准，内容源自 V20260919003 固定 UUID 集，幂等守卫改写）。
5. **SQL Server 端到端**：scratch 空库建表 → 三脚本 → 应用可用 + 计数一致 + 闭环不变式。
6. **代码改动**：4 处 yml 默认 URL、DatasourceSettingsPage 默认值、`BaselineScriptPresetService`（TDD）接入 presetAll、`LanguagePresetSeeder` 退役。
7. **目录/批处理/文档**：`data/scripts` 布局、.gitignore 收窄、删 db-baseline、init-baseline.bat 三场景沙箱实测、data/README.md、根 README 同步。
8. **预置库**：新金库清理临时产物 → `data/metadata-registry.db`，沙箱拷贝即用验证。
9. **回归**：web 全量、vitest+build、detect_changes、openspec validate --all。

## 验证命令（关键）

- 抽取 diff：`python extract_ws363.py && python extract_ws364.py` 后 `fc /b` 对比既有 CSV
- 不变式：缺环查询 remaining=0；唯一来源 COUNT=1 分组无异常；组 1–5 裁决断言
- 端到端：sqlcmd `-f 65001 -b -I` 执行；应用 8082 旁路 readiness + API 计数
- 回归：`mvnw -pl metadata-registry-web -am test`、`vitest run`、`node .gitnexus/run.cjs detect-changes --scope all --repo .`
