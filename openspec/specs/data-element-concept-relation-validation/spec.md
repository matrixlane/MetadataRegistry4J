# data-element-concept-relation-validation Specification

## Purpose
TBD - created by archiving change metadata-object-attribute-association. Update Purpose after archive.
## Requirements
### Requirement: 数据元概念关联的对象类类型必须正确

系统 SHALL 确保数据元概念只能关联到 `ObjectClass` 类型的对象。

#### Scenario: 关联正确的对象类类型
- **WHEN** 为数据元概念关联一个 `ObjectClass` 实例
- **THEN** 校验通过

#### Scenario: 关联错误类型到对象类位置
- **WHEN** 为数据元概念关联一个非 `ObjectClass` 类型的对象
- **THEN** 校验器抛出 `MetadataAssociationValidationException`
- **AND** 提示“数据元概念必须关联到 ObjectClass”

### Requirement: 数据元概念关联的属性类型必须正确

系统 SHALL 确保数据元概念只能关联到 `Property` 类型的对象。

#### Scenario: 关联正确的属性类型
- **WHEN** 为数据元概念关联一个 `Property` 实例
- **THEN** 校验通过

#### Scenario: 关联错误类型到属性位置
- **WHEN** 为数据元概念关联一个非 `Property` 类型的对象
- **THEN** 校验器抛出 `MetadataAssociationValidationException`
- **AND** 提示“数据元概念必须关联到 Property”

### Requirement: 数据元概念关联的概念域类型必须正确

系统 SHALL 确保数据元概念只能关联到 `ConceptualDomain` 类型的对象。

#### Scenario: 关联正确的概念域类型
- **WHEN** 为数据元概念关联一个 `ConceptualDomain` 实例
- **THEN** 校验通过

#### Scenario: 关联错误类型到概念域位置
- **WHEN** 为数据元概念关联一个非 `ConceptualDomain` 类型的对象
- **THEN** 校验器抛出 `MetadataAssociationValidationException`
- **AND** 提示“数据元概念必须关联到 ConceptualDomain”

### Requirement: 数据元概念与概念域的关联必须双向一致

系统 SHALL 确保数据元概念与概念域（Conceptual Domain）之间的多对多关联在双向集合中保持一致。

#### Scenario: 建立关联后双向集合均包含对方
- **WHEN** 为数据元概念关联一个概念域
- **THEN** 数据元概念的 `conceptualDomains` 集合包含该概念域
- **AND** 概念域的 `dataElementConcepts` 集合包含该数据元概念

#### Scenario: 解除关联后双向集合均移除对方
- **WHEN** 解除数据元概念与概念域的关联
- **THEN** 数据元概念的 `conceptualDomains` 集合不再包含该概念域
- **AND** 概念域的 `dataElementConcepts` 集合不再包含该数据元概念

