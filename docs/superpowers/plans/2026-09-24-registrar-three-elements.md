# Implementation Plan — Registrar 三要素（个人/角色/组织）编辑与初始化

## 0. 元信息

- OpenSpec change: `add-registrar-three-elements`
- Superpowers design: `docs/superpowers/specs/2026-09-24-registrar-three-elements-design.md`
- 工作分支: `feature/registrar-three-elements`（repo-local worktree，路径 `.worktrees/registrar-three-elements/`）
- 完成定义: tasks.md 8 组全部勾选 + 验证组 4 条全绿 + `openspec validate` 通过 + git commit 干净

## 1. 工作区准备

1. 在 master 收尾上一轮 default 改名等遗留修改：
   - `metadata-registry-ui/src/views/init-wizard/InitWizard.vue`（注册机构默认名"默认注册机构"、注册规范默认名"默认注册规范"、e2e mock 期望"默认注册机构"）
   - `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/InitWizardService.java`（同步默认名）
   - `metadata-registry-web/src/test/java/io/github/matrixlane/metadataregistry/web/service/InitWizardServiceIntegrationTest.java`（断言更新）
   - `metadata-registry-ui/e2e/init-wizard.spec.ts`（mock 与期望同步）
   - `openspec/specs/registry-specification/spec.md`（默认值字面值同步）
   - `docs/superpowers/specs/2026-09-24-registrar-three-elements-design.md`（本次 design）
   - `openspec/changes/add-registrar-three-elements/`（本次新 OpenSpec change 全部 artifacts）
   - 提交信息: `chore(defaults): align registration authority and registry specification default names`
2. `git worktree add .worktrees/registrar-three-elements -b feature/registrar-three-elements`
3. 在 worktree 内实施本计划。

## 2. 实施阶段（与 tasks.md 编号同构）

### 阶段 A：后端契约（tasks 1）

- 1.1 `RegistrarRequest`：`roleId` 字段 + `organizationId @NotNull`。
- 1.2 `RegistrarService.applyRequest`：补 Role 处理（null 即 setRole(null)，非空按 id 查 Role）。
- 验证: `mvn -pl metadata-registry-web test -Dtest=RegistrarServiceTest`；若无该类，新建最小单测覆盖（apply 三要素 round-trip）。

### 阶段 B：后端初始化（tasks 2）

- 2.1 `RegistrarService.createRegistrarForUser` 接受 individualName / roleName / organizationId。
  - individualName 非空 → 创建 Individual(name = Sign.of(name), identifier = account.username)；空 → 不创建
  - roleName 必填：按 name 不区分大小写查重；不存在则创建
  - organizationId 为 null 时回退 ra
- 2.2 `InitWizardSetupRequest` 增三字段；`InitWizardService.setup` 默认填充；传给 createRegistrarForUser。
- 验证: `mvn -pl metadata-registry-web test -Dtest=InitWizardServiceIntegrationTest#setup_personalEdition_*`

### 阶段 C：ContactService "我的" 联系人身份与 /registrars 对齐（tasks 3）

- 3.1 `ContactService.updateMyContact` 对 Registrar 行放开三要素编辑（含 Organization 在 Registrar 中拒绝置 null）。
- 3.2 `/registrars/{id}` PUT 端到端走 1.1/1.2 的字段，无需后端改动。
- 验证: `mvn -pl metadata-registry-web test -Dtest=ContactServiceMyContactsTest#updateMyContact_*`

### 阶段 D：前端列表（task 4）

- 4.1 `RegistrarList.vue`：列结构改为「标识符 | 姓名 | 角色 | 组织 | 操作」；删 fallback 链。
- 验证: `cd metadata-registry-ui && npx vitest run src/views/registrar`

### 阶段 E：前端编辑表单（task 5）

- 5.1 `ResourceFormDialog` 的 `#fields`：删 `titleZh/titleEn`；新增 individualName input + role select + organization select。
- 5.2 若 `/api/v1/organizations/options` 不存在：补端点 + service + 单测。
- 验证: vitest 单测 round-trip

### 阶段 F：初始化向导（task 6）

- 6.1 `InitWizard.vue` 注册机构步骤增加三字段（姓名 / 角色 / 组织），提交 payload 追加三字段。
- 验证: e2e `init-wizard.spec.ts`

### 阶段 G：文档/归档（task 7）

- 7.1 重新生成 openapi.json
- 7.2 `ARCHITECTURE.md` 与 `metadata-registry-web-src.md` 同步
- 7.3 `.superpowers-memory/session-journal/2026-09-24-registrar-three-elements.md`
- 7.4 `openspec validate add-registrar-three-elements --strict` 通过

### 阶段 H：验证（task 8）

- 8.1 后端 `mvn -pl metadata-registry-web test` 全绿
- 8.2 前端 `npx vitest run` 全绿 + build
- 8.3 e2e 冒烟（PERSONAL + TEAM 路径）
- 8.4 `node .gitnexus/run.cjs detect-changes --scope all --repo .` 干净

## 3. 风险与次序约束

- 1.1 必须先于 1.2；1.x 先于 2.x 先于 5.2 端到端联调。
- 后端三要素字段命名约定与 DTO contract 直接驱动前端 payload，必须保持一致。
- `Role.roleName` 唯一性既存在于既有 RoleService 单测；createRegistrarForUser 复用之即可。
- ResourceNotFoundException（4xx）需要让前端 e2e 可断言。

## 4. 完成退出条件

- [ ] tasks.md 8 组全部 checkbox 勾上
- [ ] `openspec validate add-registrar-three-elements --strict` 通过
- [ ] 后端 vitest/build/e2e 全绿（可在 worktree 内跑，因依赖同 repo）
- [ ] git detect-changes 干净（无 partial/truncated/HIGH/CRITICAL 警告）
- [ ] 单 commit 信息：包含 OpenSpec change id 与主要 commit body
- [ ] 告知用户 ready for `continue-review`
