# 实现计划：Submitter 联系人身份体系（2026-09-22）

依据：`openspec/changes/add-submitter-contact-identity/`（proposal/design/specs/tasks）、`docs/superpowers/specs/2026-09-22-submitter-contact-identity-design.md`。

## 阶段 1：数据模型与迁移（tasks 1.1–1.2）

- `ContactAccountBinding` 加 `isDefault`（`@Column(name = "is_default", nullable = false)`）。
- 迁移三方言：`metadata-registry-web/src/main/resources/db/migration/{sqlite,sqlserver,dm}/V20260922001__Contact_account_binding_default_flag.sql`（`ALTER TABLE contact_account_binding ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT 0`；dm 按其方言惯例核对既有迁移写法）。
- Repository 方法：`findByAccountId`、`findByAccountIdAndIsDefaultTrue`、`findAllByAccountIdOrderBy...`。
- 编辑前按 AGENTS.md 对 `ContactAccountBinding` 及 Repository 做 gitnexus impact 分析。

## 阶段 2：Submitter 权限通道（tasks 2.1–2.5，TDD）

- `JwtService` 注入 `ContactAccountBindingRepository`（或经 `AuthService` 判定的布尔），生成 `submitter` claim。
- `JwtAuthenticationFilter`：`submitter=true` → `ROLE_SUBMITTER`。
- `SecurityConfig`：写操作 `hasAnyRole('SUPER_ADMIN','REGISTRAR','SUBMITTER')`。
- `ItemAdministrationService.resolveOwnershipForCreate`：Registrar 优先 → Submitter 归属系统唯一 RA（`registrationAuthorityRepository` 查全部，size==1 取用，否则既有异常）。
- `ActiveRegistrarService.assertCanModifyItem`：无活动 Registrar 且为 Submitter 且条目归属 RA == 系统唯一 RA → 放行。Aspect 调用点同步确认无需重复判断。
- 状态推进不动，补 Submitter 推进被拒的回归测试。

## 阶段 3：SubmissionRecord 补链（tasks 3.1–3.2，TDD）

- `ContactService.resolveDefaultSubmissionContext(accountId)` → `{contact, organization}`。
- Item Request DTO 加 `submission` 块；各 Item Service create/update 生成 `SubmissionRecord`（同事务）。改动面广（所有 Item 类型），先在 `DataElementService` 落地并测，再推广到其余 Item Service（优先列表页在用的类型）。

## 阶段 4：我的联系人身份接口（tasks 4.1–4.4，TDD）

- 新 `MyContactsController` + `MyContactsService`：列表/CRUD/set-default（默认唯一性事务、删默认递补、Registrar 行 409、删空失权）。
- `AuthService.login`/`me` 响应加 `hasContact`/`hasRegistrar`/`profilePrompt`。
- 重生成 openapi.json。

## 阶段 5：前端（tasks 5.1–5.4）

- `auth` store 存新标志；`AppLayout` 首登引导弹窗（一次性、可跳过）。
- `MyProfile.vue` 联系人身份管理区。
- `ItemFormDialog` 提交信息区（联系人/组织选择 + 预填）。
- i18n zh/en；build + vitest。

## 阶段 6：验证（tasks 6.1–6.3）

- web 全量测试、vitest、scratch 副本库冒烟脚本、detect-changes。
- 登录一律用专用 Admin 测试账户（AGENTS.md 规则，禁应急码）。

## 工作方式

- 每处后端编辑前跑 gitnexus impact（AGENTS.md 强制）。
- 提交前 `detect-changes --scope all` 确认无 partial/truncated。
