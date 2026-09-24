# contact-identity-management Specification

## Purpose
本能力允许注册用户为自己的账号登记并维护一个或多个联系人身份（Contact），设置默认联系人，并在"我的"入口统一查看与管理联系人身份及 Registrar 身份；联系人身份是 Submitter 提交元数据资格与 SubmissionRecord 默认预填的来源。

## Requirements

### Requirement: 多联系人身份与默认联系人

系统 SHALL 允许任何注册用户为自己的账号创建、编辑、删除多个联系人身份；每个账号 SHALL 至多有一个默认联系人身份（Registrar 行同时是 Contact 的一种，也可被设为默认）。用户将某身份设为默认时，系统 MUST 在同一事务内取消该账号其他身份的默认标记。删除默认身份后，系统 SHALL 自动把该账号剩余身份中最早绑定的一条设为默认；删除最后一条身份后，该账号失去 Submitter 能力。用户 SHALL NOT 通过该入口删除 Registrar 身份（Registrar 身份的授予与撤销走既有 Registrar 管理流程）；Registrar 行 SHALL 仅允许修改 Role 关联（身份由 Contact 子类 Registrar 实例的类型确定，与 Role 无关；Role 为描述性元数据，创建 Registrar 时有意置空，由用户后续自建自关联）。

#### Scenario: 创建并绑定联系人身份

- **WHEN** 登录用户在"我的"入口新建联系人身份并选择所属组织与角色
- **THEN** 系统创建该 Contact 并绑定到当前账号，账号立即获得 Submitter 能力

#### Scenario: 任何路径创建的首个身份自动默认

- **WHEN** 通过"我的"入口、初始化向导（成为 Registrar）或管理员指派 Registrar/批量配置等任一路径，为尚无身份绑定的账号创建第一个联系人身份
- **THEN** 该身份自动标记为默认；既有单身份账号的存量绑定由迁移补标为默认

#### Scenario: 设置默认联系人

- **WHEN** 用户将某联系人身份设为默认
- **THEN** 该身份成为默认，同账号其他身份的默认标记被清除

#### Scenario: 删除默认身份自动递补

- **WHEN** 用户删除当前默认的联系人身份，且账号仍有其他身份
- **THEN** 系统将该账号剩余身份中最早绑定的一条自动设为默认

#### Scenario: 删除 Registrar 身份被拒绝

- **WHEN** 用户尝试在"我的"入口删除自己的 Registrar 身份
- **THEN** 系统拒绝该操作，并提示 Registrar 身份需通过 Registrar 管理流程变更

### Requirement: 身份列表可见性

"我的"入口 SHALL 展示当前账号的全部联系人身份与 Registrar 身份：包括每个身份的名称、所属组织、角色、是否为 Registrar、Registrar 所属注册机构（如适用）及是否默认。用户 SHALL 能区分自建联系人身份与 Registrar 身份。

#### Scenario: 多身份同列展示

- **WHEN** 同时拥有自建联系人身份与 Registrar 身份的用户打开"我的"页
- **THEN** 列表同时展示两类身份，Registrar 身份标注其注册机构

### Requirement: 首登完善联系人提示

普通用户注册账号后首次登录时，系统 SHALL 提示其完善联系人信息，并说明完善后即可提交元数据；该提示 MUST 允许跳过。账号已具有 Registrar 身份（含初始化时选择成为 Registrar 的 Admin）或已关联 Contact 时，系统 MUST NOT 弹出该提示。用户跳过提示后，系统在下次会话 MUST NOT 重复骚扰（同一账号仅提示一次）。

#### Scenario: 普通用户首登提示并可跳过

- **WHEN** 新注册且未关联任何 Contact 的普通用户首次登录
- **THEN** 系统弹出完善联系人信息提示，用户可跳过后继续浏览（仅可查看）

#### Scenario: Registrar 不提示

- **WHEN** 初始化向导中选择成为 Registrar 的 Admin 登录
- **THEN** 系统不弹出完善联系人提示

#### Scenario: 已登记联系人的用户不提示

- **WHEN** 账号已关联至少一个 Contact 的用户登录
- **THEN** 系统不弹出完善联系人提示

#### Scenario: 跳过不再骚扰

- **WHEN** 用户曾在首登时跳过完善联系人提示
- **THEN** 后续登录不再重复弹出该提示
