# Active Registrar Ownership Delta Specification

## MODIFIED Requirements

### Requirement: 活动身份只读展示

账号激活 Registrar 身份后，界面顶栏 SHALL 展示当前注册机构名称标签；无活动身份时 MUST NOT 展示该标识。账号持有多个 Registrar 身份时，该标签 SHALL 可点击弹出切换菜单：菜单 MUST 仅列出当前用户担任注册人员的注册机构（其 Registrar 身份），选择其中一项后系统 SHALL 切换激活身份并使界面随之更新；单一身份或无身份时 MUST NOT 提供切换菜单。

#### Scenario: 顶栏只读展示

- **WHEN** 账号持有多个 Registrar 身份并已自动激活其一
- **THEN** 顶栏展示当前注册机构名称的可点击标签，点击弹出切换菜单且菜单仅含本人担任注册人员的机构

#### Scenario: 无身份不展示

- **WHEN** 账号无任何 Registrar 身份
- **THEN** 顶栏不展示该标识
