# Registrar 三要素（个人/角色/组织）编辑与初始化设计

## 背景

初始化向导完成后注册人员列表的"名称"列 fallback 到组织机构名（`metadata-registry-ui/src/views/registrar/RegistrarList.vue:16`），既不是个人姓名也不是角色名：

```vue
{{ row.individualName || row.organizationName || row.identifier || '-' }}
```

这是巧合性表象：之前 RA 默认名恰为"注册管理员"，与角色概念名撞字。RA 默认名改为"默认注册机构"后，列表即显示 RA 名，暴露出语义错位。

`RegistrarService.createRegistrarForUser`（`metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/RegistrarService.java:142-160`）当前**故意不创建** Individual 与 Role，依赖：

- `add-individual-identifier`：用户首次"我的 → 编辑个人资料"时回填 Individual 到 Registrar
- `add-submitter-contact-identity`：Role 由用户后续在 `/role` 页创建并关联

`RegistrarRequest`（`dto/registrar/RegistrarRequest.java`）当前没有 `roleId` 字段，`RegistrarService.applyRequest`（:82-109）也未处理 Role——编辑表单无法编辑角色字段。

## 需求（用户明确给出）

1. **列表**：分三列独立展示姓名、角色、组织（不再 fallback）。
2. **编辑**：直接编辑个人姓名；选择角色（下拉）；选择组织（下拉）。
3. **初始化**：admin 选择成为 Registrar 时，让用户直接填写三要素并创建对象、关联。

## 决策

### D1. 数据形态（已达成一致）

按 ISO/IEC 11179-3:2023 §6.3 三要素各自为独立实体，不退化到字符串字面值。

| 要素 | 实体 | 必填 |
| --- | --- | --- |
| 个人（姓名） | `Individual`（已存在） | 个人版可选；团队版强烈建议 |
| 角色（名称） | `Role`（已存在） | 必填，默认"注册管理员" |
| 组织（名称） | `Organization`（已存在） | 必填，默认 = 当前创建的 RA |

### D2. 初始化向导形态

- **个人姓名**：输入框（`el-input`），可选；后端非空时创建 `Individual` 实体，`name = Sign.of(input.trim())`，`identifier = account.getUsername()`。
- **角色名称**：输入框（`el-input`），必填，默认"注册管理员"。后端按 name 唯一：
  - 已存在同名 Role → 复用（按 `RoleService.assertNameAvailable` 的反义）
  - 不存在 → 新建 Role 实体（`roleName = input.trim()`）
- **组织（联系所属）**：下拉（`el-select`），默认 = 当前创建的 RA（不可改），团队版可改。

### D3. 编辑表单形态

| 字段 | 类型 | 来源/校验 |
| --- | --- | --- |
| identifier | input 必填 | 既有 |
| 个人姓名 | input | 改 Individual.name，新建时创建 Individual；为空白即解绑 Individual |
| 角色 | select | 来源 `/api/v1/roles/options`；可清空（与 `add-submitter-contact-identity` 已落地"角色可清空"一致） |
| 组织 | select | 来源 `/api/v1/organizations/options`（如无该端点则新增一个 options-only 端点）；必填 |

### D4. 服务边界

- **`RegistrarService.applyRequest`** 增加 Role 处理（与既有 Individual/Organization 风格一致）。
- **`RegistrarService.createRegistrarForUser`** 接收 `individualName`、`roleName`、`organizationId`，改造：
  - 旧实现：只 `setIndividual(null)` 与 `setRole(null)`，靠 `add-individual-identifier` 流程补
  - 新实现：传参非空时，按 D2 创建实体并 set
  - 旧 `displayName` 参数降级为内部 derive（不再被调用方显式传）
- **`InitWizardService.setup`** 请求 DTO 增加 `individualName / roleName / organizationId`。
- **`RegistrarRequest`** 增加 `roleId`（与现有 `individualId / organizationId` 对称）。

