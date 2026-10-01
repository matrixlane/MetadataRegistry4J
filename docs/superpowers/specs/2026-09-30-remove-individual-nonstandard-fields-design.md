# 设计：移除 Individual 非标准字段（identifier 删除 + avatar 迁至 ext.account）

日期：2026-09-30
状态：待用户确认（含 3 个决策点的推荐默认值）
关联变更：OpenSpec 新变更 `remove-individual-nonstandard-fields`；作废 `add-individual-identifier`（已 `git rm`，未提交）

## 1. 背景与依据

项目原则（用户 2026-09-30 裁定）：**schema 为 mdr 的表必须严格按照 ISO/IEC 11179 标准中定义过的内容设计；标准定义之外的部分统一使用 ext schema 作为扩展表存在。内部标识（id 列）和审计字段除外。**

ISO/IEC 11179-3:2023 §6.3.2 Individual 类的属性（Table 1 — Attributes of Individual class）仅五个：

| 属性 | 多重性 | 数据类型 |
| --- | --- | --- |
| name | 1 | Sign (6.2.10) |
| title | 0..1 | String (6.2.11) |
| mail_address | 0..1 | Postal_Address (6.2.9) |
| email_address | 0..* | String (6.2.11) |
| phone_number | 0..* | Phone_Number (6.2.8) |

当前 `mdr.individual` 多出两个非标准列：

- `identifier`（V20260917001 加入，OpenSpec 变更 `add-individual-identifier`）——标准无此属性
- `avatar`（V20260901016 加入）——标准无此属性，且语义上是账号级属性，放错了宿主

`add-individual-identifier` 变更（2026-09-18 提交 `9d2a46c`）用户已裁定取消，随本设计一并回滚。该变更从未归档进主 specs，回滚不影响 `openspec/specs/` 主干。

## 2. 目标 / 非目标

### 目标

1. `mdr.individual` 仅保留标准属性 + id（+ 审计字段约定，本表当前无额外审计列）
2. `identifier` 列与全部相关代码移除
3. `avatar` 迁移为 `ext.account.avatar`（account 表本就在 ext schema，见 V20260901014 schema 拆分）
4. 存量 avatar 数据搬迁（按 `account.individual_id` 关联复制）

### 非目标（本次不动）

- `mdr.registrar.identifier`（同为非标准字段，Registrar extends Contact §6.3.5）——记入 backlog 另行审计处理
- 其他 mdr 表的标准符合性全面审计——记入 backlog
- `Individual` 其余标准属性（name/title/mail/email/phone）不动

## 3. 决策点（推荐默认值，待确认）

### D1：头像与 Individual 解耦语义

**推荐：彻底解耦**。现行为"上传头像时若账号无 Individual 记录会先创建一条 Individual"（`AuthServiceAvatarTest` 用例即此语义）。avatar 迁至 account 后，头像纯账号级属性，上传头像**不再创建 Individual**。账号无 Individual 时资料页姓名等仍走原有 updateMyIndividual 链路，互不影响。

### D2：Avatar API 路径

**推荐：改名 `/auth/me/avatar`**（现为 `/auth/me/individual/avatar`）。存储宿主已从 Individual 变为 Account，路径继续带 `individual` 是对语义的误导。改动面：`AuthController` 3 个端点、`MyProfile.vue` 3 处调用、openapi 再生成。若用户倾向零 UI 改动，可保持原路径，代码注释标注语义迁移。

### D3：Registrar.identifier 处置

**推荐：本次不动，记 backlog**。该字段当前承担注册人员列表名称兜底显示（`individualName || organizationName || identifier`），移除会连带 RegistrarService/DTO/UI 兜底链改动，超出本次明确指令范围。在 `.superpowers-memory/KNOWN_FAILURES.md`（或 LEARNING_BACKLOG）记录"mdr 表非标准字段审计"条目，含 Registrar.identifier。

## 4. 方案细节（方案 A：硬移除 + 数据搬迁）

### 4.1 迁移（新增三方言 `V20260930001__Remove_individual_nonstandard_fields.sql`）

SQLite：

```sql
alter table account add column avatar text;
update account set avatar = (select avatar from individual where individual.id = account.individual_id)
 where individual_id is not null;
alter table individual drop column avatar;
alter table individual drop column identifier;
```

SQL Server（ext 表按既有惯例 schema 限定 + 守卫）：

```sql
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ext') EXEC('CREATE SCHEMA ext');
IF COL_LENGTH('ext.account', 'avatar') IS NULL
    ALTER TABLE ext.account ADD avatar NVARCHAR(MAX) NULL;
UPDATE ext.account SET avatar = (SELECT avatar FROM mdr.individual WHERE mdr.individual.id = ext.account.individual_id)
 WHERE individual_id IS NOT NULL;
IF COL_LENGTH('mdr.individual', 'avatar') IS NOT NULL ALTER TABLE mdr.individual DROP COLUMN avatar;
IF COL_LENGTH('mdr.individual', 'identifier') IS NOT NULL ALTER TABLE mdr.individual DROP COLUMN identifier;
```

达梦 DM8：仿 SQL Server 写法（DM8 支持窗口函数与 `ALTER TABLE ... DROP COLUMN`；列存在性守卫按达梦字典视图适配）。

