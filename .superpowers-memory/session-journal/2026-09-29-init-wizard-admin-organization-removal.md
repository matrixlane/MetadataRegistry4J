# 2026-09-29 初始化向导「所属组织」下拉移除（规划阶段）

## 触发

用户对初始化向导页顶部红色提示「系统尚未完成初始化，请按向导完成配置」提出质疑：既然已经在初始化，为何还要这种提示。

## 已交付（代码）

`metadata-registry-ui/src/utils/request.ts` —— 删除 499 分支在 `/init-wizard` 上的 `ElMessage.error` 兜底。

原实现在向导页放弃整页跳转（避免触发 `beforeunload` 原生弹窗）后用 toast 顶替，而该 toast 是同义反复（用户就在向导里），且单方面绕过了 `silenceCount` 抑制——同一文件其余三处报错路径都检查了。

验证（Playwright A/B 对照，注入有效 token 复现"已登录但未初始化"）：

| | `/organizations/options` | 页面提示 |
|---|---|---|
| 修改前 | 403 | 系统尚未完成初始化，请按向导完成配置 |
| 修改后 | 403 | 无任何提示 |

403 依旧发生，只去掉了前端反应。`npm run build`（含 vue-tsc）通过、vitest 262/262、lint 0 error。

**踩坑**：第一版验证脚本是假阴性——Element Plus 默认 3s 自动消失，`networkidle` 后再采样已经看不到；且 MutationObserver 挂在 `document.documentElement` 上，而 `addInitScript` 在 document-start 执行时它为 null，observer 根本没装上。两处修正后对照组才正确抓到 toast。

## 根因（比提示本身更严重）

追 `/organizations/options` 的 403 来源，查出三个缺陷：

1. **`RegistrationAuthority extends Organization`**，`Organization` 为 `@Inheritance(JOINED)` 共表 → RA 本身就是组织。
2. **`InitializationWizardInterceptor.java:21-30` 白名单不含 `/organizations/**`** → 未初始化时无法创建组织；且 RA 行到 `InitWizardService.java:247` 才首次落库（向导最后一步）。故注册机构步骤执行时，系统里存在的 Organization 有且只有正在创建的那一个，TEAM 版下拉在任何时刻都不可能选到别的东西。
3. **TEAM 版要素校验未与 `selfAsRegistrar` 联动**：`InitWizardService.java:207` 的校验块在 `if (edition == TEAM)` 内、与 `selfAsRegistrar` 无关，而 `:250` 才判断该开关。TEAM 版关闭「设为注册人员」后 Registrar 不会创建，用户却仍被强制要求填角色和组织。

结果：spec 条款「TEAM 版 MUST 从已有组织列表中选择」在全新部署时必然违反——团队版向导走不下去。

## 决策

注册管理员的所属组织恒等于本次创建的 Registration_Authority，两个版本均不暴露选择入口。姓名与角色的填写义务以「确实要创建 Registrar」为前提。

边界：仅移除初始化阶段入口。初始化完成后 `/registrars/:id` 编辑页的组织下拉保留（那时确有多个组织）。

## 产出物

- `docs/superpowers/specs/2026-09-29-init-wizard-admin-organization-removal-design.md`
- 修正既有在途 change `add-registrar-three-elements`（tasks 全未勾选，不另开 change）：`specs/registrar-identity-elements/spec.md`、`specs/system-initialization/spec.md`、`design.md`、`tasks.md`（2.2 / 6.1 / 8.3）
- `openspec validate add-registrar-three-elements --strict` → valid；status 4/4

## 待决

- `add-registrar-three-elements` 工作区已有大量未提交实现，tasks 勾选状态与代码脱节。
- 建议开 repo-local worktree 隔离。

---

# 实施（续）

## 归因：既有的 2 失败 + 3 错误

`InitWizardServiceIntegrationTest` 的 5 个红测**同源**：`add-registrar-three-elements` 加了 TEAM 角色必填校验，但没同步测试——它们都是 `selfAsRegistrar=true` + `adminRoleName=null`。不是并行改动惹的祸。

