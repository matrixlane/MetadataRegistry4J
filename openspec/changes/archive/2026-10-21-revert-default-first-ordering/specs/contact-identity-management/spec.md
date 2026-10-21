# Contact Identity Management Delta Specification

## MODIFIED Requirements

### Requirement: 身份列表可见性

"我的"入口 SHALL 展示当前账号的全部联系人身份与 Registrar 身份：包括每个身份的名称、所属组织、角色、是否为 Registrar、Registrar 所属注册机构（如适用）及是否默认。用户 SHALL 能区分自建联系人身份与 Registrar 身份。列表 SHALL 按绑定顺序展示，默认身份 MUST NOT 因默认标记而改变展示位置。

#### Scenario: 多身份同列展示

- **WHEN** 同时拥有自建联系人身份与 Registrar 身份的用户打开"我的"页
- **THEN** 列表同时展示两类身份，Registrar 身份标注其注册机构

#### Scenario: 默认身份置顶

- **WHEN** 用户的默认联系人身份不是最早绑定的一条
- **THEN** 列表仍按绑定顺序展示，默认身份不置顶（仅以默认标记区分）
