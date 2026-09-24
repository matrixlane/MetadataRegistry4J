## Purpose

本能力定义注册人员（Registrar）三要素——个人（Individual）、角色（Role）、组织（Organization）——的完整管理契约：列表展示、编辑、初始化阶段填写并创建对象。明确三要素各自独立、互不耦合，列表展示与编辑表单不再依赖 fallback 语义。

依据 ISO/IEC 11179-3:2023 6.3（Individual / Organization / Role / Contact）与 11179-6:2023（Registrar 概念）。

## ADDED Requirements

### Requirement: 注册人员列表按三要素独立列展示

注册人员列表 MUST 显式按四列展示：「标识符」「姓名」「角色」「组织」。姓名 MUST 来源于所关联的 `Individual` 实体的 `name`；角色 MUST 来源于所关联的 `Role` 实体的 `roleName`；组织 MUST 来源于所关联的 `Organization` 实体的 `name`。该列表 MUST NOT 使用任何 fallback 链从其他字段派生显示内容（即姓名无值即留空、角色无值即留空、组织无值即留空）。

#### Scenario: 完整三要素的注册人员行

- **WHEN** 系统中存在一条 Registrar 且已关联 Individual、Role、Organization 实体
- **THEN** 列表该行"姓名"列显示 `Individual.name`，"角色"列显示 `Role.roleName`，"组织"列显示 `Organization.name`

#### Scenario: 三要素部分缺失的注册人员行

- **WHEN** 系统中存在一条 Registrar 仅关联了 Organization 而未关联 Individual 与 Role
- **THEN** 列表该行"姓名"列与"角色"列显示空值；"组织"列显示该 Organization.name；不许从其他字段派生填充

### Requirement: 联系人列表按三要素独立列展示

联系人列表 MUST 与注册人员列表的列结构一致：「标识符」「个人」「角色」「组织」。姓名 MUST 来源于所关联的 `Individual.name`；角色 MUST 来源于所关联的 `Role.roleName`；组织 MUST 来源于所关联的 `Organization.name`。列表 MUST NOT 使用任何 fallback 链从其他字段派生显示内容（任一列无值即留空，不许从其他列兜底）。两列表的列顺序 MUST 保持一致——便于用户跨页对照"哪些个人/组织/角色已存在"。

#### Scenario: 联系人行三要素完整展示

- **WHEN** 系统中存在一条普通 Contact（非 Registrar）且已关联 Individual、Role、Organization 实体
- **THEN** 列表该行"个人"列显示 `Individual.name`，"角色"列显示 `Role.roleName`，"组织"列显示 `Organization.name`

#### Scenario: 联系人行三要素缺失时的留空

- **WHEN** 系统中存在一条普通 Contact 仅关联 Organization，未关联 Individual 与 Role
- **THEN** 列表该行"个人"列与"角色"列显示空值；"组织"列显示该 Organization.name；列表不许从其他列派生填充

### Requirement: 注册人员编辑表单支持编辑三要素

编辑注册人员的表单 MUST 支持编辑三要素，且三要素在表单内各自独立、不耦合。具体形态：

- 个人姓名 MUST 为可输入文本框；表单提交时若该字段非空 MUST 创建 `Individual` 实体（或复用已存在的个体）并 set 为该 Registrar 的 Individual。提交时若该字段为空 MUST NOT 自动创建 Individual，但 MUST NOT 主动清除已有关联。
- 角色 MUST 为可下拉选择的控件，候选项 MUST 包含系统中现有全部 `Role`；表单提交时 MUST 把 `Role` 实体关联到该 Registrar 的 `Contact.role`。允许用户在编辑时清空角色。
- 组织 MUST 为可下拉选择的控件，候选项 MUST 包含系统中现有全部 `Organization`；表单提交时 MUST 把 `Organization` 实体关联到该 Registrar 的 `Contact.organization`，该关联 MUST NOT 被清空。

#### Scenario: 编辑注册人员切换个人姓名

- **WHEN** 用户编辑注册人员，将"个人姓名"由 `A` 改为 `B`
- **THEN** 该 Registrar 关联的 Individual 实体的 `name` 字段更新为 `B`，其他字段不受影响

