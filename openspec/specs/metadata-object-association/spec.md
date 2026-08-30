# metadata-object-association Specification

## Purpose

统一维护数据元（Data Element）、数据元概念（Data Element Concept）、概念域（Conceptual Domain）、值域（Value Domain）四个核心元数据对象之间的标准关联：以 ISO/IEC 11179-31:2023 为准定义允许的关联集合与基数，提供建立/解除/替换关联的统一服务入口与 REST 端点，在关联变更前执行业务规则校验，并在四个实体的编辑界面中提供无方向之分的关联管理视图。

## Requirements
### Requirement: 建立两个元数据对象之间的关联

系统 SHALL 提供统一入口，用于在允许的元数据对象对之间建立关联，并持久化到数据库。

#### Scenario: 成功建立数据元与值域的关联
- **WHEN** 调用关联服务为已存在的数据元 `DataElement` 建立与已存在值域 `ValueDomain` 的关联
- **THEN** 数据库中该数据元的 `value_domain_id` 被设置为指定值域的 ID
- **AND** 关联服务返回关联后的数据元对象

#### Scenario: 建立不允许的关联组合应被拒绝
- **WHEN** 调用关联服务在两个未在白名单中定义的元数据对象类型之间建立关联
- **THEN** 系统抛出 `IllegalArgumentException` 或业务异常
- **AND** 数据库状态不发生变化

### Requirement: 解除两个元数据对象之间的关联

系统 SHALL 提供统一入口，用于解除已存在的关联，并处理必要的级联或反向清理。解除操作 SHALL 要求源对象当前确实与目标对象存在对应关联。

#### Scenario: 成功解除数据元与值域的关联
- **WHEN** 调用关联服务解除数据元 `DataElement` 与其值域 `ValueDomain` 的关联
- **THEN** 数据库中该数据元的 `value_domain_id` 被置为空
- **AND** 关联服务返回解除关联后的数据元对象

#### Scenario: 解除不存在的关联应被拒绝
- **WHEN** 调用关联服务解除一个原本就不存在的关联
- **THEN** 系统抛出 `MetadataAssociationValidationException`
- **AND** 数据库状态不发生变化

### Requirement: 替换元数据对象的关联目标

系统 SHALL 提供统一入口，用于将对象 A 与对象 B 的关联替换为对象 A 与对象 C 的关联。

#### Scenario: 成功替换数据元的值域
- **WHEN** 调用关联服务将数据元 `DataElement` 的值域从 `ValueDomain A` 替换为 `ValueDomain B`
- **THEN** 数据库中该数据元的 `value_domain_id` 被更新为 `ValueDomain B` 的 ID
- **AND** 关联服务返回更新后的数据元对象

### Requirement: 关联操作前执行校验

系统 SHALL 在执行任何关联变更前，调用对应关联对的校验器进行业务规则校验。

#### Scenario: 校验不通过时拒绝关联
- **WHEN** 调用关联服务建立违反业务规则的关联
- **THEN** 校验器抛出校验异常
- **AND** 数据库状态不发生变化

### Requirement: 四实体标准关联集合与基数

系统 SHALL 仅允许 ISO/IEC 11179-31:2023 定义的四实体标准关联，并强制其基数约束：

- `data_element_meaning`（§7.2.3.3 / §7.6.3.2）：Data_Element 0..* → 0..1 Data_Element_Concept——一个数据元至多关联一个数据元概念，一个数据元概念可被多个数据元关联。
- `data_element_domain`（§7.2.3.2 / §7.6.3.1）：Data_Element 0..* → 0..1 Value_Domain——一个数据元至多关联一个值域，一个值域可被多个数据元关联。
- `value_domain_meaning`（§7.2.3.1 / §7.4.4.1）：Value_Domain 0..* → 0..1 Conceptual_Domain，且值域与概念域的子类型 SHALL 一致（§7.4.5.2.1）。
- `data_element_concept_domain`（§7.2.3.4）：Data_Element_Concept 0..* ↔ 0..* Conceptual_Domain（多对多）。

