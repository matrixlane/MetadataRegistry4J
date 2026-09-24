# Session Journal — add-registrar-three-elements（2026-09-24）

## 背景

之前几轮把注册机构(Registration_Authority)与注册规范(Registry_Specification)的默认名字符串改成了
"默认注册机构" / "默认注册规范"。这暴露了一个独立的语义问题：

注册人员（Registrar）列表的"名称"列实际是从 `individualName || organizationName || identifier` fallback
来的——之前 RA 默认名是"注册管理员"，与角色概念名撞字掩盖了 fallback 真面目；现在 RA 默认名改为"默认注册机构"
后，列表显示 RA 名而不是真正的个人姓名或角色名。

## 用户要求（2026-09-24 本会话）

- 列表三列：姓名 / 角色 / 组织（不再 fallback）
- 编辑表单：直接编辑个人姓名 + 选择角色（下拉）+ 选择组织（下拉）
- 初始化阶段（admin 自动成为 Registrar 时）：直接填写并创建对象与关联

## 决策路径

按 `superpowers-openspec-superpowers-workflow` 强制流程推进：

1. 探索 + 澄清需求（一轮 user turn 即可收敛）
2. 写 `docs/superpowers/specs/2026-09-24-registrar-three-elements-design.md`
3. 创建 OpenSpec change `add-registrar-three-elements`，按依赖顺序产出 artifacts：
   - `proposal.md`（Why / What Changes / Capabilities / Impact / Out of Scope）
   - `design.md`（后端 + 前端 + 验收 + 风险）
   - `specs/registrar-identity-elements/spec.md`（新能力 6 requirement）
   - `specs/system-initialization/spec.md`（ADDED 1 requirement：初始化三要素）
   - `specs/contact-identity-management/spec.md`（MODIFIED：解除"仅允许编辑 Role"硬约束）
   - `specs/user-profile-individual/spec.md`（MODIFIED：Registrar 行编辑入口三要素）
   - `tasks.md`（8 组共 ~22 条）
4. `openspec validate add-registrar-three-elements --strict` 通过

## 关键技术决策

### 1. 三要素各自对应独立实体

按 ISO/IEC 11179-3 §6.3 三要素各自为独立实体（`Individual` / `Role` / `Organization`），不引入新表，零 schema 变更。

### 2. 后端签名扩展

`RegistrarService.createRegistrarForUser` 改为：

```java
public Registrar createRegistrarForUser(
    UUID accountId,
    String username,
    String individualName,    // 可空；空 = 不创建 Individual
    String roleName,          // 必填；按 name 唯一性查重，存在则复用
    UUID organizationId,      // 可空；空 = 回退到 RA
    RegistrationAuthority ra
)
```

`RegistrarRequest` 增 `roleId` 字段；`applyRequest` 补 Role 处理分支（null 即 setRole(null)，非空按 id 查）。

### 3. 兼容性约束

- `add-submitter-contact-identity` 已落地"Registrar 行仅允许修改 Role 关联"——本变更在 spec 层面把它改为允许同时编辑 Role / Individual / Organization 三要素，Role 仍可清空。
- `add-individual-identifier`（active change）的"首次保存个人资料时回填 Individual"前置条件是"individual 为空"——本变更后 init 阶段可能已绑定，Registrar.individual 非空时不再回填；未绑定时保持原规约。两者隐式兼容，无需修改 active change。

### 4. 前端 schema

- `RegistrarResponse` 加 `roleId` / `roleName` 字段（已有 roleName 但 DTO 字段集不完整；补 roleId）。
- `RegistrarRequest` 加 `roleId` + `organizationId @NotNull`。
- `MyContactRequest` 加 `individualId` 字段（"我的"联系人身份编辑时用）。
- 新增 `OrganizationController#options` 端点，供前端"组织"下拉。
- 前端 `RegistrarList.vue` 删 fallback 链，列结构「标识符 | 姓名 | 角色 | 组织」；编辑表单替换 titleZh/titleEn 为 individualName input + roleId/role select + organizationId/organization select。

## 验证

由于本会话环境无 Maven / Node 工具链（PATH 不含 `mvn`/`npm`），跑测试要等用户本地执行。

```bash
# 后端
cd metadata-registry-web
mvn test                                                # 全量
mvn -Dtest=InitWizardServiceIntegrationTest test         # 初始化关键测试
mvn -Dtest=ContactServiceMyContactsTest test             # 我的联系人三要素编辑

# 前端
cd metadata-registry-ui
npm run build                                           # 打包（含打包产物 dist → web/src/main/resources/static）
npx vitest run                                          # vitest 全量
npx playwright test --grep "init-wizard"                # e2e

# OpenSpec 校验
cd openspec && openspec validate add-registrar-three-elements --strict
```

预期：后端全绿 + 前端全绿 + e2e 路径（PERSONAL/TEAM）全绿。

## 实施阶段（本会话内）

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| A | 后端契约（RegistrarRequest/Response + applyRequest + Role 处理） | done |
| B | 后端初始化（createRegistrarForUser 扩签名 + InitWizardSetupRequest 三字段 + setup 透传） | done |
| C | ContactService.updateMyContact 对 Registrar 行放开三要素编辑 | done |
| D | 前端 RegistrarList 列表三列 | done |
| E | 前端编辑表单 + api/resources.ts organizationApi.options | done |
| F | 初始化向导三要素字段 + edition 切换默认填充 + TEAM 必填校验 | done |
| G | 文档 / openapi.json / session-journal / e2e 调整 | done |
| H | worktree 验证 + commit + 提示 continue-review | 进行中 |

## 风险与回退

- 既有 init 流程的 `admin-registrar` 仍未关联 Individual/Role（存量数据）；本变更不自动迁移，列表三列显示空值，等用户在编辑表单或"我的"中补完——这是预期行为，避免凭空补出内容。
- 后端 service 行为是单向扩展（新增参数兼容旧调用方；旧调用方仅 InitWizard，已改造）。
- 新增 spec `registrar-identity-elements` 5 个 requirement + 修订 3 个既有 spec 的 MODIFIED；归档时由 `openspec archive` 自动合并。

## 附：与 OpenSpec 的边界

本 change 涉及 4 个 spec：

| spec | 操作 |
| --- | --- |
| `registrar-identity-elements` | 新增（独立能力，6 requirement） |
| `system-initialization` | ADDED 1 requirement |
| `contact-identity-management` | MODIFIED 第 1 条 requirement |
| `user-profile-individual` | MODIFIED "我的"界面联系人身份管理区 requirement |

未涉及：`active-registrar-ownership`（无显式规约在 spec 中，仅是实现层面约束；本次以实现变更解决，无 spec 变动）。
未涉及：`individual-identifier`（active change 的"首次保存回填"语义由前置条件"individual 为空"隐式兼容）。
