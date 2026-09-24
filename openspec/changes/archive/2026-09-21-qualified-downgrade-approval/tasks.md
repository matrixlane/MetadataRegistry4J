# Tasks: qualified-downgrade-approval

## 1. 代码修改

- [x] 1.1 扩充 `RegistrationLifecycleService.DEFAULT_APPROVAL_TRANSITIONS`：新增 `QUALIFIED→RETIRED`、`STANDARD→RETIRED`、`PREFERRED_STANDARD→RETIRED`、`QUALIFIED→SUPERSEDED`、`STANDARD→SUPERSEDED`、`PREFERRED_STANDARD→SUPERSEDED`、`SUPERSEDED→RETIRED` 7 条默认审核迁移，并同步更新该常量与 `isDefaultApprovalTransition` 的 javadoc（依据 11179-6:2023 D.4.2/D.4.3/D.2.9）；验证：`metadata-registry-web` 模块编译通过
- [x] 1.2 更新 `RegistrationLifecycleService` 类级 javadoc 中"Recorded 及以上可降入 Superseded/Retired（Superseded 创建仅限 ADMIN）"及 `ROLE_PERMISSIONS` 相关注释，使描述与新审核语义一致（→SUPERSEDED 由 Registrar quorum 批准生效，角色矩阵仅约束静态底线路径）；验证：注释与代码行为一致（人工审查）

## 2. 测试对齐（TDD：先改断言后跑）

- [x] 2.1 更新 `RegistrationProcessServiceTest.ensureSeed_createsStandardRulesWith4ApprovalPoints`：默认审核点由 4 处改为 11 处，期望迁移对集合加入 7 条 Qualified+ 降级迁移；验证：该测试通过
- [x] 2.2 更新 `RegistrationLifecycleServiceTest`：`transition_recordedAndQualifiedToSuperseded_asAdmin_shouldSucceed` 拆分——`RECORDED→SUPERSEDED` 保持 ADMIN 直推成功；`QUALIFIED→SUPERSEDED` ADMIN 直推改为期望 1120；`transition_adminCanDoSuperseded_shouldSucceed`（STANDARD→SUPERSEDED）改为期望 1120 并更名；验证：相关测试通过
- [x] 2.3 扩充 M1 守卫测试：无归属注册机构条目 `QUALIFIED→RETIRED`、`SUPERSEDED→RETIRED` 直推拒绝 1120；`RECORDED→RETIRED`、`RECORDED→SUPERSEDED` 保持放行；验证：相关测试通过
- [x] 2.4 新增测试：`RegistrationProcessService.execute` 对无 RA 条目 `QUALIFIED→RETIRED` 拒绝 1120；`availableTransitions` 对 `QUALIFIED`/`SUPERSEDED` 源状态的降级目标标记 `requiresApproval=true` 且 `canExecute=false`；验证：新增测试通过

## 3. 验证与收尾

- [x] 3.1 运行 `mvn -pl metadata-registry-web test` 全量：1178 tests，39 errors——全部位于 11 个既有 SQLite 集成测试类（ApplicationContext 加载失败，`java.sql.SQLException: database in auto-commit mode`）；已在 clean HEAD worktree（不含本次任何改动）复跑代表类确认同款失败，属既有环境问题非本次引入；与本次变更相关的 3 个注册流程测试类 106 tests 0 failures；验证：对照证据 /tmp/baseline-wt.log
- [x] 3.2 运行 `openspec validate qualified-downgrade-approval` 通过；验证：命令输出 "Change 'qualified-downgrade-approval' is valid"
- [x] 3.3 提交前运行 GitNexus `detect_changes --scope all` 确认无 partial/truncated；验证：图谱分析输出（无 partial/truncated 标记；risk=critical 源自工作区既有未提交改动）
- [x] 3.4 更新 `.superpowers-memory/CURRENT_STATE.md` 并追加 session-journal 条目记录本次标准对齐决策；验证：文件已更新

## 4. 修订（2026-09-19 当日，用户裁定"与标准对齐"）

- [x] 4.1 依据 4.4 c)/d) 与 D.2.3 原文复核，从 `DEFAULT_APPROVAL_TRANSITIONS` 移除 `CANDIDATE→RECORDED`（治理加严撤销），更新 javadoc/注释（RegistrationLifecycleService、RegistrationProcessService）；验证：模块编译通过
- [x] 4.2 更新 `registration-process` spec delta 与 proposal/design：默认审核点 10 处；`CANDIDATE→RECORDED` 恢复直推并补 scenario；验证：`openspec validate qualified-downgrade-approval` 通过
- [x] 4.3 测试对齐：ensureSeed 审核点 10 处断言 + CANDIDATE→RECORDED 免审核播种断言；漂移归位测试改用 QUALIFIED→RETIRED；新增"旧库加严规则归位撤销"测试；CANDIDATE→RECORDED 相关 execute/availableTransitions/approve/M1 用例改为直推语义（approve 用例改挂 QUALIFIED→RETIRED 覆盖 D.4.2 批准路径）；验证：3 个注册流程测试类 107 tests 0 failures
