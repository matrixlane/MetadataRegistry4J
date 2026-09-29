# user-profile-individual 增量（add-registrar-three-elements）

## MODIFIED Requirements

### Requirement: "我的"界面联系人身份管理区

"我的"界面 SHALL 在既有个人资料（Individual）维护功能之外，提供"联系人身份"管理区：展示当前账号的全部联系人身份与 Registrar 身份，支持新建、编辑、删除自建联系人身份以及设置默认联系人。该管理区 SHALL 面向所有登录用户开放，与账号角色无关。Registrar 身份 SHALL 仅可查看，其变更 MUST 走既有 Registrar 管理流程。Registrar 行的编辑入口 SHALL 同时允许修改 Role、Individual、Organization 三要素的关联，其中 Role 可被显式清空，Individual 与 Organization 在 Registrar 上下文中 MUST NOT 被清空。

#### Scenario: 普通用户管理联系人身份

- **WHEN** 普通用户打开"我的"界面
- **THEN** 可见联系人身份管理区，可新建联系人并设为默认

#### Scenario: Registrar 行可编辑角色但不可删除

- **WHEN** 持有 Registrar 身份的用户在"我的"界面查看其 Registrar 身份
- **THEN** 联系人身份列表中展示其 Registrar 身份及所属注册机构；提供编辑入口且编辑时仅允许修改 Role 关联；不提供删除入口
