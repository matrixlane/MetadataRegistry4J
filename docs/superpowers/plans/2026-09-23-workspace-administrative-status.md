# 实施计划：工作空间与管理状态（2026-09-23）

对应 OpenSpec 变更 `add-workspace-administrative-status`（tasks.md 7 组 27 项）。设计契约：`docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md`。

## 执行顺序（TDD，每步先红后绿）

1. **T1 枚举与持久化**（tasks 1.1–1.2）：common-facilities 新增 `AdministrativeStatus`（16 常量）+ `AdministrativeStatusConverter`（autoApply，仿 MappingDegreeConverter）；`RegistrationState.administrativeStatus` 换型。零迁移。
2. **T2 管理状态核心服务**（tasks 2.1–2.4）：`AdministrativeStatusService`（落点规则：清空约定 + INCOMPLETE→INITIAL）；`ItemAdministrationService` 编辑→Draft 钩子；`RegistrationLifecycleService` 迁移清空；`RegistrationProcessService.approve` 清空。
3. **T3 版本快照与回退**（tasks 3.1–3.4）：`VersionHistoryService` 全量 DTO 快照 + meta.kind；BASELINE 捕获；回退服务；三方言索引迁移 V20260924001。
4. **T4 审批流扩展**（tasks 4.1–4.3）：reject 端点、request-qualification 端点、Submitter 白名单（INCOMPLETE→CANDIDATE）。
5. **T5 工作空间后端**（tasks 5.1–5.5）：WorkspaceController/Service/DTO（summary/items 三视图）、submit、request-candidate、rollback、SecurityConfig/ApiCodes。
6. **T6 前端**（tasks 6.1–6.5）：菜单/路由/i18n/e2e、workspaceApi、WorkspaceView、SubmissionRecordPicker、标准文案。
7. **T7 验证**（tasks 7.1–7.5）：全量测试、构建、scratch 副本库冒烟（自建 Admin 测试账户）、detect-changes、文档同步。

## 关键约束

- 编辑前已完成 GitNexus 影响分析（本计划同目录留档）；提交前必须 `detect_changes --scope all` 无 partial/truncated。
- SQLite 64 表 join 上限：工作空间查询沿用 type() 探测 + getReference 代理手法。
- 管理状态写入只经 `AdministrativeStatusService`；Converter 未知值响亮失败。
- 登录验证自建 Admin 测试账户，禁用应急码（AGENTS.md 事故记录）。
- 不执行 git 提交（除非用户要求）。

## 验证基线

- 后端：`mvn -pl metadata-registry-web -am test`（含 common-facilities）；openapi.json 再生成。
- 前端：`pnpm build`、`pnpm vitest`。
- 冒烟：`scratch/workspace-smoke/` 副本库模式（参照 scratch/registry-context-smoke）。
