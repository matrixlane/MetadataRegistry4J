## ADDED Requirements

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

系统 SHALL 提供统一入口，用于安全解除已存在的关联，并处理必要的级联或反向清理。

#### Scenario: 成功解除数据元与值域的关联
- **WHEN** 调用关联服务解除数据元 `DataElement` 与其值域 `ValueDomain` 的关联
- **THEN** 数据库中该数据元的 `value_domain_id` 被置为空
- **AND** 关联服务返回解除关联后的数据元对象

#### Scenario: 解除不存在的关联应保持稳定
- **WHEN** 调用关联服务解除一个原本就不存在的关联
- **THEN** 系统不抛出异常
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
