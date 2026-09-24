# Submitter 联系人身份体系设计

日期：2026-09-22
状态：已确认（方案 A）
OpenSpec 变更：`add-submitter-contact-identity`

## 1. 背景与目标

原先假设只有 Registrar 才能提交元数据，现更正为：**Submitter 可以是任何已登记为 Contact 的注册用户**。

确认的业务语义（用户裁定）：

- 应用当前为单注册表模式，注册表隐式关联注册机构，`Administered_Item.registration_authority_id` **永不为空**（Item 从属于指定注册表，非孤立存在）。
- `SubmissionRecord` 随 `Administered_Item` 保存而生成：新建 Item、以及每次提交一版新的 Item 内容（update），都生成一条新的 `SubmissionRecord`。
- 新建/编辑 Item 界面中 `SubmissionRecord` 的联系人、组织字段**默认预填**为用户默认联系人及其所属组织，用户可改。
- 首次登录"完善联系人信息"提示**可跳过**；但既非 Registrar 也非 Contact 的账号**无写操作权限**（只能查看）。
- 未来多注册表模式下同一时间只打开一个注册表工作，不存在跨注册表矛盾；本期不实现多注册表。

## 2. 现状要点（探索结论）

- `Account → Individual → Contact`；`contact_account_binding`（ext 模块卫星表）已支持一个账号绑多个 Contact（`contact_id` 唯一、`account_id` 不唯一）。
- `Registrar extends Contact`（JPA JOINED）；初始化向导 `InitWizardService.setup` 的 `selfAsRegistrar` 会把 admin 建为 Registrar 并绑定账号。
- `SubmissionRecord` 实体存在（`organization`、`contact` 必填），但**业务链路无人生成它**，仅导出/测试读取。
- 权限三层：`SecurityConfig` 写操作需 `SUPER_ADMIN/REGISTRAR`（`metadata-registry-web/.../auth/SecurityConfig.java:95-98`）；`JwtAuthenticationFilter` 由 JWT claim 装配角色；`ItemAdministrationService.resolveOwnershipForCreate`（`:518-532`）要求活动 Registrar 并把 Item 归属 RA；`NamespacePermissionAspect` + `ActiveRegistrarService.assertCanModifyItem`（`:142-168`）校验修改/删除。
- 首登：admin 免密强制改密；普通用户注册后无任何引导。无"默认联系人"概念。
- "我的"页 `/my-profile`（`MyProfile.vue`）目前只改 Individual 资料与头像。

## 3. 设计（方案 A）

### 3.1 数据模型

- `contact_account_binding` 新增列 `is_default BOOLEAN NOT NULL DEFAULT 0`；应用层保证**每个账号至多一个默认**（设默认时事务内清掉同账号其他默认）。三方言迁移：`V20260922xxx`（sqlite / sqlserver / dm 各一）。
- 不动 `contact` 表主表；Registrar 身份即 `Contact` 的 JOINED 子类行，天然出现在绑定表中。

### 3.2 权限与身份推导

- **JWT claim**：`JwtService` 新增 `submitter` claim —— 账号绑定的 Contact（含 Registrar 行）数量 ≥1 即为 `true`；`registrar` claim 维持现状（任一 Contact 的 role 为"注册管理员"或 SUPER_ADMIN）。
- **SecurityConfig**：写操作（POST/PUT/PATCH/DELETE `/api/v1/**`）放行 `SUPER_ADMIN / REGISTRAR / SUBMITTER`。无 Contact 且无 Registrar 的账号只保留 GET → **需求 5 在网关层天然成立**。
- **归属推导**：`ItemAdministrationService.resolveOwnershipForCreate` 调整优先级：
  1. 有活动 Registrar → 归属其 RA（现状不变）；
  2. 否则若为 Submitter → 归属系统唯一 RA（单注册表隐式推导；查不到唯一 RA 时报既有错误）。
