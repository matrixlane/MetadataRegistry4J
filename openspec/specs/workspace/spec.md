# workspace Specification

## Purpose
工作空间能力：为提交者与注册人员提供一级菜单入口"工作空间"，集中展示当前需要关注的元数据对象——被本人作为提交人新增或编辑过的管理项，以及注册人员需要集中审核的管理项——并支持批量提交、请求成为候选、申请合格、批准/拒绝、修订与回退等行级操作。需要关注的判定以管理状态与归属规则为准：归属指当前账号绑定联系人属于该管理项的提交记录联系人（最近一条 SubmissionRecord.contact）或主管记录联系人（StewardshipRecord.contact）。

## Requirements

### Requirement: 三视图聚合与归属过滤

系统 SHALL 提供"工作空间"一级菜单入口，以三个视图集中展示需要关注的管理项，并支持分页：
- 草稿（DRAFTS）：管理状态为 `DRAFT` 且归属当前用户的管理项；
- 待审核（PENDING）：当前用户持活动 Registrar 身份时，本注册机构下管理状态为 `PROVISIONALLY_QUALIFIED` 的管理项，以及存在待批准迁移的管理项；
- 已拒绝（REJECTED）：管理状态为 `REJECTED` 且归属当前用户的管理项。

系统 SHALL 提供三视图计数汇总接口。工作空间查询 MUST NOT 遍历 `previous_state` 链，只关联当前注册状态；MUST NOT 因 Item 联合子类整图 join 超出 SQLite 单条 SQL 表数上限。

#### Scenario: 提交者看到本人草稿

- **GIVEN** 用户绑定联系人为某管理项最近提交记录的联系人，该管理项管理状态为 `DRAFT`
- **WHEN** 该用户打开工作空间草稿视图
- **THEN** 列表包含该管理项，并展示其注册状态、管理状态与可用操作

#### Scenario: 非归属用户的草稿不可见

- **GIVEN** 某管理项管理状态为 `DRAFT`，其提交记录联系人与主管联系人均不属于当前账号
- **WHEN** 当前用户打开工作空间草稿视图
- **THEN** 列表不包含该管理项

#### Scenario: 注册人员看到待审核项

- **GIVEN** 当前用户持有本注册机构活动 Registrar 身份，某本机构管理项管理状态为 `PROVISIONALLY_QUALIFIED`
- **WHEN** 该用户打开待审核视图
- **THEN** 列表包含该管理项及其申请信息

### Requirement: 批量提交草稿管理项

工作空间 SHALL 支持用户勾选一个或多个管理状态为 `DRAFT` 且归属当前用户的管理项进行批量提交。提交 SHALL 弹出提交信息（Submission_Record）选择器：联系人与组织默认预填当前用户默认联系人及其组织，用户可修改为本账号绑定的其他联系人；显式给出的联系人 MUST 属于当前用户账号绑定，否则拒绝（422）。提交成功后，每条管理项 SHALL 生成一条新的 SubmissionRecord，管理状态按清空规则落地（Incomplete 落 `INITIAL`，其余清空）。部分失败时系统 SHALL 返回逐条结果（成功/失败及原因），成功项保持生效。

#### Scenario: 批量提交成功

- **GIVEN** 用户勾选三个归属本人的草稿管理项并确认提交信息
- **WHEN** 用户确认批量提交
- **THEN** 三个管理项各生成新 SubmissionRecord，管理状态按清空规则落地，列表刷新后不再出现在草稿视图

#### Scenario: 非草稿项被拒绝

- **WHEN** 批量提交请求中包含管理状态非 `DRAFT` 的管理项
- **THEN** 该项提交失败并返回原因，其余项不受影响

#### Scenario: 代填他人联系人被拒绝

- **WHEN** 提交信息中的 contactId 不是当前账号绑定的任何 Contact
- **THEN** 系统拒绝该请求并返回校验错误

### Requirement: 请求成为候选的可见性与生效规则

