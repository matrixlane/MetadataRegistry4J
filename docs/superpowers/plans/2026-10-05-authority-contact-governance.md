# 实施计划：authority-contact-governance（2026-10-05）

- 关联：OpenSpec 变更 `openspec/changes/authority-contact-governance/`、设计 `docs/superpowers/specs/2026-10-05-authority-contact-governance-design.md`
- 实施环境：隔离 worktree `.worktrees/authority-contact-governance`（分支 `feat/authority-contact-governance`，基于 master HEAD 3ee1822；主树有大量并行未提交改动，故隔离实施）
- 方法：TDD（先写失败测试再实现）；每个任务以测试/命令通过为完成标准（tasks.md 逐项）

## 影响分析结论（编辑前 GitNexus impact，索引落后 HEAD 14 提交，风险均 LOW）

| 目标 | 直接调用方 | 风险 |
| --- | --- | --- |
| RegistrationAuthorityService | RegistrationAuthorityController | LOW |
| ContactService.createMyContact / deleteMyContact | MyContactsController | LOW |
| WorkspaceService.validateSubmissionInfo / ItemAdministrationService.resolveContactAndOrganization | WorkspaceController（submit/requestCandidate 流程） | LOW |
| InitWizardService.setup | InitWizardController | LOW |

## 波次安排

| 波次 | 内容（tasks.md 编号） | 并行度 | 说明 |
| --- | --- | --- | --- |
| W1 | 1.1–1.3 实体+三方言迁移+启动回填 | 1 代理 | 基础：is_primary 列 |
| W2 | 2.1–2.5 机构治理后端 ∥ 3.1–3.3 初始化顺序 | 2 代理 | 文件不相交，可并行 |
| W3 | 4.1–4.4 身份增删版本/角色分支 | 1 代理 | 触 i18n |
| W4 | 5.1–5.4 + 6.1–6.2 提交约束与机构页前端 | 1 代理 | 触 i18n/types/resources，避免与 W3 冲突故串行 |
| W5 | 7.1–7.3 全量验证与冒烟 | 主代理 | 全量测试、双版本冒烟、validate、detect_changes、openapi |

## 关键实现约束（对实施代理的统一要求）

1. 单事务级联删除严禁 REQUIRES_NEW 嵌套（SQLite 单连接池死联先例）。
2. 三方言迁移同版本号 `V20261005001__Registration_authority_primary_flag`；SQLite/SQL Server 用 ALTER TABLE 加列；达梦按等效语法。
3. 提交校验两处重复逻辑（`WorkspaceService.validateSubmissionInfo`、`ItemAdministrationService.resolveContactAndOrganization`）必须同步修改、行为一致。
4. `is_primary` 不进入任何 Response DTO / 前端。
5. 后端验证命令：`./mvnw -pl metadata-registry-web -am test`（目标类先行 `-Dtest=...`）；前端：`npm ci && npx vitest run && npm run build`（在 worktree 的 metadata-registry-ui 下）。
6. 既有平台约束：SQLite 64 表 join 上限（跨方言代码避免无界 join）；`contact.organization` 单值。
7. 每波完成后汇报：修改文件清单、测试结果、遗留问题。

## 验证基线（W5）

- web 全量测试零新增失败（既有失败集合以 worktree 干净 HEAD 为基线）
- vitest 全绿、前端 build 通过
- 冒烟（scratch 副本库）：个人版建第二机构/自动注册人员/非 primary 级联删/primary 拒删；团队版非管理员建注册人员被拒/删 primary 级联；提交无身份提示+422
- `openspec validate --all`、`gitnexus detect_changes` 无 partial/truncated
