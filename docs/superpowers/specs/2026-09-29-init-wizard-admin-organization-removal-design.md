# 移除初始化向导「所属组织」选择框 —— 设计

- 日期：2026-09-29
- 关联变更：`add-registrar-three-elements`（在途修正，不另开 change）
- 触发：初始化向导页出现无意义错误提示「系统尚未完成初始化，请按向导完成配置」

## 1. 问题

初始化向导注册机构步骤为团队版提供「所属组织」下拉，其数据源为 `GET /api/v1/organizations/options`。该提示与该下拉同源：向导页 `onMounted` 无条件调用 `organizationApi.options()`，在系统未初始化时必然被 `InitializationWizardInterceptor` 以 `{"code":499}` 拒绝，前端随即弹出「系统尚未完成初始化，请按向导完成配置」。

顺着这条链继续核查，发现该下拉本身即无解，而非仅数据延迟。

## 2. 事实依据

### 2.1 RA 本身就是 Organization

```
metadata-registry-common-facilities/.../basicandcore/basicclasses/Organization.java:33
    @Inheritance(strategy = InheritanceType.JOINED)
    public class Organization extends Entity

metadata-registry-common-facilities/.../registration/RegistrationAuthority.java:24
    public class RegistrationAuthority extends Organization
```

JOINED 继承共表：注册机构行同时是一条 Organization 行。因此「注册管理员所属哪个组织」在注册机构场景下的答案恒为其所属的 RA。

### 2.2 初始化时刻不存在任何可供选择的组织

- `InitializationWizardInterceptor.java:21-30` 白名单仅含 `init-wizard/`、`datasource/`、`migration/`、`identifier-strategy/`、`auth/login`、`auth/me`、`auth/allow-registration`。`/organizations/**` 不在其中，未初始化时一律 `403 + code 499`。
- 组织管理 CRUD 同样在该拦截器之后，故**未初始化状态下无任何途径创建组织**。
- 注册机构行在 `InitWizardService.java:247`（`registrationAuthorityRepository.save(ra)`）才首次落库，时点已是向导最后一步。

结论：向导注册机构步骤执行时，系统内存在的 Organization 有且只有一个——正在创建的这一条。TEAM 版下拉在任何时刻都不可能选到别的东西。

### 2.3 现有兜底逻辑已指向同一答案

`InitWizardService.java:251-252`：

```java
java.util.UUID resolvedAdminOrgId = adminOrganizationId != null ? adminOrganizationId : ra.getId();
```

PERSONAL 版走兜底取 `ra.getId()`；TEAM 版要求显式选择，但可选值也只能是同一个 `ra.getId()`。两分支同归，却多绕一圈并引入一个必然失败的下拉。

### 2.4 规范条款本身不成立

`add-registrar-three-elements/specs/registrar-identity-elements/spec.md:71` 与 `specs/system-initialization/spec.md:11`：

> 所属组织（…TEAM 版 MUST 由管理员从已有组织列表中选择；任一版本该字段 MUST NOT 为空）

配合 `:40-43` 的「TEAM 版组织留空被拒绝」Scenario：全新部署时该 MUST **必然违反**——用户面对一个永远为空的必填下拉，前端 `InitWizard.vue:934` 与后端 `InitWizardService.java:211` 双重拦截，团队版向导无法完成。

### 2.5 顺带暴露：TEAM 版要素校验未与 selfAsRegistrar 联动

三要素服务于「是否创建 Registrar」这一前提。`InitWizard.vue:194-200` 为 TEAM 版提供「将当前超级管理员设为该机构的注册人员」开关（PERSONAL 版恒为 true，无开关），关闭即不创建 Registrar。

但两端校验都只看版本、不看该开关：

```
InitWizardService.java:207   if (edition == TEAM) { if (adminRoleName 空) throw; if (adminOrganizationId == null) throw; }
InitWizardService.java:250   if (selfAsRegistrar) { registrarService.createRegistrarForUser(...); }
InitWizard.vue:929-936       if (form.edition === 'TEAM') { if (!adminRoleName) 报错; if (!adminOrganizationId) 报错; }
```

结果：**团队版关掉开关后，Registrar 不会创建，用户却仍被强制要求填写它的要素**。不会创建的东西不该有必填项，此项一并修正。

## 3. 决策

**注册管理员的所属组织恒等于本次创建的 Registration_Authority，两个版本均不向用户暴露选择入口。**

| | 修改前 | 修改后 |
|---|---|---|
| PERSONAL | 锁定为当前 RA（仅前端提示，后端兜底） | 同左，无 UI |
| TEAM | 必须从下拉显式选择 | 同左，无 UI |
| 落库组织 | PERSONAL=`ra.id`；TEAM=用户所选 | 恒为 `ra.id` |
| TEAM 关闭「设为注册人员」后 | 仍强制要求填角色与组织 | 不创建 Registrar，也不校验要素 |

