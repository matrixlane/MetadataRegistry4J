# namespace-identifier-policy Specification

## Purpose
TBD - created by archiving change namespace-identifier-strategy. Update Purpose after archive.
## Requirements
### Requirement: 命名空间级标识符输入模式
命名空间 MUST 支持标识符输入模式配置（`identifierInputMode`），取值为 `AUTO_ONLY`（自动生成、禁止手动）、`AUTO_WITH_OVERRIDE`（自动生成为主、允许手动覆盖）、`MANUAL_ONLY`（禁用自动生成、必须手动填写），缺省值 MUST 为 `AUTO_WITH_OVERRIDE`。系统 SHALL 在所有创建受管项/作用域标识符的入口统一裁决：AUTO_ONLY 下请求携带非空标识符 MUST 拒绝；MANUAL_ONLY 下标识符为空 MUST 拒绝；AUTO_WITH_OVERRIDE 下空则生成、非空则校验后采用。模式值无法解析时 MUST 回退 `AUTO_WITH_OVERRIDE`。

#### Scenario: AUTO_ONLY 拒绝手动标识符
- **WHEN** 命名空间的标识符输入模式为 AUTO_ONLY，创建请求携带非空标识符
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误，提示该命名空间禁止手动指定标识符

#### Scenario: MANUAL_ONLY 拒绝空标识符
- **WHEN** 命名空间的标识符输入模式为 MANUAL_ONLY，创建请求未携带标识符
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误，提示自动生成已禁用、须手动填写标识符

#### Scenario: AUTO_WITH_OVERRIDE 空值自动生成
- **WHEN** 命名空间的标识符输入模式为 AUTO_WITH_OVERRIDE，创建请求未携带标识符
- **THEN** 系统 SHALL 按该命名空间生效的生成策略自动分配标识符

#### Scenario: 缺省模式保持兼容
- **WHEN** 既有命名空间未显式设置标识符输入模式（升级存量数据）
- **THEN** 系统 SHALL 按 AUTO_WITH_OVERRIDE 裁决，行为与升级前一致

### Requirement: 命名空间级版本输入模式与版本化策略校验
命名空间 MUST 支持独立于标识符的版本输入模式（`versionInputMode`，同一组枚举，缺省 `AUTO_WITH_OVERRIDE`）与版本化策略（`versioningPolicy`：SEMVER / SEQUENTIAL / DATE，缺省 SEMVER）。创建时版本为空且版本模式为 AUTO 类时，系统 MUST 按版本化策略生成初始版本（SEMVER → `1.0.0`，SEQUENTIAL → `1`，DATE → 当天 `yyyy-MM-dd`）；版本模式为 MANUAL_ONLY 时版本为空 MUST 拒绝。手动提供的版本号（含创建与版本变更）MUST 通过版本化策略的格式校验（SEMVER 走语义化版本规则、SEQUENTIAL 为非负整数、DATE 为合法 `yyyy-MM-dd` 日期）；版本模式为 AUTO_ONLY 时手动版本号 MUST 拒绝。

#### Scenario: 自动生成初始版本
- **WHEN** 命名空间版本输入模式为 AUTO_WITH_OVERRIDE、版本化策略为 SEQUENTIAL，创建请求未携带版本号
- **THEN** 系统 SHALL 将初始版本置为 `1`

#### Scenario: MANUAL_ONLY 版本必填
- **WHEN** 命名空间版本输入模式为 MANUAL_ONLY，创建请求未携带版本号
- **THEN** 系统 SHALL 拒绝该请求并提示版本号必填

#### Scenario: AUTO_ONLY 拒绝手动版本
- **WHEN** 命名空间版本输入模式为 AUTO_ONLY，创建或变更请求携带版本号
- **THEN** 系统 SHALL 拒绝该请求并提示禁止手动指定版本号

#### Scenario: 手动版本格式校验
- **WHEN** 命名空间版本化策略为 DATE，用户在创建或版本变更时提供 `2026-13-45` 作为版本号
- **THEN** 系统 SHALL 拒绝并提示版本号须符合日期版本规则（yyyy-MM-dd）

