## MODIFIED Requirements

### Requirement: Recorded 及以上 Draft 与 Rejected 可回退上一提交版本

注册状态 ∈ {`CANDIDATE`, `RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED_STANDARD`, `SUPERSEDED`, `RETIRED`} 且管理状态为 `DRAFT` 或 `REJECTED` 的管理项，归属用户可将其直接回退到上一个提交版本：系统 SHALL 恢复该管理项最近一条基线（`BASELINE`）快照的内容，丢弃草稿或被拒绝版本，写回 `ROLLED_BACK` 快照，并将管理状态清空（NULL）。回退 MUST NOT 改变注册状态。无基线快照时，若管理项为 `REJECTED` 且自进入拒绝以来内容无变更，系统 SHALL 仅将管理状态清空；其他无基线情形 MUST 拒绝并返回无版本可回退的错误。

回退窗口的注册状态范围由 `version-control` 能力的"回退窗口"节规定：依据 ISO/IEC 11179-6:2023 4.3.3.1.3 / 4.3.3.1.4 字面表述，`CANDIDATE` 起即受版本控制，故回退窗口按标准字面取 `CANDIDATE` 起（本条原先限定为 `RECORDED` 及以上，现与 11179-6 字面对齐）。

#### Scenario: 草稿回退恢复上一提交版本

- **GIVEN** 某注册状态为 `CANDIDATE` 的管理项管理状态为 `DRAFT`，且存在进入草稿前的基线快照
- **WHEN** 归属用户执行回退
- **THEN** 管理项内容恢复为基线快照内容，生成 `ROLLED_BACK` 快照，管理状态清空，注册状态保持 `CANDIDATE`

#### Scenario: 被拒绝版本回退并丢弃

- **GIVEN** 某注册状态 ∈ 回退窗口的管理项管理状态为 `REJECTED`，其被拒绝的内容相对基线有变更
- **WHEN** 归属用户执行回退
- **THEN** 被拒绝版本的内容被丢弃，管理项恢复为基线内容，管理状态清空

#### Scenario: 无基线且内容无变更的拒绝仅清状态

- **GIVEN** 某管理项管理状态为 `REJECTED`，自申请合格以来内容无变更（无基线快照）
- **WHEN** 归属用户执行回退
- **THEN** 管理状态清空，内容不变

#### Scenario: 无基线可回退被拒绝

- **GIVEN** 某管理项管理状态为 `DRAFT`，且不存在任何基线快照
- **WHEN** 归属用户执行回退
- **THEN** 系统拒绝并返回无版本可回退的错误