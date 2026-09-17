# Delta: registry-specification

## ADDED Requirements

### Requirement: Registry Specification Configuration Step in Initialization Wizard
初始化向导 MUST 包含注册规范配置步骤（位于命名空间之后、管理员密码之前），开放编辑 `Registry_Specification`（ISO/IEC 11179-3:2023 表 28）的标量属性：name（必填）、comment、web_address、character_repertoire（必填）、standard、degree_of_conformance（conforming / strictly_conforming）、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM）、default_language、alternative_language（0..*）、reference_document_identifier_kind（0..*）、representation_class_scheme_id。各属性 MUST 预填系统默认值；上下文与指称/定义多语言编辑不在向导内提供（初始化后于设置页维护）。

#### Scenario: 向导中配置注册规范并带入 setup
- **WHEN** 超级管理员在注册规范步骤修改字符集、标准、合规程度、时区、替代语言等属性并完成后续初始化
- **THEN** System SHALL 按所填值创建注册规范，初始化完成后设置页回显一致

#### Scenario: 向导中保留默认值提交
- **WHEN** 超级管理员不修改注册规范步骤的预填默认值直接提交
- **THEN** System SHALL 以默认值创建注册规范（字符集 UTF-8、标准 ISO/IEC 11179-3:2023、默认语言 zh-CN、时区 +0800）

### Requirement: Registry Specification Full Attribute Editing in Settings
注册规范设置页的编辑界面 MUST 开放 `Registry_Specification`（ISO/IEC 11179-3:2023 表 28）全部属性的编辑：name、comment、web_address、default_language、alternative_language（0..*）、character_repertoire、reference_document_identifier_kind（0..*）、representation_class_scheme_id、context、standard、degree_of_conformance（conforming / strictly_conforming）、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM），以及继承自 Item 的指称（0..*，任意语言与上下文）与定义（0..*，任意语言与上下文）。其中 name 与 character_repertoire 为必填。

#### Scenario: 编辑注册规范并保存全部属性
- **WHEN** 注册管理员在设置页打开注册规范编辑对话框，填写替代语言、参考文档标识种类、表示类方案标识、上下文等属性并保存
- **THEN** 系统持久化全部属性，重新打开时回显一致

#### Scenario: 指称与定义支持任意语言
- **WHEN** 注册管理员在编辑对话框的指称/定义折叠面板中添加非中/英文语言的指称或定义行并保存
- **THEN** 系统按语言保存各行（含上下文与可接受度），回显时完整还原

#### Scenario: 非法时区格式被拒绝
- **WHEN** 提交的 default_time_zone 不匹配 [-+]HHMM 格式（如 "800"、"+08:00"）
- **THEN** 系统返回 400 业务校验错误；前端在失焦时即提示格式错误且不提交

## REMOVED Requirements

### Requirement: Locking Critical Registry Specification Settings When Metadata Exists
**Reason**: 该要求所列关键字段（identifierPrefix、autoGenerateIdentifier、identifierPattern、versioningPolicy、mandatoryClassification）已不在注册规范实体上——标识与版本生成策略已迁至命名空间级别（namespace_identifier_settings / identifier_strategy_config），注册规范不再承载这些属性；要求自引入以来从未实施，继续保留会与注册规范属性开放编辑（含 default_language）直接冲突。default_language 变更仅影响新建指称/定义的语言缺省回退，不破坏既有数据。
**Migration**: 无需迁移。标识/版本策略的查看与编辑在命名空间管理界面进行；注册规范的全部属性（含默认语言）可在设置页直接编辑。