要点：

- 数据搬迁用**关联子查询**，三方言通用，避免方言特定的 `UPDATE ... FROM`
- SQLite 用**不带 IF EXISTS** 的 `DROP COLUMN`（3.35+ 支持；项目既有失败先例是 `DROP COLUMN IF EXISTS`，两者不同）。若实测驱动内置 SQLite < 3.35，回退重建表法（V20260921001 模式，需重建 account.individual_id 与 contact 侧 FK）
- 未绑定账号的孤儿 Individual 的 avatar 随列删除丢失（已知可接受，该类行属 2026-09-30 清理对象）

### 4.2 实体与仓库

- `Individual`（common-facilities）：删除 `identifier`、`avatar` 两个字段
- `Account`：新增 `@Lob @Column(name = "avatar") private String avatar;`

### 4.3 服务与 DTO

- `IndividualService`：list 关键词搜索去掉 identifier 谓词（保留 name/title）；`applyRequest` 去掉 identifier 处理；删 `generateIdentifier()`；`toResponse` 去掉 identifier
- `AuthService.updateMyIndividual`：删 3 处 `setIdentifier(...)`；保留 Individual 复用/新建与 Registrar 回填逻辑（2026-09-30 收敛语义不受影响）
- `AuthService` 头像三方法（getMyAvatar / updateMyAvatar / deleteMyAvatar）：从 `user.getIndividual().getAvatar()` 改为 `user.getAvatar()`；**不再在缺 Individual 时创建 Individual**
- `RegistrarService`：删 `individual.setIdentifier(account.getUsername())`
- DTO：`IndividualRequest` / `IndividualResponse` 删 identifier 字段（API 契约变更）
- `AccountProfileResponse` / login/me 响应是否带 avatar：当前头像走独立端点，me 响应不含 avatar——保持不变

### 4.4 API

- `GET/PUT/DELETE /auth/me/avatar`（D2 推荐改名；旧路径不保留重定向——本项目前后端同源发布，无第三方消费者）
- openapi.json/yaml 再生成

### 4.5 UI

- `IndividualList.vue`：删标识符列（`prop="identifier"`）与表单项
- `MyProfile.vue`：头像端点路径改 `/auth/me/avatar`（若 D2 选保守则不动）
- 其余页面无 identifier/avatar 引用（已全库核实）

### 4.6 OpenSpec 处理

- `add-individual-identifier`：**已 `git rm -r` 取消**（未归档变更，主 specs 无其痕迹，直接删除即完成作废）
- 新建变更 `remove-individual-nonstandard-fields`：proposal → design → specs（新能力 `individual-standard-compliance`：mdr.individual 列集与 §6.3.2 严格一致、扩展属性归宿规则、avatar 账号级语义）→ tasks

### 4.7 测试

- TDD 顺序：先改 `AuthServiceAvatarTest`（Individual 语义 → Account 语义）、`AuthServiceTest`（去 identifier 断言）、`IndividualServiceIntegrationTest:115`（identifier 断言删除/替换）为红，再实施转绿
- 新增：`Account.avatar` 字段持久化由既有头像用例覆盖
- 全量：`./mvnw -pl metadata-registry-web -am test`、`vitest`、`npm run build`

## 5. 验证计划

1. 三方言迁移在 scratch 副本库应用：列删除、account.avatar 数据复制（造 1 个带 avatar 的账号 + 1 条孤儿 individual 验证丢失语义）、幂等重放
2. SQL Server 真实库（localhost）随后端重启自动应用，校验列不存在 + avatar 已搬迁
3. 后端全量测试 + 前端构建 + vitest
4. 手工冒烟：资料页头像上传/回显/删除（无 Individual 的账号也能传头像）、管理端 Individual 列表无标识符列、关键词搜索正常
5. `openspec validate`、`gitnexus detect-changes --scope all`（提交前）
6. 达梦：环境不可达则按惯例记"待实测"

## 6. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| SQL Server 该实例对 mdr.individual 曾有 QP 错误 8624 史（DELETE 触发，2026-09-30 换表法绕过） | 本次是 DDL DROP COLUMN 非 DML；若触发同类问题，启用换表法重建（备份→新表→重命名→补 PK/FK，已有一遍实操经验） |
| SQLite 驱动内置版本 < 3.35 不支持 DROP COLUMN | 迁移任务第一步先在 scratch 库实测；失败则切重建表法 |
| 工作区存在并行会话未提交 WIP（ItemFormDialog.vue、InitWizardService 等） | 本变更只碰 Individual/Account/头像链路，与 WIP 文件零重叠；提交按变更分组 |
| enforce-language-identification-uniqueness 的 V20260926001 已先于本变更存在于主树 | 两份迁移版本号段不冲突（26001 < 30001），Flyway 顺序无交叉 |

## 7. Backlog（本设计记录，不实施）

- `mdr.registrar.identifier` 非标准字段移除（连带 RegistrarList 名称兜底链重构）
- mdr 表标准符合性全面审计（同类候选：Account 已在 ext 无需处理；Organization/Contact/Registration_Authority 等的扩展列逐一核对 §6.3 各表）