### D5. 与既有 OpenSpec 变更的关系

- `add-individual-identifier/specs/individual-identifier/spec.md:43-48`：首次保存个人资料时回填规则不变，但**回填前置**改为"Registrar.individual 为空"（已满足，因为它本来就不强制绑定）。
  - 现有设计：init 不创建 → 首次保存时首次绑定
  - 新设计：init 可选创建 → 首次保存时仍是覆写（去重逻辑已落到 `add-individual-identifier`）
- `add-submitter-contact-identity`：初始化向导个人版自动成为 Registrar 无开关保持不变。本变更只改变 init 写入的素材，不改流程。

## 数据模型

零 schema 变更。`Individual`、`Role`、`Organization` 表已存在。

| 表 | 已用列 |
| --- | --- |
| `individual` | `name`, `identifier`, `title`, `mail_address`, `email_address`, `phone_number`, `avatar` |
| `role` | `role_name`, `mail_address`, `email_address`, `phone_number` |
| `organization` (继承 RA) | `name`, `mail_address`, `email_address`, `phone_number`, `web_address` |

## 验收

### 后端单元/集成
- `RegistrarServiceTest`：createRegistrarForUser 传入 individualName/roleName 时正确创建；roleName 命中已存在 Role 时复用不重建；organizationId 切换生效。
- `InitWizardServiceIntegrationTest#setup_personalEdition_*`：验证 init wizard 个人姓名/角色姓名落库到 Individual/Role。
- `RegistrarRequestValidationTest`：roleId/individualId/organizationId 形态校验。

### 后端 API
- `POST /api/v1/registrars` 与 `PUT /api/v1/registrars/{id}` 接受 `roleId`，落地到 Contact.role 字段。
- `GET /api/v1/registrars` 响应 `roleName` 已有，保持。

### 前端
- `RegistrarList.vue`：列顺序变为「标识符 | 姓名 | 角色 | 组织 | 操作」；fallback 链删除。
- `RegistrarList`（编辑 `ResourceFormDialog` 的 #fields slot）：替换"中文名称/英文名称"为 personalName input + role select + organization select。
- `InitWizard.vue`：注册机构步骤增加「注册管理员姓名（可选）」「注册管理员角色（默认"注册管理员"）」「注册管理员所属组织（默认新建 RA）」三字段；个人版强制写默认值；团队版显式填写。

### E2E
- `init-wizard.spec.ts`：验证三字段提交；个人版默认值；团队版 RA 改名。
- 新增或扩展 `registrar.spec.ts`（如有）：编辑表单三要素 round-trip。

## 风险与兼容性

- **存量数据兼容**：已初始化的库中 `admin-registrar` 没绑定 Individual/Role。本变更后用户再次访问注册人员页面，列表"姓名""角色"列显示为空（不 fallback）。用户可在编辑表单或"我的"中补完。
- **破坏面**：`add-individual-identifier/spec.md:43-48` 的回填行为需要追加一段兼容性说明："init 已写入的不再回填"——走 spec 修改流程（与本次同处一个 change）。
- **回退**：spec 修改可独立回退；后端 `createRegistrarForUser` 增加参数兼容旧调用方——实际没有旧调用方（仅 init wizard）。

## 待确认与不在范围内

- ❓ 编辑表单的个人姓名是改 Individual 还是另设机制？答：**直接改 Individual.name**。若 Individual 由多个 Contact 共享，会有连带更新——这是预期行为（个人姓名应当一致）。
- ❓ 角色是否允许编辑为清空？答：**允许**（与 `add-submitter-contact-identity` 已落地的"角色可清空"一致）。
- ❓ 组织是否允许编辑为清空？答：**不允许**——Registrar 必须挂在某个组织（通常为 RA）。
- ❌ 不在本变更范围：Organization CRUD 端点补全（当前已有最小集，本变更仅消费 `options()`）。
