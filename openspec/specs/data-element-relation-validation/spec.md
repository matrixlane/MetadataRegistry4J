# data-element-relation-validation Specification

## Purpose
TBD - created by archiving change metadata-object-attribute-association. Update Purpose after archive.
## Requirements
### Requirement: 数据元关联的数据元概念类型必须正确

系统 SHALL 确保数据元只能关联到 `DataElementConcept` 类型的对象。

#### Scenario: 关联正确的数据元概念类型
- **WHEN** 为数据元关联一个 `DataElementConcept` 实例
- **THEN** 校验通过

#### Scenario: 关联错误类型到数据元概念位置
- **WHEN** 为数据元关联一个非 `DataElementConcept` 类型的对象
- **THEN** 校验器抛出 `MetadataAssociationValidationException`
- **AND** 提示“数据元必须关联到 DataElementConcept”

### Requirement: 数据元关联的值域类型必须正确

系统 SHALL 确保数据元只能关联到 `ValueDomain` 类型的对象。

#### Scenario: 关联正确的值域类型
- **WHEN** 为数据元关联一个 `ValueDomain` 实例
- **THEN** 校验通过

#### Scenario: 关联错误类型到值域位置
- **WHEN** 为数据元关联一个非 `ValueDomain` 类型的对象
- **THEN** 校验器抛出 `MetadataAssociationValidationException`
- **AND** 提示“数据元必须关联到 ValueDomain”

### Requirement: 数据元关联目标必须存在

系统 SHALL 确保数据元所关联的数据元概念和值域在数据库中存在且未被逻辑删除。该检查由 Repository 加载逻辑保证；若找不到目标，应抛出 `ResourceNotFoundException` 而非继续关联操作。

#### Scenario: 关联已存在的有效目标
- **WHEN** 数据元关联一个数据库中存在且状态有效的数据元概念或值域
- **THEN** Repository 成功加载目标对象
- **AND** 关联操作继续执行

#### Scenario: 关联不存在或已删除的目标
- **WHEN** 数据元关联一个不存在或已被删除的数据元概念或值域
- **THEN** Repository 加载失败并抛出 `ResourceNotFoundException`
- **AND** 数据库状态不发生变化

