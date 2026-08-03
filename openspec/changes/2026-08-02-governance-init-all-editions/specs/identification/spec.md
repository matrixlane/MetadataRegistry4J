## ADDED Requirements

### Requirement: 初始化时命名空间级标识与版本生成策略配置
初始化向导创建默认命名空间时，系统 SHALL 同步采集并创建该命名空间的标识生成策略配置（`IdentifierStrategyConfig`：策略类型 UUID/SEQUENCE/TEMPLATE 及前缀、模板参数），状态 MUST 为 PUBLISHED 且立即生效；同时 SHALL 将版本生成策略（如 SEMVER/SEQUENTIAL/DATE）持久化为命名空间级设置（`Namespace.versioningPolicy`，默认 SEMVER）。跳过命名空间创建时 MUST NOT 创建策略配置。注册规范 MUST NOT 承载标识/版本生成策略字段。

#### Scenario: 创建命名空间时策略配置同步落库
- **WHEN** 管理员在初始化向导中创建默认命名空间并选择 SEQUENCE 标识策略与前缀、SEMVER 版本策略
- **THEN** 系统 SHALL 创建命名空间级 `IdentifierStrategyConfig`（PUBLISHED、version=1）并将 `versioningPolicy` 写入命名空间，新策略立即参与标识符生成

#### Scenario: 跳过命名空间时不创建策略配置
- **WHEN** 管理员在初始化向导中跳过命名空间设置
- **THEN** 系统 MUST NOT 创建任何命名空间级 `IdentifierStrategyConfig`

### Requirement: 自有数据标准的命名空间前置条件
在注册机构下创建自有数据标准（注册受管项并在命名空间下生成 `Scoped_Identifier`）前，系统 MUST 存在至少一个与该注册机构关联的命名空间。缺失时系统 SHALL 阻止创建操作并返回业务校验错误，错误信息引导用户先创建命名空间；前端 SHALL 禁用相关创建入口并展示补建引导。导入或访问外部组织数据标准（外部命名空间，无关联注册机构）MUST NOT 受此限制。系统内部命名空间（system-default）SHALL NOT 计入该前置条件。

#### Scenario: 无注册机构关联命名空间时创建自有数据标准被阻止
- **WHEN** 用户在系统中尚无任何与注册机构关联的命名空间时，尝试注册受管项或创建自有数据标准
- **THEN** 系统 SHALL 拒绝该操作并返回业务校验错误，提示必须先创建基于注册机构的命名空间

#### Scenario: 补建命名空间后放行
- **WHEN** 用户在初始化时跳过了命名空间设置，之后创建了基于注册机构的命名空间
- **THEN** 系统 SHALL 允许其创建自有数据标准

#### Scenario: 导入外部数据标准不受限
- **WHEN** 系统中不存在与注册机构关联的命名空间，用户导入外部组织的数据标准（使用外部命名空间）
- **THEN** 系统 SHALL 正常完成导入，不要求先创建命名空间
