# System Initialization Capability Specification

## Purpose

本能力定义系统初始化向导在不同部署版本（PERSONAL / TEAM）下的差异化流程、数据库配置步骤、初始化接口契约、幂等与可恢复语义、提交语义以及未初始化状态下的访问控制规则。

## Requirements

### Requirement: 向导流程按版本差异化
初始化向导 SHALL 以版本选择为第一步，数据库配置为第二步（语义见"初始化向导中的数据库配置"）。两个版本 MUST 均包含治理配置步骤，且步骤顺序为：注册规范 → 注册机构 → 命名空间。管理员角色分配 SHALL 并入注册机构步骤。版本差异仅体现在默认值策略：选择 `PERSONAL` 时，向导 SHALL 为治理字段预填合理默认值以保持精简体验；选择 `TEAM` 时，向导 MUST 要求显式填写必填治理字段。注册规范、注册机构与命名空间在两个版本中 MUST 均为必选：初始化必须先创建注册规范，再创建注册机构与注册人员，最后创建命名空间；初始化必须创建注册机构关联的自有命名空间；仅导入/访问外部组织数据标准的使用场景 SHALL 通过初始化完成后创建外部命名空间来支持，而非跳过自有命名空间。

#### Scenario: 个人版展示治理步骤并预填默认值
- **WHEN** 管理员选择 `PERSONAL` 并完成数据库步骤
- **THEN** 向导 SHALL 依次展示注册规范、注册机构与命名空间步骤，治理字段预填合理默认值，管理员可直接确认或修改后提交

#### Scenario: 团队版完整向导
- **WHEN** 管理员选择 `TEAM`
- **THEN** 向导 SHALL 要求完成数据库配置，并依次展示注册规范、注册机构与命名空间配置步骤，全部有效填写后方可进入密码设置步骤

#### Scenario: 任一版本不允许跳过命名空间设置
- **WHEN** 管理员（`PERSONAL` 或 `TEAM`）尝试跳过命名空间步骤并提交
- **THEN** 系统 SHALL 阻止提交并提示自有命名空间为必选项

### Requirement: 初始化向导中的数据库配置
数据库配置 MUST 作为初始化向导的独立步骤，紧随版本选择之后。`PERSONAL` 版 MUST 允许跳过该步骤，跳过时使用默认 SQLite 文件库；`PERSONAL` 版也允许配置 SQL Server 或达梦。`TEAM` 版 MUST 强制完成数据库配置（不得跳过），且 MUST NOT 提供 SQLite 选项。数据库配置的提交、连接测试、持久化与生效方式遵循 `datasource-configuration` 能力的规则。

#### Scenario: 个人版跳过数据库配置
- **WHEN** 管理员在个人版向导的数据库配置步骤选择"使用默认 SQLite"并继续
- **THEN** 向导 SHALL 进入后续步骤，系统继续使用默认 SQLite 文件库

#### Scenario: 团队版必须配置数据库
- **WHEN** 管理员在团队版向导中未完成数据库配置就尝试进入下一步
- **THEN** 向导 SHALL 阻止继续并提示必须先完成数据库配置

#### Scenario: 配置外部数据库后重启继续
- **WHEN** 管理员在向导中配置了 SQL Server 或达梦并通过连接测试
- **THEN** 系统 SHALL 持久化配置并引导重启，重启后向导从目标数据库重新评估状态并允许继续初始化

### Requirement: 初始化请求扩展版本字段
初始化提交接口（`/api/v1/init-wizard/setup`）的请求 SHALL 接受 `edition` 字段（`PERSONAL`/`TEAM`），缺失时 MUST 返回校验错误。初始化状态接口 SHALL 返回当前版本与初始化状态。`PERSONAL` 下治理布尔开关（`selfAsRegistrar`、`createNamespace`、`createRegistrySpecification`）缺省（null）时 SHALL 按 `true` 处理以保持向后兼容；显式 `createNamespace=false` 时 MUST 返回业务校验错误（自有命名空间必选）；显式 `createRegistrySpecification=false` 时 MUST 返回业务校验错误（注册规范必选）。

