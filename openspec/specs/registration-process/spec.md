# registration-process Specification

## Purpose
TBD - created by archiving change registration-process-designer. Update Purpose after archive.

## Requirements

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

### Requirement: 审批累积与生效模型

对需要审核的迁移，系统 SHALL 累积合格 Registrar 的批准记录：同一 Registrar 对同一 (item, from, to) 迁移 MUST 只计一次批准。当去重后的批准人数达到该迁移规则的通过人数（任一人或至少 N 人）时，系统 SHALL 在同一事务内完成状态迁移并清除该迁移的全部批准记录。Administered Item 的注册状态发生任何变化后，系统 SHALL 清除该项全部未生效的批准记录。系统 MUST NOT 提供发起审核操作。

对 `RECORDED→QUALIFIED` 申请（管理状态 `PROVISIONALLY_QUALIFIED`），系统 SHALL 提供驳回操作：具备批准资格的 Registrar 驳回后，注册状态保持不变，管理状态变为 `REJECTED`，该迁移的全部未生效批准记录 SHALL 被作废。

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

#### Scenario: 驳回置管理状态为 Rejected 并作废批准

- **GIVEN** 某管理项处于 `PROVISIONALLY_QUALIFIED`（`RECORDED→QUALIFIED` 申请审核中）
- **WHEN** 具备批准资格的 Registrar 驳回该申请
- **THEN** 注册状态保持 `RECORDED`，管理状态变为 `REJECTED`，该迁移全部未生效批准被作废

### Requirement: 迁移执行与批准的权限

无需审核的迁移 SHALL 仅由具备写权限的用户（现行 ADMIN/REGISTRAR 角色矩阵，以及下述归属本用户的提交者白名单迁移）直接执行。需要审核的迁移的批准操作 SHALL 要求操作者持有该 Administered Item 所属注册机构的活动 Registrar 身份，且在该迁移规则的审核人集合内。Recorded 及以上目标状态的强制属性/关联校验 SHALL 在迁移生效时强制执行（11179-3:2023, 4.5）。

`INCOMPLETE→CANDIDATE` 迁移 SHALL 允许归属本用户的提交者直接执行，前置条件为：该管理项满足 Candidate 最低需求（具有主管记录），且当前用户属于该管理项的提交记录联系人或主管记录联系人。该白名单 MUST NOT 扩展到其他迁移。

#### Scenario: 非审核集合内用户批准被拒

- **GIVEN** 用户不持有该项所属注册机构的活动 Registrar 身份
- **WHEN** 该用户尝试批准一项需要审核的迁移
- **THEN** 系统拒绝并返回权限错误

#### Scenario: 生效时强制校验失败则回滚

- **GIVEN** 某迁移达到通过人数，但目标状态的强制校验（如 MeasureClass 须关联 UnitOfMeasure）不满足
- **WHEN** 系统尝试生效该迁移
- **THEN** 整个生效事务回滚，并返回校验错误，批准记录的处理方式与回滚一致

#### Scenario: 归属提交者执行请求成为候选

- **GIVEN** 某 `INCOMPLETE` 管理项具有主管记录，当前用户为提交者角色且属于该管理项提交记录联系人
- **WHEN** 该用户执行 `INCOMPLETE→CANDIDATE` 迁移
- **THEN** 迁移被执行，注册状态变为 `CANDIDATE`

#### Scenario: 非归属用户执行白名单迁移被拒

- **GIVEN** 当前用户不属于某 `INCOMPLETE` 管理项的提交记录联系人或主管记录联系人
- **WHEN** 该用户（即使是提交者角色）执行 `INCOMPLETE→CANDIDATE` 迁移
- **THEN** 系统拒绝并返回权限错误

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

### Requirement: 标准默认审核点（10 处）

依据 ISO/IEC 11179-6:2023, 4.4 d)，目标状态为 `QUALIFIED`、`STANDARD` 或 `PREFERRED_STANDARD` 的迁移 SHALL 默认要求注册机构批准（审核点），共 3 条：`RECORDED→QUALIFIED`、`QUALIFIED→STANDARD`、`STANDARD→PREFERRED_STANDARD`。

依据 ISO/IEC 11179-6:2023 D.4.2/D.4.3（第 35 页），Qualified 及以上管理项变更到 `RETIRED` 或 `SUPERSEDED` SHALL 默认要求注册机构批准后方可生效（标准原文：registrar 对 Qualified 及以上管理项执行降级 "after presentation to the control committee"）；依据 D.2.9，`SUPERSEDED→RETIRED` 同属审核点。据此，以下 7 条降级迁移 SHALL 默认要求批准：`QUALIFIED→RETIRED`、`STANDARD→RETIRED`、`PREFERRED_STANDARD→RETIRED`、`QUALIFIED→SUPERSEDED`、`STANDARD→SUPERSEDED`、`PREFERRED_STANDARD→SUPERSEDED`、`SUPERSEDED→RETIRED`。

依据 ISO/IEC 11179-6:2023, 4.4 c)（第 9 页），`CANDIDATE→RECORDED` SHALL 默认无需审核，可由具备写权限的操作者直接执行——submitter/steward 补齐强制属性即可晋升，无需批准。同理，依据 D.4.2/D.4.3，Recorded 级管理项的降级无需审查（"The submitter may change the registration status of administered items at the 'Recorded' levels to 'Retired'/'Superseded' at any time, without review by the control committee"），`RECORDED→RETIRED` 与 `RECORDED→SUPERSEDED` SHALL 默认无需审核，可直接执行。

