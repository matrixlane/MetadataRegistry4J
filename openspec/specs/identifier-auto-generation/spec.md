# 标识符自动生成 (Identifier Auto-Generation)

## Purpose
This capability defines the rules for namespace-level unique identifier auto-generation (supporting UUID, SEQUENCE with pessimistic locking, and TEMPLATE strategies) and frontend form control / validation switching.
## Requirements
### Requirement: 命名空间内唯一标识符自动生成 (Auto-Generation of Unique Identifiers)

对于自有命名空间内的本地注册元数据，当且仅当用户选择自动生成且注册请求中没有填写标识符时，系统 MUST 基于该命名空间已发布的段模板策略自动分配唯一标识符；输入变量段的值 SHALL 由注册请求的 variables 提供并通过段级校验；元数据对象缩写段的值 SHALL 由系统按被注册条目的对象类型经系统级静态映射表推导。独立标识策略选项 MUST 仅包含段模板：UUID、URN、SEQUENCE MUST NOT 作为独立策略出现（顺序号与 UUID 作为模板段组件可用）。已发布模板 MUST 通过唯一性段校验（恰好一个 SEQUENCE/TIMESTAMP/UUID）；模板缺少唯一性段而无法保证唯一值时，系统 MUST NOT 执行自动生成。顺序号作用域 SHALL 按模板结构自动推导：模板含 OBJECT_ABBREVIATION 段时按对象缩写值区分作用域（每类型独立计数），否则为命名空间级全局计数。手动输入时，系统 MUST 对输入的标识符执行格式与唯一性验证。

#### Scenario: 段模板自动生成

- **WHEN** 自有命名空间已发布段模板策略（OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)），注册请求标识符为空
- **THEN** 系统按模板生成标识（首个数据元为 `DE.00001`），序号在对象缩写作用域内以悲观锁原子递增并保证命名空间内唯一

#### Scenario: 变量缺失拒绝注册

- **WHEN** 模板含必填变量段而注册请求未提供对应值
- **THEN** 系统拒绝注册并指明缺失的变量段

#### Scenario: 独立策略选项收敛为段模板

- **WHEN** 管理员配置命名空间的标识生成策略
- **THEN** 独立策略选项仅呈现段模板（含预设库）；UUID 可作为模板中的一个段组件选用

#### Scenario: 对象缩写由条目类型经系统映射表推导

- **WHEN** 模板以 OBJECT_ABBREVIATION 段开头，用户在自有命名空间注册值域（系统映射表 ValueDomain→VD）
- **THEN** 生成的标识以 VD 开头，无需用户填写该段，也无需在模板中配置映射

#### Scenario: 手动输入仍受格式与唯一性约束

- **WHEN** 用户在自有命名空间选择手动输入标识符
- **THEN** 系统按命名空间已发布模板派生的格式正则校验，并校验命名空间内唯一性

### Requirement: 前端标识符生成模式切换与验证控制 (Dynamic Validation and Form Control)

前端项创建表单 MUST 在"标识符"输入框的右侧内嵌一个切换控件（与输入框视觉复合，承载于 `el-input` 的 `append` 槽位，如 `el-segmented` 或等价的紧凑单选组件），提供"自动生成"与"手动输入"两种模式；该内嵌控件仅在所选命名空间的 `identifierInputMode = AUTO_WITH_OVERRIDE` 且非编辑态下显示，`AUTO_ONLY` 与 `MANUAL_ONLY` 模式下 MUST 隐藏（无需切换，由命名空间输入策略直接确定）。当内嵌控件选中"自动生成"时，标识符输入框 MUST 被禁用、提示"保存后自动生成"占位符，且前端表单必填校验 MUST 放过；当内嵌控件选中"手动输入"时，输入框 MUST 启用并强制必填校验，可按命名空间 `manualIdentifierPattern` 校验。当编辑已有项时，标识符输入框 MUST 置灰禁用，内嵌切换控件 MUST 不显示。同样的内嵌控件模式 SHALL 应用于版本字段：`AUTO_ONLY` / `MANUAL_ONLY` / 编辑态 MUST 隐藏控件；自动模式下版本输入框禁用并提示"保存后自动生成"占位符；手动模式下启用并按命名空间 `versioningPolicy` 校验。

