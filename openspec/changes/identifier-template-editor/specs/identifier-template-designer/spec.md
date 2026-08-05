# identifier-template-designer Delta Spec

## MODIFIED Requirements

### Requirement: 段模板模型

标识生成模板 SHALL 为段（Segment）的有序列表，以 JSON 持久化于 `IdentifierStrategyConfig.configJson`。每段 MUST 具有类型与类型化参数。段类型 MUST 支持且仅支持：固定字符串（LITERAL，如自定义前缀）、元数据对象缩写（OBJECT_ABBREVIATION）、顺序号（SEQUENCE）、UUID 段（UUID）、分隔符（SEPARATOR，多种字符可选）、时间戳（TIMESTAMP）、校验位（CHECKSUM）、输入变量（VARIABLE）。随机段（RANDOM）MUST NOT 出现在段类型目录中；含 RANDOM 段的模板 MUST 校验拒绝。SEPARATOR 段的取值 MUST 受限于预定义分隔字符集（如 `.` `-` `_` `/` `:`）。

#### Scenario: 固定前缀加对象缩写加顺序号的通用编排

- **WHEN** 管理员按"公司缩写.对象类型.序号"设计标识模板（期望示例 `WN.DE.00001`）
- **THEN** 模板可表达为：LITERAL("WN") + SEPARATOR(".") + OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)，预览渲染为 `WN.DE.00001`

#### Scenario: 元数据对象缩写随条目类型取值

- **WHEN** 同一套模板（OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)）下，先注册第一个数据元、再注册第一个值域（系统映射表为 DataElement→DE、ValueDomain→VD）
- **THEN** 数据元标识为 `DE.00001`，值域标识为 `VD.00001`，两者各自从起始值计数

#### Scenario: 分隔符多种字符可选且取值受限

- **WHEN** 管理员为 SEPARATOR 段选择字符
- **THEN** 候选为预定义分隔字符集（`.` `-` `_` `/` `:` 等）；输入字符集以外的字符 SHALL 被拒绝

#### Scenario: 混合分隔符与无分隔定长编排

- **WHEN** 管理员设计含混合分隔符（如 `DE-00001`）或完全无分隔的定长模板
- **THEN** 模板通过组合 SEPARATOR 段（或省略之）表达，无需引入额外概念

#### Scenario: RANDOM 段被拒绝

- **WHEN** 提交的模板 JSON 中含 type 为 RANDOM 的段
- **THEN** 系统校验报错，提示该段类型已移除，模板不得保存或发布

### Requirement: 顺序号段与作用域

顺序号段 MUST 支持参数：起始值、步长、补零位、溢出策略。顺序号作用域 SHALL 由引擎按模板结构自动推导，MUST NOT 接受用户显式配置：模板含 OBJECT_ABBREVIATION 段时，作用域 MUST 取该段的解析值（每种对象类型独立计数），该行为强制、隐式，MUST NOT 可切换回全局作用域；模板不含 OBJECT_ABBREVIATION 段时为命名空间级全局计数。模板 JSON 携带显式 `scopeRefs` 参数 MUST 校验拒绝。系统 MUST 以 (`namespace_id`, `scope_key`) 持久化计数器并以数据库行级排他锁原子分配序号。

#### Scenario: 含对象缩写段时按对象类型各自计数

- **WHEN** 模板为 LITERAL("WN") + SEPARATOR(".") + OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)，先后注册两个数据元与一个值域
- **THEN** 依次生成 `WN.DE.00001`、`WN.DE.00002`、`WN.VD.00001`；数据元与值域各自独立计数，用户无需也无法配置作用域

#### Scenario: 无对象缩写段时全局计数

- **WHEN** 模板仅含 SEQUENCE(pad=5) 作为唯一性段，先后注册一个数据元与一个值域
- **THEN** 依次生成 `00001`、`00002`，全命名空间共享一条计数序列

#### Scenario: 显式 scopeRefs 被拒绝

- **WHEN** 提交的模板 JSON 中 SEQUENCE 段携带 `scopeRefs` 参数
- **THEN** 系统校验报错，提示作用域由系统自动推导、不允许显式配置

#### Scenario: 序号溢出

- **WHEN** 某作用域的序号达到补零位可表示的上限
- **THEN** 系统 SHALL 拒绝生成并按溢出策略提示管理员调整配置，MUST NOT 产生截断或重复的标识

### Requirement: 三向派生

同一份段定义 SHALL 派生：（1）注册时的生成器；（2）用于手工录入/导入校验的格式正则；（3）将既有标识拆回段值的反向解析器。系统 SHALL 支持依据反向解析结果对既有标识集合执行序号回填，使各作用域计数器对齐已用最大值。

