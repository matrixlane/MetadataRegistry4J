# registry-federation Specification

## Purpose
TBD - created by archiving change registry-federation-merge-request. Update Purpose after archive.
## Requirements
### Requirement: 联邦节点登记
系统 SHALL 允许管理员登记对端注册表节点（RegistryNode），字段至少包括：名称、地址（可选，离线节点可空）、注册机构标识（RAI 的 organizationIdentifier）、认证凭据（预共享令牌）。节点登记 MUST 与部署版本无关：PERSONAL 与 TEAM 实例均可作为源或目标。

#### Scenario: 登记一个对端节点
- **WHEN** 管理员提交节点名称、RAI 与预共享令牌
- **THEN** 系统 SHALL 保存该节点并使其可用于合并请求的提交与接收

#### Scenario: 个人版与团队版互认
- **WHEN** 个人版实例与团队版实例互相登记对方为联邦节点
- **THEN** 两个实例 SHALL 能在任一方向发起合并请求

### Requirement: 跨实例全局逻辑身份
元数据项的跨实例身份 MUST 由「来源 RAI + 来源标识符（ScopedIdentifier）」二元组表示，实例内 UUID MUST NOT 用作跨实例身份。合并包中的每个条目 MUST 携带该二元组及来源实例 UUID（仅供回执对照）。

#### Scenario: 条目在合并包中的身份表示
- **WHEN** 源节点导出包含某数据元素的合并包
- **THEN** 该条目 SHALL 包含来源 RAI、来源标识符与来源实例 UUID

### Requirement: 跨实例条目链接
系统 SHALL 通过 `item_link` 表记录跨实例条目链接：本地条目、对端节点、对端逻辑身份、对端实例 UUID、建立来源（合并请求）、建立时间。链接 MUST 可由合并评审建立，并作为后续合并的合并基定位依据。

#### Scenario: 评审确认后建立链接
- **WHEN** 评审人在合并评审中确认某传入条目与本地条目为同一逻辑项
- **THEN** 系统 SHALL 写入一条 `item_link` 记录

#### Scenario: 后续合并复用链接
- **WHEN** 同一对端节点的后续合并请求到达
- **THEN** 系统 SHALL 使用既有链接确定合并基，而非重新进行身份匹配

### Requirement: 同步水位线与合并回执
系统 SHALL 按（源节点, 目标节点）方向维护同步水位线（最近成功合并的合并请求与时间）。目标端并入成功后 MUST 生成合并回执（含并入条目对照与目标端链接信息）；源节点凭回执更新自身水位线与反向链接。回执丢失时，重复提交同一合并包 MUST 被目标端按条目版本幂等跳过。

#### Scenario: 增量提交
- **WHEN** 源节点在同一目标节点上发起第二次合并请求
- **THEN** 合并包 SHALL 仅包含自上次水位线以来的新建与变更条目

#### Scenario: 回执丢失重放
- **WHEN** 源节点未收到回执并重放同一合并包
- **THEN** 目标端 SHALL 跳过已并入的相同版本条目，不产生重复数据

### Requirement: 合并包格式与传输通道
合并包 MUST 采用统一格式（federation bundle v1：manifest.json + items/*.json + receipt-request.json），并 MUST 支持两种传输通道：文件包（离线/内网场景，导出导入）与 HTTP 推送（在线场景，目标端接收端点）。两条通道 MUST 共用同一解析与处理管线。新增表 MUST 遵守数据库可移植性规则（UUID 主键、不绑定 schema、双方言迁移脚本）。

#### Scenario: 离线文件包提交
- **WHEN** 源节点导出合并包文件并由管理员在目标节点导入
- **THEN** 目标端 SHALL 生成与 HTTP 推送等价的合并请求

#### Scenario: HTTP 推送提交
- **WHEN** 源节点通过 API 将合并包推送到目标节点
- **THEN** 目标端 SHALL 校验预共享令牌并生成合并请求

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

