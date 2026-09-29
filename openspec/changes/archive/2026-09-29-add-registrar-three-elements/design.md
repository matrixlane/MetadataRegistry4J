# Design — Registrar 三要素（个人/角色/组织）编辑与初始化

## 1. 目标

让 Registrar 三要素（个人姓名、角色、组织）成为独立的、可编辑的列与字段，并在初始化阶段可直接填写并创建对象、关联。从而修复当前：

- 注册人员列表的"名称"列 fallback 到组织机构名（恰好与 RA 名撞字掩盖了真问题）
- 编辑表单无法编辑 Role
- init 阶段不创建 Individual 与 Role，依赖后续流程补全

## 2. 数据形态与复用

按 ISO/IEC 11179-3:2023 §6.3，三要素各自对应独立实体，本变更不引入新表：

| 要素 | 已存在实体 | 字段 |
| --- | --- | --- |
| 个人姓名 | `Individual` | `name (Sign)`、`identifier`、`title`、`mailAddress`、`emailAddress`、`phoneNumber`、`avatar` |
| 角色 | `Role` | `roleName (String)`、`mailAddress`、`emailAddress`、`phoneNumber` |
| 组织 | `Organization` | `name (List<Sign>)`、`mailAddress`、`emailAddress`、`phoneNumber`、`webAddress` |

`Contact` / `Registrar` 已是 `individual_id / role_id / organization_id` 三外键，对应三个实体已存在。零 schema 变更。

## 3. 后端设计

### 3.1 `RegistrarService.applyRequest`(RegistrarRequest)

扩展为同时支持 role。结构与既有 individualId/organizationId 处理一致：

```java
if (request.roleId() != null) {
    Role r = roleRepository.findById(request.roleId())
            .orElseThrow(...);
    entity.setRole(r);
} else {
    entity.setRole(null); // 与 add-submitter-contact-identity 兼容，角色可清空
}

if (request.individualId() != null) { ... }
if (request.organizationId() != null) { ... }
```

行为变更要点：
- **role** 现在可被 `applyRequest` 处理（之前完全忽略）。
- **organization** 必填（init 不变空）。
- **individual** 允许为 null。

### 3.2 `RegistrarService.createRegistrarForUser`

签名扩展：

```java
public Registrar createRegistrarForUser(
    UUID accountId,
    String individualName,    // 可为 null
    String roleName,          // 必填
    UUID organizationId,      // 必填；通常 = RA.id
    RegistrationAuthority ra
)
```

实现：
1. 解析 account。
2. **Individual**：
   - 若 `individualName` 非空 → 创建 `Individual { name = Sign.of(individualName.trim()), identifier = account.username }` 保存
   - 取出持久化 ID 用于 set 关联
3. **Role**：
   - 按 `roleName.trim()` 在 `roleRepository.findByRoleNameIgnoreCase(...)` 查重
   - 不存在 → 创建 `Role { roleName = roleName.trim() }` 保存
   - 取出 ID 用于 set 关联
4. **Registrar**：
   - 创建实体 `identifier = username + "-registrar"`
   - `setIndividual(individual)`、`setRole(role)`
   - `setOrganization(organizationId == null ? ra : organizationRepo.findById(...))`
   - `setRegistrationAuthority(ra)`
5. `contactAccountBindingService.bind(saved.id, account.id)`
6. 返回 saved。

边界：
- `organizationId` 为 null 时回退到 `ra`，PERSONAL 版主路径。
- `roleName` 为 null/空 → 抛业务错误（不可降级，避免静默丢字段）。
- `individualName` 为 null → 不创建 Individual；列表"姓名"列为空。

### 3.3 `RegistrarRequest`

增加 `roleId`：

```java
public record RegistrarRequest(
    @NotBlank String identifier,
    UUID registrationAuthorityId,
    UUID userId,
    UUID organizationId,        // 必填
    UUID individualId,          // 可空
    UUID roleId                 // 可空（角色可清空）
) {}
```

校验：
- `identifier` 非空（既有）
- `organizationId` 必填
- `individualId` / `roleId` 可空（其中 role 清空合法）

### 3.4 `InitWizardService.setup` 与 DTO

`InitWizardSetupRequest` 增加 2 字段：

```java
String adminIndividualName,  // 可空；PERSONAL 不兜底
String adminRoleName         // 必填；PERSONAL 默认 "注册管理员"
```

所属组织不设请求字段：`RegistrationAuthority extends Organization` 且 Organization 用 `@Inheritance(JOINED)` 共表，注册机构本身即组织；且初始化拦截器在系统未初始化时屏蔽 `/organizations/**`，RA 行又要到 setup 最后阶段才落库——向导执行注册机构步骤时系统中不存在任何可选组织，TEAM 版"从已有组织列表选择"是必然失败的需求。故组织恒取 `ra.getId()`。

