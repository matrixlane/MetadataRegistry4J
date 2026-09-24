# Design: initial-status-incomplete

## Context

唯一改动点是 `ItemAdministrationService.ensureAdministeredItem` 的初始 `RegistrationState` 落点（`metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/ItemAdministrationService.java:416-438`）。全部创建路径（14 处服务 + 联邦 `GenericItemCreator`）汇聚于此；既有条目的表单状态变更守卫（1124）与 M2 遗留路径（无状态条目先落默认再比对）同在此方法内。superpowers 设计全文（含标准逐条引用）见 `docs/superpowers/specs/2026-09-21-initial-status-incomplete-design.md`。

## Goals / Non-Goals

**Goals:**

- 新建管理项初始注册状态为 `INCOMPLETE`（ISO/IEC 11179-6:2023 4.3.3.1.2 生命周期入口态）。
- 创建时显式 `registrationStatus` 仅接受 `INCOMPLETE`/`APPLICATION`/`HISTORICAL`；其余拒绝（1125）。
- 既有条目路径行为不变（1124 守卫、同值放行）。

**Non-Goals:**

- 不改动 `RegistrationLifecycleService` 状态机、角色矩阵与 `DEFAULT_APPROVAL_TRANSITIONS`（10 处审核点）。
- 不改动 `GlobalNamespaceService.wrapperOf`（系统内部簿记包装物，非注册生命周期参与者）。
- 不做存量数据迁移。
- 不引入"版本控制按状态启用"（标准 Incomplete 不入版本控制；本系统创建即赋版本，维持现状，记录为已知偏差）。
- 不新增前端提示文案（可选后续）。

## Decisions

**D1：默认落 INCOMPLETE，而非保留 RECORDED 作为"已补全"快路径。**
Recorded 的准据是全部强制属性完成并经确认（4.3.3.1.4 / Table 1），属推进结果而非出生点。创建表单虽收集较多字段，但"完整性确认"是注册机构的推进行为，不能由创建默认值替代。

**D2：显式标定用白名单（INCOMPLETE/APPLICATION/HISTORICAL），而非黑名单或全量保留。**
黑名单需逐一枚举拒绝值且易随枚举扩展漂移；全量保留允许直建 QUALIFIED，与生命周期入口语义冲突。白名单中 APPLICATION/HISTORICAL 是 11179-6 的文档态入口标定，正是该显式能力的原始设计意图（代码注释可考）；INCOMPLETE 允许显式携带以保持幂等。

**D3：白名单校验嵌入 ensureAdministeredItem 既有分支，不新增端点/服务。**
执行顺序保持"先落默认 → 显式值校验/覆盖"：默认 INCOMPLETE → `existing == null && req.registrationStatus() != null` 时白名单校验，通过则设置、不通过抛 `BusinessException(1125)`；`existing != null` 分支完全不动。

**D4：错误码 `INVALID_INITIAL_STATUS = 1125` 占用 1100–1199 注册生命周期段内下一空闲值。**

**D5：联邦并入不特判。**
`GenericItemCreator` 创建请求不携带状态，自然落 INCOMPLETE；语义上并入条目在目的注册机构即新提交项，与标准一致。

## Standard Basis（已逐条核实）

- ISO/IEC 11179-6:2023 4.3.2 Table 1：状态分级与准据。
- 4.3.3.1.2 Incomplete：入口态；最低元数据 = 标识符 + 提交者组织名 + 提交者联系人 + 提交者联系信息；不入版本控制；约束不强制。
- 4.3.3.1.3 Candidate：提议晋级；入版本控制；补 stewardship 信息。
- 4.3.3.1.4 Recorded：全部强制属性完成 + 强制关联实例化 + 约束生效。