工作空间列表中的"请求成为候选"按钮 SHALL 仅当以下全部条件满足时显示：注册状态为 `INCOMPLETE`；Candidate 最低需求满足（管理项具有主管记录 StewardshipRecord，即 ISO/IEC 11179-6:2023 4.3.3.1.3 所要求的主管组织与主管联系人信息齐备）；归属当前用户。按钮可见性与管理状态是否为草稿无关。点击后系统 SHALL 将注册状态直推为 `CANDIDATE`（附录 C：提交人可自行决定将管理项记为 Incomplete 或 Candidate），管理状态清空（NULL），并记录 SubmissionRecord。后端 SHALL 在请求时重复校验上述条件，不信任前端标志。归属本用户的提交者（Submitter）角色 SHALL 获准执行此迁移，其余迁移的执行权限不变。

#### Scenario: 满足条件的归属用户可见按钮

- **GIVEN** 某 `INCOMPLETE` 管理项具有主管记录，其提交记录联系人为本用户
- **WHEN** 该用户在工作空间查看该行
- **THEN** 显示"请求成为候选"按钮，无论该管理项管理状态是否为草稿

#### Scenario: 缺少主管记录不显示按钮

- **GIVEN** 某 `INCOMPLETE` 管理项无主管记录
- **WHEN** 任何用户查看该行
- **THEN** 不显示"请求成为候选"按钮

#### Scenario: 点击后注册状态成为 Candidate

- **WHEN** 归属用户点击"请求成为候选"并确认提交信息
- **THEN** 注册状态为 `CANDIDATE`，管理状态清空，生成一条 SubmissionRecord

#### Scenario: 非归属用户请求被拒绝

- **WHEN** 管理项提交记录联系人与主管联系人均不属于当前账号的用户调用请求成为候选端点
- **THEN** 系统拒绝并返回权限错误

### Requirement: 行级操作权限标志

工作空间列表每行 SHALL 由后端返回该行当前用户可用的操作标志（提交、请求成为候选、申请合格、批准、拒绝、修订、回退），前端按标志渲染操作入口；后端各操作端点 SHALL 重复校验权限与前置条件。

#### Scenario: 待审核行对 Registrar 返回批准与拒绝

- **GIVEN** 某管理项管理状态为 `PROVISIONALLY_QUALIFIED`，当前用户为该项所属注册机构活动 Registrar
- **WHEN** 该行被查询
- **THEN** 行内返回可用操作含批准与拒绝

#### Scenario: 非 Registrar 无批准拒绝操作

- **GIVEN** 同上管理项，当前用户无活动 Registrar 身份
- **WHEN** 该行被查询
- **THEN** 行内返回的可用操作不含批准与拒绝

### Requirement: 未提交数量徽标

系统 SHALL 在顶栏右上角提供工作空间入口图标，图标区域以徽标显示当前用户归属且管理状态为 `DRAFT`（未提交）的 Item 数量（数据源为 workspace summary 的 drafts 计数）。计数为 0 时徽标 SHALL 隐藏。徽标计数 SHALL 在工作空间页内完成提交、请求成为候选、回退、批准、拒绝等变更操作后立即刷新，并以定时轮询作为补充刷新机制；获取失败时 SHALL 保留上一次计数。

#### Scenario: 有未提交草稿时显示数量

- **GIVEN** 当前用户归属的两个管理项管理状态为 `DRAFT`
- **WHEN** 用户查看顶栏右上角工作空间入口
- **THEN** 徽标显示数字 2

#### Scenario: 无未提交草稿时隐藏徽标

- **GIVEN** 当前用户没有任何管理状态为 `DRAFT` 的归属管理项
- **WHEN** 用户查看顶栏右上角工作空间入口
- **THEN** 徽标隐藏

#### Scenario: 提交后立即刷新

- **GIVEN** 徽标当前显示 2
- **WHEN** 用户在工作空间提交这两个草稿且成功
- **THEN** 徽标计数立即更新为 0 并隐藏，无需等待下一次轮询