#### Scenario: 编辑注册人员切换角色

- **WHEN** 用户编辑注册人员，从下拉中将角色由 `Role_X` 切换为 `Role_Y`
- **THEN** 该 Registrar 关联的 Role 实体由 `Role_X` 改为 `Role_Y`

#### Scenario: 编辑注册人员清空角色

- **WHEN** 用户编辑注册人员，主动将"角色"下拉清空
- **THEN** 该 Registrar 的 `Contact.role` 设为 `null`，不抛错

#### Scenario: 编辑注册人员切换组织

- **WHEN** 用户编辑注册人员，从下拉中将组织由 `Org_X` 切换为 `Org_Y`
- **THEN** 该 Registrar 关联的 Organization 实体由 `Org_X` 改为 `Org_Y`

### Requirement: 初始化向导为自动成为 Registrar 的管理员提供三要素填写入口

初始化向导的注册机构步骤 MUST 提供三个字段供管理员填写并成为该注册机构的 Registrar 时使用：

- 注册管理员姓名（PERSONAL 版 SHALL 默认"注册管理员"，TEAM 版 MUST 由管理员显式输入；可空，提交时若非空 MUST 创建 `Individual` 实体并关联）
- 注册管理员角色（PERSONAL 版 SHALL 默认"注册管理员"，TEAM 版 MUST 由管理员显式输入；必填，提交时 MUST 创建或复用同名 Role 实体并关联；同名 Role 已存在时 MUST 复用而非新建）
- 所属组织（PERSONAL 版 SHALL 默认且 MUST 锁定为当前创建的 Registration_Authority；TEAM 版 MUST 由管理员从已有组织列表中选择；任一版本该字段 MUST NOT 为空）

初始化向导在注册机构步骤提交时 MUST 把这三字段一并提交到 `setup` 接口，setup 后端 MUST 按上述规则创建个体、角色并关联到新创建的 Registrar。

#### Scenario: PERSONAL 版初始化三字段默认值

- **WHEN** 管理员选择 PERSONAL 版且未显式修改注册管理员姓名与角色字段
- **THEN** 提交时 setup 使用 `"注册管理员"` 作为姓名与角色默认字符串，后端创建 `Individual(name="注册管理员")` 与 `Role(roleName="注册管理员")` 并关联到 Registrar，注册人员列表中该行"姓名/角色"列均显示"注册管理员"

#### Scenario: TEAM 版初始化三字段显式输入

- **WHEN** 管理员选择 TEAM 版并填写注册管理员姓名 `"李雷"`、角色 `"数据治理主管"`
- **THEN** setup 后端创建 `Individual(name="李雷")` 与 `Role(roleName="数据治理主管")` 并关联到 Registrar；若系统中已存在同名 `Role("数据治理主管")` 则复用该 Role 而非新建

#### Scenario: PERSONAL 版姓名留空时的回退

- **WHEN** 管理员选择 PERSONAL 版且将注册管理员姓名清空
- **THEN** setup 不创建 Individual；该 Registrar 行的"姓名"列在列表中显示空值，系统不再以任何其他字段填充

#### Scenario: TEAM 版角色留空时的校验拒绝

- **WHEN** 管理员选择 TEAM 版且未填写角色
- **THEN** setup 接口 MUST 返回业务校验错误，初始化流程 MUST 不继续

### Requirement: 三要素编辑与初始化不得影响已有关联

对一条已有的 Registrar 进行三要素编辑或初始化阶段写入时：

- 若 Individual / Role / Organization 中某项原本未关联，编辑/写入该字段 SHALL NOT 影响其他两个已经关联的字段
- 若原本已关联某项，编辑/写入另一项 SHALL NOT 主动清除该项
- 已关联实体的唯一性（Individual.identifier / Role.roleName） MUST NOT 因关联行为被改变

#### Scenario: 编辑不联动清空其他字段

- **WHEN** 用户编辑一条已同时关联 Individual 与 Role 的 Registrar，仅修改 Individual 字段
- **THEN** 该 Registrar 的 Role 与 Organization 关联保持不变