#### Scenario: 导入后序号回填

- **WHEN** 向某命名空间导入 `DE.00001` 至 `DE.00026` 共 26 个既有数据元标识并执行序号回填后，再注册新数据元
- **THEN** 新标识为 `DE.00027`（DE 作用域计数器已对齐），不与既有标识冲突

#### Scenario: 生成与解析往返一致

- **WHEN** 系统按模板生成任一标识并用派生的解析器拆回
- **THEN** 解析所得各段值与生成时的输入一致

### Requirement: 段规则设计器 UI

命名空间设置与初始化向导中的标识模板编辑器 SHALL 提供：段块条（增删段、上下移排序、点击编辑）、按组分类的段类型调色板（内容段：固定字符串/元数据对象缩写/输入变量；唯一性段：顺序号/时间戳/UUID；修饰段：分隔符/校验位）、类型化参数面板、实时预览（以样例值即时渲染完整标识示例）、合法性校验提示、只读模板表达式视图（形如 `{LITERAL:WN}.{OBJECT_ABBREVIATION}.{SEQUENCE(pad=5)}`，随编辑实时刷新）、试生成多个样例（顺序号样例按起始值递增展示，至多连续 5 个）。调色板 MUST NOT 出现 RANDOM。添加唯一性段时若模板已含唯一性段，编辑器 MUST 引导用户确认替换而非共存。顺序号段的参数面板 MUST NOT 暴露作用域配置项，并 SHALL 静态说明作用域自动推导规则。时间戳段参数面板 SHALL 提供格式预设下拉（年月日、完整到秒、毫秒、微秒）并允许自定义。设计器 MUST 内置通用编排预设（对象缩写+顺序号、固定前缀+对象缩写+顺序号、对象缩写+UUID、对象缩写+时间戳），可一键载入后继续修改；预设 SHALL 为可扩展的 JSON 资源，其名称与描述 MUST NOT 引用任何特定行业标准编号。

#### Scenario: 唯一性段互斥引导

- **WHEN** 模板已含 UUID 段，管理员再从调色板添加顺序号段
- **THEN** 编辑器提示"模板已含唯一性段"，经确认后以顺序号替换 UUID，而非两段共存

#### Scenario: 载入通用预设并微调

- **WHEN** 管理员在设计器中载入"固定前缀+对象缩写+顺序号"预设并将固定前缀改为本机构缩写
- **THEN** 实时预览与模板表达式立即反映修改（如 `WN.DE.00001`），保存后成为该命名空间的草稿策略

#### Scenario: 时间戳精度不足的即时提示

- **WHEN** 管理员以时间戳为唯一性段并选择精度仅到秒的格式（`yyyyMMddHHmmss`）
- **THEN** 编辑器即时提示该格式不满足唯一性段的毫秒级精度要求，并阻止发布

#### Scenario: 非法配置提示

- **WHEN** 模板缺少唯一性段，或变量段缺少校验正则导致无法派生格式正则
- **THEN** 设计器 SHALL 给出明确的校验提示并阻止发布

### Requirement: 策略配置接口与版本化

系统 SHALL 提供策略配置 REST：查询、保存草稿、发布、停用、预览试生成、格式校验。除按命名空间的预览端点外，系统 SHALL 提供无命名空间上下文的预览端点 `POST /identifier-strategy/preview`（请求携带 configJson、样例变量值与条目类型），供初始化向导等尚无命名空间的场景使用；该端点 MUST 与按命名空间预览共用同一引擎渲染管线，返回试生成样例、派生格式正则与校验错误。策略版本化 MUST 复用 `IdentifierStrategyConfig` 既有状态机（DRAFT/PUBLISHED/DISABLED，同一命名空间同一时刻至多一个 PUBLISHED 配置）。策略变更 MUST NOT 回改已生成的历史标识。

#### Scenario: 预览试生成

- **WHEN** 管理员在发布前调用预览接口并提供样例变量值
- **THEN** 系统按草稿模板返回试生成结果与派生的格式正则，不产生任何序号分配

#### Scenario: 无命名空间上下文预览

- **WHEN** 初始化向导中管理员编排模板后，前端调用 `POST /identifier-strategy/preview` 并携带模板 JSON 与样例条目类型 DataElement
- **THEN** 系统返回与运行时生成完全一致的样例标识、派生格式正则与校验错误，且不产生任何序号分配

#### Scenario: 策略升级不影响历史标识

- **WHEN** 某命名空间从模板 v1 升级到 v2 后查询既有标识
- **THEN** 历史标识保持原值，仅新注册的标识使用 v2 模板

### Requirement: 输入变量段的注册期取值

