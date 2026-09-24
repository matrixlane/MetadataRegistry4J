# 注册流程（Registration Process）设计文档

日期：2026-09-18
状态：待用户确认
OpenSpec 变更名（拟定）：`registration-process-designer`

## 1. 背景与已确认决策

注册模块需要一个"注册流程"能力：以 ISO/IEC 11179 的 Registration_Status 状态机为骨架，向所有用户展示标准规定的注册流程（教学目的），并让状态迁移从"下拉菜单直接设置"升级为"按流程规则执行（含审核点）"。

本设计基于用户 2026-09-18 确认的六项决策：

| # | 决策 |
|---|---|
| 1 | 允许迁移集原以现行代码 `ALLOWED_TRANSITIONS` 为准；**2026-09-18 经 Annex D.2 标准核验纠正为 20 条**（原 Incomplete/Candidate→Retired 等 6 条与 2023 版不符，见 §2.5 纠正记录）；Application 与 Historical 是**第一状态**（可作为初始状态标定） |
| 2 | **取消**基于自定义 Role 的流程定制（推迟，未来聚焦 `administrative_status` 字段）；本期只实现标准（重点 11179-6）已规范的流程 |
| 3 | 迁移 A→B 需要审核时：审核人是 Registrar（可指定若干或全部，默认全部）；通过规则支持"任一人通过"或"至少 N 人通过"；是否需要审核以 11179-6 为准，未定义则默认无需审核；**审核只有"批准"一个操作，无驳回、无发起动作** |
| 4 | 当前一个应用 = 一个注册表 = 一个注册机构；但数据模型按"每个 RA 一套流程"设计，为将来每 RA 定制留好结构 |
| 5 | 注册流程页本期**只读展示**（训练用户理解流程）；流程执行（含审核）发生在 Administered Item 的编辑/操作中；待审项目列表 = 注册项列表按状态过滤 |
| 6 | 设计器技术选型按推荐：**Vue Flow** |
| 7 | 2026-09-19：**CANDIDATE→RECORDED 纳入默认审核点**（治理加严，超出 11179-6 4.4 d) 最低要求）；规则即数据，由种子调和（字段漂移归位）收敛到既有库 |

## 2. 标准依据（不可变部分）

以 PDF 原文核验为准（Milvus 向量库无法生成查询向量，已按 AGENTS.md 回退到 `ISO/IEC 11179 标准原文目录` 源文件）：

### 2.1 状态集（ISO/IEC 11179-3:2023, 9.6.2, Table 30）

10 个状态，代码枚举 `RegistrationStatus` 已与之逐字对齐（2026-09-01 标准符合性整改）：
INCOMPLETE、CANDIDATE、RECORDED、QUALIFIED、STANDARD、PREFERRED_STANDARD、SUPERSEDED、RETIRED、HISTORICAL、APPLICATION。

### 2.2 状态分类（ISO/IEC 11179-6:2023, 4.3.2 Table 1）

- **生命周期状态**（可推进）：Incomplete → Candidate → Recorded → Qualified → Standard → Preferred Standard；
- **文档状态**（不推进）：Historical、Application —— 按决策 1，两者可作为**初始状态**标定。

### 2.3 标准强制的审核点（11179-6:2023, 4.4 d)）

> Progression of administered items to registration status of "Qualified" or higher **shall require** the sponsorship of a steward and **approval of the registration authority**.

即：目标状态为 QUALIFIED、STANDARD、PREFERRED_STANDARD 的迁移，**默认必须审核**（注册机构批准 → 其 Registrar 代行）。其余迁移标准未定义审核要求，按决策 3 默认无需审核。

### 2.4 其他规范约束（已在代码中存在，保持）

- Recorded 及以上：强制属性/关联约束强制生效（11179-3:2023, 4.5；代码 `validateMandatoryAssociations`）；
- 状态历史链式保存（Registration_State.previous_state，Table 22）；
- 悬停释义文本来源：Table 30（11179-3）+ 4.3.3 详述（11179-6），中文由项目翻译，英文用标准原文。

### 2.5 本期移植的迁移白名单（Annex D.2 核验版）

