# Proposal: Submitter 联系人身份体系（add-submitter-contact-identity）

## Why

原先假设只有 Registrar 才能提交元数据，这一前提需要更正：Submitter 可以是任何已登记为 Contact 的注册用户。当前系统把写操作权限与条目归属完全绑定在 Registrar 身份上，且 `Submission_Record` 实体在业务链路中无人生成（仅存于导出与测试中）。需要打通"普通用户 → 联系人身份 → 提交元数据"的完整链路。

## What Changes

- **Submitter 能力**：账号绑定 ≥1 个 Contact（含 Registrar 行）即获得 Submitter 能力；写操作放行 `SUPER_ADMIN / REGISTRAR / SUBMITTER`。既非 Registrar 也非 Contact 的账号无写权限，只能查看详情。
- **条目归属推导**：Submitter（无活动 Registrar）创建 `Administered_Item` 时归属系统唯一注册机构（单注册表模式隐式推导）；`registration_authority_id` 永不为空。修改/删除通道对 Submitter 放行；状态推进（生命周期治理）仍仅 Registrar/Admin。
- **SubmissionRecord 补链**：新建 Item、每次提交新版 Item 内容（update）时同事务生成一条 `SubmissionRecord`；界面默认预填用户默认联系人及其所属组织，可修改；`contactId` 必须是提交者本人绑定的 Contact。
- **默认联系人**：`contact_account_binding` 增加 `is_default` 标志，每账号至多一个默认；提交时作为 SubmissionRecord 预填来源。
- **首登引导**：普通用户注册并首次登录时提示完善联系人信息（可跳过）；Admin 初始化时选择成为 Registrar 的账号不再提示。
- **"我的"入口**：可管理多个联系人身份（新建/编辑/删除/设默认），Registrar 身份同列可见。
- **迁移**：SQLite / SQL Server / 达梦三方言新增 `contact_account_binding.is_default` 列。

## Capabilities

### New Capabilities

- `contact-identity-management`: 用户为自己的账号创建/维护多个联系人身份，设置默认联系人，并在"我的"入口统一查看联系人身份与 Registrar 身份。
- `submission-record`: SubmissionRecord 随 Administered_Item 保存生成（新建与每次内容修订各一条），联系人与组织默认预填且可修改，提交身份必须是本人绑定的 Contact。

### Modified Capabilities

- `active-registrar-ownership`: 注册工作权限由"仅 Registrar"放宽为"Registrar 或 Submitter（已登记 Contact 的用户）"；无活动 Registrar 的 Submitter 创建条目时归属系统唯一注册机构；修改/删除校验增加 Submitter 通道；状态治理仍限 Registrar/Admin。
- `user-self-registration`: 普通用户注册后首次登录增加"完善联系人信息"提示（可跳过）；初始化向导中选择成为 Registrar 的 Admin 不再收到该提示。
- `user-profile-individual`: "我的"界面在现有个人资料之外新增联系人身份管理区。

## Impact

- 后端：`JwtService`/`JwtAuthenticationFilter`（submitter claim）、`SecurityConfig`（写操作放行矩阵）、`ItemAdministrationService.resolveOwnershipForCreate`、`ActiveRegistrarService` / `NamespacePermissionAspect`（Submitter 写通道）、各 Item Service 与 Request DTO（SubmissionRecord 生成）、新增 `MyContactsController`、迁移脚本（三方言）。
- 前端：`AppLayout`（首登引导弹窗）、`MyProfile.vue`（联系人身份管理区）、`ItemFormDialog`（提交信息区预填）。
- 既有行为兼容：现有 Registrar/Admin 全链路不变；状态机与生命周期角色矩阵不动。