除上述 10 处审核点外，其余迁移 SHALL 默认无需审核。全部默认审核点的审核人均为该项所属注册机构的全部 Registrar，默认通过规则为任一人批准即通过。

#### Scenario: 推进到 Qualified 需要批准

- **GIVEN** 一个 Administered Item 处于 `RECORDED` 状态
- **WHEN** 用户查看该项的可用迁移
- **THEN** 到 `QUALIFIED` 的迁移被标记为需要审核，且不能直接执行

#### Scenario: Candidate 到 Recorded 无需审核

- **GIVEN** 一个 Administered Item 处于 `CANDIDATE` 状态且强制属性已齐备
- **WHEN** 合格操作者执行到 `RECORDED` 的迁移
- **THEN** 迁移不经审核直接生效

#### Scenario: Recorded 到 Retired 无需审核

- **GIVEN** 一个 Administered Item 处于 `RECORDED` 状态
- **WHEN** 合格操作者执行到 `RETIRED` 的迁移
- **THEN** 迁移不经审核直接生效

#### Scenario: Recorded 到 Superseded 无需审核

- **GIVEN** 一个 Administered Item 处于 `RECORDED` 状态
- **WHEN** 合格操作者执行到 `SUPERSEDED` 的迁移
- **THEN** 迁移不经审核直接生效

#### Scenario: Qualified 到 Retired 需要批准

- **GIVEN** 一个 Administered Item 处于 `QUALIFIED` 状态
- **WHEN** 用户查看该项的可用迁移
- **THEN** 到 `RETIRED` 的迁移被标记为需要审核，且不能直接执行

#### Scenario: Standard 到 Superseded 需要批准

- **GIVEN** 一个 Administered Item 处于 `STANDARD` 状态
- **WHEN** 用户查看该项的可用迁移
- **THEN** 到 `SUPERSEDED` 的迁移被标记为需要审核，且不能直接执行

#### Scenario: Superseded 到 Retired 需要批准

- **GIVEN** 一个 Administered Item 处于 `SUPERSEDED` 状态
- **WHEN** 用户查看该项的可用迁移
- **THEN** 到 `RETIRED` 的迁移被标记为需要审核，且不能直接执行

#### Scenario: Qualified 降级直接执行被拒绝

- **GIVEN** 一个归属注册机构的 Administered Item 处于 `QUALIFIED` 状态
- **WHEN** 任何角色直接执行到 `RETIRED` 或 `SUPERSEDED` 的迁移
- **THEN** 系统拒绝执行，返回需经注册机构批准的错误（1120），且不发生状态变更

#### Scenario: 未归属注册机构的 Qualified 条目降级被拒绝直推

- **GIVEN** 一个未归属注册机构的 Administered Item 处于 `QUALIFIED` 状态
- **WHEN** 用户尝试推进到 `RETIRED` 或 `SUPERSEDED`
- **THEN** 系统拒绝直推并提示先为条目归属注册机构，再经批准生效

### Requirement: 布局持久化（Admin 保存与启动预加载）

注册流程页的布局（节点坐标与连线折线段微调）SHALL 支持持久化：持有 SUPER_ADMIN 角色的用户 SHALL 能够将当前画布布局保存到服务器本地 JSON 文件（`layout/registration-process-layout.json`，路径可经 `app.registration-process-layout-file` 配置覆盖）；保存操作 SHALL 校验提交体为 JSON 对象且体积不超过 64KB，并以原子写入生效。任何登录用户 SHALL 能够读取该布局文件用于页面渲染。应用启动时若该文件不存在，系统 SHALL 以打包的预置模板（`classpath:layout-defaults/registration-process-layout.json`）自动播种该文件（开放式脚本，可手工编辑）。

页面加载时 SHALL 按以下优先级合成初始布局：内置默认布局 < 已保存布局文件 < 本次会话内的用户拖动。重置布局 SHALL 恢复至上一次保存的布局（无保存记录时为内置默认）。非 SUPER_ADMIN 用户 MUST NOT 看到保存入口，且保存接口 MUST 拒绝非 SUPER_ADMIN 请求。

#### Scenario: Admin 保存当前布局并预加载恢复

- **GIVEN** 用户以 SUPER_ADMIN 身份拖动节点与连线折线段后点击"保存布局"
- **WHEN** 保存请求成功写入服务器本地 JSON 文件
- **THEN** 页面提示保存成功；刷新或重新打开页面后，节点位置与连线折线段恢复到保存时的状态

#### Scenario: 首启自动播种布局文件

- **GIVEN** 工作目录下不存在布局文件且应用首次启动
- **WHEN** 应用启动完成
- **THEN** `layout/registration-process-layout.json` 由预置模板生成，GET 端点可读取其内容

#### Scenario: 非 Admin 不能保存

- **GIVEN** 用户不具有 SUPER_ADMIN 角色
- **WHEN** 其直接调用保存接口
- **THEN** 系统拒绝该请求（403），且前端不展示保存入口

#### Scenario: 损坏的布局文件不导致白屏

- **GIVEN** 布局文件被手工编辑为缺字段或含非法坐标
- **WHEN** 页面加载并解析该文件
- **THEN** 缺失/非法项回落到下一优先级的默认布局，页面正常渲染
