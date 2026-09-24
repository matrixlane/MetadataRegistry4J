# registration-process Specification（变更：registration-process-designer）

## ADDED Requirements

### Requirement: 注册流程状态机底线

系统 SHALL 以 ISO/IEC 11179-3:2023 Table 30（9.6.2）定义的十个 Registration_Status 状态作为注册流程的状态集，并以 ISO/IEC 11179-6:2023 Annex D.2.2–D.2.11 逐状态 Possible progression（2026-09-18 标准核验）为状态机底线：`INCOMPLETE→{CANDIDATE, HISTORICAL, APPLICATION}`、`CANDIDATE→{RECORDED, HISTORICAL, APPLICATION}`、`APPLICATION→{CANDIDATE}`、`RECORDED→{QUALIFIED, RETIRED, SUPERSEDED}`、`QUALIFIED→{STANDARD, RETIRED, SUPERSEDED}`、`STANDARD→{PREFERRED_STANDARD, RETIRED, SUPERSEDED}`、`PREFERRED_STANDARD→{STANDARD, RETIRED, SUPERSEDED}`、`SUPERSEDED→{RETIRED}`、`RETIRED→{}`、`HISTORICAL→{}`。

`APPLICATION` 与 `HISTORICAL` 为文档状态（11179-6:2023, 4.3.2），SHALL 允许作为 Administered Item 的初始状态标定。任何流程定制 MUST NOT 产生超出上述迁移集的状态迁移。RETIRED 与 HISTORICAL 为终态，无任何出边；SUPERSEDED 只能从 Recorded 及以上状态进入（4.3.3.1.8/4.3.3.1.9 旁证）。

#### Scenario: 底线内的迁移被接受

- **GIVEN** 一个 Administered Item 处于 `RECORDED` 状态
- **WHEN** 合格操作者完成到 `QUALIFIED` 的迁移（含审核生效）
- **THEN** 迁移被接受并持久化新的 Registration_State

#### Scenario: 底线外的迁移被拒绝

- **WHEN** 任何路径尝试将处于 `HISTORICAL` 状态的 Administered Item 迁移到任意状态
- **THEN** 系统拒绝该迁移并返回迁移不允许的错误

#### Scenario: 文档状态可作为初始状态

- **WHEN** 创建 Administered Item 时指定初始注册状态为 `APPLICATION` 或 `HISTORICAL`
- **THEN** 系统接受该初始状态标定

### Requirement: 标准默认审核点

依据 ISO/IEC 11179-6:2023, 4.4 d)，目标状态为 `QUALIFIED`、`STANDARD` 或 `PREFERRED_STANDARD` 的迁移 SHALL 默认要求注册机构批准（审核点）。依据本注册表 2026-09-19 治理加严决策（超出 4.4 d) 最低要求，规则即数据、由种子调和收敛），`CANDIDATE→RECORDED` 迁移 SHALL 默认要求 Registrar 人工复核批准——除强制属性完备的硬性条件外，必须经批准才能进入 Recorded。其余迁移 SHALL 默认无需审核。四处审核点的默认审核人均为该项所属注册机构的全部 Registrar，默认通过规则为任一人批准即通过。

#### Scenario: 推进到 Qualified 需要批准

- **GIVEN** 一个 Administered Item 处于 `RECORDED` 状态
- **WHEN** 用户查看该项的可用迁移
- **THEN** 到 `QUALIFIED` 的迁移被标记为需要审核，且不能直接执行

#### Scenario: 进入 Recorded 需要复核批准

- **GIVEN** 一个 Administered Item 处于 `CANDIDATE` 状态
- **WHEN** 用户查看该项的可用迁移
- **THEN** 到 `RECORDED` 的迁移被标记为需要审核，且不能直接执行

#### Scenario: Recorded 到 Retired 无需审核

- **GIVEN** 一个 Administered Item 处于 `RECORDED` 状态
- **WHEN** 合格操作者执行到 `RETIRED` 的迁移
- **THEN** 迁移不经审核直接生效

### Requirement: 审批累积与生效模型

对需要审核的迁移，系统 SHALL 累积合格 Registrar 的批准记录：同一 Registrar 对同一 (item, from, to) 迁移 MUST 只计一次批准。当去重后的批准人数达到该迁移规则的通过人数（任一人或至少 N 人）时，系统 SHALL 在同一事务内完成状态迁移并清除该迁移的全部批准记录。Administered Item 的注册状态发生任何变化后，系统 SHALL 清除该项全部未生效的批准记录。系统 MUST NOT 提供驳回操作与发起审核操作。

#### Scenario: 达到 quorum 自动生效

- **GIVEN** 迁移 `RECORDED→QUALIFIED` 的通过规则为至少 2 人，且已有 1 名 Registrar 批准
- **WHEN** 第 2 名不同的合格 Registrar 批准该迁移
- **THEN** 状态迁移在同一事务内生效，且该迁移的批准记录被清除

