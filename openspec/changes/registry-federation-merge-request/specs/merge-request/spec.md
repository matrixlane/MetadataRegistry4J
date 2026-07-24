## ADDED Requirements

### Requirement: 合并请求生命周期
合并请求（MergeRequest）MUST 经历状态机：DRAFT → SUBMITTED → UNDER_REVIEW → MERGED / REJECTED（异常路径 FAILED 并附原因）。每个合并请求 MUST 指定目标命名空间。状态流转 MUST 可追溯（提交人、评审人、时间戳）。

#### Scenario: 完整生命周期
- **WHEN** 源节点提交合并请求，评审人批准后系统执行并入
- **THEN** 合并请求 SHALL 依次经过 SUBMITTED、UNDER_REVIEW，最终置为 MERGED

#### Scenario: 评审拒绝
- **WHEN** 评审人拒绝合并请求
- **THEN** 合并请求 SHALL 置为 REJECTED，目标库数据不变

### Requirement: 增量变更集计算
源节点 SHALL 基于同步水位线计算增量变更集：自上次成功合并以来新建与变更的条目集合，来源为版本历史（version_history）。无水位线（首次合并）时 MUST 包含全部候选条目。

#### Scenario: 首次合并全量候选
- **WHEN** 源节点从未向该目标节点成功合并过
- **THEN** 合并请求 SHALL 包含源节点全部拟提交条目，并进入身份匹配流程

#### Scenario: 周期性增量提交
- **WHEN** 源节点每周向团队实例提交一次
- **THEN** 每次合并请求 SHALL 仅包含本周内的变更条目

### Requirement: 首次合并的身份匹配
首次合并无既有链接时，系统 SHALL 用匹配引擎按标识符精确相等（最高置信度）、designation 相似度与结构特征给出候选匹配，呈现为 MATCH_CANDIDATE，评审人确认后方可建立链接。未经人工确认的匹配 MUST NOT 建立链接。

#### Scenario: 标识符精确匹配
- **WHEN** 传入条目的（RAI, 标识符）与本地某条目的标识符精确相等
- **THEN** 系统 SHALL 将其列为高置信度匹配候选

#### Scenario: 未确认不建链接
- **WHEN** 评审人未对匹配候选做确认就结束评审
- **THEN** 系统 MUST NOT 为该候选建立条目链接

### Requirement: 三方比对与冲突裁决
对已建立链接的条目，系统 SHALL 以「上次并入的源端版本快照」为合并基做三方比对：仅源侧变更 → 干净更新（UPDATE）；仅目标侧变更 → 跳过并提示；双侧均变更 → 标记冲突（CONFLICT），由评审人整项裁决（采用传入 / 保留现有）。本期 MUST NOT 做字段级编辑合并与删除传播。

#### Scenario: 干净更新
- **WHEN** 某已链接条目自上次合并以来仅源侧发生变更
- **THEN** 系统 SHALL 将其标记为 UPDATE，批准后更新目标库

#### Scenario: 双侧冲突
- **WHEN** 某已链接条目两侧均发生变更
- **THEN** 系统 SHALL 将其标记为 CONFLICT，等待评审人整项裁决

### Requirement: 评审与事务性并入
合并请求到达目标端 SHALL 创建评审流程（默认团队负责人单级，可配置多级），评审工作台按条目列出 ADD / UPDATE / CONFLICT / MATCH_CANDIDATE 四类及建议操作。批准后在单事务内完成：应用条目写入、写入 `change_type=MERGE` 的版本历史、建立/更新链接、更新水位线、生成回执；任一条目失败 MUST 整体回滚并置 FAILED。

#### Scenario: 批准并入
- **WHEN** 评审人对全部条目完成裁决并批准
- **THEN** 系统 SHALL 在单事务内完成并入并生成回执

#### Scenario: 并入失败回滚
- **WHEN** 并入过程中某条目写入失败
- **THEN** 系统 SHALL 回滚全部已写入数据，合并请求置 FAILED 并附原因

### Requirement: 评审范围与权限
评审人资格 MUST 为目标命名空间上具备相应权限的账号（namespace_permission）或超级管理员。源端提交者身份 MUST 以对端节点凭据与包内签署信息表示，MUST NOT 要求源端账号在目标端存在。

#### Scenario: 无命名空间权限者不可评审
- **WHEN** 不具备目标命名空间权限的用户尝试评审合并请求
- **THEN** 系统 SHALL 拒绝并返回权限错误