#### Scenario: 缺少版本字段的提交
- **WHEN** 调用方提交初始化请求但未提供 `edition`
- **THEN** 系统 SHALL 返回参数校验错误，不写入任何业务数据

#### Scenario: 个人版显式关闭命名空间被拒绝
- **WHEN** 调用方提交 `PERSONAL` 初始化请求且 `createNamespace=false`
- **THEN** 系统 SHALL 返回业务校验错误，不写入任何业务数据

#### Scenario: 个人版显式关闭注册规范被拒绝
- **WHEN** 调用方提交 `PERSONAL` 初始化请求且 `createRegistrySpecification=false`
- **THEN** 系统 SHALL 返回业务校验错误，不写入任何业务数据

### Requirement: 幂等与可恢复语义保持
初始化向导 MUST 保持现有幂等与可恢复语义：`setup` 可重复提交（自动清理上次未完成的数据）、`abort` 可安全多次调用、系统未提交（commit）前 MUST 保持未初始化状态。该语义在全部受支持方言上 MUST 一致。`setup` 的清理动作 MUST 保留静态预置表（见"初始化向导清理保留静态预置表"），清理后由统一静态预置入口幂等兜底，确保任何情况下静态预置数据完整可用。

#### Scenario: 重复提交 setup
- **WHEN** 管理员完成一次 `setup` 后未提交，又以不同配置再次调用 `setup`
- **THEN** 系统 SHALL 清理上一次写入的业务数据并以新配置重建，不产生重复注册机构或命名空间，且静态预置数据（语言及名称、系统默认命名空间、全局默认策略）保持完整

#### Scenario: 出厂后再次走初始化向导
- **WHEN** 恢复出厂设置后管理员重新执行初始化向导
- **THEN** 向导各步骤可见的语言下拉选项包含中文语言名称，文档语言预填项显示中文名称而非 BCP 47 标识符

#### Scenario: 放弃后重新开始
- **WHEN** 管理员调用 `abort` 后重新进入向导
- **THEN** 系统 SHALL 处于可重新初始化的干净状态，管理员账号密码重置为待设置状态，静态预置数据保持完整

### Requirement: 提交语义
`commit` MUST 校验管理员处于待设置密码状态、两次密码输入一致且长度合规（8-64 字符），随后持久化管理员密码哈希、写入 `system.edition`、写入注册表治理策略，并将 `system.initialized` 置为 `true`。提交后 `abort` MUST NOT 再清除业务数据。

#### Scenario: 成功提交
- **WHEN** 管理员完成 setup 后提交合法的新密码
- **THEN** 系统 SHALL 完成初始化，后续登录须使用新密码

#### Scenario: 提交后调用放弃
- **WHEN** 系统已完成初始化，调用方请求 `abort`
- **THEN** 系统 SHALL 视为无操作，业务数据不受影响

### Requirement: 未初始化访问控制保持
系统未初始化时，初始化向导相关端点、数据源连接测试端点与登录端点 MUST 保持匿名可访问；其余 API MUST 要求认证。该行为 MUST NOT 因数据库方言或部署版本不同而改变。

#### Scenario: 未初始化时访问业务接口
- **WHEN** 系统未初始化，未认证客户端请求业务 API
- **THEN** 系统 SHALL 拒绝该请求并返回未认证错误

### Requirement: 注册规范步骤的字段归属
注册规范步骤 MUST 仅包含 `RegistrySpecification` 实体实际持有的设置（名称、默认语言、描述等）。标识生成策略与版本生成策略属于命名空间设置范畴，MUST NOT 出现在注册规范步骤；`identifierPrefix`、`autoGenerateIdentifier`、`identifierPattern`、`versioningPolicy`、`mandatoryClassification` 等无实体承载的字段 MUST NOT 作为注册规范设置采集。

