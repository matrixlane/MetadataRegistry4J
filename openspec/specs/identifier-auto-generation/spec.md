# 标识符自动生成 (Identifier Auto-Generation)

## Purpose
This capability defines the rules for namespace-level unique identifier auto-generation (supporting UUID, SEQUENCE with pessimistic locking, and TEMPLATE strategies) and frontend form control / validation switching.
## Requirements
### Requirement: 命名空间内唯一标识符自动生成 (Auto-Generation of Unique Identifiers)
对于本地注册元数据，标识符是否自动生成 MUST 由命名空间的标识符输入模式裁决（见 `namespace-identifier-policy`）：AUTO_ONLY 与 AUTO_WITH_OVERRIDE 模式下，当注册请求中的标识符为空时，系统 MUST 基于命名空间的策略配置自动分配唯一的标识符；AUTO_ONLY 模式下系统 MUST 拒绝任何手动标识符。手动提供的标识符（仅 AUTO_WITH_OVERRIDE / MANUAL_ONLY 允许）MUST 通过格式（命名空间手动标识正则，如配置）与唯一性验证。

#### Scenario: 自动生成 UUID 标识符
- **WHEN** 命名空间标识符输入模式允许自动生成，请求中的标识符为空，且该命名空间默认或全局策略为 UUID
- **THEN** 系统必须自动生成一个有效的 UUID 作为标识符，并成功注册

#### Scenario: 自动生成自增序列标识符
- **WHEN** 命名空间标识符输入模式允许自动生成，请求中的标识符为空，且命名空间配置了 SEQUENCE 生成策略
- **THEN** 系统必须以悲观锁保护的行级排他锁形式，从数据库中查询并递增获取下一个序列号，加上配置的 Prefix（支持 `{ns}`/`{type}` 令牌替换）后保存，确保在高并发下不出现重复冲突

#### Scenario: 自动生成模板标识符
- **WHEN** 命名空间标识符输入模式允许自动生成，请求中的标识符为空，且命名空间配置了 TEMPLATE 生成策略
- **THEN** 系统必须基于配置的模板（如 `{ns}.{type}.{seq:4}`）渲染生成最终标识符；模板含 `{seq}` 占位符而调用方未分配序列号时，系统必须从命名空间序列注册表（悲观锁）自分配序列号

### Requirement: 前端标识符生成模式切换与验证控制 (Dynamic Validation and Form Control)
前端项创建表单 MUST 根据所选命名空间的标识符/版本输入模式呈现控件：仅当命名空间为 AUTO_WITH_OVERRIDE 时显示"自动生成/手动输入"单选框（默认选中自动生成，切换命名空间时重置）；AUTO_ONLY 时不显示单选框且输入框恒禁用（占位符"保存后自动生成"）；MANUAL_ONLY 时不显示单选框，输入框必填并启用。自动生成生效时前端表单必填校验 MUST 放过；手动输入生效时 MUST 执行非空校验，且命名空间配置手动标识正则时前端 SHALL 预校验。版本输入框 MUST 同样按版本输入模式联动，占位提示随版本化策略变化（SEMVER 如 1.0.0、SEQUENTIAL 如 1、DATE 如 2026-01-31）。当编辑已有项时，标识符输入框 MUST 禁用且不可编辑，版本框按版本输入模式裁决。

#### Scenario: AUTO_WITH_OVERRIDE 显示切换控件
- **WHEN** 用户在新建元数据表单中选择输入模式为 AUTO_WITH_OVERRIDE 的命名空间
- **THEN** 表单显示"自动生成/手动输入"单选框，默认自动生成，标识符输入框禁用并提示"保存后自动生成"

#### Scenario: AUTO_ONLY 禁用输入框
- **WHEN** 用户在新建元数据表单中选择输入模式为 AUTO_ONLY 的命名空间
- **THEN** 不显示生成方式单选框，标识符输入框保持禁用，前端校验放过标识符必填项

#### Scenario: MANUAL_ONLY 强制手动填写
- **WHEN** 用户在新建元数据表单中选择输入模式为 MANUAL_ONLY 的命名空间
- **THEN** 标识符输入框启用且必填，配置手动标识正则时前端按正则预校验，提交前必须通过校验

#### Scenario: 编辑已有元数据项
- **WHEN** 用户打开已有数据元的编辑表单
- **THEN** 生成方式组件不显示，标识符输入框呈现禁用状态，显示当前的标识符，且不可编辑

### Requirement: 模板与序列策略的命名空间/类型占位符
模板（TEMPLATE）策略 MUST 支持 `{ns}`（命名空间简写前缀 `shorthandPrefix`）与 `{type}`（注册项类型缩写：取类简单名的大写字母，如 DataElement → DE、DataElementConcept → DEC、ValueDomain → VD）占位符；SEQUENCE 策略的前缀字段与 TEMPLATE 的 prefix 字段 MUST 同样支持 `{ns}`/`{type}` 令牌替换。命名空间简写或项类型缺失时对应占位符 MUST 渲染为空串。模板白名单 MUST 允许 `.` 作为分隔符。

#### Scenario: 模板渲染命名空间与类型占位符
- **WHEN** 命名空间简写前缀为 `org:` 下配置模板 `{ns}.{type}.{seq:4}`，创建一个 DataElement 且触发自动生成
- **THEN** 系统 SHALL 生成形如 `org:.DE.0001` 的标识符（`{ns}` 取简写前缀原值，`{type}` 取 DE，序列号按 4 位补零）

#### Scenario: 序列前缀令牌替换
- **WHEN** SEQUENCE 策略前缀配置为 `{type}-`，创建一个 ValueDomain 且触发自动生成
- **THEN** 系统 SHALL 生成以 `VD-` 开头的序列标识符

#### Scenario: 占位符来源缺失时降级为空串
- **WHEN** 命名空间未设置简写前缀，模板为 `{ns}-{seq:4}` 且触发自动生成
- **THEN** 系统 SHALL 将 `{ns}` 渲染为空串，生成形如 `-0001` 的标识符且不报错

