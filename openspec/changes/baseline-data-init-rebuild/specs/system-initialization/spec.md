## ADDED Requirements

### Requirement: 通用基准数据由脚本承载并自动导入

通用基准数据（语言标识等必选基准）MUST 由 `data/scripts/common/<方言>/00-common.sql` 承载（单一事实源），MUST NOT 由 Java 播种逻辑复制其内容。系统初始化链路（启动期静态预置、初始化向导 setup/abort、恢复出厂设置）MUST 按当前数据源方言自动执行该通用基准脚本；脚本执行 MUST 幂等，重复执行数据保持一致。脚本目录缺失或脚本不存在时系统 MUST 记录警告并跳过，MUST NOT 阻断启动或初始化流程；用户可经一键初始化批处理补入（见 `baseline-data-scripts` 能力）。

#### Scenario: 全新库启动自动获得通用基准

- **GIVEN** 全新空库且脚本目录在位
- **WHEN** 应用首次启动完成静态预置
- **THEN** 语言标识 22 条在位（含中文名称），语言下拉等读取路径正常

#### Scenario: 恢复出厂后通用基准完整

- **WHEN** 执行恢复出厂设置后重新进入初始化向导
- **THEN** 通用基准数据经脚本重放保持完整且行数不重复

#### Scenario: 脚本目录缺失优雅降级

- **GIVEN** 部署环境中脚本目录不存在
- **WHEN** 应用启动
- **THEN** 记录警告日志并正常完成启动，不阻断初始化向导

### Requirement: 初始化向导清理保留基准数据

初始化向导的清理（setup 重入与 abort 共用路径）MUST 保留基准数据：基准脚本承载的内容链、受管链与衍生表（item/concept/value_domain/data_element/permissible_value 系、registered_item/administered_item/registration_state、reference_document 系、designation/definition 系、context、slot 系、scoped_identifier 系等）MUST 整表保留；治理表（organization/registration_authority/namespace 及其集合与卫星表）MUST 仅清除非基准行，基准常量行（`ws-` 命名空间、WJW 标委会与统计信息中心机构、WJW 注册机构）MUST 保留。恢复出厂设置不受此约束（仍全清，基准数据随之清除，可经一键初始化批处理重新导入——见 `baseline-data-scripts` 能力）。

#### Scenario: 预置库上 setup 后基准完整

- **GIVEN** 拷贝预置基准库（含 WS 基准数据与基准治理常量行）的全新系统
- **WHEN** 用户完成初始化向导 setup
- **THEN** 基准内容行与基准治理常量行全部保留，半途脏治理行被清除，语言 22 条在位，向导新建的注册机构/命名空间正常创建

#### Scenario: abort 后基准完整

- **WHEN** 用户在 setup 后执行 abort
- **THEN** 基准数据保持完整，向导写入的治理行被清除（仅剩基准常量行）

#### Scenario: 重复 setup 不产生重复治理行

- **WHEN** 用户连续两次提交 setup
- **THEN** 注册机构与命名空间不产生重复行（基准常量各一、本次向导各一）