#### Scenario: 注册规范步骤不采集策略字段
- **WHEN** 管理员进入注册规范步骤
- **THEN** 步骤中仅呈现名称、默认语言、描述等注册规范自有设置，不呈现标识生成策略或版本生成策略控件

### Requirement: 向导与全局界面自适应滚动
初始化向导及全局布局 MUST 在内容超过一屏时支持纵向滚动，MUST NOT 出现内容被视口裁切且无法下滚的情况。

#### Scenario: 向导内容超过满屏时可下滚
- **WHEN** 管理员在较小视口（或较多步骤内容）下使用初始化向导，页面内容高度超过视口高度
- **THEN** 页面 SHALL 可纵向滚动，全部步骤内容与操作按钮均可到达

### Requirement: 初始化必须创建注册机构自有命名空间

初始化 MUST 创建注册机构，且 MUST 同时创建与该注册机构关联的自有命名空间（1:1，两边 0..1）；命名空间设置 MUST NOT 可跳过。自有命名空间创建时 SHALL 同步建立标识生成策略（段模板，PUBLISHED 且立即生效）与版本生成策略（自动化）。标识生成策略类型 MUST 收敛为段模板唯一选择：初始化请求携带 `identifierStrategy` 为 `SEGMENT_TEMPLATE` 以外任何值（含 UUID、SEQUENCE、TEMPLATE）MUST 返回业务校验错误；向导界面 MUST NOT 呈现策略类型单选。向导命名空间步骤 MUST 就地内嵌标识模板编辑器，供管理员在初始化时直接编排模板；请求未携带模板 JSON 时 SHALL 落系统默认模板（OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)，示例 `DE.00001`）；标识符输入模式为 MANUAL_ONLY 时编辑器 SHALL 禁用。默认模板、预设与界面文案 MUST NOT 引用任何特定行业标准编号。初始化完成之后创建的命名空间 SHALL 为外部命名空间，使用手动编辑策略。

#### Scenario: 初始化完成即具备自有命名空间与策略

- **WHEN** 管理员完成初始化向导
- **THEN** 系统存在注册机构及其关联的自有命名空间，该命名空间的段模板标识策略（PUBLISHED）与版本自动化策略已生效

#### Scenario: 向导就地编排模板并生效

- **WHEN** 管理员在向导命名空间步骤中将模板编排为 LITERAL("WN") + SEPARATOR(".") + OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5) 并完成初始化
- **THEN** 自有命名空间已发布策略即为该模板，首个数据元注册生成 `WN.DE.00001`

#### Scenario: 策略类型白名单收敛

- **WHEN** 初始化请求携带 `identifierStrategy` 为 `UUID`（或 `SEQUENCE`、`TEMPLATE`）
- **THEN** 系统返回业务校验错误，提示标识生成策略仅支持段模板

#### Scenario: 不允许跳过命名空间创建

- **WHEN** 初始化请求显式声明不创建命名空间
- **THEN** 系统返回业务校验错误，提示自有命名空间为必选项

#### Scenario: 初始化后创建的命名空间为外部

- **WHEN** 初始化完成后用户再创建命名空间
- **THEN** 新命名空间的性质为外部，其标识与版本策略为手动编辑

### Requirement: 个人版自动成为 Registrar

初始化向导 SHALL 按版本差异化处理"超级管理员是否成为 Registrar"：`PERSONAL` 版 SHALL NOT 向管理员提供该选择——当前超级管理员 SHALL 自动成为所创建注册机构的 Registrar（后端强制 `selfAsRegistrar=true`，前端不展示开关并提示"自动"）；仅 `TEAM` 版 SHALL 提供该选择的开关，由管理员自行决定。

#### Scenario: 个人版无需选择自动成为

