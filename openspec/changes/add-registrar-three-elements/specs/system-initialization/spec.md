# system-initialization 增量（add-registrar-three-elements）

## ADDED Requirements

### Requirement: 初始化向导为管理员成为 Registrar 提供三要素填写入口

初始化向导的注册机构步骤 MUST 在管理员选择成为 Registrar 时（PERSONAL 版自动、TEAM 版勾选 selfAsRegistrar=true）提供三要素填写入口，提交时 setup 后端 MUST 按如下规则创建并关联对象：

- **注册管理员姓名**：`Individual` 字段。PERSONAL 版 SHALL 默认"注册管理员"，TEAM 版 MUST 由管理员显式输入；任一版本下该字段可空，提交时若非空 MUST 创建 `Individual { name = Sign.of(姓名), identifier = 账号用户名 }` 并 set 为该 Registrar 的 Individual。提交时若为空 MUST NOT 创建 Individual，但 MUST NOT 主动清除已有关联。
- **注册管理员角色**：`Role` 字段。PERSONAL 版 SHALL 默认"注册管理员"，TEAM 版 MUST 由管理员显式输入；该字段 MUST NOT 为空。提交时 MUST 创建 `Role { roleName = 角色名 }` 或复用已存在同名 Role（按 `roleName` 唯一性判定），并 set 为该 Registrar 的 Role。同名 Role 已存在时 MUST 复用而非新建。
- **注册管理员所属组织**：`Organization` 字段。PERSONAL 版 SHALL 默认且 MUST 锁定为当前创建的 Registration_Authority，TEAM 版 MUST 由管理员从已有组织列表中选择；该字段 MUST NOT 为空，提交时 MUST set 为该 Registrar 的 organization。

`/api/v1/init-wizard/setup` 请求 MUST 接受上述三字段（缺省视为 PERSONAL 默认值），setup 后端 MUST 在创建 Registrar 时按上述规则创建并 set 关联。该语义在 PERSONAL 与 TEAM 两版本之间 MUST 通过版本差异化策略保持一致：PERSONAL 版默认值由前端表单预填，TEAM 版默认值显式校验。

#### Scenario: PERSONAL 版三字段默认填充

- **WHEN** 管理员选择 PERSONAL 版
- **THEN** setup 后端对 `adminIndividualName` 与 `adminRoleName` 兜底默认值 `"注册管理员"`，无论前端是否传入或用户是否在前端显式修改（前端向导在前端预填占位字符串以提示用户）；后端创建 `Individual(name="注册管理员")` 与 `Role(roleName="注册管理员")` 并 set 关联到 Registrar；注册人员列表该行"姓名/角色"列均显示"注册管理员"

#### Scenario: PERSONAL 版姓名留空

- **WHEN** 管理员选择 PERSONAL 版且将注册管理员姓名清空
- **THEN** setup 后端兜底默认 `"注册管理员"` 仍生效（PERSONAL 版 MUST 始终有可显示的注册管理员姓名），列表"姓名"列显示"注册管理员"；用户若希望使用其他姓名 SHALL 显式输入非空字符串

#### Scenario: PERSONAL 版角色名已存在时复用

- **WHEN** 管理员选择 PERSONAL 版，系统中已存在 Role(`roleName="注册管理员"`)，未显式修改角色名
- **THEN** setup 后端 MUST NOT 新建 Role，复用既有 Role 并 set 关联到 Registrar

#### Scenario: TEAM 版三字段显式填写

- **WHEN** 管理员选择 TEAM 版并填写注册管理员姓名 `"李雷"`、角色 `"数据治理主管"`、组织（从下拉中选择）
- **THEN** setup 后端创建 `Individual(name="李雷")` 与 `Role(roleName="数据治理主管")`，按需 set organization；该 Registrar 行三列均展示对应值

#### Scenario: TEAM 版角色留空被拒绝

- **WHEN** 管理员选择 TEAM 版且未填写角色
- **THEN** setup 接口 MUST 返回业务校验错误，注册机构步骤 MUST NOT 进入下一步

#### Scenario: TEAM 版组织留空被拒绝

- **WHEN** 管理员选择 TEAM 版且未选择组织
- **THEN** setup 接口 MUST 返回业务校验错误，注册机构步骤 MUST NOT 进入下一步