#### Scenario: 重复批准被拒绝

- **GIVEN** Registrar 甲已对某迁移投出批准
- **WHEN** 甲再次批准同一迁移
- **THEN** 系统拒绝重复批准或幂等忽略，批准人数不增加

#### Scenario: 状态变化清除陈旧批准

- **GIVEN** 某 Administered Item 的迁移 A→B 已有未生效批准
- **WHEN** 该项的注册状态经其他路径发生变化
- **THEN** 全部未生效批准记录被清除

### Requirement: 迁移执行与批准的权限

无需审核的迁移 SHALL 仅由具备写权限的用户（现行 ADMIN/REGISTRAR 角色矩阵）直接执行。需要审核的迁移的批准操作 SHALL 要求操作者持有该 Administered Item 所属注册机构的活动 Registrar 身份，且在该迁移规则的审核人集合内。Recorded 及以上目标状态的强制属性/关联校验 SHALL 在迁移生效时强制执行（11179-3:2023, 4.5）。

#### Scenario: 非审核集合内用户批准被拒

- **GIVEN** 用户不持有该项所属注册机构的活动 Registrar 身份
- **WHEN** 该用户尝试批准一项需要审核的迁移
- **THEN** 系统拒绝并返回权限错误

#### Scenario: 生效时强制校验失败则回滚

- **GIVEN** 某迁移达到通过人数，但目标状态的强制校验（如 MeasureClass 须关联 UnitOfMeasure）不满足
- **WHEN** 系统尝试生效该迁移
- **THEN** 整个生效事务回滚，并返回校验错误，批准记录的处理方式与回滚一致

### Requirement: 注册流程只读展示页

系统 SHALL 提供注册流程展示页，向所有登录用户只读展示当前注册机构的注册流程：全部十个状态、允许迁移的流向、每条迁移是否需要审核及其审核配置（审核人模式、通过人数、操作名）。状态节点 SHALL 提供该状态的释义展示（定义与解释，随界面语言显示对应语言文本），作为用户学习注册流程的渠道；页面文案 MUST NOT 直接引用标准文档编号或条款号。本期 MUST NOT 提供流程定义的编辑能力。

#### Scenario: 用户查看流程图与状态释义

- **WHEN** 登录用户打开注册流程页并将鼠标浮动在某个状态节点上
- **THEN** 页面展示该状态的释义（定义与解释，按当前界面语言单语显示）

#### Scenario: 用户查看迁移审核配置

- **WHEN** 用户查看某条迁移的详情
- **THEN** 页面展示该迁移是否需要审核、审核人模式与通过人数，并以功能性说明告知需审核迁移须经注册机构审核批准

### Requirement: 注册项列表中的流程执行

注册项列表 SHALL 以"推进流程"方式呈现当前状态的可达迁移：逐条显示目标状态、操作名、是否需要审核与审批进度（已批准人数/通过人数）。对无需审核的迁移，合格操作者 SHALL 看到执行入口；对需要审核的迁移，合格 Registrar SHALL 看到批准入口（已批准者显示已批准状态）；无权限用户 SHALL 仅看到只读进度。列表 SHALL 支持按注册状态过滤。

#### Scenario: 合格 Registrar 批准迁移

- **GIVEN** 某 Administered Item 处于 `RECORDED`，当前用户持有所属注册机构的活动 Registrar 身份
- **WHEN** 用户在推进流程面板中对 `QUALIFIED` 迁移点击批准
- **THEN** 批准被记录，进度即时更新；达到通过人数时状态自动迁移

#### Scenario: 按状态过滤注册项

- **WHEN** 用户在注册项列表选择某个注册状态过滤条件
- **THEN** 列表仅展示处于该状态的 Administered Item

### Requirement: 状态不再经表单下拉直接设置

Administered Item 的注册状态 MUST NOT 通过编辑表单或通用状态下拉直接修改；状态变更 SHALL 只能经迁移执行或审批生效两条路径完成。编辑表单中的注册状态字段 SHALL 只读展示。

#### Scenario: 编辑表单不接受状态变更

- **WHEN** 更新 Administered Item 的请求携带与当前不同的注册状态
- **THEN** 系统拒绝该变更或忽略状态字段，状态保持不变

### Requirement: 流程定义按注册机构作用域与种子播种

流程迁移规则 SHALL 按注册机构（Registration Authority）作用域存储，唯一性由 `(registration_authority_id, from_status, to_status)` 约束。系统 SHALL 为每个注册机构幂等播种标准默认规则（迁移集、默认审核点、默认审核人模式与通过规则）。本期一个应用即一个注册机构，但结构 MUST 支持每注册机构一套流程。

#### Scenario: 种子幂等

- **WHEN** 应用启动或新注册机构创建时播种默认规则
- **THEN** 重复播种不产生重复规则，已存在的规则不被覆盖
