# language-identification-edit 增量规范

## MODIFIED Requirements

### Requirement: 导航与列表状态展示
系统 MUST 在左侧导航栏提供“语言标识”的编辑/查看入口。语言列表 MUST 清晰展示各语言标识名、文字标识、地区标识、以及语言名称与地区名称，以及一个可直接操作的“是否启用”开关。语言名称与地区名称 MUST 仅来自语言扩展表中持久化的值；当扩展记录缺失或对应名称为空时，该名称展示为空，系统 MUST NOT 对语言标识或地区标识做任何运行时解析（如基于 JDK Locale 计算显示名称）。

#### Scenario: 访问列表页面
- **WHEN** 用户点击左侧“注册与公共设施”下的“语言标识”
- **THEN** 路由成功跳转到 `/languages`，表格中分页列出所有内置的语言数据，包含语言标识名、文字标识、地区标识代码，以及语言名称与地区名称（仅取语言扩展表持久化值，缺失或为空时展示为空），并用开关控件显示每个语言的启用状态

#### Scenario: 切换启用开关立即保存
- **WHEN** 用户在列表中切换某语言的“是否启用”开关
- **THEN** 系统立即调用更新接口保存新的启用状态，成功后列表刷新并展示最新状态；保存失败时开关回滚到切换前的状态并提示失败

#### Scenario: 扩展表存在持久化名称
- **WHEN** 扩展表中已登记某语言的名称
- **THEN** 语言列表与详情展示该持久化名称，与系统语言无关

#### Scenario: 扩展记录缺失或名称为空
- **WHEN** 某语言在扩展表中无记录，或对应名称字段为空
- **THEN** 语言名称与地区名称展示为空，系统不对语言标识或地区标识做运行时解析

## ADDED Requirements

### Requirement: 语言名称与地区名称可编辑
管理员或有权限的用户 MUST 能够在语言编辑表单中填写或修改「语言名称」与「地区名称」；保存后系统 MUST 将值写入与该语言一对一关联的语言扩展表（不存在则创建，存在则更新），再次查询时 MUST 返回最新值。当用户将名称留空时，系统 MUST 将对应字段保存为空（null），MUST NOT 对语言标识或地区标识做运行时解析或自动回填。

#### Scenario: 编辑并保存名称
- **WHEN** 用户在编辑表单中填写语言名称“中文”和地区名称“中国”并保存
- **THEN** 扩展表 upsert 对应记录，列表重新加载后展示“中文”和“中国”

#### Scenario: 名称为空则保存为空
- **WHEN** 用户编辑语言时未填写语言名称或地区名称
- **THEN** 系统将对应字段保存为空（null），再次查询时该名称展示为空，不做任何自动回填或运行时解析

### Requirement: 语言扩展表与标准语言表一对一且同名（schema 区分）
语言扩展表 MUST 与标准语言表 `language_identification` 保持一对一关系（经语言主键唯一引用），表名 MUST 与标准表一致、仅以 schema 区分：SQL Server/达梦下为 `ext.language_identification`，SQLite（无 schema 概念）下物理表名为 `ext_language_identification`。预置名称数据（内置 G20 二十二种语言的中文语言名称与地区名称）MUST 仅存在于 SQL 迁移脚本（V20260919002/V20260919003，sqlserver/sqlite/dm 三方言，幂等插入）中，应用代码 MUST NOT 包含名称预置数据，MUST NOT 对语言标识或地区标识做运行时名称解析。系统 MUST 启用 Flyway（默认开启）并基线化到既有最大版本号 20260918002（`baseline-on-migrate` + `spring.jpa.defer-datasource-initialization` 使 ddl-auto 先于 Flyway 执行），既有库首次启用时只应用基线后带幂等守卫的新迁移。预置脚本分工：V20260919002 面向既有库按 `language_identifier` 关联补种名称；V20260919003 面向全新库与恢复出厂场景，以固定 UUID 同时播种 22 条语言代码与名称（`LanguageInitializer` 对这 22 条永远查到已存在而跳过，保留作历史库缺行兜底）。恢复出厂设置 MUST 在数据重置完成后重放 V20260919003 脚本（由出厂流程读取同一份 classpath 脚本执行，脚本为唯一数据源），保证出厂后开箱即有预置名称。

#### Scenario: 团队版数据库
- **WHEN** 系统运行于 SQL Server 或达梦（team profile）
- **THEN** 扩展表以 `ext.language_identification` 存在，与主表同名不同 schema

#### Scenario: SQLite 个人版数据库
- **WHEN** 系统运行于 SQLite（personal profile）
- **THEN** 扩展表以 `ext_language_identification` 存在，不与标准表冲突

### Requirement: 列表与表单字段文案
语言页表格列与编辑表单项 MUST 使用以下文案：「语言标识」「文字标识」「地区标识」「语言名称」「地区名称」；MUST NOT 再出现「文字」「地区」作为字段文案。

#### Scenario: 打开语言页
- **WHEN** 用户进入语言管理页
- **THEN** 表格列头与编辑表单标签显示「文字标识」「地区标识」
