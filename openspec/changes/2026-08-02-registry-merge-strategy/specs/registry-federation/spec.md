## ADDED Requirements

### Requirement: 注册表合并的 RAI 一致性前置

注册表级合并 MUST 以来源方与目的方注册机构的 RAI（Registration_Authority_Identifier）一致为前置条件；RAI 不一致的合并请求 SHALL 被拒绝。RAI 一致的双方实例 SHALL 被视为同一注册机构在不同实例上的表示。

#### Scenario: RAI 一致允许合并

- **WHEN** 来源节点与目的节点的注册机构 RAI 一致，来源方发起注册表合并
- **THEN** 系统 SHALL 接受该合并请求并进入评审与并入流程

#### Scenario: RAI 不一致拒绝合并

- **WHEN** 来源节点与目的节点的注册机构 RAI 不一致，来源方发起注册表合并
- **THEN** 系统 SHALL 拒绝该合并请求并说明原因

### Requirement: 全局命名空间

每个注册表 MUST 最多拥有一个全局命名空间，专用于数据迁移等跨实例"外交"任务；其名称 SHALL 一眼可辨为外部用途（如"全局命名空间"）。全局命名空间的用途 SHALL 限于为注册表中的任意项目保持一个全局唯一的业务标识（当前强制用于命名空间跨实例映射，见下方要求）。全局命名空间 MUST NOT 关联注册机构，其标识生成策略 SHALL 为 UUID，以保证其下 Scoped_Identifier.identifier 全局唯一。向同一或不同目的方的多次迁移 SHALL 复用同一个全局命名空间。

#### Scenario: 首次迁移时创建全局命名空间

- **WHEN** 注册表首次发起迁移且全局命名空间尚不存在
- **THEN** 系统 SHALL 创建全局命名空间，后续迁移一律复用该命名空间

#### Scenario: 全局命名空间唯一

- **WHEN** 注册表已存在全局命名空间，再次发起迁移
- **THEN** 系统 SHALL 复用既有全局命名空间，MUST NOT 新建第二个

### Requirement: 命名空间跨实例全局标识映射

为使来源方与目的方的命名空间始终保持可匹配，每个待迁移命名空间 SHALL 以 Administered_Item 包装后，在本方全局命名空间下持有一个 Scoped_Identifier；目的方在创建来源命名空间副本时，SHALL 在目的方全局命名空间（同一个单例）下为该副本建立 Scoped_Identifier，其 identifier 与来源方的对应值一致（UUID 相同）。来源方与目的方命名空间的映射 MUST 以该共享 UUID 建立。一个目的方自多个来源方迁入时，SHALL 在同一个全局命名空间下为各来源命名空间副本建立不同的 Scoped_Identifier。

#### Scenario: 目的方副本与来源方标识一致

- **WHEN** 目的方并入来源方命名空间的副本
- **THEN** 该副本 SHALL 在目的方全局命名空间下持有与来源方相同 UUID 的 Scoped_Identifier，两侧命名空间据此建立映射

#### Scenario: 多来源共用一个全局命名空间

- **WHEN** 目的方先后迁入来自两个不同来源方的数据
- **THEN** 目的方 SHALL 在同一个全局命名空间下为两个来源命名空间副本分别建立不同的 UUID 标识，每个标识与其对应来源方一致
