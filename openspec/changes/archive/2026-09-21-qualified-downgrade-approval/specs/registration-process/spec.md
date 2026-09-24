# registration-process Delta

## REMOVED Requirements

### Requirement: 标准默认审核点

**Reason:** 2026-09-19 经 ISO/IEC 11179-6:2023 原文逐条复核：① 4.4 c)（第 9 页）"Submitters or stewards can progress administered items in the 'Candidate' status to the 'Recorded' registration status by completing all mandatory metadata attributes"——CANDIDATE→RECORDED 的治理加严审核点与标准最低要求不一致，予以撤销（D.2.3 的 steward 复审/registrar 筛查为建议性 annex 实践，非前置批准闸门）；② 4.4 d) 的批准要求仅覆盖"Qualified or higher"，遗漏了 D.4.2/D.4.3/D.2.9 强制的 Qualified+ 降级批准。原需求整体由"标准默认审核点（10 处）"替代。

**Migration:** CANDIDATE→RECORDED 恢复直接执行（强制属性完备校验照旧）；7 条 Qualified+ 降级迁移纳入默认审核点，直推返回 1120、须经所属注册机构 Registrar 批准达 quorum 生效；旧库 `registration_transition_rule` 种子行由 `ensureSeed` 在下次访问时自动收敛（翻转为新默认值；displayName 非空的定制规则不触碰），无手工迁移脚本。

## ADDED Requirements

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