- **WHEN** 管理员在初始化向导选择 `PERSONAL` 并进入注册机构步骤
- **THEN** 向导不展示"设为注册管理员"开关，以"自动"标识说明当前超级管理员将自动兼任 Registrar；提交后管理员即持有 Registrar 身份

#### Scenario: 团队版提供选择开关

- **WHEN** 管理员在初始化向导选择 `TEAM` 并进入注册机构步骤
- **THEN** 向导展示"将当前超级管理员设为该机构的注册管理员"开关，管理员勾选则提交后成为 Registrar，不勾选则不创建

### Requirement: setup 业务数据范围含注册表上下文

初始化向导的业务数据创建范围 SHALL 在注册规范创建完成之后扩展一步：创建注册表上下文（Registry Context）并回填注册规范的默认上下文。该步骤 MUST 晚于注册机构自有命名空间标识/版本生成策略的配置生效，Registry Context 的命名空间 MUST 为注册机构的自有命名空间，其标识符与版本 MUST 由该命名空间已配置的生成规则产出；该步骤 MUST 在系统初始化提交（commit）时以独立事务执行，其失败 MUST NOT 阻断 setup 的其余业务数据创建与 commit 本身。

#### Scenario: 初始化完成后默认上下文就绪

- WHEN 管理员完成初始化向导（setup 提交且 commit 成功）
- THEN 系统中存在归属向导注册机构的 Registry Context（标识/版本来自其自有命名空间的生成规则），且注册规范的默认上下文指向该 Registry Context

#### Scenario: 该步骤失败不阻断向导

- WHEN Registry Context 创建步骤失败而其余治理数据（注册机构、命名空间、注册规范）创建成功
- THEN 初始化照常完成；Registry Context 由后续启动的补建路径再次尝试创建

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

### Requirement: 静态预置数据的统一定义与时机
系统 SHALL 将全部静态预置数据收敛到单一幂等入口 `StaticDataPresetService.presetAll()`，数据值仅以 SQL 预置脚本为数据源（2026-09-19 用户裁定：应用代码 MUST NOT 包含名称预置数据、不做运行时 Locale 解析）。静态预置集合 MUST 包含：G20 语言代码（22 条，固定 UUID）、语言中文名称与地区名称（22 条）、系统组织与 `sys:` 系统默认命名空间（含中英双指称）、`identifier_strategy_config` 全局默认行（`UUID`/`INTERNAL`/`is_global_default=1`）、占位超级管理员（`admin` / `NO_PASSWORD`）、每注册机构的标准注册流转规则（调和语义，不覆盖用户定制规则）。`presetAll()` MUST 幂等：连续执行两次结果集不变。

系统组织不再是预置数据：`sys:` 系统默认命名空间的 NamingAuthority MUST 为初始化向导创建的注册机构（如"默认注册机构"）；初始化完成前无注册机构时 NamingAuthority 留空，setup MUST 在创建注册机构后完成挂接。历史系统组织（固定 UUID `00000000-0000-0000-0000-000000000001`）不再创建；存量库由启动调和改挂注册机构，并在无任何引用（命名空间 NamingAuthority、联系人所属组织）时移除该行。用户可见的组织列表与组织下拉选项 MUST NOT 包含系统组织；系统组织 MUST NOT 被删除（删除会破坏 `sys:` 命名空间所属关系）。

#### Scenario: 数据库设置完成后静态数据全部就绪
- **WHEN** 管理员在向导数据库步骤保存数据源配置并重启
- **THEN** Flyway 完成建表与迁移后，`presetAll()` MUST 在应用就绪前执行完毕，静态预置集合中的全部数据可用

#### Scenario: 幂等重复执行
- **WHEN** `presetAll()` 在同一数据库上连续执行两次
- **THEN** 两张语言表行数、系统组织与命名空间、全局默认策略、占位管理员均保持不变，不产生重复行

