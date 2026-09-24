# Tasks: add-submitter-contact-identity

## 1. 数据模型与迁移

- [x] 1.1 `ContactAccountBinding` 增加 `isDefault` 字段，编写 SQLite / SQL Server / 达梦三方言迁移（`V20260922xxx`，`DEFAULT 0`），并在 SQLite 副本库与 SQL Server 实测迁移通过
- [x] 1.2 `ContactAccountBindingRepository` 增加按账号查询默认绑定、清除同账号默认标记的方法，编写单测验证查询语义

## 2. Submitter 能力与权限通道（后端）

- [x] 2.1 `JwtService` 生成 `submitter` claim（账号绑定 Contact 数 ≥1），`JwtAuthenticationFilter` 映射 `ROLE_SUBMITTER`；单测覆盖有/无 Contact、Registrar 兼任三种账号
- [x] 2.2 `SecurityConfig` 写操作放行 `SUPER_ADMIN/REGISTRAR/SUBMITTER`；集成测试验证：无身份账号 POST/PUT/DELETE 403、GET 200，Submitter 写操作放行
- [x] 2.3 `ItemAdministrationService.resolveOwnershipForCreate` 增加 Submitter 通道：无活动 Registrar 时归属系统唯一 RA，无唯一 RA 走既有错误；单测覆盖三优先级
- [x] 2.4 `ActiveRegistrarService.assertCanModifyItem` 与 `NamespacePermissionAspect` 增加 Submitter 写通道（条目归属系统唯一 RA 放行）；单测覆盖 Submitter 放行 / 无身份拒绝 / 跨机构 Registrar 拒绝
- [x] 2.5 验证生命周期状态推进仍仅 Registrar/Admin（`RegistrationLifecycleService` 不变，补回归测试确认 Submitter 状态推进被拒）

## 3. SubmissionRecord 持久化

- [x] 3.1 `ContactService` 提供 `resolveDefaultSubmissionContext(accountId)`（默认联系人 + 其组织，缺省回退绑定列表第一条）；单测覆盖有默认/无默认/无绑定三态
- [x] 3.2 Item Request DTO 增加可选 `submission: { contactId, organizationId }`；各 Item Service create/update 同事务生成 `SubmissionRecord`（校验 contactId 属于当前账号，organizationId 缺省取联系人组织）；单测覆盖预填、显式传参、代填他人 Contact 422

## 4. 我的联系人身份接口

- [x] 4.1 `GET /api/v1/my/contacts`：返回当前账号全部身份（含 Registrar 行、isRegistrar、注册机构摘要、isDefault）；单测覆盖多身份混合场景
- [x] 4.2 `POST/PUT/DELETE /api/v1/my/contacts` + `POST /{id}/set-default`：自建身份 CRUD、设默认事务清其他、删默认自动递补最早绑定、删空即失 Submitter 能力、Registrar 行删除 409；单测全覆盖
- [x] 4.3 登录/me 响应增加 `hasContact`/`hasRegistrar`/`profilePrompt`；单测覆盖四种账号组合
- [x] 4.4 重生成 `openapi.json` 并确认新端点契约入库

## 5. 前端

- [x] 5.1 `AppLayout` 首登引导：依据 `profilePrompt` 一次性弹窗（可跳过、localStorage 标记不重复）；vitest 覆盖提示/不提示/跳过三态
- [x] 5.2 `MyProfile.vue` 新增"联系人身份"管理区：列表（区分 Registrar 身份与注册机构）、新建/编辑/删除、设默认；vitest 覆盖交互
- [x] 5.3 `ItemFormDialog` 新增"提交信息"区：联系人选择器（限本人身份）+ 组织选择器，默认回填默认联系人及其组织可改；vitest 覆盖预填与修改
- [x] 5.4 i18n 中英文案补齐；前端 build 通过

## 6. 验证

- [x] 6.1 web 模块全量测试 0 failures，vitest 全绿
- [x] 6.2 冒烟（scratch 副本库 + 专用 Admin 测试账户，禁用应急码）：普通用户注册→首登提示→跳过→写操作 403→完善联系人→建 Item（SubmissionRecord 预填正确）→ 修订 Item 生成新 SubmissionRecord → 设默认→非 Contact 新用户仅可查看；脚本与结果存 `scratch/`
- [x] 6.3 `detect-changes --scope all` 无 partial/truncated；按 OpenSpec 变更分组整理变更文件
