# system-initialization

## ADDED Requirements

### Requirement: setup 业务数据范围含注册系统默认上下文

初始化向导的业务数据创建范围 SHALL 在注册规范创建完成之后扩展一步：创建注册系统默认上下文（Registry Context）并回填注册规范的默认上下文。该步骤 MUST 晚于注册机构自有命名空间标识/版本生成策略的配置生效，Registry Context 的命名空间 MUST 为注册机构的自有命名空间，其标识符与版本 MUST 由该命名空间已配置的生成规则产出；该步骤 MUST 在系统初始化提交（commit）时以独立事务执行，其失败 MUST NOT 阻断 setup 的其余业务数据创建与 commit 本身。

#### Scenario: 初始化完成后默认上下文就绪

- WHEN 管理员完成初始化向导（setup 提交且 commit 成功）
- THEN 系统中存在归属向导注册机构的 Registry Context（标识/版本来自其自有命名空间的生成规则），且注册规范的默认上下文指向该 Registry Context

#### Scenario: 该步骤失败不阻断向导

- WHEN Registry Context 创建步骤失败而其余治理数据（注册机构、命名空间、注册规范）创建成功
- THEN 初始化照常完成；Registry Context 由后续启动的补建路径再次尝试创建