> **2026-09-18 标准核验纠正**：按 ISO/IEC 11179-6:2023 **Annex D.2.2–D.2.11** 逐状态
> Possible progression 穷举复核，原决策 1 中 Incomplete/Candidate→Retired、
> Application→Recorded/Retired、Superseded→Historical、Retired→Historical 与 2023 版
> 不符，已纠正为下表（旁证 4.3.3.1.8/4.3.3.1.9：Superseded/Retired 只能从 Recorded
> 及以上进入；RETIRED 与 HISTORICAL 无出边）。规则表种子由 `ensureSeed` 调和收敛
> （删除标准集外旧种子、插入缺失项、定制规则不动）。

| From | To | 默认需审核 |
|---|---|---|
| INCOMPLETE | CANDIDATE / HISTORICAL / APPLICATION | 否 |
| CANDIDATE | RECORDED | **是**（治理加严，2026-09-19 用户决策） |
| CANDIDATE | HISTORICAL / APPLICATION | 否 |
| APPLICATION | CANDIDATE | 否 |
| RECORDED | QUALIFIED | **是**（4.4 d)） |
| RECORDED | RETIRED / SUPERSEDED | 否 |
| QUALIFIED | STANDARD | **是**（4.4 d)） |
| QUALIFIED | RETIRED / SUPERSEDED | 否 |
| STANDARD | PREFERRED_STANDARD | **是**（4.4 d)） |
| STANDARD | RETIRED / SUPERSEDED | 否 |
| PREFERRED_STANDARD | STANDARD / RETIRED / SUPERSEDED | 否 |
| SUPERSEDED | RETIRED | 否 |
| RETIRED | （无出边） | — |
| HISTORICAL | （无出边） | — |

共 **20** 条迁移；默认审核点共 **四处**：→QUALIFIED / →STANDARD / →PREFERRED_STANDARD（依 4.4 d)）+ CANDIDATE→RECORDED（**2026-09-19 用户决策**：本注册表治理加严，进入 Recorded 除强制属性完备外须经 Registrar 人工复核批准；超出标准最低要求，规则即数据，由 `ensureSeed` 调和收敛到既有库）。

## 3. 方案对比

### 方案 A（推荐）：持久化流程规则 + 审批累积模型

- 迁移规则入库（`registration_transition_rule`，按 RA 作用域），标准默认值作为种子数据；审批记录入库（`registration_transition_approval`），达到 quorum 自动生效迁移。
- 优点：规则是数据不是代码，决策 4（每 RA 一套）与未来的自定义 UI 直接落在现成结构上；"不可变底线"由种子数据 + 校验表达，不靠硬编码。
- 缺点：比硬编码多两张表与一层服务。

### 方案 B：代码常量表达标准流程 + 仅审批记录表

- 迁移集与审核点继续硬编码，只把"谁批准了哪次迁移"入库。
- 优点：表最少。缺点：重蹈现有静态矩阵覆辙；决策 4 的每 RA 定制与将来的自定义都要再次重构；流程页需要把代码常量再序列化一份给前端。

### 方案 C：引入 BPMN 引擎（Flowable 等）

- 重量级通用工作流。与标准定义的简单状态机不匹配，引入大量概念与依赖，否决。

**推荐方案 A。**

## 4. 数据模型（方案 A）

### 4.1 `registration_transition_rule`（流程迁移规则，按 RA 作用域）

| 列 | 类型 | 说明 |
|---|---|---|
| id | UUID/BLOB PK | |
| registration_authority_id | FK→registration_authority | 决策 4：每 RA 一套；本期种子为当前唯一 RA |
| from_status / to_status | varchar | RegistrationStatus 枚举名 |
| display_name | varchar null | 操作命名（决策 3 的"提议"等；本期默认空=用标准迁移名） |
| requires_approval | boolean | 默认按 §2.5 |
| approver_mode | varchar | `ALL_REGISTRARS`（默认）/ `SELECTED_REGISTRARS` |
| quorum_type | varchar | `ANY_ONE`（默认）/ `AT_LEAST` |
| quorum_count | int null | `AT_LEAST` 时的人数 |
| enabled | boolean | 默认 true；false 可禁用某条迁移（未来定制） |

唯一约束：`(registration_authority_id, from_status, to_status)`。
子表 `registration_transition_rule_approver(rule_id, registrar_id)`：`SELECTED_REGISTRARS` 时的指定审核人集合（JPA ElementCollection）。