TDD：先加 3 个用例（无组织字段可成功 / Registrar 组织恒等于 RA / 关闭 selfAsRegistrar 要素留空不报错），确认全红且红的正是 `注册管理员所属组织不能为空` 与 `注册管理员角色不能为空`，再改实现。

## 改动

- `InitWizardSetupRequest` 删除 `adminOrganizationId`
- `InitWizardService`：删所属组织校验；角色必填校验移出 `if (edition == TEAM)`、改为 `if (selfAsRegistrar && 空)`；`createRegistrarForUser` 组织恒传 `ra.getId()`
- `InitWizard.vue`：删下拉、`organizationApi.options()` 调用、`OrganizationOption`/`organizationOptions`、payload 字段；`nextStep` 的 TEAM 分支加 `form.selfAsRegistrar` 前提
- 同步 3 个后端测试文件的位置参数；修 1 个过时断言（`getRole()==null` → 按 `adminRoleName` 断言已关联）

## 顺带修掉的既有 e2e 缺陷（init-wizard.spec.ts 原本 2/3 红）

1. **无 `/languages` mock** → 文档语言下拉空，TEAM 版卡死
2. **无 `/startup/status` mock** → 启动等待屏依赖真实后端，后端不在即整页超时（本地 8181 在排查期间已停止监听）
3. **陈旧 selector**：密码 placeholder `6-20 个字符`→`8-64 个字符`；姓名 placeholder 按表单项标签定位（原按文案匹配已失效）；语言选项按中文名 `英语` 而非 `en-US` 匹配（Element Plus 渲染 label）

改完 3/3 绿，且从 35s（含 30s 超时）降到 7s——不再依赖真实后端。另加 fail-fast 路由：向导一旦请求 `/organizations/options` 即判回归失败。

## selfAsRegistrar 缺省语义（两次修正，最终定案）

**第一次**：评审时我提出「null 视为关闭 → 静默不创建 Registrar 是否合理」，用户回「应为 true，除非明确指定不作为 Registrar」。据此把 `Boolean.TRUE.equals(...)` 改为 `!Boolean.FALSE.equals(...)`，并补 2 个用例 + 回归敏感性验证（退回旧写法恰好 2 个变红）。

**第二次（最终）**：用户澄清是理解有误——**API 与界面默认分离**：

- **API**：省略（null）即不创建 Registrar，保持入参简洁 → 维持 `Boolean.TRUE.equals(request.selfAsRegistrar())` 原写法
- **界面**：缺省呈现为开启 → 前端 `form.selfAsRegistrar` 初值 `true`，`callSetup` 经 `...base` 始终显式提交

**教训**：这类"缺省语义"问题在读代码时两种解读都说得通（`Boolean.TRUE.equals` 与 `!Boolean.FALSE.equals` 在 null 上相反）。我不该自行推断并实施，应在动手前把两种语义及其后果摆出来让用户定。

最终产物：
- 后端保留原写法，仅注释写明"缺省视为关闭以保持 API 简洁；界面 MUST 显式提交且缺省呈现为开启"
- 用例 `setup_teamEdition_selfAsRegistrarNull_treatedAsOff`：省略与显式 false 同义，不建 Registrar、不校验要素
- 两份 spec 补「省略与显式关闭同义」+「界面缺省为开启」两个 Scenario，并要求前端 MUST 显式提交、MUST NOT 依赖 API 缺省语义
- `InitWizardServiceIntegrationTest` 最终 13/13（原 12 + null 同义 1；两次修正共新增 3 个，撤回 2 个）

## 代码评审


自查发现并修正 3 项：

1. **代码注释里写了硬编码行号**「（见 :252）」——行号会漂移，改为纯文字说明。
2. **tasks.md 夸大陈述**：原写「e2e 验证了 TEAM 关闭开关后要素留空可进入下一步」，但那条 e2e 并不存在。已补 `团队版关闭「设为注册人员」后要素留空仍可完成注册机构步骤`（4/4 绿）。
3. **补的 e2e 一开始只走到命名空间就结束**，`mockWizardApis` 里新增的 payload 断言根本不触发（等于死代码）。已补完后续步骤直到 commit。

两条断言的有效性都做了实证，不靠推断：

