## Purpose

承载管理项的"版本控制"语义：规定何时受版本控制、回退窗口与快照分类。依据 ISO/IEC 11179-6:2023 4.3.3.1.2 / 4.3.3.1.3 / 4.3.3.1.4 字面表述。

## ADDED Requirements

### Requirement: 版本控制范围

系统 MUST NOT 为处于 `INCOMPLETE` 注册状态的管理项维护版本控制；系统 MUST 为处于 `CANDIDATE` 及更高注册状态的管理项（`CANDIDATE`、`RECORDED`、`QUALIFIED`、`STANDARD`、`PREFERRED_STANDARD`、`SUPERSEDED`、`RETIRED`）维护版本控制。

"受版本控制"指管理项的任何属性变更（含指称、定义、关联、提交记录）须以"快照"形式持久化至 `version_history` 表；其条目识别由 `(item_id, version_number)` 唯一键确定，版本号从 1 开始自增。

#### Scenario: 新建 Incomplete 条目不入版本控制

- **WHEN** 用户新建任意 Item 类型条目（DataElement / ValueDomain / DataElementConcept / ObjectClass / Property / ConceptualDomain / DataType / UnitOfMeasure / MeasureClass），初始注册状态为 `INCOMPLETE`
- **THEN** `version_history` 表不为该 item_id 写入任何快照

#### Scenario: Candidate 起条目受版本控制

- **WHEN** 任意 item_id 当前注册状态为 `CANDIDATE` 且被用户编辑并保存
- **THEN** `version_history` 表依次为该 item 写入一条 `BASELINE` 快照（编辑前一刻）与一条 `DRAFT_EDIT` 快照（编辑后状态）

### Requirement: SnapshotKind 分类

`version_history.snapshot_json.meta.kind` SHALL 取下列四个枚举值之一，写入触发条件如下：

- `SUBMITTED`：管理项创建（`CREATE` changeType）或提交后被 `UPDATE`（adminStatus 已非 Draft）。
- `DRAFT_EDIT`：管理项在 adminStatus = `DRAFT` 时被 `UPDATE`。
- `BASELINE`：管理项从非 Draft 进入 Draft 编辑前一瞬的完整状态；调用方须提供编辑前一刻的 Response DTO。同一 Item 仅首次进入 Draft 时落基线，adminStatus 仍为 Draft 的后续编辑不重复落基线。
- `ROLLED_BACK`：管理项回退操作期间产生的快照；`isRestoreInProgress()` 期间任何 `recordSnapshot` 调用均落入此 kind，无论原 changeType。

#### Scenario: 首次进入 Draft 落 BASELINE

- **WHEN** adminStatus 当前为 null / `INITIAL` / 其他非 Draft / 非 Rejected 的 item 被用户编辑并保存
- **THEN** `version_history` 表按顺序写入 `BASELINE`（kind=BASELINE）+ `DRAFT_EDIT`（kind=DRAFT_EDIT）

#### Scenario: 持续 Draft 编辑不再补 BASELINE

- **WHEN** 同一 item 已在 adminStatus = `DRAFT` 状态下再次被编辑保存
- **THEN** 仅写入 `DRAFT_EDIT`，不再补 `BASELINE`

### Requirement: 回退窗口

注册状态 ∈ {`CANDIDATE`, `RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED_STANDARD`, `SUPERSEDED`, `RETIRED`} 且 adminStatus ∈ {`DRAFT`, `REJECTED`} 的管理项，归属当前用户的提交者可触发"回退到上一个提交版本"操作：恢复该管理项最近一条 `BASELINE` 快照的内容并写一条 `ROLLED_BACK` 快照；adminStatus SHALL 清空（NULL）；注册状态 SHALL 不变。

#### Scenario: Candidate + Draft 可回退

- **WHEN** item 注册状态为 `CANDIDATE`，adminStatus 为 `DRAFT`，存在至少一条 `BASELINE` 快照
- **THEN** 系统按最近一条 BASELINE 内容恢复主表，写一条 `ROLLED_BACK` 快照，adminStatus 清空，注册状态保持 `CANDIDATE`

#### Scenario: Rejected + 无 BASELINE 降级

- **WHEN** item 注册状态 ∈ 回退窗口，adminStatus = `REJECTED`，且该 item 在被拒绝以来无任何 BASELINE 快照
- **THEN** 系统仅将 adminStatus 清空（NULL），主表内容不变，不写 `ROLLED_BACK` 快照

#### Scenario: Recorded+ 回退不受影响

- **WHEN** item 注册状态 ∈ {`RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED_STANDARD`, `SUPERSEDED`, `RETIRED`} 且 adminStatus ∈ {`DRAFT`, `REJECTED`}
- **THEN** 既有回退路径继续生效，行为不变