#### Scenario: 0..1 基数下重复建立关联表现为替换
- **WHEN** 数据元已关联值域 A，再次为其建立与值域 B 的 `data_element_domain` 关联
- **THEN** 该数据元的值域更新为 B
- **AND** 与 A 的关联不再存在

#### Scenario: 值域与概念域子类型不一致时拒绝关联
- **WHEN** 为枚举值域建立与描述型概念域的 `value_domain_meaning` 关联
- **THEN** 校验器抛出 `MetadataAssociationValidationException`
- **AND** 数据库状态不发生变化

#### Scenario: 数据元概念与概念域的多对多关联
- **WHEN** 为一个数据元概念依次建立与两个不同概念域的 `data_element_concept_domain` 关联
- **THEN** 两个关联同时存在（多对多，不做替换）

### Requirement: 关联方向一视同仁

关联的「正向」与「反向」MUST 视为同一关联的两个视角：查询焦点实体的关联时 SHALL 同时汇集其作为源与作为目标的实例；写操作 SHALL 按标准定义的固定方向落库（源/目标角色由关联类型决定）；界面与 API 表述中 MUST NOT 出现「被引用」等方向性概念。

#### Scenario: 从值域视角查看数据元关联
- **WHEN** 查询某值域的关联列表
- **THEN** 结果包含所有以该值域为目标的 `data_element_domain` 关联（即关联该值域的全部数据元）
- **AND** 这些条目与值域自身发起的关联并列展示，不标注方向差异

### Requirement: 关联查询与维护 REST 端点

系统 SHALL 提供以下端点管理四实体标准关联：

- `GET /api/v1/items/{itemId}/associations`：列出焦点实体的全部标准关联（正向 + 反向）。
- `POST /api/v1/associations/{relationType}`：建立关联，请求体为 `{sourceItemId, targetItemId}`。
- `DELETE /api/v1/associations/{relationType}`：解除关联，请求体同上。

`relationType` SHALL 取 `data-element-meaning` / `data-element-domain` / `value-domain-meaning` / `data-element-concept-domain` 之一；写操作成功后 SHALL 返回 `sourceItemId` 所指实体的最新关联列表，并记录版本历史快照。

#### Scenario: 建立关联后返回最新关联列表
- **WHEN** `POST /api/v1/associations/data-element-domain`，请求体为已存在数据元与值域的 ID
- **THEN** 该数据元的值域被设置为目标值域
- **AND** 响应体为源数据元的完整关联列表
- **AND** 记录一条 UPDATE 版本历史快照

#### Scenario: 未知关联类型
- **WHEN** `POST /api/v1/associations/unknown-type`
- **THEN** 返回业务错误（未知的关联类型）
- **AND** 数据库状态不发生变化

#### Scenario: 关联目标不存在
- **WHEN** 建立或解除关联时 `sourceItemId`/`targetItemId` 所指实体不存在
- **THEN** 返回 `ResourceNotFoundException` 对应的错误响应
- **AND** 数据库状态不发生变化

### Requirement: 四实体表单的关联管理界面

数据元、数据元概念、概念域、值域的编辑对话框 SHALL 提供「关联」tab，按目标实体类型分组展示该实体的全部标准关联，并支持新增与解除。一对多关联（如值域→数据元、概念域↔数据元概念）SHALL 支持多选批量添加；基数为 0..1 的关联 SHALL 单选。实体尚未保存（无 ID）时 MUST 提示先保存再管理关联。

#### Scenario: 批量添加多个关联
- **WHEN** 在值域「关联」tab 的「数据元」分组中选择多个数据元并点击「添加」
- **THEN** 系统逐个建立 `data_element_domain` 关联
- **AND** 全部成功后刷新关联列表；部分失败时提示「已建立 N 个关联，M 个失败」

#### Scenario: 未保存实体不可管理关联
- **WHEN** 打开新建实体的对话框（尚未保存）
- **THEN** 「关联」tab 显示提示「保存后即可管理关联。」
- **AND** 不提供添加/解除操作入口

#### Scenario: 解除关联
- **WHEN** 用户在某条关联条目上点击解除
- **THEN** 调用 `DELETE /api/v1/associations/{relationType}` 解除该关联
- **AND** 刷新关联列表
