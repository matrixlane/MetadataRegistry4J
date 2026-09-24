# 实现计划：initial-status-incomplete

日期：2026-09-21
关联设计：`docs/superpowers/specs/2026-09-21-initial-status-incomplete-design.md`
OpenSpec：`openspec/changes/initial-status-incomplete/`（tasks.md 已确认）
前置：GitNexus 影响分析（`ensureAdministeredItem` 上游消费者）

## 改动范围

| 文件 | 改动 |
|---|---|
| `metadata-registry-web/.../common/ApiCodes.java` | 新增 `INVALID_INITIAL_STATUS = 1125` |
| `metadata-registry-web/.../service/ItemAdministrationService.java` | 默认初始状态 RECORDED → INCOMPLETE（:419）；新建显式状态白名单校验 |
| `metadata-registry-web/.../dto/common/ItemRegistrationRequest.java` | `registrationStatus` 注释同步 |
| `metadata-registry-web/src/test/.../ItemAdministrationServiceTest.java` | M2 断言更新、QUALIFIED 直建改期望 1125、新增 5 个用例 |
| 其他测试类 | 逐一核对修正"创建即 RECORDED"假设 |

## 步骤（TDD）

1. **影响分析**：`impact ensureAdministeredItem --direction upstream`（MCP），确认调用方集合与风险等级；HIGH/CRITICAL 则先警告用户。
2. **红灯**：更新/新增 `ItemAdministrationServiceTest` 用例（tasks 2.1–2.3），跑定向测试确认按新语义失败。
3. **绿灯**：实施 1.1–1.3；定向测试通过。
4. **核对**：tasks 2.4 列出的测试类与前端 e2e/spec 中"创建即 RECORDED"假设，逐一修正。
5. **全量验证**：`mvn -pl metadata-registry-web test`（既有 SQLite 集成失败需 clean-HEAD worktree 对照）；前端 `npm run build` + 相关 spec。
6. **手工冒烟**：本地后端实例（独立端口 + scratch 新库，不打扰 8081 在跑实例）：新建数据元不带状态 → INCOMPLETE；显式 APPLICATION → APPLICATION；显式 RECORDED → 1125；推进 INCOMPLETE→CANDIDATE→RECORDED 走通。
7. **收尾**：`openspec validate`、文档同步检索、`detect_changes --scope all`、记忆库更新。

## 回滚

改动集中且无量纲变更：单文件级 `git checkout` 即可回滚；无数据迁移。
