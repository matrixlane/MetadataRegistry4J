# 实施计划:mdr-standard-compliance-cleanup（2026-10-08）

> 约束输入：`openspec/changes/mdr-standard-compliance-cleanup/tasks.md`（用户已确认）+ 已确认设计稿。
> 实施环境：`.worktrees/mdr-standard-cleanup`（master HEAD 拉出，隔离主树 118 项并行 WIP）。

## 顺序

1. **worktree 基线**：建 worktree + 分支；记录 web 全量测试基线失败集（跑 1 次，~数分钟）。
2. **JPA 层（2.1–2.5）**：9 处更名注解 → 删字段 → ValueMeaning 映射 → orm xml/测试 → 原生 SQL 引用点。编译驱动，逐文件改。
3. **服务/DTO 层（3.1–3.3）**：TDD——先改 `ItemAdministrationServiceTest`/`WorkspaceServiceTest` 等去除 source 断言（红），再改实现（绿）；acceptability DTO/透出点改经 context 条目。
4. **前端（4.1–4.3）**：types → 组件 → vitest/vue-tsc/build。
5. **迁移（5.1–5.4）**：先写 SQL Server 版并真实库预演（副本/直接应用均可回滚设计），再派生 SQLite/达梦版；迁移重放测试。
6. **验证（6.1–6.4）**：全量测试 → 真实库应用+重启冒烟（含 FlywayHistoryRelocator 闭环）→ SQLite scratch → openapi/validate/detect_changes。
7. **收尾（7.1–7.3）**：memory 更新、提交、continue-review → continue-archive。

## TDD 要点

- `recordSubmission` 签名变更：测试先行（去 source 参数/断言）。
- acceptability：`DesignationServiceTest`/`DefinitionServiceTest`/`ItemAdministrationServiceTest` 中断言主记录 acceptability 的用例先行改造。
- 迁移：沿用 `RegistrationAuthorityPrimaryFlagMigrationTest` 真实 Flyway 重放模式。

## 风险与对策

- 索引落后 HEAD 18 提交：影响分析以文本检索为准（AGENTS.md 对 UNKNOWN 的确认路径）。
- 并行 WIP 同文件：提交前 `git log`/diff 对比主树，合并时按文件仲裁。
- 真实库迁移不可逆：所有步骤幂等守卫；应用前备份（`BACKUP DATABASE` 或库级快照）。