### Requirement: 手动标识符正则校验
命名空间 SHALL 支持可选的手动标识校验正则（`manualIdentifierPattern`）。配置后，手动提供的标识符 MUST 完整匹配该正则，否则系统拒绝保存并提示不符合校验规则；正则配置本身非法时系统 SHALL 记警告并跳过该校验（不阻断保存）。未配置正则时手动标识符仅做非空与唯一性校验。

#### Scenario: 手动标识符不通过正则
- **WHEN** 命名空间配置手动标识校验正则 `^[A-Z0-9_-]+$`，用户手动填写 `de 001`
- **THEN** 系统 SHALL 拒绝保存并提示标识符不符合该校验规则

#### Scenario: 非法正则配置跳过校验
- **WHEN** 命名空间的手动标识校验正则为无法编译的字符串，用户手动填写任意标识符
- **THEN** 系统 SHALL 记录警告并按未配置正则处理，不阻断保存

### Requirement: 命名空间设置页的策略配置与版本化管理
命名空间创建/更新接口 MUST 接受策略字段（输入模式、版本化策略、手动标识正则、生成策略类型及其参数），请求省略某字段时 MUST NOT 改动对应既有设置。请求携带生成策略时，系统 SHALL 对该命名空间的 `IdentifierStrategyConfig` 做版本化 upsert：禁用既有 PUBLISHED 配置、写入递增版本的新 PUBLISHED 配置并立即刷新生效；策略为 UUID 时 SHALL 仅禁用既有配置而不新增配置行（UUID 为内置兜底）。命名空间查询响应 MUST 回传当前生效的策略设置供设置页回填。前端命名空间设置页 SHALL 提供"标识生成策略"与"版本生成策略"页签，MANUAL_ONLY 时禁用生成策略控件并给出说明。

#### Scenario: 设置页修改生成策略产生新版本
- **WHEN** 管理员在命名空间设置页将生成策略从 UUID 改为 SEQUENCE（起始值 100、补零 6 位）并保存
- **THEN** 系统 SHALL 禁用该命名空间既有 PUBLISHED 配置，创建 version 递增的 SEQUENCE 配置（PUBLISHED）并立即用于后续标识符生成

#### Scenario: 省略策略字段不影响既有配置
- **WHEN** 调用方更新命名空间基础信息但未携带任何策略字段
- **THEN** 系统 MUST NOT 改动该命名空间的输入模式、版本化策略与生效的生成策略配置

#### Scenario: 改为 UUID 清除自定义配置
- **WHEN** 管理员将命名空间生成策略从 SEQUENCE 改回 UUID 并保存
- **THEN** 系统 SHALL 禁用既有 SEQUENCE 配置且不新增配置行，后续生成走内置 UUID 兜底

#### Scenario: 设置页 MANUAL_ONLY 禁用策略控件
- **WHEN** 管理员在命名空间设置页将标识符输入模式切为 MANUAL_ONLY
- **THEN** 前端 SHALL 禁用生成策略类型控件并展示"手动模式不做自动生成"的说明

### Requirement: 初始化向导的标识/版本策略采集
初始化向导命名空间步骤 SHALL 采集标识符输入模式、版本输入模式、生成策略类型与参数（前缀、模板、序列起始值、补零位数）及版本化策略。创建默认命名空间时，系统 MUST 将输入模式与版本化策略写入命名空间；标识符输入模式为 MANUAL_ONLY 时 MUST NOT 创建 `IdentifierStrategyConfig`；其余模式按所选策略创建 PUBLISHED 配置。非法输入模式值 MUST 归一为 `AUTO_WITH_OVERRIDE`。前缀缺省 MUST 为空串，不再默认 `ITEM`。

#### Scenario: MANUAL_ONLY 初始化不建策略配置
- **WHEN** 管理员在初始化向导将标识符输入模式设为 MANUAL_ONLY 并完成初始化
- **THEN** 系统 SHALL 创建命名空间并持久化输入模式，但不创建任何 `IdentifierStrategyConfig`

#### Scenario: 向导参数完整落库
- **WHEN** 管理员在初始化向导选择 TEMPLATE 策略、模板 `{ns}.{type}.{seq:4}`、补零 4 位、版本模式 MANUAL_ONLY
- **THEN** 系统 SHALL 创建对应 PUBLISHED 策略配置（含模板与 pad 参数）并将版本输入模式写入命名空间