- **fail-fast 路由**：探针实测 route handler 抛错确实使测试失败（`Error: 探针触发` 直接作为失败抛出），非静默装饰。
- **payload 断言**：把 `expect(postData.selfAsRegistrar).toBe(false)` 临时改成错误值，确认恰好那一个测试变红，随后还原。

## 最终验证

| 项 | 结果 |
|---|---|
| `InitWizardServiceIntegrationTest` | 12/12 绿 |
| `InitWizardControllerTest` + `RegistryContextBootstrapServiceTest` | 26/26 绿 |
| 后端全量 | 1333 跑 / 6 failures / 9 errors —— 全部与本次改动无关（归因见上） |
| 前端 vitest | 28 文件 / 266 用例全绿 |
| `npm run build`（含 vue-tsc） | 通过 |
| `init-wizard.spec.ts` e2e | 4/4 绿 |
| `openspec validate --strict` | valid |

## 边界复查

主 spec（`openspec/specs/`）中无「从已有组织列表中选择」旧条款（该 change 尚未归档，正确）。前端残留的「所属组织」文案全部属于 `contactIdentity` / `itemForm` 等运行期编辑页的 i18n 键，向导用硬编码中文，不受本次改动影响——符合 spec 中「仅移除初始化阶段入口」的边界。

## 归档

`openspec archive add-registrar-three-elements --yes` → `2026-09-29-add-registrar-three-elements`

合并结果：+6 需求、~2 修改、-0。四个 spec 全部校验通过：
`registrar-identity-elements`（新建，5 需求）、`system-initialization`（+1）、`contact-identity-management`（~1）、`user-profile-individual`（~1）。

全仓库已无「TEAM 版 MUST 从已有组织列表中选择」旧条款。

归档前按代码证据校准了 tasks.md 的勾选状态（此前与代码脱节）：1.1/1.2/2.1/3.1/4.1/5.1/5.2/7.3/7.4/8.4 经核对已落地并勾选，最终 12/18。每条勾选都附了核对依据，并把发现的缺口写进任务注记（如 `RegistrarControllerValidationTest` 从未新建、5.1 的 round-trip 无专门单测）。

**未随归档完成（6 项）**：3.2 round-trip 验证、7.1 openapi.json 重生成（该文件三个字段一个都没有，生成于在途 change 加字段之前）、7.2 架构文档未述三要素、8.1 后端全量未全绿（15 个既有失败）、8.2/8.3 真实后端冒烟（本地 8181 未恢复）。

`openspec validate --specs --strict` 全库 29 passed / 32 failed——**失败项全在本 change 未触及的 spec**（acceptability-select、acp-agent-protocol、ai-*、database-migration 等），属仓库既有状态。

## 未完成

- `docs/technical/api/openapi.json` 三个字段一个都没有（生成于在途 change 加字段之前），本次无需重新生成，待整个 change 落地时统一处理
- tasks 8.3 真实后端冒烟（role 已存在复用、注册人员列表"组织"列渲染）待后端起来后补


## 剩余 15 个失败的归因（重要）

`RegistrarServiceTest`(6) + `WorkspaceServiceIntegrationTest`(9)。证据：

- HEAD 的 `RegistrarService.create()` 已含 `applyAccountBinding(saved.getId(), ...)`，工作区未改此处
- `RegistrarServiceTest` 的未提交 diff **只有** `Sign` API 迁移（`getName().add()`→`setName()`）与一处 Account username，未碰 `applyRequest_*` 的 mock
- 那 6 个测试在 HEAD 就 `service.create(req)` 而未给 `save()` 打桩 → Mockito 返回 null → NPE

之所以长期未被察觉：**HEAD 本身编译不过**（`RegistrarService.java:108` 对单个 `Sign` 调 `.clear()/.add()`，工作区已修）。整套测试从未在 HEAD 上跑通，故无法用 HEAD 作基线做 A/B——最终改用 `git show HEAD:<file>` 逐处比对完成归因。

## 未完成

- `docs/technical/api/openapi.json` 三个字段一个都没有（生成于在途 change 加字段之前），本次无需重新生成，待整个 change 落地时统一处理
- tasks 8.3 真实后端冒烟（role 已存在复用、注册人员列表"组织"列渲染）待后端起来后补

