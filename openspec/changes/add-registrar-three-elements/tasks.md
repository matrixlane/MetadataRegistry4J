## 1. 后端能力补全（Registrar DTO 与 Service）

- [ ] 1.1 `RegistrarRequest` 增 `roleId` 字段（组织可空、Individual 可空）；并验证 `@NotBlank identifier` 现状后增加 `@NotNull organizationId` 校验；运行 `mvn -pl metadata-registry-web test -Dtest=RegistrarControllerValidationTest`（如该测试不存在则新建）确认契约落地
- [ ] 1.2 `RegistrarService.applyRequest` 增 Role 处理分支（null 即 setRole(null)，非空按 id 查 RoleRepository）；同步确保 individualId 留空时不变更既有 Individual 关联；运行 `mvn -pl metadata-registry-web test -Dtest=RegistrarServiceTest`（如不存在则新建）覆盖三要素 round-trip

## 2. 后端初始化流程：让 admin 自动成为 Registrar 时携带三要素

- [ ] 2.1 `RegistrarService.createRegistrarForUser` 扩签名：增加 `individualName`、`roleName`、`organizationId` 三个入参；内部按 design §3.2 创建 Individual（name 用 Sign.of）、Role（按 name 查重复用或新建）、按 organizationId 解析 Organization（null 回退 ra）；运行单元测试覆盖三要素全填/roleName 已存在复用/individualName 为空不创建/organizationId=null 回退 RA 四个场景
- [ ] 2.2 `InitWizardSetupRequest` 增加 `adminIndividualName`、`adminRoleName`、`adminOrganizationId`；`InitWizardService.setup` 在 PERSONAL 版下分别 default `"注册管理员"`、`"注册管理员"`、ra.id；TEAM 版必填校验；并在调用 `createRegistrarForUser` 时透传；运行 `mvn -pl metadata-registry-web test -Dtest=InitWizardServiceIntegrationTest#setup_personalEdition_appliesDefaults` 验证落库到 Individual/Role

## 3. 编辑表单：让"我的"联系人身份与 /registrars 编辑对齐

- [ ] 3.1 `ContactService.updateMyContact` 对 Registrar 行放开三要素编辑（同时允许改 Role、Individual、Organization；Organization 在 Registrar 上下文中拒绝置 null）；运行 `mvn -pl metadata-registry-web test -Dtest=ContactServiceMyContactsTest#updateMyContact_*` 覆盖三要素（含 Organization 清空被拒）
- [ ] 3.2 `RegistrarService.update` 对应前端 `/registrars/:id` 编辑路径：组织变更必填校验已落在 1.1，新增 role 字段落库已落在 1.2，无需后端额外改动，但需 e2e/手动验证 round-trip

## 4. 前端：注册人员列表三列

- [ ] 4.1 `RegistrarList.vue` 删除 fallback 链 `{{ row.individualName || row.organizationName || row.identifier || '-' }}`，改为四列：标识符、姓名（prop=individualName）、角色（prop=roleName）、组织（prop=organizationName）；运行 `npx vitest run metadata-registry-ui/src/views/registrar` 单测通过

## 5. 前端：注册人员编辑表单改造

- [ ] 5.1 `RegistrarList` 中 `ResourceFormDialog` 的 `#fields` slot：删除 `titleZh/titleEn` 输入；新增「个人姓名」`el-input`（绑 `individualName`）、「角色」`el-select`（clearable filterable，候选项来自 `GET /api/v1/roles/options`，绑 `roleId`）、「组织」`el-select`（required，候选项来自 `GET /api/v1/organizations/options`，绑 `organizationId`）；保存映射到 `RegistrarRequest` 全部 6 字段（identifier、registrationAuthorityId、userId、organizationId、individualId、roleId）；运行 vitest 覆盖表单打开→编辑→保存 round-trip
- [ ] 5.2 若 `/api/v1/organizations/options` 端点不存在：在 `OrganizationController` 增加 `@GetMapping("/options")` 与 `OrganizationService.options()`，仅返回 `(id, name)`；运行后端单测覆盖

## 6. 前端：初始化向导三要素字段

- [ ] 6.1 `InitWizard.vue` 注册机构步骤（在 `registrationAuthorityEnglishName/Email` 之后、`wizard-settings-box` 之前）新增三字段：「注册管理员姓名」输入框（PERSONAL 默认"注册管理员"，可清空）、「注册管理员角色」输入框（PERSONAL 默认"注册管理员"，必填）、「所属组织」下拉（PERSONAL 锁定 = 当前 RA、TEAM 显式选）；向 setup payload 追加 `adminIndividualName / adminRoleName / adminOrganizationId`；运行 e2e `init-wizard.spec.ts` 验证默认填充与 TEAM 显式输入

## 7. 文档与归档

- [ ] 7.1 重生成 `metadata-registry-web/src/main/resources/static/assets/openapi.json` 并确认新字段契约入库；运行 `./scripts/generate-openapi.sh`（或既有方式）核对 diff
- [ ] 7.2 更新 `docs/architecture/ARCHITECTURE.md` 与 `docs/architecture/modules/metadata-registry-web-src.md`（若仍有引用）注册人员管理章节，标注三要素列表展示与编辑语义
- [ ] 7.3 `.superpowers-memory/session-journal/` 新建 `2026-09-24-registrar-three-elements.md`，记录方案、关键决策、验证证据
- [ ] 7.4 确认本 change 可由 `openspec validate --change add-registrar-three-elements --strict` 通过

## 8. 验证

- [ ] 8.1 后端 `mvn -pl metadata-registry-web test` 全绿；前端 `cd metadata-registry-ui && npx vitest run` 全绿；前端 build 通过
- [ ] 8.2 端到端冒烟（PERSONAL 版）：从 scratch 跑 `init-wizard.spec.ts`，验证三字段默认值落到 db，且列表三列均为期望值
- [ ] 8.3 端到端冒烟（TEAM 版）：手动或 e2e 验证三字段显式填写、组织下拉切换、role 已存在复用语义
- [ ] 8.4 `node .gitnexus/run.cjs detect-changes --scope all --repo .` 干净（无 partial/truncated），如出现 HIGH/CRITICAL 影响分析告警立刻修
