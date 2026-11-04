## MODIFIED Requirements

### Requirement: 既有已初始化库启动补建

对已完成初始化但尚无 Registry Context 的既有部署，系统 SHALL 在应用启动时自动补建（含注册规范默认上下文回填，若为空）；补建 MUST NOT 阻止应用启动，失败时记录告警并在下次启动重试。补建 MUST 作为数据库初始化引导的一个步骤执行，位置在静态预置与标识符策略装载之后；引导在此前步骤失败时 MUST 跳过补建，MUST NOT 使应用无法启动。

#### Scenario: 已初始化库升级补建

- WHEN 已初始化系统升级到新版本后首次启动且 Registry Context 缺失
- THEN 系统自动按本能力全部规则补建 Registry Context，注册规范默认上下文为空时一并补挂

#### Scenario: 补建失败不阻断启动

- WHEN 启动补建过程中发生异常（如注册机构缺少自有命名空间）
- THEN 应用正常启动完成，日志记录告警，Registry Context 保持缺失待下次启动重试

#### Scenario: 引导前置步骤失败时跳过补建

- WHEN 数据库初始化引导在连接校验或迁移步骤失败
- THEN 补建 MUST NOT 执行，且应用 MUST 保持可运行并展示失败原因

## ADDED Requirements

### Requirement: 引导步骤的顺序与失败短路

数据库初始化引导 MUST 由单一入口按固定顺序执行：连接校验、结构创建、迁移或基线化、旧库修复、静态预置、标识符策略装载、治理数据回填。任一步骤失败时 MUST 跳过后续步骤并记录失败阶段与原因，MUST NOT 继续执行依赖其结果的后续步骤。启动期执行与运行期重试 MUST 复用同一入口。

#### Scenario: 前序失败跳过后续步骤

- **WHEN** 数据库初始化引导在静态预置步骤失败
- **THEN** 标识符策略装载与治理数据回填 MUST NOT 执行，且失败原因被记录

#### Scenario: 重试与启动复用同一入口

- **WHEN** 用户在失败状态下触发重试初始化
- **THEN** 系统 SHALL 通过与启动期相同的入口重新执行完整的步骤序列
