# dimensionality-management — ISO/IEC 11179-31:2023 §7.5.2.3 Dimensionality 的管理契约

## Purpose

补全 Dimensionality(ISO/IEC 11179-31:2023 §7.5.2.3,Concept 子类)的端到端管理契约:REST CRUD、Table 22 `coordinate_indicator` 三态属性透传、`dimensionality_measure_class`(§7.5.3.1)关联维护、删除保护、维度管理页,以及 `Conceptual_Domain.dimensionality`(Table 2,0..1)接线与 §7.4.2.1.5.2/§7.4.5.3 维度一致性校验。

## ADDED Requirements

### Requirement: Dimensionality CRUD API

系统 SHALL 提供 `/api/v1/dimensionalities` 资源端点:分页列表(`q` 按指称 sign 忽略大小写包含匹配)、`GET /{id}` 详情、`POST` 新增、`PUT /{id}` 更新、`DELETE /{id}` 删除。请求 MUST 覆盖 Concept 公共内容(sourceUri/designations/definitions/registration,经 ConceptContentApplier 写入)与 Dimensionality 专有内容:`coordinateIndicator`(Table 22,Boolean 三态——TRUE=坐标测量、FALSE=区间测量、NULL=非物理单位即 Not applicable)与 `measureClassIds`(§7.5.3.1,0..\*)。响应 MUST 在 Concept 同构字段(id/label/sourceUri/designations/definitions/meta)上附加 `coordinateIndicator` 与 `measureClasses`(元素含 id 与 label)。`measureClassIds` 中任一 id 不存在时 MUST 返回 404 语义错误。

#### Scenario: 三态坐标指示透传

- **WHEN** 创建维度提交 `coordinateIndicator=true` 与 `measureClassIds=[mc1, mc2]`
- **THEN** 响应 `coordinateIndicator` 为 true,`measureClasses` 含 mc1、mc2 的 id 与 label

#### Scenario: 不适用为空

- **WHEN** 创建维度不提交 `coordinateIndicator`
- **THEN** 响应 `coordinateIndicator` 为 null(Not applicable)

#### Scenario: 关键词搜索

- **WHEN** 以 `q=长度` 调用 `GET /api/v1/dimensionalities`
- **THEN** 结果仅含指称包含"长度"(忽略大小写)的维度

### Requirement: Dimensionality 删除保护

删除 Dimensionality 时,若其被任一 ConceptualDomain 引用(conceptual_domain.dimensionality_id),系统 MUST 拒绝删除并返回含引用数的业务错误;未被引用时 MUST 删除成功,且 `dimensionality_measure_class` 关联行一并清理。

#### Scenario: 被概念域引用

- **WHEN** 维度 D 被 2 个概念域引用时调用 `DELETE /api/v1/dimensionalities/{D}`
- **THEN** 返回业务错误"维度正被 N 个概念域引用",维度保留

#### Scenario: 无引用删除

- **WHEN** 维度 D 未被任何概念域引用时调用删除
- **THEN** 维度及其 measure_class 关联被删除

### Requirement: ConceptualDomain.dimensionality 接线

`ConceptualDomainRequest` MUST 接受 `dimensionalityId`(0..1;空表示清除),`ConceptualDomainResponse` MUST 返回 `dimensionalityId` 与 `dimensionalityLabel`。Service 在 create/update 时校验 dimensionalityId 存在性(不存在返回 404 语义错误)并挂载;概念域类型重建(子类型切换)时维度引用 MUST 保留。

#### Scenario: 挂载维度

- **WHEN** 更新概念域提交 `dimensionalityId=D`
- **THEN** 详情响应 `dimensionalityId` 为 D,`dimensionalityLabel` 为 D 的首选指称

#### Scenario: 清除维度

- **WHEN** 更新概念域提交 `dimensionalityId=null`
- **THEN** 详情响应 `dimensionalityId` 为 null

#### Scenario: 类型切换保留维度

- **WHEN** 已挂维度的概念域从 DESCRIBED 切换为 LOCAL_ENUMERATED
- **THEN** 切换后维度引用不变

### Requirement: 维度一致性校验

关联校验器在 `VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN` 与 `VALUE_DOMAIN_TO_UNIT_OF_MEASURE` 两类关联上 MUST 执行 ISO/IEC 11179-31:2023 §7.4.2.1.5.2 / §7.4.5.3 校验:当概念域的 dimensionality 与值域的 unitOfMeasure 均已指定时,unitOfMeasure 的 measureClasses 与 dimensionality 的 measureClasses 交集 MUST 非空,否则拒绝关联并返回含条款号的业务错误;任一侧未指定时不校验。

#### Scenario: 计量单位与维度不一致

- **WHEN** 概念域 C(dimensionality=D,D 仅关联计量类"长度")的值域 V(unitOfMeasure 仅属计量类"质量")发起 V→C 关联
- **THEN** 关联被拒绝,错误提示引用 ISO/IEC 11179-31:2023 §7.4.5.3

#### Scenario: 一致时放行

- **WHEN** V 的 unitOfMeasure 所属计量类与 D 的计量类存在交集
- **THEN** 关联建立成功

#### Scenario: 未指定维度不校验

- **WHEN** 概念域未指定 dimensionality 时建立 V→C 关联
- **THEN** 关联建立成功(约束以"指定时"为前提)

### Requirement: 维度管理页

前端 `/dimensionality` MUST 提供维度管理页:关键词搜索、分页表格(标识符/名称/坐标指示/计量类数)、新建/编辑对话框(沿用 ItemFormDialog;属性页含坐标指示三态选择——是/否/不适用——与计量类多选)。导航注册状态 MUST 从 `planned` 改为 `implemented`,i18n MUST 提供 `meta.itemType.dimensionality`(zh:维度,en:Dimensionality)。

#### Scenario: 访问维度页

- **WHEN** 用户经菜单进入 /dimensionality
- **THEN** 看到维度分页列表与新建按钮,不再显示占位页

### Requirement: 概念域页维度选择

概念域新建/编辑对话框 MUST 提供维度单选(SmartItemPicker,类型 Dimensionality),保存时透传 `dimensionalityId`;编辑时 MUST 回填已选维度。

#### Scenario: 编辑回填

- **WHEN** 打开已挂维度 D 的概念域编辑框
- **THEN** 维度选择器显示 D 的名称,保存后详情维度不变
