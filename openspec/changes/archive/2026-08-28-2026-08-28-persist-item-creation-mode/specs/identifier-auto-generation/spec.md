## MODIFIED Requirements

### Requirement: 命名空间内唯一标识符自动生成 (Auto-Generation of Unique Identifiers)

对于自有命名空间内的本地注册元数据，当且仅当用户选择自动生成且注册请求中没有填写标识符时，系统 MUST 基于该命名空间已发布的段模板策略自动分配唯一标识符；输入变量段的值 SHALL 由注册请求的 variables 提供并通过段级校验；元数据对象缩写段的值 SHALL 由系统按被注册条目的对象类型经系统级静态映射表推导。独立标识策略选项 MUST 仅包含段模板：UUID、URN、SEQUENCE MUST NOT 作为独立策略出现（顺序号与 UUID 作为模板段组件可用）。已发布模板 MUST 通过唯一性段校验（恰好一个 SEQUENCE/TIMESTAMP/UUID）；模板缺少唯一性段而无法保证唯一值时，系统 MUST NOT 执行自动生成。顺序号作用域 SHALL 按模板结构自动推导：模板含 OBJECT_ABBREVIATION 段时按对象缩写值区分作用域（每类型独立计数），否则为命名空间级全局计数。手动输入时，系统 MUST 对输入的标识符执行格式与唯一性验证。

创建时用户选择的标识符生成方式（`AUTO` / `MANUAL`） MUST 持久化到 `ScopedIdentifier.identifierCreationMode`。当请求体未显式携带 `identifierMode` 时，系统 SHALL 按"请求体 identifier 是否为空"反推（空 → `AUTO`，非空 → `MANUAL`）并写入。编辑态可编辑性与后端行为 MUST 依据持久化的 `identifierCreationMode` 决定：`AUTO` 时后端 MUST 拒绝任何对 identifier 的手动修改请求；`MANUAL` 时后端允许在单 SI 条件下改写 identifier 并按命名空间 `manualIdentifierPattern` 与已发布模板格式校验；`NULL`（既存记录）按 `MANUAL` 等价处理以保持向后兼容。mode 字段本身在编辑过程中 MUST 保持不变。

具体生成策略类型（UUID / URN / SEQUENCE / TEMPLATE）由命名空间级 `IdentifierStrategyConfig.strategyType` 全局承载，不在 `ScopedIdentifier` 上重复记录。

#### Scenario: 段模板自动生成

- **WHEN** 自有命名空间已发布段模板策略（OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)），注册请求标识符为空
- **THEN** 系统按模板生成标识（首个数据元为 `DE.00001`），序号在对象缩写作用域内以悲观锁原子递增并保证命名空间内唯一；同时写入 `identifierCreationMode='AUTO'`

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
- **THEN** 系统按命名空间已发布模板派生的格式正则校验，并校验命名空间内唯一性；同时写入 `identifierCreationMode='MANUAL'`

#### Scenario: 编辑 AUTO 模式的标识符被拒绝

- **WHEN** 用户尝试编辑 `identifierCreationMode='AUTO'` 的 ScopedIdentifier，请求体包含非空 identifier
- **THEN** 后端 MUST 拒绝请求并返回业务校验错误，提示"该标识符创建时选择自动生成，不允许手动修改"

### Requirement: 前端标识符生成模式切换与验证控制 (Dynamic Validation and Form Control)

前端项创建表单 MUST 在"标识符"输入框的右侧内嵌一个切换控件（与输入框视觉复合，承载于 `el-input` 的 `append` 槽位，如 `el-segmented` 或等价的紧凑单选组件），提供"自动生成"与"手动输入"两种模式；该内嵌控件仅在所选命名空间的 `identifierInputMode = AUTO_WITH_OVERRIDE` 且非编辑态下显示，`AUTO_ONLY` 与 `MANUAL_ONLY` 模式下 MUST 隐藏（无需切换，由命名空间输入策略直接确定）。当内嵌控件选中"自动生成"时，标识符输入框 MUST 被禁用、提示"保存后自动生成"占位符，且前端表单必填校验 MUST 放过；当内嵌控件选中"手动输入"时，输入框 MUST 启用并强制必填校验，可按命名空间 `manualIdentifierPattern` 校验。