保留 `Registrar` 的 Organization 关联本身——该关联仍然建立且非空，只是不再由用户在初始化阶段指定。初始化完成后的「注册人员」编辑页（`/registrars/:id`）仍保留组织下拉，因为那时系统中已存在多个组织，选择是真实有效的。这条区分是本决策的边界：**仅初始化阶段移除，运行期编辑不受影响**。

同理，姓名与角色的填写义务以「确实要创建 Registrar」为前提：TEAM 版关闭该开关后不再校验。

## 4. 变更清单

### 4.1 前端 `metadata-registry-ui`

| 位置 | 变更 |
|---|---|
| `src/views/init-wizard/InitWizard.vue:238-252` | 删除「所属组织」`el-form-item`（TEAM 专用下拉） |
| 同上 `:517` | 从 `@/api/resources` 的 import 中移除 `organizationApi` |
| 同上 `:668-669` | 删除 `interface OrganizationOption` 与 `organizationOptions` ref |
| 同上 `:665, :836` | 删除表单字段 `adminOrganizationId` 与 setup payload 中的该字段 |
| 同上 `:934-936` | TEAM 分支追加 `form.selfAsRegistrar` 前提；关闭开关时跳过要素校验 |
| 同上 `:1123-1128` | 删除 `organizationApi.options()` 调用及其 try/catch |

删除该调用后，向导页对 `/organizations/**` 的零请求，403 与随后的提示一并从源头消失。

### 4.2 后端 `metadata-registry-web`

| 位置 | 变更 |
|---|---|
| `dto/initwizard/InitWizardSetupRequest.java:55` | 删除 `adminOrganizationId` 字段 |
| `service/InitWizardService.java:159, :180` | 删除读取与注释 |
| `service/InitWizardService.java:211-213` | 删除 TEAM 版「注册管理员所属组织不能为空」校验；角色必填校验收进 `selfAsRegistrar` 判定之内 |
| `service/InitWizardService.java:251-252` | `resolvedAdminOrgId` 直接取 `ra.getId()`，去掉三元回退 |

### 4.3 测试

| 位置 | 变更 |
|---|---|
| `metadata-registry-ui/e2e/init-wizard.spec.ts:82, :87` | 移除 `adminOrganizationId` 断言；TEAM 场景改为断言该键不再出现于 payload |
| `InitWizardServiceIntegrationTest.java`（13 处）、`InitWizardControllerTest.java:79`、`RegistryContextBootstrapServiceTest.java:345` | 位置参数构造同步移除 `null` 占位 |
| 新增用例 | TEAM 版 setup 不再因组织字段被拒；`createRegistrarForUser` 收到的 organizationId 恒等于 `ra.getId()`；TEAM 版关闭 `selfAsRegistrar` 后要素留空仍能完成初始化且不创建 Registrar |

### 4.4 OpenSpec 产物

| 文件 | 变更 |
|---|---|
| `specs/registrar-identity-elements/spec.md:71` | 改写为「所属组织恒等于本次创建的 Registration_Authority，任一版本 MUST NOT 提供选择入口」 |
| 同文件 `:80-83` | TEAM Scenario 移除组织选择描述 |
| 同文件 `:90-93` | 「TEAM 版角色留空时的校验拒绝」保留；删除组织相关的拒绝语义 |
| `specs/system-initialization/spec.md:11, :13, :30-33, :40-43` | 同步改写 |
| `design.md:104-112` | 同步 DTO 与 setup 描述 |
| `tasks.md:2.2, :6.1, :8.3` | 同步任务描述 |

## 5. 兼容性

- **API 契约变更**：`/api/v1/init-wizard/setup` 请求体移除 `adminOrganizationId`。该字段在未完成初始化的部署中本就无法产生有效值，不存在依赖它的既有数据。`openapi.json` 需重新生成。
- **数据层**：无 schema 变更。`Registrar` 的 Organization 关联仍写入，值改为 `ra.id`，与 PERSONAL 版既有行为一致。
- **运行期编辑**：不受影响。`/registrars/:id` 的组织下拉保留，`RegistrarService.applyRequest` 的 Organization 处理分支保留。
- **spec 影响面**：仅 `add-registrar-three-elements`（在途）与 `system-initialization` 增量。`active-registrar-ownership`、`individual-identifier`、`contact-identity-management` 均无冲突。

## 6. 验证

1. **TDD**：先补「TEAM 版 setup 成功且 Registrar 组织 = ra.id」失败用例，再改实现转绿。
2. 后端 `mvn -pl metadata-registry-web test` 全绿。
3. 前端 `npx vitest run` 全绿 + `npm run build` 通过。
4. **端到端**：带有效 token 打开 `/init-wizard`（未初始化态），断言不再出现任何 `el-message`，且后端日志无 `GET /api/v1/organizations/options` 记录。
5. `openspec validate --change add-registrar-three-elements --strict` 通过。
6. `detect_changes` 无 partial/truncated。