- **修改/删除**：`ActiveRegistrarService.assertCanModifyItem` 与 `NamespacePermissionAspect` 增加 Submitter 通道：无活动 Registrar 但为 Submitter，且单 RA 模式下 Item 归属 RA 即系统 RA → 放行。SUPER_ADMIN 通道不变。
- **状态推进**：`RegistrationLifecycleService` 的角色矩阵本期**不放开**（Submitter 不参与状态治理），维持 Registrar/Admin 专属。

### 3.3 SubmissionRecord 持久化补链

- 各 Item Service 的 create/update（提交新版内容）在保存 `AdministeredItem` 的同事务生成一条 `SubmissionRecord`：`contact`、`organization` 取自请求 DTO；DTO 缺省时服务端预填 = 账号默认联系人 + 该联系人所属组织；`registeredItems` 关联当前 Item。
- DTO 在各 Item 的 Request 中新增可选块 `submission: { contactId, organizationId }`。
- 兜底校验：提交的 contactId 必须是当前账号绑定的 Contact（防止代填他人身份）；organizationId 缺省取 contact 所属组织。

### 3.4 后端接口

- `GET /api/v1/my/contacts` — 当前账号全部 Contact 身份（含 Registrar 行，附 `isRegistrar`、`registrationAuthority` 摘要、`isDefault`）。
- `POST /api/v1/my/contacts` — 自建联系人（选组织、角色；自动绑定当前账号）。
- `PUT /api/v1/my/contacts/{id}`、`DELETE /api/v1/my/contacts/{id}` — 改/删自建联系人；Registrar 行不可在此删除（走既有 Registrar 管理）；删默认联系人后自动把剩余最早一条置默认；最后一条删除后账号失去 Submitter 能力（符合需求 5）。
- `POST /api/v1/my/contacts/{id}/set-default`。
- `AuthService` 的 login/me 响应新增：`hasContact`、`hasRegistrar`、`profilePrompt`（= 非 SUPER_ADMIN && 无 Registrar && 无 Contact）。
- Admin 初始化向导（`selfAsRegistrar=true`）创建的 admin 因已绑 Registrar，`profilePrompt=false`，**不弹提示**（需求 2 自然满足）。

### 3.5 前端

- **首登引导**：`AppLayout` 依据 `profilePrompt` 弹一次性引导对话框（说明完善联系人后即可提交元数据），可跳过；跳过不再骚扰（localStorage 标记，仅首登弹一次）。
- **"我的"页**（`MyProfile.vue`）新增"联系人身份"区：列表（含 Registrar 身份标识与所属注册机构）、新建/编辑/删除、设默认；个人信息区维持现状。
- **ItemFormDialog**：新增"提交信息"折叠区，联系人选择器（限本人 Contact）+ 组织选择器，默认回填默认联系人及其组织，可改；无默认联系人时留空并由服务端预填。
- 无写权限用户：写按钮已由后端 403 兜底；本期不做前端按钮级大范围隐藏（保持现状，避免 25+ 列表页改动）。

### 3.6 不做的事

- 不引入 SUBMITTER 角色表/独立提交工作流（方案 B 留待未来）。
- 不动状态机与生命周期角色矩阵。
- 不实现多注册表/多 RA 归属切换（代码中归属推导集中到 `resolveOwnershipForCreate` 一处，便于未来扩展）。

## 4. 测试与验证

- 后端单测：JWT claim 生成（有/无 Contact）、SecurityConfig 放行矩阵、`resolveOwnershipForCreate` 三优先级、Submitter 修改/删除放行、SubmissionRecord 生成与预填、my/contacts CRUD + 默认唯一性。
- 前端 vitest：引导弹窗逻辑、"我的"页联系人管理、ItemFormDialog 提交信息预填。
- 冒烟（scratch 副本库）：普通用户注册→首登提示→完善联系人→建 Item（含 SubmissionRecord 预填）→ 非 Contact 用户 403。
- 验证基线：web 全量测试 0 failures、vitest 全绿、三方言迁移、detect-changes 无 partial/truncated。