#### Scenario: 三方言一致
- **WHEN** 分别在 SQLite、SQL Server、达梦全新库上完成上述流程
- **THEN** 静态预置集合内容一致（方言差异仅限 SQL 语法与 schema 限定）

### Requirement: 静态预置的三处执行时机复用同一入口
`presetAll()` MUST 在三处被调用且顺序一致：应用启动期（Flyway 之后）、恢复出厂设置清空后、初始化向导 `setup()` 清理用户数据后。初始化向导 `setup()` MUST NOT 直接写入任何静态预置数据（不得调用语言初始化、预置重放等步骤的旁路实现）；`FactoryResetService` MUST NOT 保留与统一入口不一致的预置序列。

#### Scenario: 恢复出厂后静态数据完整
- **WHEN** 管理员执行恢复出厂设置
- **THEN** 出厂完成后静态预置集合完整可用，且 `identifier_strategy_config` 全局默认行存在（含 SQL Server 方言）

#### Scenario: 向导 setup 不写静态预置
- **WHEN** 调用 `/api/v1/init-wizard/setup`
- **THEN** 请求处理过程中静态预置数据仅由 `presetAll()` 幂等兜底维护，`setup` 代码路径 MUST NOT 包含语言代码或语言名称的写入逻辑

### Requirement: 初始化向导清理保留静态预置表
`InitWizardService.clearBusinessDataOnly()` 的清理保留集合 MUST 包含纯静态预置表 `language_identification` 与 `ext_language_identification`，与 `account`、`system_setting`、`flyway_schema_history` 一并不被清空；其余业务表允许清空，清空后由 `presetAll()` 幂等兜底恢复静态行。

#### Scenario: setup 清理后语言预置保持
- **WHEN** 管理员重复提交 `setup`（上一次未完成）
- **THEN** 清理完成后 `language_identification` 22 行与 `ext_language_identification` 22 行保持不变（或经 `presetAll()` 兜底后恢复），语言中文名称不丢失

### Requirement: 演示数据与初始化隔离
上下文演示数据（8 条演示 Context）MUST 由独立配置项 `app.seed.demo-data.enabled` 控制，默认值 MUST 为 `false`；为 `false` 时应用启动 MUST NOT 灌入演示数据，初始化向导与恢复出厂设置亦 MUST NOT 产生演示数据。

#### Scenario: 默认配置下无演示数据
- **WHEN** 全新部署使用默认配置完成初始化
- **THEN** `context` 表中不含任何演示上下文数据

#### Scenario: 显式开启后演示数据可用
- **WHEN** 部署方设置 `app.seed.demo-data.enabled=true` 并重启
- **THEN** 应用启动时按既有规则灌入 8 条演示上下文（仅当 context 表为空）

### Requirement: 治理对象创建顺序与注册机构 Primary 标志
初始化 `setup` MUST 在同一事务内按以下顺序创建治理对象：先创建注册规范（Registry_Specification），再创建注册机构（Registration_Authority）与注册人员（Registrar），最后创建自有命名空间。注册规范的创建 MUST NOT 依赖注册机构的存在，且注册规范 MUST 作用于系统全局（每个注册系统一条）。初始化创建的注册机构 MUST 在后台被标记为 Primary（隐式标志，不进入任何响应 DTO 或前端界面）；删除保护语义见 `registration-authority-lifecycle` 能力。初始化完成后由注册表上下文引导创建 Registry Context 并回填注册规范默认上下文的机制保持不变。

#### Scenario: 注册规范先于注册机构创建

- **WHEN** 管理员提交初始化 setup
- **THEN** 系统先于注册机构创建注册规范；注册规范不引用任何注册机构，且创建失败时不产生注册机构数据

#### Scenario: 初始化机构带 Primary 标志

- **WHEN** 管理员完成初始化向导提交
- **THEN** 本次创建的注册机构在后台被标记为 Primary；任何 API 响应与前端界面均不出现该标志
