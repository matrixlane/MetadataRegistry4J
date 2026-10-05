# Registration Authority Lifecycle Delta Specification

## MODIFIED Requirements

### Requirement: 按部署版本的注册机构创建规则

当部署版本为 `PERSONAL` 时，系统 SHALL 允许超级管理员创建任意数量的注册机构；创建成功后系统 MUST 自动为当前超级管理员建立该机构的注册人员（Registrar）身份并绑定其账号。当部署版本为 `TEAM` 时，系统同样 SHALL 允许超级管理员创建任意数量的注册机构（不存在运行模式或数量上限概念）；团队版创建机构 MUST NOT 自动创建注册人员身份。任何版本下创建注册机构 MUST 要求调用者为超级管理员。创建与编辑注册机构时，调用方 MUST 按 ISO/IEC 11179-3:2023 §6.3.9 的 `Registration_Authority_Identifier` 复合结构提供国际代码标识符（international_code_designator，必备）与组织标识符（organization_identifier，必备）；组织部件标识符（organization_part_identifier）为选填；缺失必备部分时系统 MUST 返回参数校验错误。注册机构表单 MUST 同时陈列组织（Organization，§6.3.3 表 2）的全部属性：名称（name）、通信地址（mail_address）、电子邮箱（email_address，0..*）、电话号码（phone_number，0..*）、网址（web_address）；创建一条注册机构记录 MUST 同时产生一条组织记录并携带上述属性。

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

#### Scenario: 缺失国际代码标识符被拒绝

- **WHEN** 调用方创建或编辑注册机构时未提供国际代码标识符（international_code_designator）
- **THEN** 系统 MUST 返回参数校验错误，且不写入任何数据

#### Scenario: 组织部件标识符选填

- **WHEN** 调用方创建注册机构时提供国际代码标识符与组织标识符、不提供组织部件标识符
- **THEN** 系统 SHALL 正常创建，组织部件标识符为空

#### Scenario: 组织属性随注册机构创建一并落库

- **WHEN** 调用方创建注册机构时填写通信地址、电子邮箱、电话号码与网址
- **THEN** 系统 SHALL 在同一事务内同时写入组织记录（organization 行及其联系属性），编辑回显一致
