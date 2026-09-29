# system-initialization 增量（add-registrar-three-elements）

## ADDED Requirements

### Requirement: 初始化向导为管理员成为 Registrar 提供三要素填写入口

初始化向导的注册机构步骤 MUST 在管理员选择成为 Registrar 时（PERSONAL 版自动、TEAM 版勾选 selfAsRegistrar=true）提供三要素填写入口，提交时 setup 后端 MUST 按如下规则创建并关联对象：

- **注册管理员姓名**：`Individual` 字段。任一版本 MUST NOT 设默认值（PERSONAL 版 MUST NOT 由后端兜底填充）；该字段可空，提交时若非空 MUST 创建 `Individual { name = Sign.of(姓名), identifier = 账号用户名 }` 并 set 为该 Registrar 的 Individual。提交时若为空 MUST NOT 创建 Individual，但 MUST NOT 主动清除已有关联。管理员未填写姓名时系统中 MUST NOT 存在以其账户名义创建的 Individual；其个人信息由后续在"我的"中填写时创建并回填 Registrar。
- **注册管理员角色**：`Role` 字段。PERSONAL 版 SHALL 默认"注册管理员"；TEAM 版在创建 Registrar 的前提下 MUST 由管理员显式输入。提交时 MUST 创建 `Role { roleName = 角色名 }` 或复用已存在同名 Role（按 `roleName` 唯一性判定），并 set 为该 Registrar 的 Role。同名 Role 已存在时 MUST 复用而非新建。
- **注册管理员所属组织**：`Organization` 字段。初始化阶段 MUST NOT 作为用户输入项暴露——向导 MUST NOT 提供组织选择控件，`setup` 请求体 MUST NOT 接受所属组织参数。setup 后端 MUST 将该 Registrar 的组织恒定关联到本次创建的 `Registration_Authority`（`Registration_Authority` 为 `Organization` 的子类且共用同一张表，注册机构本身即组织）。

`/api/v1/init-wizard/setup` 请求 MUST 接受姓名与角色两个字段（角色缺省视为 PERSONAL 默认值，姓名缺省视为留空），setup 后端 MUST 在创建 Registrar 时按上述规则创建并 set 关联。该语义在 PERSONAL 与 TEAM 两版本之间 MUST 通过版本差异化策略保持一致：PERSONAL 版角色默认值由后端兜底、姓名留空不创建 Individual，TEAM 版角色显式校验；两版本的所属组织均 MUST 为本次创建的 Registration_Authority。

两字段的必填性 MUST 以是否创建 Registrar 为前提。TEAM 版的「将当前超级管理员设为该机构的注册人员」开关为 true 时 setup MUST 创建 Registrar 并要求角色非空；该开关为 false 或缺省（未提交、值为 null）时 setup MUST NOT 创建 Registrar，也 MUST NOT 对这两字段执行必填校验。前端 MUST 显式提交该开关，且其界面缺省呈现为开启。PERSONAL 版不提供该开关，恒定创建 Registrar。

#### Scenario: PERSONAL 版两字段默认填充

- **WHEN** 管理员选择 PERSONAL 版且未显式填写注册管理员姓名
- **THEN** setup 后端 MUST NOT 对 `adminIndividualName` 兜底任何默认字符串，不创建 Individual，注册人员列表该行"姓名"列显示空值；setup 仅对 `adminRoleName` 兜底默认值 `"注册管理员"`，创建（或复用）`Role(roleName="注册管理员")` 并 set 关联到 Registrar，"角色"列显示"注册管理员"

#### Scenario: 任一版本所属组织恒等于本次创建的注册机构

- **WHEN** 管理员选择任一部署版本并完成注册机构步骤
- **THEN** setup 将 Registrar 的 organization 关联到本次创建的 `Registration_Authority`，注册人员列表该行"组织"列显示该注册机构名称；setup MUST NOT 接受也不需要任何来自请求的所属组织参数

#### Scenario: 初始化向导不暴露组织选择控件

- **WHEN** 管理员在注册机构步骤查看表单（含 TEAM 版）
- **THEN** 表单 MUST NOT 出现组织选择控件，TEAM 版无需为组织做任何选择；向导 MUST NOT 为该控件发起组织列表请求

#### Scenario: PERSONAL 版姓名留空

- **WHEN** 管理员选择 PERSONAL 版且未填写（或清空）注册管理员姓名
- **THEN** setup 不创建 Individual，后端 MUST NOT 以"注册管理员"或任何其他默认字符串兜底填充；列表"姓名"列显示空值，系统不再以任何其他字段填充；用户若希望展示姓名 SHALL 显式输入非空字符串，或后续在"我的"中填写个人信息由绑定流程回填

#### Scenario: PERSONAL 版角色名已存在时复用

- **WHEN** 管理员选择 PERSONAL 版，系统中已存在 Role(`roleName="注册管理员"`)，未显式修改角色名
- **THEN** setup 后端 MUST NOT 新建 Role，复用既有 Role 并 set 关联到 Registrar

#### Scenario: TEAM 版两字段显式填写

- **WHEN** 管理员选择 TEAM 版并填写注册管理员姓名 `"李雷"`、角色 `"数据治理主管"`
- **THEN** setup 后端创建 `Individual(name="李雷")` 与 `Role(roleName="数据治理主管")`，并将 organization set 为本次创建的 `Registration_Authority`；该 Registrar 行三列均展示对应值

#### Scenario: TEAM 版角色留空被拒绝

- **WHEN** 管理员选择 TEAM 版、已开启「将当前超级管理员设为该机构的注册人员」且未填写角色
- **THEN** setup 接口 MUST 返回业务校验错误，注册机构步骤 MUST NOT 进入下一步

#### Scenario: TEAM 版未设为注册人员时不校验要素

- **WHEN** 管理员选择 TEAM 版并关闭「将当前超级管理员设为该机构的注册人员」，姓名与角色均留空
- **THEN** 注册机构步骤 MUST 正常进入下一步，setup MUST 正常完成初始化且 MUST NOT 返回校验错误；注册人员 MUST NOT 被创建

#### Scenario: 省略开关与显式关闭同义

- **WHEN** setup 请求未提交「设为注册人员」开关或提交值为 null
- **THEN** setup MUST NOT 创建注册人员，MUST NOT 对注册人员要素执行必填校验，初始化 MUST 正常完成

#### Scenario: 界面缺省为开启

- **WHEN** 管理员在团队版注册机构步骤查看「将当前超级管理员设为该机构的注册人员」开关
- **THEN** 该开关 MUST 呈现为开启；向导 MUST 显式提交 true 或 false，MUST NOT 依赖 API 的缺省语义

#### Scenario: 任一版本所属组织缺失不构成失败原因

- **WHEN** 管理员在任一部署版本下提交注册机构步骤而请求未携带所属组织参数
- **THEN** setup MUST NOT 因所属组织缺失而返回校验错误，注册机构步骤 MUST 正常进入下一步
