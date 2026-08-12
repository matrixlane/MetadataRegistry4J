# system-initialization Delta Spec

## MODIFIED Requirements

### Requirement: 初始化必须创建注册机构自有命名空间

初始化 MUST 创建注册机构，且 MUST 同时创建与该注册机构关联的自有命名空间（1:1，两边 0..1）；命名空间设置 MUST NOT 可跳过。自有命名空间创建时 SHALL 同步建立标识生成策略（段模板，PUBLISHED 且立即生效）与版本生成策略（自动化）。标识生成策略类型 MUST 收敛为段模板唯一选择：初始化请求携带 `identifierStrategy` 为 `SEGMENT_TEMPLATE` 以外任何值（含 UUID、SEQUENCE、TEMPLATE）MUST 返回业务校验错误；向导界面 MUST NOT 呈现策略类型单选。向导命名空间步骤 MUST 就地内嵌标识模板编辑器，供管理员在初始化时直接编排模板；请求未携带模板 JSON 时 SHALL 落系统默认模板（OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)，示例 `DE.00001`）；标识符输入模式为 MANUAL_ONLY 时编辑器 SHALL 禁用。默认模板、预设与界面文案 MUST NOT 引用任何特定行业标准编号。初始化完成之后创建的命名空间 SHALL 为外部命名空间，使用手动编辑策略。

#### Scenario: 初始化完成即具备自有命名空间与策略

- **WHEN** 管理员完成初始化向导
- **THEN** 系统存在注册机构及其关联的自有命名空间，该命名空间的段模板标识策略（PUBLISHED）与版本自动化策略已生效

#### Scenario: 向导就地编排模板并生效

- **WHEN** 管理员在向导命名空间步骤中将模板编排为 LITERAL("WN") + SEPARATOR(".") + OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5) 并完成初始化
- **THEN** 自有命名空间已发布策略即为该模板，首个数据元注册生成 `WN.DE.00001`

#### Scenario: 策略类型白名单收敛

- **WHEN** 初始化请求携带 `identifierStrategy` 为 `UUID`（或 `SEQUENCE`、`TEMPLATE`）
- **THEN** 系统返回业务校验错误，提示标识生成策略仅支持段模板

#### Scenario: 不允许跳过命名空间创建

- **WHEN** 初始化请求显式声明不创建命名空间
- **THEN** 系统返回业务校验错误，提示自有命名空间为必选项

#### Scenario: 初始化后创建的命名空间为外部

- **WHEN** 初始化完成后用户再创建命名空间
- **THEN** 新命名空间的性质为外部，其标识与版本策略为手动编辑