#### Scenario: 切换为自动生成模式

- **WHEN** 用户在新建元数据表单中通过标识符输入框右侧的内嵌切换控件选中"自动生成"
- **THEN** 标识符输入框变为禁用状态，值被清空，且前端表单通过"标识符必填项"的校验

#### Scenario: 切换为手动输入模式

- **WHEN** 用户在新建元数据表单中通过标识符输入框右侧的内嵌切换控件选中"手动输入"
- **THEN** 标识符输入框变为可用状态，用户可以直接编辑，并且前端提交前必须通过"标识符不能为空"的非空规则校验

#### Scenario: 编辑已有元数据项

- **WHEN** 用户打开已有数据元的编辑表单
- **THEN** 内嵌切换控件不显示，标识符输入框呈现禁用状态，显示当前的标识符，且不可编辑

#### Scenario: AUTO_ONLY 命名空间下隐藏内嵌切换控件

- **WHEN** 所选命名空间的 `identifierInputMode` 为 AUTO_ONLY
- **THEN** 标识符输入框右侧的内嵌切换控件 MUST 隐藏，标识符输入框禁用并提示"保存后自动生成"

#### Scenario: MANUAL_ONLY 命名空间下隐藏内嵌切换控件

- **WHEN** 所选命名空间的 `identifierInputMode` 为 MANUAL_ONLY
- **THEN** 标识符输入框右侧的内嵌切换控件 MUST 隐藏，标识符输入框启用并强制必填（可按 `manualIdentifierPattern` 校验）

#### Scenario: 版本字段内嵌切换控件在 AUTO_WITH_OVERRIDE 下显示

- **WHEN** 所选命名空间的 `versionInputMode` 为 AUTO_WITH_OVERRIDE 且非编辑态
- **THEN** 版本输入框右侧的内嵌切换控件 MUST 显示，默认选中"自动生成"；切换至"手动输入"后版本输入框启用并按命名空间 `versioningPolicy` 校验

#### Scenario: 版本字段内嵌切换控件在 AUTO_ONLY / MANUAL_ONLY / 编辑态下隐藏

- **WHEN** 所选命名空间的 `versionInputMode` 为 AUTO_ONLY 或 MANUAL_ONLY，或当前为编辑态
- **THEN** 版本输入框右侧的内嵌切换控件 MUST 隐藏；AUTO_ONLY 下版本输入框禁用并提示"保存后自动生成"；MANUAL_ONLY 下启用并强制必填；编辑态下输入框禁用并保留当前版本号

### Requirement: 外部命名空间标识与版本手动编辑策略

外部命名空间（相对当前注册者非自有） MUST NOT 自动生成标识：无标识的创建请求 SHALL 被拒绝；即使该命名空间未配置任何策略，全局默认策略 MUST NOT 回退为其生成。标识与版本 SHALL 手动录入或随导入提供；命名空间可增加可选的格式校验规则（直接配置正则表达式，仅校验，不生成）。

#### Scenario: 无标识创建被拒绝

- **WHEN** 在外部命名空间提交未填写标识的创建请求
- **THEN** 系统拒绝该请求并提示外部命名空间的标识必须手动录入

#### Scenario: 全局默认策略不回退生成

- **WHEN** 外部命名空间未配置任何策略而系统存在全局默认策略
- **THEN** 系统 MUST NOT 为该命名空间生成标识

#### Scenario: 导入时格式校验

- **WHEN** 外部命名空间配置了校验正则 `DE\d{5}`，导入标识 `DE00001`
- **THEN** 校验通过并正常入库；导入不符合格式的标识（如 `DE001`）时被拒绝并提示格式要求

#### Scenario: 版本手动编辑

- **WHEN** 在外部命名空间登记外部标准的新版次（如 "2023" 版）
- **THEN** 版本字符串由用户手动录入，系统 MUST NOT 按版本自动化策略递增