`InitWizardService` 落地：
- PERSONAL 版本：上述两字段缺省时填默认值。
- TEAM 版本：用表单值；`selfAsRegistrar` 为 true 时 adminRoleName 必填校验。
- 任一版本：`resolvedAdminOrgId = ra.getId()`，不读请求参数。

必填校验 MUST 置于 `selfAsRegistrar` 判定之内——开关关闭即不创建 Registrar，不应要求其要素。现有实现的 TEAM 校验块整体位于 `if (edition == TEAM)` 内、与 `selfAsRegistrar` 无关，导致关闭开关后仍被要求填角色和组织，属缺陷一并修正。

调用：`registrarService.createRegistrarForUser(accountId, individualName, roleName, ra.getId(), ra)`。

### 3.5 兼容性

- 既有 `add-submitter-contact-identity` "Registrar 行仅允许编辑 Role"约束将在 `active-registrar-ownership` 上变更为允许三要素编辑；行为兼容：原用例新增"角色可清空"仍通过。
- 既有 `individual-identifier/spec.md:43-48` "首次保存个人资料时回填 Individual 到 Registrar"兼容：init 已绑定 individual 时不再触发回填；未绑定时保持原规约。
- 既有 `RegistrarList` UI 响应 DTO 不变（早就有 `individualName`/`roleName`/`organizationName` 字段）。

## 4. 前端设计

### 4.1 `RegistrarList.vue`

原：

```vue
<el-table-column label="名称">
  <template #default="{ row }">
    {{ row.individualName || row.organizationName || row.identifier || '-' }}
  </template>
</el-table-column>
```

改为：

```vue
<el-table-column label="姓名" prop="individualName" />
<el-table-column label="角色" prop="roleName" />
<el-table-column label="组织" prop="organizationName" />
```

空值显示"—"由 el-table 默认行为处理；删除 fallback 链。

### 4.2 `ResourceFormDialog` 的 #fields slot

替换 `titleZh/titleEn` 为三要素：

```vue
<el-form-item label="个人姓名">
  <el-input v-model="form.individualName" />
</el-form-item>
<el-form-item label="角色">
  <el-select v-model="form.roleId" clearable filterable>
    <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
  </el-select>
</el-form-item>
<el-form-item label="组织" required>
  <el-select v-model="form.organizationId" filterable>
    <el-option v-for="o in organizationOptions" :key="o.id" :label="o.name" :value="o.id" />
  </el-select>
</el-form-item>
```

数据来源：
- `roleOptions = GET /api/v1/roles/options`（已存在端点）
- `organizationOptions = GET /api/v1/organizations/options`（需确认是否已有；若无，在 web 模块新增 options-only 端点）

请求载荷：`{ identifier, registrationAuthorityId, userId, organizationId, individualId, roleId }`。

### 4.3 `InitWizard.vue`

注册机构步骤（在 `registrationAuthorityEnglishName` / `registrationAuthorityEmail` 之后、追加到 wizard-settings-box 之前）增加：

- 注册管理员姓名（PERSONAL 默认"注册管理员"，TEAM 显式填）
- 注册管理员角色（PERSONAL 默认"注册管理员"，TEAM 显式填）
- 所属组织（PERSONAL 默认锁定为当前 RA，不可改；TEAM 显式选）

提交时把三字段加到 `payload` 并经 `setup` 落到后端。

## 5. 验收

### 后端
- `RegistrarServiceTest.createRegistrarForUser_*`：
  - 三字段全空 → Individual 不创建，Role 命中已存在复用
  - individualName="李雷" → 创建 Individual 且关联
  - roleName="注册管理员" 重复 → 复用
  - organizationId=null → 回退到 RA
- `InitWizardServiceIntegrationTest#setup_personalEdition_*`：验证 init 时三字段写入 Individual / Role。

### API / E2E
- `POST /api/v1/registrars`：payload 包含 `roleId` 落到 Contact.role
- 编辑表单 round-trip：列表→编辑→列表（三列均为期望值）

### 前端
- `RegistrarList.vue` 列表 5 列；fallback 链不存在；空值"—"
- 编辑对话框：3 字段（input/select/select）正常打开、保存

## 6. 风险与回退

- **数据兼容**：已初始化的库中 `admin-registrar` 仍无 Individual/Role。本变更后该行三列为空，用户在编辑表单或"我的"中补完；不自动迁移，避免用户预期之外的"凭空补出内容"。
- **回退**：所有变更均为本 OpenSpec change 范围，回退即 OpenSpec archive 流程；后端 service 行为是单向扩展。
