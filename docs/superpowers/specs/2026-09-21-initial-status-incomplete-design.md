# 设计：新建管理项初始注册状态统一从 Incomplete 开始

日期：2026-09-21
状态：已确认（2026-09-21，用户选定方案 A）
标准依据：ISO/IEC 11179-6:2023 4.3.2 Table 1、4.3.3.1.2–4.3.3.1.4（本地 PDF `ISO/IEC 11179 标准原文目录/ISO_IEC 11179-6-2023.pdf` 逐条核实）

## 1. 背景与标准依据

### 1.1 问题

用户发现：新增一个数据元，注册状态默认即为"已记录（Recorded）"。经核实，这是 `ItemAdministrationService.ensureAdministeredItem` 的兜底默认值（`ItemAdministrationService.java:419`）：

```java
boolean stateCreatedNow = ai.getRegistrationState() == null;
if (stateCreatedNow) {
    RegistrationState state = new RegistrationState();
    state.setRegistrationStatus(RegistrationStatus.RECORDED);   // ← 本次改动点
    state.setEffectiveDate(LocalDateTime.now());
    ai.setRegistrationState(state);
}
```

且系统内部口径自相矛盾：推进流程把 `null` 状态视为 INCOMPLETE（`RegistrationProcessService.java:482`、`RegistrationLifecycleService.java:266`），落库时却被写成 RECORDED。

### 1.2 标准依据（ISO/IEC 11179-6:2023，逐条核实）

- **4.3.2 Table 1**：生命周期状态集 Incomplete / Candidate / Recorded / Qualified / Standard / Preferred Standard；文档态 Historical / Application。
- **4.3.3.1.2 Incomplete（入口态）**：提交者希望让社区知晓该管理项的存在；**不纳入版本控制**；最低元数据仅要求标识符 + 提交者组织名 + 提交者联系人 + 提交者联系信息；允许强制属性未填全、约束不强制。
- **4.3.3.1.3 Candidate**：已提议向更高注册级别晋级；纳入版本控制；需补管理方（stewardship）信息。
- **4.3.3.1.4 Recorded**：**全部强制元数据属性填写完成、全部强制关联已实例化、相关约束开始生效**（Table 1 准据表述："注册机构已确认所有强制元数据属性填写完整"）；纳入版本控制。
- 生命周期顺序：`Incomplete → Candidate → Recorded → Qualified → Standard → Preferred Standard`。

结论：Recorded 是"完整性经确认之后"的状态，不是注册生命周期的出生点。**新建条目的初始注册状态应为 Incomplete**；Recorded 只能经 INCOMPLETE→CANDIDATE→RECORDED 推进获得。

### 1.3 现状盘点（已核实）

- 全部 14 处创建路径（数据元、值域、语境、注册规范、约束集、数据类型、数据类型体系、许可值含义、条目映射、概念内容应用等）均汇聚到 `ItemAdministrationService.ensureAdministeredItem`，联邦并入（`GenericItemCreator.java:124`）亦同，未携带状态时一律落上述默认 RECORDED。
- 创建时显式携带 `registrationStatus` 的语义（`existing == null`）：**直接标定为该状态，可直达 QUALIFIED**（回归测试 `ensureAdministeredItem_create_explicitStatus_setsInitialStateDirectly` 即此语义）——可绕过生命周期入口，与标准冲突。
- 该显式标定功能的原始设计意图是 APPLICATION / HISTORICAL 两个文档态（代码注释："显式初始状态标定（含 APPLICATION/HISTORICAL）仅限真正新建"）。
- 既有条目经表单改状态一律拒绝（1124）；与当前值相同则放行（表单回显兼容）。
- M2 遗留路径：既有但无 RegistrationState 的条目，先落默认初始状态，再按上述规则比对。
- 前端创建表单**不携带** `registrationStatus`（已核实），i18n 已有 `registrationStatus.INCOMPLETE`（不完整）键。
- 唯一另一处初始状态创建点：`GlobalNamespaceService.wrapperOf`（命名空间的系统内部簿记包装物，不关联注册机构、不参与生命周期推进）。

## 2. 候选方案对比与决策

| 方案 | 内容 | 结论 |
|---|---|---|
| **A（选定）** | 新建默认 INCOMPLETE；显式标定收窄为 APPLICATION / HISTORICAL（+ INCOMPLETE 幂等）；其余一律从 INCOMPLETE 起步 | ✅ 用户 2026-09-21 选定 |
| B | 彻底统一：创建时忽略/拒绝 `registrationStatus`，一律 INCOMPLETE | 废除有设计依据的文档态标定功能，破坏面最大 |
| C | 只改默认值，显式标定完全保留（仍可直建 QUALIFIED） | "统一从 Incomplete 开始"名不副实，与标准入口语义冲突 |

**方案 A**。理由：既纠正标准入口语义，又保留 APPLICATION/HISTORICAL 标定这一有原始设计意图的合法能力；RECORDED 及以上只能经推进流程获得，生命周期治理闭环。

### 2.1 随案一并裁定（用户未异议）

- **联邦并入条目**：同样落 INCOMPLETE（目的注册机构视角下即新提交项，语义正确，不特判、不改 `GenericItemCreator` 代码）。
- **`GlobalNamespaceService.wrapperOf`**：保持 RECORDED 不动——系统内部簿记对象，非注册生命周期参与者。
- **存量数据**：不迁移，已有条目状态保持原样。
- **版本控制行为**：标准中 Incomplete 不纳入版本控制，本系统创建即赋版本；维持现状，记录为已知偏差（超出本 change 范围）。

