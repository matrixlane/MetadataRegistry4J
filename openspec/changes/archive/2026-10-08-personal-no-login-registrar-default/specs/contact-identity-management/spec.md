# Contact Identity Management Delta Specification

## MODIFIED Requirements

### Requirement: 多联系人身份与默认联系人

系统 SHALL 允许注册用户为自己的账号创建、编辑、删除多个联系人身份；`kind=REGISTRAR` 的注册人员身份 SHALL 仅允许超级管理员（SUPER_ADMIN）为自己创建，非超级管理员的注册人员身份创建请求 MUST 被拒绝（团队版与个人版一致）。Contact 与账号的绑定关系 MUST 存放于 ext schema 卫星表（与 `mdr.contact` 按 id 一一对应，含 `account_id` 列）；一个 Contact MUST 至多绑定一个账号，一个账号 MAY 绑定多个 Contact。默认身份概念收窄为"默认注册人员"（标志存于 registrar 卫星表，见 `active-registrar-ownership` 能力）：每个账号 SHALL 至多有一个默认注册人员身份；用户将某 Registrar 身份设为默认时，系统 MUST 在同一事务内取消该账号其他 Registrar 的默认标记；普通 Contact 身份 MUST NOT 被设为默认（请求返回业务校验错误）。删除默认 Registrar 身份后，系统 SHALL 自动把该账号剩余 Registrar 中的首个设为默认；删除最后一条身份后，该账号失去 Submitter 能力。

身份删除规则 SHALL 按部署版本差异化：`PERSONAL` 版本下，Registrar 行 MUST NOT 通过"我的"入口删除（其所属注册机构仍存在时拒绝，提示需先删除该注册机构）；CONTACT 行沿用引用保护（已被提交记录引用时拒绝删除）。`TEAM` 版本下，任何用户 SHALL 可删除其全部联系人身份（含 Registrar 行），删除时系统 MUST 解绑账号绑定并清理 registrar 卫星表默认标志（按默认递补规则处理）；删除后历史数据中的悬空引用（如历史提交记录的联系人展示为空）为可接受行为。

Registrar 行 SHALL 同时允许修改 Role、Individual、Organization 三要素的关联（身份由 Contact 子类 Registrar 实例的类型确定，与 Role 无关）。Role 为描述性元数据，可被显式清空；Individual 与 Organization 既可被改也可被清空，但 Organization 在 Registrar 上下文中 MUST NOT 被清空。创建 Registrar 时有意置空的三要素由本变更新增的初始化流程支持填写。

#### Scenario: 创建并绑定联系人身份

- **WHEN** 登录用户在"我的"入口新建联系人身份（kind=CONTACT）并选择所属组织与角色
- **THEN** 系统创建该 Contact 并绑定到当前账号（写入 contact 卫星表），账号立即获得 Submitter 能力

#### Scenario: 非超级管理员创建注册人员身份被拒绝

- **WHEN** 非 SUPER_ADMIN 用户在"我的"入口提交 kind=REGISTRAR 的身份创建请求（或前端对非管理员隐藏该选项）
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误

#### Scenario: 任何路径创建的首个身份自动默认

- **WHEN** 通过"我的"入口、初始化向导（成为 Registrar）或管理员指派 Registrar/批量配置等任一路径，为尚无 Registrar 身份的账号创建第一个 Registrar 身份
- **THEN** 该 Registrar 自动标记为默认注册人员；首个身份为普通 Contact 时不产生默认标记

#### Scenario: 设置默认联系人

- **WHEN** 用户将其某个 Registrar 身份设为默认
- **THEN** 该身份成为默认注册人员，同账号其他 Registrar 的默认标记被清除；普通 Contact 身份的设默认请求被拒绝

#### Scenario: 删除默认身份自动递补

- **WHEN** 用户删除当前默认的 Registrar 身份，且账号仍有其他 Registrar 身份
- **THEN** 系统将该账号剩余 Registrar 中的首个自动设为默认

#### Scenario: 删除普通 Contact 不触发递补

- **WHEN** 用户删除一个非默认的普通 Contact 身份，或删除任意 Contact 身份而账号默认注册人员是其他 Registrar
- **THEN** 系统仅解绑并删除该身份，默认注册人员标志不变

#### Scenario: 删除 Registrar 身份被拒绝

- **WHEN** `PERSONAL` 版本下用户尝试在"我的"入口删除自己的 Registrar 身份
- **THEN** 系统拒绝该操作，并提示该身份所属注册机构仍存在，如确需删除请先删除该注册机构

#### Scenario: 团队版删除注册人员身份成功

- **WHEN** `TEAM` 版本下用户删除自己的 Registrar 身份，即使该身份已被提交记录引用
- **THEN** 系统 SHALL 允许删除，解绑账号绑定并清理 registrar 卫星行（含默认标志，按递补规则处理）；历史提交记录的联系人展示为空

#### Scenario: Registrar 行编辑三要素

- **WHEN** 持有 Registrar 身份的用户在"我的"入口编辑其 Registrar 身份
- **THEN** 编辑表单 MUST 同时允许修改 Role、Individual、Organization 三要素的关联；保存时 MUST 同步落库；Organization 在 Registrar 上下文中 MUST NOT 被清空（保存为 null 时 MUST 返回校验错误）

#### Scenario: Registrar 行清空 Role

- **WHEN** 持有 Registrar 身份的用户在"我的"入口编辑其 Registrar 身份，主动清空 Role
- **THEN** 该 Registrar 的 `Contact.role` 设为 `null`，不抛错；其他字段不受影响

### Requirement: 身份列表可见性

"我的"入口 SHALL 展示当前账号的全部联系人身份与 Registrar 身份：包括每个身份的名称、所属组织、角色、是否为 Registrar、Registrar 所属注册机构（如适用）及是否为默认注册人员。用户 SHALL 能区分自建联系人身份与 Registrar 身份。列表 SHALL 按绑定顺序展示，默认注册人员 MUST NOT 因默认标记而改变展示位置。

#### Scenario: 多身份同列展示

- **WHEN** 同时拥有自建联系人身份与 Registrar 身份的用户打开"我的"页
- **THEN** 列表同时展示两类身份，Registrar 身份标注其注册机构，默认注册人员以默认标记区分

#### Scenario: 默认身份置顶

- **WHEN** 用户的默认注册人员身份不是最早绑定的一条
- **THEN** 列表仍按绑定顺序展示，默认注册人员不置顶（仅以默认标记区分）