### 4.2 `registration_transition_approval`（审批记录）

| 列 | 说明 |
|---|---|
| id PK | |
| administered_item_id | FK→administered_item |
| from_status / to_status | 针对哪次迁移 |
| registrar_id | 批准人（Registrar 身份） |
| account_id | 操作账号 |
| approved_at | 时间 |

唯一约束：`(administered_item_id, from_status, to_status, registrar_id)`（同一 Registrar 对同一迁移只计一次）。

**生效规则**：某 (item, from, to) 的 distinct 批准人数 ≥ quorum 时，事务内执行状态迁移（新建 RegistrationState 并链接 previous_state），随后清除该 (item, from, to) 的全部审批记录；item 状态发生任何变化时清除其全部未生效审批（防陈旧）。

### 4.3 迁移脚本

按仓库最近惯例三方言同名同版各一份（`db/migration/{sqlserver,sqlite,dm}/V20260918<seq>__Registration_process.sql`）：

- sqlite：**必须**（personal profile `ddl-auto: none`，唯一建表来源），沿用现有 sqlite 脚本风格（blob 主键、枚举 check 约束）；
- sqlserver：完整 DDL（T-SQL）；
- dm：可移植 DDL（仅 CREATE TABLE/索引，Hibernate 可表达时按政策也可留说明性 no-op，但为一致起见写 portable 版本）。

种子数据不由迁移写入，而在应用层 `RegistrationProcessService.ensureSeed(raId)` 完成（注册机构创建/应用启动时惰性播种），便于联邦新增 RA 时复用。

## 5. 后端服务与 API

### 5.1 新服务 `RegistrationProcessService`（web 模块）

- `getProcess(authorityId)`：规则 + 状态元数据；
- `ensureSeed(authorityId)`：幂等播种 §2.5 默认规则；
- `availableTransitions(itemId, account)`：返回每个可达迁移的执行视图（是否需要审核、已批准人数、quorum、当前用户可否直接执行/可否批准、是否已批准过）；
- `execute(itemId, to, note, account)`：无需审核迁移的直接执行（保留现有角色矩阵 ADMIN/REGISTRAR 与强制属性校验）；
- `approve(itemId, to, account)`：需审核迁移的批准（校验当前活动 Registrar 身份属于该项 RA 且在审核人集合内；达到 quorum 则迁移生效）。

`RegistrationLifecycleService` 保留状态机底层能力（状态写入、历史链接、强制校验），白名单来源从静态 Map 切换为规则表查询（缓存）。

### 5.2 REST 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/registration-process` | 当前 RA 的完整流程定义（状态 + 迁移 + 审核配置），登录即可读 |
| GET | `/api/v1/administered-items/{id}/transitions` | 该项当前可用的迁移动作视图（含审批进度与当前用户权限） |
| POST | `/api/v1/administered-items/{id}/transitions/execute` | 直接执行无需审核的迁移 `{to, note}` |
| POST | `/api/v1/administered-items/{id}/transitions/approve` | 批准需审核的迁移 `{to}`；达到 quorum 自动生效 |

旧端点 `POST /administered-items/{id}/transition` **移除**：前端唯一调用点（AdministeredItemList.vue:93）改用新端点；`ItemAdministrationService:414-423` 的编辑路径改为拒绝状态字段变更（状态不再经表单下拉修改，见 §6.3），或内部委托 `execute/approve`（实现时取其一并在 tasks 中固定）。

## 6. 前端设计

### 6.1 注册流程页（新建 `views/registration-process/RegistrationProcessView.vue`，路由 `/registration-process`）

- **Vue Flow 只读流程图**（`nodes-draggable=false`、`nodes-connectable=false`，新增依赖 `@vue-flow/core`，Vue3 兼容已确认）：
  - 10 个状态节点，按生命周期主链 + 文档状态侧链布局；
  - 边 = 允许迁移；需审核的边带醒目标记（如鎖/印章图标 + 不同线型）；
  - **节点悬停 Tooltip**：该状态的中文释义 + 标准英文原文（Table 30 + 11179-6 4.3.3 对应小节），作为学习渠道；
  - 点击边 → 抽屉展示该迁移的规则细节（是否需审核、审核人模式、通过人数、操作名）与标准依据条文摘要；
  - 图例：生命周期状态 vs 文档状态、审核边 vs 直接边、Recorded 约束强制阈值说明。
