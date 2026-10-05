# Registration Authority Lifecycle Delta Specification

## MODIFIED Requirements

### Requirement: 按部署版本的注册机构创建规则

当部署版本为 `PERSONAL` 时，系统 SHALL 允许超级管理员创建任意数量的注册机构；创建成功后系统 MUST 自动为当前超级管理员建立该机构的注册人员（Registrar）身份并绑定其账号。当部署版本为 `TEAM` 时，系统同样 SHALL 允许超级管理员创建任意数量的注册机构（不存在运行模式或数量上限概念）；团队版创建机构 MUST NOT 自动创建注册人员身份。任何版本下创建注册机构 MUST 要求调用者为超级管理员。

#### Scenario: 个人版创建机构并自动成为注册人员

- **WHEN** PERSONAL 版本下超级管理员通过注册机构页面创建新机构
- **THEN** 机构创建成功，当前管理员自动获得该机构的注册人员身份（账号绑定、默认身份规则与既有身份一致）

#### Scenario: 个人版不受运行模式数量限制

- **WHEN** PERSONAL 版本下系统已存在一个（含 Primary）注册机构，超级管理员再创建新机构
- **THEN** 系统 SHALL 允许创建（系统不存在运行模式数量限制）

#### Scenario: 团队版 SINGLE 模式仍限建一个

- **WHEN** TEAM 版本下系统已存在一个注册机构，超级管理员请求再创建
- **THEN** 系统 SHALL 允许创建（系统不存在运行模式，无任何数量限制）

#### Scenario: 团队版创建机构不自动产生注册人员

- **WHEN** TEAM 版本下超级管理员创建新机构
- **THEN** 系统 SHALL NOT 自动为其创建该机构的注册人员身份
