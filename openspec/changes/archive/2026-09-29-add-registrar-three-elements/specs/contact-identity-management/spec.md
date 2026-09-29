# contact-identity-management 增量（add-registrar-three-elements）

## MODIFIED Requirements

### Requirement: 多联系人身份与默认联系人

系统 SHALL 允许任何注册用户为自己的账号创建、编辑、删除多个联系人身份；每个账号 SHALL 至多有一个默认联系人身份（Registrar 行同时是 Contact 的一种，也可被设为默认）。用户将某身份设为默认时，系统 MUST 在同一事务内取消该账号其他身份的默认标记。删除默认身份后，系统 SHALL 自动把该账号剩余身份中最早绑定的一条设为默认；删除最后一条身份后，该账号失去 Submitter 能力。用户 SHALL NOT 通过该入口删除 Registrar 身份（Registrar 身份的授予与撤销走既有 Registrar 管理流程）。Registrar 行 SHALL 同时允许修改 Role、Individual、Organization 三要素的关联（身份由 Contact 子类 Registrar 实例的类型确定，与 Role 无关）。Role 为描述性元数据，可被显式清空；Individual 与 Organization 既可被改也可被清空，但 Organization 在 Registrar 上下文中 MUST NOT 被清空。创建 Registrar 时有意置空的三要素由本变更新增的初始化流程支持填写。

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

#### Scenario: Registrar 行编辑三要素

- **WHEN** 持有 Registrar 身份的用户在"我的"入口编辑其 Registrar 身份
- **THEN** 编辑表单 MUST 同时允许修改 Role、Individual、Organization 三要素的关联；保存时 MUST 同步落库；Organization 在 Registrar 上下文中 MUST NOT 被清空（保存为 null 时 MUST 返回校验错误）

#### Scenario: Registrar 行清空 Role

- **WHEN** 持有 Registrar 身份的用户在"我的"入口编辑其 Registrar 身份，主动清空 Role
- **THEN** 该 Registrar 的 `Contact.role` 设为 `null`，不抛错；其他字段不受影响