创建时用户在切换控件上的选择 MUST 经 `ItemRegistrationRequest.identifierMode`（`AUTO` / `MANUAL`）传至后端，由后端持久化到 `ScopedIdentifier.identifierCreationMode`。

当编辑已有 Item 时，标识符输入框与内嵌切换控件的状态 MUST 按持久化的 `identifierCreationMode` 决定：

- `AUTO`：标识符输入框 MUST 置灰禁用、内嵌切换控件 MUST 不显示、占位符"自动生成，不可编辑"；
- `MANUAL`：标识符输入框 MUST 启用、内嵌切换控件可显示（AUTO_WITH_OVERRIDE 下）；
- `NULL`（既存记录）：等价 `MANUAL`。

同样的内嵌控件模式 SHALL 应用于版本字段：编辑态 MUST 按持久化的 `versionCreationMode` 决定版本输入框禁用与内嵌控件可见性；`AUTO_ONLY` / `MANUAL_ONLY` 模式下 MUST 隐藏内嵌控件；自动模式下版本输入框禁用并提示"保存后自动生成"占位符；手动模式下启用并按命名空间 `versioningPolicy` 校验。

#### Scenario: 切换为自动生成模式

- **WHEN** 用户在新建元数据表单中通过标识符输入框右侧的内嵌切换控件选中"自动生成"
- **THEN** 标识符输入框变为禁用状态，值被清空，前端表单通过"标识符必填项"的校验；提交时携带 `identifierMode='AUTO'` 与空 identifier

#### Scenario: 切换为手动输入模式

- **WHEN** 用户在新建元数据表单中通过标识符输入框右侧的内嵌切换控件选中"手动输入"
- **THEN** 标识符输入框变为可用状态，用户可以直接编辑，前端提交前必须通过"标识符不能为空"的非空规则校验；提交时携带 `identifierMode='MANUAL'`

#### Scenario: 编辑已有元数据项（AUTO 模式）

- **WHEN** 用户打开 `identifierCreationMode='AUTO'` 的已有数据元的编辑表单
- **THEN** 内嵌切换控件不显示，标识符输入框呈现禁用状态，占位符提示"自动生成，不可编辑"；提交时携带 `identifierMode='AUTO'` 与空 identifier

#### Scenario: 编辑已有元数据项（MANUAL 模式）

- **WHEN** 用户打开 `identifierCreationMode='MANUAL'` 的已有数据元的编辑表单
- **THEN** 内嵌切换控件可显示（AUTO_WITH_OVERRIDE 下），标识符输入框呈现可用状态，显示当前标识符且可编辑

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

- **WHEN** 所选命名空间的 `versionInputMode` 为 AUTO_ONLY 或 MANUAL_ONLY，或当前为编辑态（无论 `versionCreationMode` 为何）
- **THEN** 版本输入框右侧的内嵌切换控件 MUST 隐藏；AUTO_ONLY 下版本输入框禁用并提示"保存后自动生成"；MANUAL_ONLY 下启用并强制必填；编辑态下输入框禁用并保留当前版本号

#### Scenario: 编辑 AUTO 模式的版本字段

- **WHEN** 用户打开 `versionCreationMode='AUTO'` 的已有 Item 编辑表单
- **THEN** 版本输入框禁用、内嵌切换控件不显示；提交时携带 `versionMode='AUTO'` 与空 version；后端按命名空间版本策略自动递增

#### Scenario: 编辑 MANUAL 模式的版本字段

- **WHEN** 用户打开 `versionCreationMode='MANUAL'` 的已有 Item 编辑表单
- **THEN** 版本输入框启用、内嵌切换控件可显示；用户显式指定版本时按命名空间版本策略校验后采用，留空按策略递增

### Requirement: 外部命名空间标识与版本手动编辑策略

外部命名空间（相对当前注册者非自有） MUST NOT 自动生成标识：无标识的创建请求 SHALL 被拒绝；即使该命名空间未配置任何策略，全局默认策略 MUST NOT 回退为其生成。标识与版本 SHALL 手动录入或随导入提供；命名空间可增加可选的格式校验规则（直接配置正则表达式，仅校验，不生成）。外部命名空间下的 SI MUST 写入 `identifierCreationMode=NULL` 与 `versionCreationMode=NULL`（按 MANUAL 等价处理），编辑态允许修改 identifier 与 version。

