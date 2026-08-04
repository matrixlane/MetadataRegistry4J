## ADDED Requirements

### Requirement: 注册表合并的 RAI 一致性前置

注册表级合并 MUST 以来源方与目的方注册机构的 RAI（Registration_Authority_Identifier）一致为前置条件；RAI 不一致的合并请求 SHALL 被拒绝。RAI 一致的双方实例 SHALL 被视为同一注册机构在不同实例上的表示。

#### Scenario: RAI 一致允许合并

- **WHEN** 来源节点与目的节点的注册机构 RAI 一致，来源方发起注册表合并
- **THEN** 系统 SHALL 接受该合并请求并进入评审与并入流程

#### Scenario: RAI 不一致拒绝合并

- **WHEN** 来源节点与目的节点的注册机构 RAI 不一致，来源方发起注册表合并
- **THEN** 系统 SHALL 拒绝该合并请求并说明原因