VARIABLE 段 SHALL 在注册时由用户或调用方提供值，段定义支持提示标签（label）、校验正则（pattern）、定长补零（pad）、默认值（default）、必填标记（required）与最大长度（maxLength，未声明按 32 计）；注册表单 MUST 按模板动态渲染对应输入控件。变量值缺失或校验失败 MUST 拒绝注册并指明具体段。

#### Scenario: 动态渲染变量输入

- **WHEN** 用户在模板含变量段（如"科室代码"，pattern=\d{2}，required）的命名空间下新建数据元
- **THEN** 注册表单按模板渲染"科室代码"输入框并按段定义执行校验

#### Scenario: 变量校验失败

- **WHEN** 用户为"科室代码"输入 "2"（不满足 \d{2}）并提交
- **THEN** 系统拒绝注册并提示该变量段的格式要求

## ADDED Requirements

### Requirement: 唯一性段约束

模板 MUST 恰好包含一个唯一性段：顺序号（SEQUENCE）、时间戳（TIMESTAMP）、UUID 三者择其一；缺少或含两个及以上唯一性段的模板 MUST 校验拒绝。TIMESTAMP 作唯一性段时，其 pattern MUST 精确到毫秒（连续 `S` 不少于 3 位），否则校验报错。模板各段最大输出长度之和 MUST 不超过 255（`scoped_identifier.identifier` 列上限）；变量段按其声明的 maxLength 计（未声明按 32 计），超限 MUST 校验拒绝。

#### Scenario: 缺少唯一性段被拒绝

- **WHEN** 提交的模板仅含 LITERAL 与 SEPARATOR 段（无任何唯一性段）
- **THEN** 系统校验报错，提示模板必须恰好包含一个顺序号、时间戳或 UUID 段

#### Scenario: 多个唯一性段被拒绝

- **WHEN** 提交的模板同时含 SEQUENCE 与 TIMESTAMP 段
- **THEN** 系统校验报错，提示唯一性段只能三者择其一

#### Scenario: 时间戳精度不足被拒绝

- **WHEN** 模板以 TIMESTAMP(pattern=yyyyMMdd) 作为唯一性段
- **THEN** 系统校验报错，提示唯一性时间戳段必须精确到毫秒（如 `yyyyMMddHHmmssSSS`）

#### Scenario: 合法唯一性段通过校验

- **WHEN** 模板为 OBJECT_ABBREVIATION + SEPARATOR(".") + UUID(form=full)
- **THEN** 校验通过，模板可保存发布

#### Scenario: 模板输出总长度超限被拒绝

- **WHEN** 模板各段最大输出长度之和超过 255（如超长固定字符串叠加完整 UUID 与多个变量段）
- **THEN** 系统校验报错，提示模板输出长度超出标识列上限

### Requirement: 对象缩写系统级映射表

系统 SHALL 提供对象缩写系统级静态映射表，覆盖全部已注册元数据对象类型（持久化类名到缩写的映射，初始至少包含 DataElement→DE、DataElementConcept→DEC、ObjectClass→OC、Property→PR、ConceptualDomain→CD、ValueDomain→VD、ValueMeaning→VM、DataType→DT、UnitOfMeasure→UOM、ClassificationScheme→CS 等）。映射表 MUST 存于 JSON 配置文件，供用户直接编辑调整。OBJECT_ABBREVIATION 段 MUST 经该映射表解析缩写；段参数中的 `mapping` 废止，提交时 SHALL 被忽略并提示以系统映射表为准；段参数 `default` 保留为未命中映射时的可选兜底。

#### Scenario: 按系统映射表解析缩写

- **WHEN** 模板含 OBJECT_ABBREVIATION 段，注册一个概念域（系统映射表 ConceptualDomain→CD）
- **THEN** 该段取值 CD，无需在模板中配置任何映射

#### Scenario: 未命中类型使用兜底缩写

- **WHEN** 注册条目的对象类型不在系统映射表中，且 OBJECT_ABBREVIATION 段配置了 default="MD"
- **THEN** 该段取值 MD；未配置 default 时生成 MUST 报错并指明缺失映射的对象类型

#### Scenario: 调整映射配置文件生效

- **WHEN** 管理员编辑映射表 JSON 配置文件将 ValueDomain 的缩写由 VD 改为 CV 并重新加载
- **THEN** 此后注册的值域标识以 CV 取值，既有标识不受影响

#### Scenario: 段内 mapping 参数被忽略

- **WHEN** 提交的模板 JSON 中 OBJECT_ABBREVIATION 段携带 `mapping` 参数
- **THEN** 系统忽略该参数并提示缩写以系统映射表为准，校验不因该参数失败