- 页面对所有登录用户只读开放（本期无编辑模式；编辑模式为未来自定义阶段，仅管理员）。
- 状态释义文案放前端 i18n（`registrationProcess.statusDesc.*` / `transitionHint.*`），中文为项目译文、英文为标准原文摘录。

### 6.2 注册项列表页（`AdministeredItemList.vue` 改造）

- 顶部加**注册状态过滤**下拉（后端 `status` 参数已存在；前端包一层 fetchFn + 扩展 PageQuery），"待审项目"即按 QUALIFIED 前置状态等过滤；
- "状态迁移"按钮改为**"推进流程"面板**：列出当前状态的可达迁移，每条显示操作名/目标状态/是否需要审核/审批进度（已批准 x / 需 y）：
  - 无需审核 → `执行`按钮（现行权限：ADMIN/REGISTRAR）；
  - 需要审核且当前用户是合格审核 Registrar → `批准`按钮（已批准过则置灰显示"已批准"）；
  - 无权限用户只读查看进度。

### 6.3 编辑表单中的状态字段（`ItemRegistrationStateSection.vue`）

状态下拉改为**只读展示**（当前状态 + 生效日期 + 历史链），并提示"状态迁移请在注册项列表的推进流程中执行"——落实"不再用下拉菜单直接设置状态"。

### 6.4 菜单与路由

- 注册模块菜单：`注册机构 / 注册规范 / 注册人员 / 注册流程 / 注册项`（新增"注册项"→`/administered-items`；"注册流程"改指 `/registration-process`）；
- 同步 `router/index.ts`（新路由 + routeTable 登记）、`zh-CN.json`/`en-US.json`（`nav.administeredItems` 已存在=注册项，复用）、`e2e/menu.spec.ts`（注册模块子项断言更新）。

## 7. 权限模型（本期）

| 能力 | 权限 |
|---|---|
| 查看流程页 | 所有登录用户 |
| 直接执行无需审核的迁移 | 沿用现有矩阵：ADMIN / REGISTRAR |
| 批准需审核的迁移 | 持有该项 RA 的**活动 Registrar 身份**，且在规则审核人集合内（默认全部 Registrar） |
| 修改流程定义 | **本期不提供**（未来自定义阶段，仅 SUPER_ADMIN / RA 管理员） |

## 8. 测试计划

- 后端：`RegistrationProcessServiceTest`（种子幂等、quorum 边界 ANY_ONE/AT_LEAST、批准去重、生效后清理、状态变化清陈旧审批、越权批准 403）；`RegistrationLifecycleService` 既有测试适配规则表数据源；
- 前端 vitest：推进流程面板（权限分支、进度展示、批准置灰）、流程页渲染（节点/边/Tooltip 内容）、列表状态过滤；
- e2e：`menu.spec.ts` 注册模块顺序更新；新增 `registration-process.spec.ts`（mock API：流程页渲染、节点悬停释义、批准按钮路径）。

## 9. 演进路线（本期明确不做）

1. 流程定义的管理员编辑 UI（自定义 display_name、审核人集合、quorum 的可视化配置）；
2. 基于自定义 Role / `administrative_status` 的流程定制（决策 2 推迟项）；
3. 每 RA 独立定制的管理界面（结构已按 RA 作用域预留）；
4. 审批通知（可复用既有 `notification_record` 设施，后续迭代）。

## 10. 影响面

- 后端：新增 2 实体 + 1 服务 + 1 控制器；`RegistrationLifecycleService` 白名单数据源切换；`ItemAdministrationService` 编辑路径行为变更；
- 前端：新增 1 页面 + 1 依赖（@vue-flow/core）；AdministeredItemList / ItemRegistrationStateSection 改造；菜单注册模块增 1 项；
- DB：三方言迁移各 1 份；
- 文档：README 页面清单、openspec/specs/standardize-menu-layout（注册模块增"注册项"）、ARCHITECTURE.md 由 GitNexus 重新生成（如内容变化大）。
