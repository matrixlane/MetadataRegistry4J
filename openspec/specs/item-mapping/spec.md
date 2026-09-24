# item-mapping Specification

## Purpose
定义 ISO/IEC 11179-3:2023 第 11 章 Item_Mapping 包的系统能力：映射条目作为 Item 子类被标识、命名、注册与管理，映射双方（subject/object）引用注册条目并携带 mapping_variable_name，映射度（degree）取标准 14 值枚举，支持转换规则（transformation_rule）及其记法（transformation_rule_notation）与映射层级（mapping_hierarchy）。

## Requirements

### Requirement: 映射条目为 Item 子类

系统 MUST 将映射条目（Item_Mapping）建模为 Item 的子类：映射条目 MUST 可经统一条目机制获得标识（ScopedIdentifier）、命名（Designation）、定义（Definition）与注册状态（Registration_State），即映射条目 MUST 能被标识、注册、管理与分类。

#### Scenario: 创建映射条目并获得条目身份

- **WHEN** 用户在项目映射管理页创建一条映射并填写标识/命名信息
- **THEN** 系统为该映射条目建立 Item 身份（item 表记录）、可选的命名与定义，且在用户提供注册请求时经统一注册管线建立受管条目外壳（AdministeredItem）与注册状态

#### Scenario: 映射条目参与注册状态流转

- **WHEN** 用户对已创建的映射条目执行注册状态变更（如提交进入 CANDIDATE）
- **THEN** 系统经统一条目注册流程完成状态流转，映射条目的注册状态与其他条目类型的行为一致

### Requirement: 映射双方引用注册条目

一条映射 MUST 包含一个或多个主体（subject）与一个或多个客体（object），忠实 ISO/IEC 11179-3:2023 11.4：Item 与 Item_Mapping 为多对多关系，每个绑定经 Subject_Mapping/Object_Mapping 关联类实现并各自携带 mapping_variable_name（转换规则中引用该条目的变量名）。系统 MUST 拒绝引用不存在条目的绑定请求，且 MUST 拒绝主体或客体为空的映射创建或更新请求。系统 MUST NOT 以裸标识符或外部 URI 作为主体/客体的引用方式（外部标准的互操作映射由独立的互操作功能承担，不属于本能力）。

#### Scenario: 创建映射时校验条目存在性

- **WHEN** 用户提交的映射创建请求中任一主体或客体引用的条目不存在
- **THEN** 系统拒绝该请求并返回明确的错误说明，不创建任何数据

#### Scenario: 拒绝空的主体或客体集合

- **WHEN** 用户提交的映射创建请求中主体列表或客体列表为空
- **THEN** 系统拒绝该请求并返回明确的错误说明

#### Scenario: 多主体携带变量名的派生映射

- **WHEN** 用户创建 degree 为 derived from 的映射，绑定 3 个主体（day、month、year，各自填写映射变量名）与 1 个客体（isodate），并提供引用这些变量名的转换规则（如 `isodate = year, month, day`）与记法 EBNF
- **THEN** 系统保存映射及其全部绑定，每个绑定各自的变量名与转换规则完整返回（忠实 Annex B isodate_mapping1 示例）

#### Scenario: 一主体对多客体

- **WHEN** 用户创建一条映射绑定 1 个主体与 2 个客体（如特殊餐食映射到两个目标值含义）
- **THEN** 系统保存该映射的全部 2 个客体绑定（忠实 Annex B.5 示例形态）

### Requirement: 映射度取标准 14 值枚举

映射的映射度（degree）MUST 取 ISO/IEC 11179-3:2023 Table 35 的 14 个值之一：related to、same as、semantically equivalent、semantically similar、derived from、has derivation、broader than、narrower than、child of、parent of、contains、contained in、instance of、has instance。degree 为必填。

#### Scenario: 选择映射度

- **WHEN** 用户创建或编辑映射并选择映射度
- **THEN** 系统提供上述 14 个标准值的选项，保存所选值；用户未选择时系统拒绝提交
- **WHEN** 用户通过 API 提交非 14 值之一的 degree
- **THEN** 系统拒绝该请求并返回明确的错误说明

### Requirement: 转换规则及其记法

映射 MUST 支持可选的转换规则（transformation_rule）与记法（transformation_rule_notation）。当转换规则存在时，记法 MUST 必填；记法描述转换规则的表示格式（如 EBNF）。系统 MUST 在创建与更新时执行该校验。

#### Scenario: 有转换规则但缺记法

- **WHEN** 用户提交的映射包含转换规则但不含记法
- **THEN** 系统拒绝该请求并提示记法必填
- **WHEN** 用户提交的映射同时包含转换规则与记法
- **THEN** 系统正常保存两者

### Requirement: 映射层级

系统 MUST 支持映射层级：一条映射可作为至多一条其他映射的 subset（即拥有一个 superset），一条映射可作为多条其他映射的 superset。系统 MUST 拒绝将映射自身设为其自身的 superset/subset，且 MUST 防止产生循环层级。

#### Scenario: 建立层级

- **WHEN** 用户为一条映射设置 superset 映射
- **THEN** 系统保存层级关系，经 superset 可查询到全部 subset 映射
- **WHEN** 用户尝试设置会形成循环的层级（如 A 的 superset 链上已含 B，再将 B 的 superset 设为 A）
- **THEN** 系统拒绝该请求并返回明确的错误说明

### Requirement: 映射条目 REST API

系统 MUST 提供 `/api/v1/item-mappings` REST 端点：分页查询（支持按主体条目、客体条目、映射度过滤）、按标识获取、创建、更新、删除，以及层级查询与维护。所有写操作 MUST 执行上述存在性与条件校验。

#### Scenario: 分页与过滤查询

- **WHEN** 客户端请求映射列表并指定主体项目、客体项目或项目映射程度过滤条件
- **THEN** 系统返回匹配的分页结果，每条结果包含标识、主体项目集合、客体项目集合、项目映射程度、转换规则及注册状态信息

#### Scenario: 删除映射

- **WHEN** 客户端删除一条未被层级关系引用的映射
- **THEN** 系统删除该映射及其主体/客体关联与命名定义数据
- **WHEN** 客户端删除的映射存在层级关系
- **THEN** 系统一并清理其层级关系后删除

### Requirement: 项目映射管理界面

系统 MUST 提供"项目映射"管理页（`/item-mappings`，菜单"项目映射"）。页面 MUST 提供：映射列表（主体项目集合、项目映射程度、客体项目集合、转换规则摘要、注册状态）、按条目与项目映射程度筛选、新建/编辑对话框（主体/客体各支持选择一个或多个项目，每个所选项目可各自填写映射变量名；项目映射程度下拉、转换规则与记法、描述）、删除操作。界面文案 MUST 支持中英文国际化。

#### Scenario: 新建多主体映射

- **WHEN** 用户在项目映射页点击新建，选择 3 个主体项目（分别填写变量名 year、month、day）与 1 个客体项目，选择项目映射程度 derived from 并填写转换规则与记法后提交
- **THEN** 系统创建映射，列表刷新显示新映射，主体列显示全部 3 个项目及其变量名，客体列显示所选项目
