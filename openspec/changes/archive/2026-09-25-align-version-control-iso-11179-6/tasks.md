## 1. 后端代码实现

- [ ] 1.1 在 `VersionHistoryService.recordSnapshot(item, changeType, responseDto, kind)` 私有重载中加入 `RegistrationState.registration_status == INCOMPLETE` 早返回（null state 视为透传），并验证：`VersionHistoryServiceTest` 中所有以 `new DataElement()` 直接构造的现有 mock 用例继续通过
- [ ] 1.2 在 `VersionHistoryService.captureBaselineIfEnteringDraft` 加入同样的 INCOMPLETE 早返回（null state 视为透传），并验证：现有 `captureBaselineOpenEnteringDraft` 相关测试继续通过
- [ ] 1.3 将 `RegistrationStatus.CANDIDATE` 加入 `WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES` 常量集合，并验证：现有 `WorkspaceRollbackServiceIntegrationTest` 中 Recorded+ 回退用例继续通过
- [ ] 1.4 将 `RegistrationStatus.CANDIDATE` 加入 `WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES` 常量集合（与 1.3 同步），`canRollback` 自动随集合更新，并验证：现有 UI 标志逻辑测试继续通过

## 2. 测试新增与更新

- [ ] 2.1 在 `VersionHistoryServiceTest` 新增"mock DataElement 携带 RegistrationState(INCOMPLETE) 时 `recordSnapshot` 不写库"用例，并验证：新用例通过、文件整体 0 failures
- [ ] 2.2 在 `WorkspaceRollbackServiceIntegrationTest` 新增"Candidate + Draft + 有 BASELINE 时回退成功，adminStatus 清空，注册状态保持 CANDIDATE，写一条 ROLLED_BACK"用例，并验证：新用例通过、文件整体 0 failures
- [ ] 2.3 在 `WorkspaceRollbackServiceIntegrationTest` 新增"Candidate + Rejected + 无 BASELINE 时仅清 adminStatus，无 ROLLED_BACK 写入"用例，并验证：新用例通过、文件整体 0 failures
- [ ] 2.4 后端全量回归：`./mvnw -pl metadata-registry-web -am test -DfailIfNoTests=false`，并验证：0 failures

## 3. 数据迁移 V20260925001（三方言）

- [ ] 3.1 新增 `metadata-registry-web/src/main/resources/db/migration/sqlite/V20260925001__Purge_incomplete_version_snapshots.sql`：子查询 `administered_item` → `registration_state`，删 `version_history` 中 `registration_status = 'INCOMPLETE'` 的 item 行；并验证：脚本可被 Flyway 解析（语法 lint）
- [ ] 3.2 新增 `metadata-registry-web/src/main/resources/db/migration/sqlserver/V20260925001__Purge_incomplete_version_snapshots.sql`：JOIN 写法对应 SQL Server，并验证：脚本可被 Flyway 解析
- [ ] 3.3 新增 `metadata-registry-web/src/main/resources/db/migration/dm/V20260925001__Purge_incomplete_version_snapshots.sql`：达梦方言对应写法，并验证：脚本可被 Flyway 解析
- [ ] 3.4 在 `scratch/` 副本库模式应用迁移，记录受影响行数；并验证：受影响行数为 0（项目历史以"系统启动补建"模式启动，主流程没有遗留 Incomplete 的运行库），如非零须在归档前追加补充迁移任务

## 4. 文档同步

- [ ] 4.1 更新 `docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md` §4.6：把"仅注册状态 ∈ {Recorded, …} 时可回退（**用户规则：版本控制内**）"改为引用新 `version-control` spec 的"回退窗口"节，并验证：文档无遗留"用户规则"措辞

## 5. 端到端冒烟

- [ ] 5.1 在 `scratch/version-control-smoke/` 新增脚本覆盖完整路径：(1) 创建 DataElement 立即查 `version_history` 应为 0 行；(2) Request Candidate 后查仍为 0 行；(3) 编辑后查应 1 条 BASELINE + 1 条 DRAFT_EDIT；(4) 提交后再编辑应累计 5 条；(5) `WorkspaceService.canRollback` 在 Candidate+Draft 状态下返回 true；并验证：冒烟脚本全断言 PASS

## 6. 验证与提交

- [ ] 6.1 `openspec validate align-version-control-iso-11179-6 --strict` 通过，所有 artifact ready
- [ ] 6.2 后端 `./mvnw -pl metadata-registry-web -am test` 0 failures；前端 `cd metadata-registry-ui && npx vitest run && npm run build` 全绿
- [ ] 6.3 `bunx gitnexus@latest detect-changes --scope all --repo .` 无 partial/truncated
- [ ] 6.4 OpenSpec `archive` 流程将 `version-control` 与 `administrative-status` 改动合入主 spec