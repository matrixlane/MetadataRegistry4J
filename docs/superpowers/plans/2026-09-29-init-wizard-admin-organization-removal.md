# 实施计划：移除初始化向导「所属组织」选择框

- 日期：2026-09-29
- 设计稿：`docs/superpowers/specs/2026-09-29-init-wizard-admin-organization-removal-design.md`
- OpenSpec change：`add-registrar-three-elements`（在途修正，tasks 2.2 / 6.1 / 8.3）
- 已先行交付：`metadata-registry-ui/src/utils/request.ts` 的 499 toast 移除（A/B 对照验证通过）

## 0. 前置

1. 查明工作区既有 `InitWizardServiceIntegrationTest` 失败来源，确认与本次改动无因果关系；若无法隔离，改在干净 worktree 上做。
2. 隔离方式：repo-local git worktree，基于当前 HEAD。若判定工作区未提交实现需带入，则先在原工作区 `git stash` 留存再迁移。

## 1. TDD：先立失败用例

在 `InitWizardServiceIntegrationTest` 新增三个用例，逐一先红：

| 用例 | 断言 | 预期现状 |
|---|---|---|
| `setup_teamEdition_noOrganizationField_succeeds` | TEAM 版不传所属组织也能完成初始化 | 红（当前 `InitWizardService.java:211` 抛「注册管理员所属组织不能为空」） |
| `setup_teamEdition_registrarOrganizationIsRegistrationAuthority` | Registrar 的 organization == 本次创建的 RA id | 红（当前取请求传入值） |
| `setup_teamEdition_selfAsRegistrarOff_skipsElementValidation` | 关闭 `selfAsRegistrar` 且姓名/角色留空时初始化成功，且不创建 Registrar | 红（当前 `:207` 校验块与开关无关） |

命令：`./mvnw -pl metadata-registry-web test -Dtest=InitWizardServiceIntegrationTest`（本机 `mvn` 不在 PATH，须用仓库自带的 `./mvnw`）

## 2. 后端改动

按依赖顺序，每步后跑一次上表命令。

1. `dto/initwizard/InitWizardSetupRequest.java:55` — 删除 `adminOrganizationId` 字段。删除后需同步全部位置参数构造点（见步骤 5）。
2. `service/InitWizardService.java`：
   - `:159` 删除 `adminOrganizationId` 读取；`:180` 删除注释；
   - `:211-213` 删除所属组织必填校验；
   - 角色必填校验（`:208-210`）从 `if (edition == TEAM)` 内移入 `selfAsRegistrar` 判定范围——注意 `selfAsRegistrar` 在 `:148` 才取值，校验点在 `:207` 之后，顺序可用；
   - `:252` `resolvedAdminOrgId` 恒取 `ra.getId()`。
3. 确认 `RegistrarService.createRegistrarForUser` 的 `organizationId` 入参签名不变（仍传 `ra.getId()`），不在本次改动范围。

## 3. 前端改动

`metadata-registry-ui/src/views/init-wizard/InitWizard.vue`：

1. 删除 `:238-252` 「所属组织」`el-form-item`；
2. `:517` import 去掉 `organizationApi`；
3. 删除 `:665` 表单字段、`:668-669` `OrganizationOption` 与 `organizationOptions`；
4. `:836` payload 去掉 `adminOrganizationId`；
5. `:1123-1128` 删除 `onMounted` 中的 `organizationApi.options()` 调用及 try/catch；
6. `:929-936` TEAM 分支改为：仅当 `form.selfAsRegistrar` 为真时才校验 `adminRoleName`，删除组织校验。

验证：`npx vitest run`、`npm run build`（含 vue-tsc）。

## 4. 测试同步

| 文件 | 改动 |
|---|---|
| `metadata-registry-ui/e2e/init-wizard.spec.ts:82,87` | 删除 `adminOrganizationId` 断言；TEAM 场景改为断言 payload 不含该键 |
| `InitWizardControllerTest.java:79` | 位置参数去掉一个 `null` |
| `RegistryContextBootstrapServiceTest.java:345` | 同上 |
| `InitWizardServiceIntegrationTest.java`（13 处） | 同上 |

## 5. 契约与文档

1. 重新生成 `metadata-registry-web/src/main/resources/static/assets/openapi.json`（`setup` 请求体少一个字段）。
2. `docs/architecture/` 下若存在 `InitWizardService` / `InitWizardSetupRequest` 字段清单，按实际内容同步。

## 6. 验证

```bash
./mvnw -pl metadata-registry-web test                               # 后端全量
cd metadata-registry-ui && npx vitest run && npm run build          # 前端单测 + 构建
openspec validate add-registrar-three-elements --strict              # 规范校验
```

端到端：带有效 token 打开未初始化态的 `/init-wizard`，断言
(a) 页面无任何 `el-message`；
(b) 后端日志无 `GET /api/v1/organizations/options` 记录；
(c) TEAM 版注册机构步骤无组织控件、可直接进入下一步；
(d) TEAM 版关闭「设为注册人员」后姓名/角色留空亦可通过。

最后 `node .gitnexus/run.cjs detect-changes --scope all --repo .`，确认无 partial/truncated。

## 7. 收尾

- `tasks.md` 勾选 2.2 / 6.1 / 8.3。
- 更新 `.superpowers-memory/CURRENT_STATE.md` 与 session-journal。
- 归档前先经代码评审（`continue-review`），对齐后再 `continue-archive`。