## 3. 详细设计

### 3.1 `ItemAdministrationService.ensureAdministeredItem`（核心改动）

**（1）默认初始状态 RECORDED → INCOMPLETE**（第 419 行）。

**（2）显式初始状态白名单校验**：`existing == null && req.registrationStatus() != null` 时——

- 允许：`INCOMPLETE`（幂等于默认）、`APPLICATION`、`HISTORICAL`；
- 拒绝：`CANDIDATE` / `RECORDED` / `QUALIFIED` / `STANDARD` / `PREFERRED_STANDARD` / `SUPERSEDED` / `RETIRED`，抛 `BusinessException`，**新增错误码 `ApiCodes.INVALID_INITIAL_STATUS = 1125`**（1100–1199 注册生命周期段内 1125 空闲），提示语指明须经推进流程获得生命周期态。

**（3）既有条目路径不变**：`existing != null` 时维持现状——与当前状态不同 → 1124 拒绝；相同 → 放行（表单回显兼容）。M2 遗留路径语义随之变化：遗留无状态条目落默认变为 INCOMPLETE；表单携带 RECORDED 与默认 INCOMPLETE 不同 → 按既有规则拒绝（语义合理：遗留条目不能经编辑表单直接变成 Recorded），相关测试更新。

**（4）执行顺序**：先落默认 INCOMPLETE → 显式状态非空时按上述白名单校验/设置。不改变方法其余流程（所有权、版本、标识等）。

### 3.2 `ItemRegistrationRequest`（注释同步）

`registrationStatus` 字段注释更新为：NULL = 不变更（创建落默认 INCOMPLETE）；显式值仅限 APPLICATION / HISTORICAL（ISO/IEC 11179-6 文档态入口标定，初始状态标定仅限真正新建）。

### 3.3 `ApiCodes`

新增 `INVALID_INITIAL_STATUS = 1125`，置于 1100–1199 注册生命周期错误段。

### 3.4 测试更新

**`ItemAdministrationServiceTest`**：
- `ensureAdministeredItem_update_legacyItemWithoutState_statusRejected`：断言由"落默认 RECORDED"改为 INCOMPLETE；
- `ensureAdministeredItem_update_legacyItemWithoutState_sameAsDefault_ok`：携带值由 RECORDED 改为 INCOMPLETE（与默认相同放行）；
- `ensureAdministeredItem_create_explicitStatus_setsInitialStateDirectly`（原期望 QUALIFIED 直建）：改为期望抛 `INVALID_INITIAL_STATUS`；
- `ensureAdministeredItem_create_explicitStatus_stillSetsInitialState`（APPLICATION 直建）：保留通过；
- 新增：`create` 默认落 INCOMPLETE（无显式状态）；显式 CANDIDATE / RECORDED 被拒；显式 INCOMPLETE 幂等；显式 HISTORICAL 直建。

**其余测试逐一核对**（此前 grep 命中 RECORDED 断言）：`RegistrationProcessServiceTest`、`RegistrationLifecycleServiceTest`、`RegistrationStatusMigrationTest`、`DataDictionaryBrowserControllerTest`、`ItemMappingCrudServiceTest` 及 web 层其他用到"创建即 RECORDED"假设的用例。

**前端 e2e / spec**：核对 `metadata-registry-ui/e2e` 与 `*.spec.ts` 是否有"创建后状态 = 已记录"断言。

### 3.5 前端

- 列表/详情状态列改显"不完整"标签（i18n 键已存在，无需改动）；
- 创建表单不携带 `registrationStatus`（维持现状），创建成功后用户经"注册项列表 → 推进流程"晋升（该 UI 已存在）；
- 本 change 不加新增 UI 提示文案，列为可选后续。

### 3.6 明确不改动

- `RegistrationLifecycleService` 状态机、角色矩阵、`DEFAULT_APPROVAL_TRANSITIONS`（10 处审核点）不变；
- `RegistrationStatus` 枚举及其标准注释（生命周期图本就正确）不变；
- `GlobalNamespaceService.wrapperOf` 保持 RECORDED；
- 无数据库 schema 变更、无迁移脚本（SQLite / SQL Server / 达梦均不涉及）。

## 4. 验证计划

1. 定向：`mvn -pl metadata-registry-web test -Dtest=ItemAdministrationServiceTest` 全绿；
2. 全量：`mvn -pl metadata-registry-web test`（重点核对 3.4 列出的测试类）；
3. 前端：`metadata-registry-ui` build + 相关 spec；
4. 手工冒烟（后端本地起服务）：POST 新建数据元（不带状态）→ 状态 INCOMPLETE；携带 APPLICATION → APPLICATION；携带 RECORDED / QUALIFIED → 业务错误 1125；经推进流程 INCOMPLETE→CANDIDATE→RECORDED 走通；
5. 提交前 `detect-changes --scope all` 确认无 partial / truncated；
6. 核对 `docs/` 与 `README.md` 是否有"创建默认已记录"表述需同步。

## 5. 风险与遗留

- 依赖"创建即 RECORDED"的外部脚本/文档：实现时全文检索文档与脚本，列入同步清单；
- 联邦并入条目落 INCOMPLETE 后，目的方需自行推进——语义变更已在 2.1 裁定，需在变更说明中告知；
- 已知偏差（不处理）：Incomplete 标准语义不纳入版本控制，本系统创建即赋版本。
