# 2026-10-29 · CV04.30.002 恢复 + 全册前言对账审计

## 起因

用户给出 PDF↔DB 差异清单 4 项（CV04.30.002 库缺；CV07.10.001/003/004 为 364.13 前言修订说明中的改号前旧编号），指令"根据标准文档的最新修改更新本地 SQL Server 数据和数据脚本"；随后扩大为"按所有标准文档前言部分的内容修订逐一复核并修订"。

## 结论

- 34 册前言所述修订在库内均已落地；唯一需要的修订是 CV04.30.002 整域恢复（撤销 2026-10-12 组2/3 整域移除裁决，新决策 decision-2026-10-29-cv0430002-restore）。
- 审计报告：`docs/audits/ws-preface-reconciliation-audit-2026-10-29.md`；缺陷登记册：`docs/audits/ws-standard-defects-register-2026-10-29.md`；处置对照：`docs/audits/ws-standard-defects-solutions-2026-10-29.md`。

## 恢复链路（精确逆操作，可复用）

1. 恢复 `scratch/ws-verify/metadata_registry-pre-errata-20261012.bak` 到临时库 `mdr_preerrata_tmp`。
2. `scratch/cv0430002-restore/extract_graph.py` 按 ws-adjudication-sqlserver.sql 阶段3 的 @it 定义抽全行图 → graph.json（38 表；id 集合与裁决断言逐项吻合：479×4、481、1918）。
3. `load_golden.py` 写入 golden-new.db（UUID 大端 BLOB、epoch 毫秒；先备份 golden-new.db.bak-cv0430002-restore）。
4. **坑**：pre-errata 行的 stewardship_record_id / submission_record_id 是旧版 id，与 golden 现行单例行不同 → 20-derived 首次应用 FK 冲突失败。修复：golden 内 481+481 行 UPDATE 指向现行 id 后重新生成脚本。
5. 重新生成 10-main/20-derived（DECLARED_COUNTS 与头注同步）；应用活库（应用前备份 metadata_registry-pre-cv0430002-restore-20261029.bak）；restore_de_link.sql 重挂 DE04.30.036.00。
6. 验证：verify_live.sql（规模/标识链/四件套/重码/CV07/受管链）+ 与 golden 按 id 对账 + API 三层冒烟（value-domains / value-meanings / data-elements 均通过）。

## 经验

- SQL Server 活库 2026-09 导入时受 `<域>.<码值>` 标识符唯一索引限制，同码双义每组仅 1 行入库（479/481）；恢复后亦然——与既有"金库双行完整、活库单行的存量局限"记录一致。
- 恢复类任务先做 COPY_ONLY 备份；活库应用时确认应用未运行。
- UUID 对账务必统一形态（SQL Server 字符串带连字符 vs BLOB hex）再比较，否则全量误报。
- PDF 前言修订说明可能出现与正文用字不一致（家庭畜禽栏 vs 家庭禽畜栏）、"删除"指移出本分册（分娩日期时间归第 3 部分）——核对应以正文现行文本为准。

## 遗留

- 旧模型金库 `data/metadata-registry.db` 仍维持 CV04.30.002 移除态（用户本次只要求 SQL Server + 脚本）；如需对齐可同法恢复。
- 临时库 mdr_preerrata_tmp 已删除；graph.json 与脚本保留在 scratch/cv0430002-restore/ 作审计轨迹。

## 追加 · 2026-10-29 晚间 · 空壳 5 域整域删除

用户新裁决（decision-2026-10-29-no-empty-shells）：枚举值域不允许 0 码值空壳。已将 KNOWN_GAP 5 壳（CV04.50.021/CV06.00.239/CV08.10.101/CV08.50.004/CV08.50.102）自 golden、重生成脚本、活库三处整域删除；5 个引用 DE 的 value_domain_id 置 NULL。坑：SQLite 无外键强制，golden 首轮回删漏了 5 行 concept（值域条目同时是 concept 行），活库执行时被 FK 拦截才发现——已补删并同步重生成脚本。教训：跨库删除同一行图时，以活库 FK 依赖清单（ws-adjudication-sqlserver.sql 阶段 3.3 的完整阶梯）为唯一准绳，勿凭记忆裁剪。删除/回滚脚本与备份在 scratch/cv0430002-restore/（metadata_registry-pre-shell-purge-20261029.bak）。

## 追加 · 2026-10-29 深夜 · 悬空引用重挂（用户裁决）

用户裁决两项：DE08.50.040.00（输血品种代码）重挂 CV04.50.020（363.16 引用 .021 系 .020 笔误）；DE06.00.339.00（入院途径代码）采用 364.17 的 CV09.00.403。已按组1/组4 先例落地：golden 重挂→重生成脚本→活库补丁 relink_dangling_refs.sql（守卫含幽灵编号 0 行、码值数 28/4 核验，幂等）→API 实测通过。新决策 decision-2026-10-29-dangling-relink；备份 metadata_registry-pre-relink-20261029.bak。处置对照文档 C2/D3 状态更新完毕。另：核实修正了一处旧表述——CV03.00.404 并非 315 空壳成员，其正确实表 CV03.00.309 早已解析在位（DE03.00.002.00 现挂该表，4 值），属引用笔误而非空壳问题。

用户问 data/metadata-registry.db 是否空库——实为 golden-new 的 10-24 字节拷贝（112 表全量基准，md5 验证同表集合），但停留在 10-24 口径（缺 CV04.30.002、5 空壳在位、未重挂）。用户选选项 2"按 golden 重新导出"：旧文件备份 data/metadata-registry.db.bak-preregen-20261029，现行 golden-new.db 直接拷贝覆盖（md5 一致）。验证：规模 2212/2156/2157/4650/11185/20486 全对、CV04.30.002 链 481、空壳 0、0-PV 枚举值域 0、四处数据元链接齐全。注意区分：仓库预置库是分发模板；个人版运行时实际使用用户目录 ~/.metadata-registry/data/ 下的拷贝，本机 8081 跑 SQL Server 不受影响。

## 追加 · 脚本一致性端到端验证（深夜）

用户问"数据脚本是否已按审计完成一致性验证"。补做全新空库回放：mdr_scriptcheck 建 schema（期间踩坑三处：新架构空库不自动建表→关引导相；个人 profile 缺 SQL Server 方言/schema 配置→按 team yml 补齐；基线化跳过遗留迁移致 4 张 ext 卫星表缺失→按活库 DDL 补建）→ 重放 00/10/20 → 验收全过（17 表计数==golden、审计 7 组断言、闭环=0）→ 验证库已清理。结论：脚本≡golden≡活库≡审计结论四方一致。验收脚本 scratch/cv0430002-restore/acceptance_scriptcheck.sql。
