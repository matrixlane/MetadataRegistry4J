# registration-authority-lifecycle Specification

## Purpose
定义注册机构（Registration_Authority）的创建与删除治理规则：初始化创建的主要（Primary）隐式标志、按部署版本（个人版/团队版）差异化的创建与删除保护，以及删除机构时的级联清理与删除前警告，保证机构生命周期在两种部署版本下均安全、可预期。

## Requirements

### Requirement: 注册机构 Primary 隐式标志

系统初始化向导创建的注册机构 SHALL 在后台被标记为"主要"（Primary）机构。该标志 MUST 仅存在于后端，MUST NOT 出现在任何响应 DTO 或前端界面中。该标志为应用扩展语义（ISO/IEC 11179-3 无此概念），MUST 存放于 ext schema 卫星表（与注册机构 1:1 关联），MUST NOT 存放在 mdr 标准表上。团队版下该标志 MUST NOT 影响任何处理（删除保护等暂不发挥作用）；个人版下该标志是删除保护的判定依据。除初始化流程外，任何途径 MUST NOT 设置或变更该标志；恢复出厂重新初始化后新创建的注册机构 MUST 重新获得该标志。存量已初始化库 MUST 在无 Primary 机构时由启动调和把最早创建的注册机构补标为 Primary。

#### Scenario: 初始化创建的主要机构带隐式标志

- **WHEN** 管理员（任一版本）完成初始化向导提交
- **THEN** 本次创建的注册机构在后台（ext 卫星表）被标记为 Primary；前端注册机构列表与详情不出现任何 Primary 标识；mdr.registration_authority 不含该扩展列

#### Scenario: 团队版下标志不影响删除

- **WHEN** 部署版本为 TEAM，管理员删除带 Primary 标志的注册机构
- **THEN** 系统仅按级联删除流程处理，不因 Primary 标志拒绝删除

#### Scenario: 存量库启动调和回填

- **WHEN** 一个早于本能力部署的已初始化库启动应用，且不存在任何 Primary 机构
- **THEN** 启动调和将最早创建的注册机构补标为 Primary，个人版下该机构随后不可被删除

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

### Requirement: 按部署版本的注册机构删除保护

删除注册机构的保护规则 SHALL 按部署版本判定，替代原有"按运行模式禁删"规则：PERSONAL 版本下带 Primary 标志的注册机构 MUST NOT 被删除（后端拒绝，前端隐藏/禁用删除入口）；PERSONAL 版本下非 Primary 注册机构 SHALL 允许删除；TEAM 版本下任何注册机构（含 Primary）SHALL 允许删除。可删除的机构删除时 MUST 执行级联清理（见"删除机构级联清理"）。

#### Scenario: 个人版主要机构删除被拒绝

- **WHEN** PERSONAL 版本下调用方请求删除带 Primary 标志的注册机构
- **THEN** 系统 SHALL 拒绝并返回业务校验错误；前端该机构行的删除入口不可用

#### Scenario: 个人版非主要机构可删除

- **WHEN** PERSONAL 版本下调用方请求删除非 Primary 的注册机构
- **THEN** 系统 SHALL 执行级联删除并返回成功

#### Scenario: 团队版任何机构可删除

- **WHEN** TEAM 版本下管理员请求删除任一注册机构（含 Primary）
- **THEN** 系统 SHALL 执行级联删除并返回成功

### Requirement: 删除机构级联清理

删除注册机构时系统 MUST 在同一事务中级联删除其下关联的全部业务数据：该机构的全部 Administered_Item（连同其 Item 图从属数据，含先清理提交关联表）、仅关联这些被删条目的 SubmissionRecord、该机构（组织）下的 StewardshipRecord、该机构的全部 Registrar 身份（先解绑账号绑定并清理各账号的激活注册人员指向）、该机构的自有命名空间及其从属数据、`sys:` 系统命名空间对该机构的命名授权置空（系统命名空间本身保留）、该机构的注册流转规则（含审批子表）与文档语言关联行，最后删除注册机构行与组织行。系统 SHALL 提供删除预览端点 `GET /api/v1/registration-authorities/{id}/deletion-preview`，返回将被删除的各类对象计数（受管项、提交记录、注册人员、命名空间等）。

#### Scenario: 删除机构后无悬挂数据

- **WHEN** 管理员删除一个名下存在受管项、注册人员、命名空间与提交记录的注册机构
- **THEN** 删除成功后上述各类数据均被清除，注册机构列表不再包含该机构，相关表不再存在指向该机构的引用

#### Scenario: 删除预览返回计数

- **WHEN** 管理员在删除前调用删除预览端点
- **THEN** 响应包含该机构下受管项数量、注册人员数量、命名空间数量、提交记录数量等计数

#### Scenario: 系统命名空间保留

- **WHEN** 被删机构的命名授权指向 `sys:` 系统默认命名空间
- **THEN** 删除机构后 `sys:` 命名空间仍存在，其命名授权被置空

### Requirement: 删除机构前端警告提示

注册机构管理页面的删除操作 SHALL 先弹出警告提示，展示删除预览计数并明确说明"删除该注册机构将同时删除其下全部受管项及关联数据，且不可恢复"，经用户确认后才执行删除；用户取消时 MUST NOT 发起删除请求。

#### Scenario: 确认后删除

- **WHEN** 管理员点击删除并在警告框中确认
- **THEN** 系统执行级联删除并刷新列表

#### Scenario: 取消不删除

- **WHEN** 管理员在警告框中取消
- **THEN** 系统 MUST NOT 发起删除请求，列表保持不变

### Requirement: 注册机构创建向导与可选命名空间

注册机构创建 SHALL 通过向导式表单完成，至少包含两个步骤：注册机构信息（复合标识、组织属性、注册人员选项）与命名空间。命名空间步骤 MUST 为可选：用户显式选择创建命名空间时，命名空间的编辑界面（名称、标识输入模式、段模板、版本输入模式、版本策略）SHALL 展现；未选择时 MUST NOT 展现且 MUST NOT 创建命名空间。用户选择创建时，系统 SHALL 在同一事务内创建该机构的自有命名空间（registrationAuthority 与 namingAuthority 均挂接该机构），命名空间治理语义与初始化向导一致。注册人员选项 SHALL 按部署版本差异化：个人版 MUST 默认当前用户成为注册人员且 MUST NOT 提供修改入口；团队版 MUST 提供开关（缺省开启），用户关闭时 MUST NOT 建立注册人员身份。

#### Scenario: 不创建命名空间仅创建机构

- **WHEN** 用户完成机构信息步骤、未勾选创建命名空间并提交
- **THEN** 系统创建注册机构与注册人员（按版本规则），不创建任何命名空间

#### Scenario: 勾选后创建自有命名空间

- **WHEN** 用户勾选创建命名空间、编辑界面展现并填写名称与策略后提交
- **THEN** 系统在同一事务内创建注册机构、注册人员与自有命名空间（双挂该机构），命名空间策略按所配生效

#### Scenario: 个人版注册人员不可修改

- **WHEN** 个人版用户在创建向导查看注册人员选项
- **THEN** 界面以固定提示说明当前用户将自动成为注册人员，不提供关闭入口

#### Scenario: 团队版关闭注册人员开关

- **WHEN** 团队版用户关闭"将自己设为该机构注册人员"开关并提交
- **THEN** 系统创建注册机构与命名空间（如勾选），但不建立注册人员身份