#### Scenario: 无标识创建被拒绝

- **WHEN** 在外部命名空间提交未填写标识的创建请求
- **THEN** 系统拒绝该请求并提示外部命名空间的标识必须手动录入

#### Scenario: 全局默认策略不回退生成

- **WHEN** 外部命名空间未配置任何策略而系统存在全局默认策略
- **THEN** 系统 MUST NOT 为该命名空间生成标识

#### Scenario: 导入时格式校验

- **WHEN** 外部命名空间配置了校验正则 `DE\d{5}`，导入标识 `DE00001`
- **THEN** 校验通过并正常入库；导入不符合格式的的标识（如 `DE001`）时被拒绝并提示格式要求；该 SI 的 `identifierCreationMode=NULL`

#### Scenario: 版本手动编辑

- **WHEN** 在外部命名空间登记外部标准的新版次（如 "2023" 版）
- **THEN** 版本字符串由用户手动录入，系统 MUST NOT 按版本自动化策略递增；该 SI 的 `versionCreationMode=NULL`

## ADDED Requirements

### Requirement: 版本生成方式持久化与编辑会话一致性 (Persisted Version Creation Mode)

版本字段的"自动生成 / 手动输入"创建选择 MUST 持久化到 `ScopedIdentifier.versionCreationMode`（取值 `AUTO` / `MANUAL`）。当请求体未显式携带 `versionMode` 时，系统 SHALL 按"请求体 version 是否为空"反推（空 → `AUTO`，非空 → `MANUAL`）并写入。编辑态可编辑性与后端行为 MUST 依据持久化的 `versionCreationMode` 决定：`AUTO` 时后端 MUST 忽略请求体携带的 version 字段并按命名空间 `versioningPolicy` 自动递增；`MANUAL` 时后端接受请求体显式 version 并按命名空间 `versioningPolicy` 校验，空时按策略递增；`NULL`（既存记录）按 `MANUAL` 等价处理。mode 字段本身在编辑过程中 MUST 保持不变。

具体版本策略类型（SEMVER / SEQUENTIAL / DATE）由命名空间级 `Namespace.versioningPolicy` 全局承载，不在 `ScopedIdentifier` 上重复记录。

前端 `ItemRegistrationSection` 编辑态按持久化的 `versionCreationMode` 决定版本输入框禁用与内嵌切换控件可见性。

#### Scenario: 创建时按 AUTO 持久化版本 mode

- **WHEN** 用户在自有命名空间下创建 Item 并选择版本自动生成（请求体 version 为空或携带 `versionMode='AUTO'`）
- **THEN** 后端按命名空间 `versioningPolicy` 自动生成初始版本并写入 `versionCreationMode='AUTO'`

#### Scenario: 创建时按 MANUAL 持久化版本 mode

- **WHEN** 用户在自有命名空间下创建 Item 并选择版本手动输入（请求体 version 非空或携带 `versionMode='MANUAL'`）
- **THEN** 后端按命名空间 `versioningPolicy` 校验用户填写的 version，写入 `versionCreationMode='MANUAL'`

#### Scenario: 编辑 AUTO 模式的版本被忽略

- **WHEN** 用户编辑 `versionCreationMode='AUTO'` 的 ScopedIdentifier，请求体包含显式 version
- **THEN** 后端 MUST 忽略请求体的 version 字段，按命名空间 `versioningPolicy` 对当前版本递增后写入；mode 字段 MUST 保持为 `AUTO`

#### Scenario: 编辑 MANUAL 模式的版本被采纳

- **WHEN** 用户编辑 `versionCreationMode='MANUAL'` 的 ScopedIdentifier，请求体包含显式 version 且与当前不同
- **THEN** 后端按命名空间 `versioningPolicy` 校验请求体版本，通过后采用新版本；mode 字段 MUST 保持为 `MANUAL`

#### Scenario: 编辑既存 NULL 模式的版本

- **WHEN** 用户编辑 `versionCreationMode='NULL'` 的既存 ScopedIdentifier
- **THEN** 行为等价 `MANUAL`：请求体 version 非空时按策略校验后采用；空时按策略递增
