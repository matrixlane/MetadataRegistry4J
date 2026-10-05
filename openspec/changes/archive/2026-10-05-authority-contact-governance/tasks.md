# Tasks: authority-contact-governance

## 1. 数据模型与迁移（Primary 标志）

- [x] 1.1 `RegistrationAuthority` 实体新增 `isPrimary` 字段（`@Column(name="is_primary", nullable=false)`），并写实体级单元测试验证默认 false；参考 `docs/superpowers/specs/2026-10-05-authority-contact-governance-design.md` D1
- [x] 1.2 编写三方言迁移 `V20261005001__Registration_authority_primary_flag`（sqlite / sqlserver / dm 同版本号；SQLite/SQL Server 用 `ALTER TABLE registration_authority ADD COLUMN is_primary ... NOT NULL DEFAULT false` 加列语法；dm 按达梦方言等效语法），并在全新 SQLite 库上重放验证
- [x] 1.3 启动调和回填：在 `RegistryContextBootstrapService.backfillIfInitialized` 邻近新增幂等调和（无任何 Primary 行时把最早创建的 RA 补标 Primary），补集成测试（无标志存量库启动 → Primary 被回填）

## 2. 注册机构创建/删除的版本化治理

- [x] 2.1 `RegistrationAuthorityService.create` 改为版本分支：PERSONAL 放行数量限制并校验调用者为 SUPER_ADMIN（Controller 注入 `@AuthenticationUser`）；TEAM 沿用 runningMode 规则；编写服务测试（PERSONAL 建第二个机构成功 / TEAM SINGLE 第二个被拒）
- [x] 2.2 PERSONAL 创建成功后自动为当前管理员建 Registrar（复用 `RegistrarService.createRegistrarForUser`，individualName=null、roleName 缺省"注册管理员"、organization=新 RA、绑定账号并确保默认身份），补服务测试断言 Registrar 存在且账号默认身份正确
- [x] 2.3 `RegistrationAuthorityService.delete` 改为版本分支：PERSONAL 且 is_primary → 拒绝；其余放行并调用级联删除服务；补测试（PERSONAL primary 删被拒 / 非 primary 删成功 / TEAM primary 删成功）
- [x] 2.4 新增 `GET /api/v1/registration-authorities/{id}/deletion-preview` 端点（返回受管项数、提交记录数、注册人员数、命名空间数等计数），补 Controller/Service 测试
- [x] 2.5 实现 `RegistrationAuthorityDeletionService` 级联删除（顺序：Item 图含先清 `submission_record_registered_item` → SubmissionRecord → StewardshipRecord → Registrar（解绑+清 activeRegistrarId）→ 命名空间及从属 → sys: namingAuthority 置空 → 流转规则(+approver) → doc_language → RA/organization 行；单事务、禁 REQUIRES_NEW），补集成测试：建 RA 并造全部关联数据 → 删除 → 相关表断言清空且无悬挂引用

## 3. 初始化流程调整（注册规范先行 + Primary）

- [x] 3.1 `InitWizardService.setup` 调整创建顺序为 Spec → RA（置 is_primary=true）→ Registrar → Namespace → sys 挂接；确认 Registry Context 回填机制不变；补 `InitWizardServiceIntegrationTest` 断言顺序（spec 不引用 RA、RA 带 Primary 标志）
- [x] 3.2 向导前端 `InitWizard.vue` 步骤顺序改为 版本→数据库→注册规范→注册机构→命名空间→密码（步骤序号、displaySteps、载荷组装同步），更新 e2e `init-wizard.spec.ts` 并跑通
- [x] 3.3 核对 `system-initialization`/`registry-specification` 相关既有测试与文案按新顺序更新（步骤顺序断言、场景描述）

## 4. 联系人身份增删的版本/角色差异化

- [x] 4.1 `ContactService.createMyContact` 增加门控：kind=REGISTRAR 且账号非 SUPER_ADMIN → 422；补测试（非管理员建 REGISTRAR 被拒 / 管理员成功）
- [x] 4.2 `ContactService.deleteMyContact` 版本分支：PERSONAL 且 Registrar 行 → 409（提示先删机构）；TEAM 移除 SubmissionRecord/Stewardship/审批引用守卫；补测试三场景
- [x] 4.3 `MyProfile.vue` 门控：非 SUPER_ADMIN 隐藏"注册人员"单选项；删除按钮按版本/身份类型控制（PERSONAL 的 Registrar 行禁用+提示；TEAM 全开放）；补/改组件测试
- [x] 4.4 i18n 文案：个人版注册人员身份不可删提示、团队版删除后果说明（zh-CN/en-US）

## 5. 提交组织可选 + 联系人属主校验

- [x] 5.1 `WorkspaceService.validateSubmissionInfo` 与 `ItemAdministrationService.resolveContactAndOrganization` 同步增加：organizationId 给出时从账号绑定身份解析该组织下身份（解析不到 → 422 提示先创建联系人）；contactId+organizationId 同给时校验从属；仅 contactId 路径兼容；补两处服务测试（无身份组织 422 / 从属不符 422 / 兼容路径不变）
- [x] 5.2 `SubmissionRecordPicker.vue` 改为组织下拉（选项=本人身份组织去重，默认=活动 RA 身份组织否则默认身份组织），联系人只读展示；无身份时打开提示弹窗（引导"我的"入口）；补组件测试并跑通 `SubmissionRecordPicker.spec.ts`
- [x] 5.3 `WorkspaceView.vue` 提交/批量提交/请求候选入口接入预检（无身份机构 → 提示不打开表单）；补 WorkspaceView 测试
- [x] 5.4 i18n 文案：提交组织占位、无身份提示（zh-CN/en-US）

## 6. 前端机构页面

- [x] 6.1 `RegistrationAuthorityList.vue`：PERSONAL 恢复新增入口（对话框加"创建后您将自动成为该机构注册人员"提示）；删除改 `ElMessageBox.confirm` 警告（展示 deletion-preview 计数+不可恢复说明）；PERSONAL primary 行禁用删除入口；补组件测试
- [x] 6.2 `types/common.ts` 与 api 封装补 deletion-preview 类型与调用

## 7. 全量验证与交付

- [x] 7.1 后端全量测试（`mvn -pl metadata-registry-web -am test`）零新增失败；前端 `vitest` 全绿 + `build` 通过
- [x] 7.2 冒烟：个人版（建第二机构→自动注册人员→非 primary 删除级联→primary 删除被拒）；团队版（非管理员建注册人员被拒→删 primary 级联）；提交（无身份提示+422→选组织提交成功）；用 scratch 副本库执行并记录脚本
- [x] 7.3 `openspec validate --all` 通过；`gitnexus detect_changes --scope all` 无 partial/truncated；openapi.json 再生成；按 AGENTS.md 检查 README/ARCHITECTURE 是否需同步
