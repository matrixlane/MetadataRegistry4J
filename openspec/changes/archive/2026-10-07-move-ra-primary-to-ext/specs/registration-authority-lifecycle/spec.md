# Registration Authority Lifecycle Delta Specification

## MODIFIED Requirements

### Requirement: 注册机构 Primary 隐式标志

系统初始化向导创建的注册机构 SHALL 在后台被标记为"主要"（Primary）机构。该标志 MUST 仅存在于后端，MUST NOT 出现在任何响应 DTO 或前端界面中。该标志为应用扩展语义（ISO/IEC 11179-3 无此概念），MUST 存放于 ext schema 卫星表（与注册机构 1:1 关联），MUST NOT 存放在 mdr 标准表上。团队版下该标志 MUST NOT 影响任何处理（删除保护等暂不发挥作用）；个人版下该标志是删除保护的判定依据。除初始化流程外，任何途径 MUST NOT 设置或变更该标志；恢复出厂重新初始化后新创建的注册机构 MUST 重新获得该标志。存量已初始化库 MUST 在无 Primary 机构时由启动调和把最早创建的注册机构补标为 Primary。

#### Scenario: 初始化创建的主要机构带隐式标志

- **WHEN** 管理员（任一版本）完成初始化向导提交
- **THEN** 本次创建的注册机构在后台（ext 卫星表）被标记为 Primary；前端注册机构列表与详情不出现任何 Primary 标识；mdr.registration_authority 不含该扩展列

#### Scenario: 团队版下标志不影响删除

- **WHEN** 部署版本为 TEAM，管理员删除带 Primary 标志的注册机构
- **THEN** 系统仅按级联删除流程处理，不因 Primary 标志拒绝删除

#### Scenario: 存量库启动调和回填

- **WHEN** 一个早于本能力部署的已初始化库启动应用，且不存在任何 Primary 机构
- **THEN** 启动调和将最早创建的注册机构补标为 Primary，个人版下该机构随后不可被删除
