# Tasks: registration-process-designer

依据：`docs/superpowers/specs/2026-09-18-registration-process-design.md`（已确认）+ 本变更 `proposal.md` / `design.md` / `specs/`。

## 1. 后端：数据模型与迁移

- [x] 1.1 新建 `RegistrationTransitionRule` 实体（含 `registration_transition_rule_approver` 子表 ElementCollection）与 `RegistrationTransitionApproval` 实体，归属 `commonfacilities/registration` 包；验证：`mvn -pl metadata-registry-common-facilities -am compile` 通过，默认 profile（ddl-auto=update）启动可自动建表
- [x] 1.2 编写三方言迁移脚本 `db/migration/{sqlserver,sqlite,dm}/V20260918<seq>__Registration_process.sql`（纯 CREATE TABLE/索引可移植语法；sqlite 遵循 blob 主键 + check 约束风格）；验证：personal profile（SQLite，ddl-auto:none + Flyway）启动成功且两表存在
- [x] 1.3 实现 `RegistrationProcessService.ensureSeed(authorityId)`：按 §2.5 迁移集调和式播种默认规则（四处默认审核点 CANDIDATE→RECORDED / RECORDED→QUALIFIED / QUALIFIED→STANDARD / STANDARD→PREFERRED_STANDARD，审核人默认全部 Registrar、任一人通过；调和含删多余种子、插缺失、字段漂移归位、保留定制规则），并在应用启动与注册机构创建路径接入；验证：新增单测证明幂等、调和与定制保留语义

## 2. 后端：迁移执行引擎

- [x] 2.1 `RegistrationLifecycleService` 迁移白名单数据源从静态 `ALLOWED_TRANSITIONS` Map 切换为规则表查询（含缓存）；Recorded+ 强制校验（`validateMandatoryAssociations`）保留；验证：既有 `RegistrationLifecycleService` 相关测试适配后全部通过
- [x] 2.2 实现 `availableTransitions(itemId, account)`（每条迁移：目标状态、操作名、是否需审核、审批进度、当前用户可否执行/批准、是否已批准）与 `GET /api/v1/administered-items/{id}/transitions`；验证：单测覆盖各权限分支
- [x] 2.3 实现 `execute`（无需审核迁移直接执行，沿用 ADMIN/REGISTRAR 矩阵）与 `POST …/transitions/execute`；验证：单测覆盖直接执行、越权 403、底线外迁移拒绝
- [x] 2.4 实现 `approve`（合格 Registrar 判定、批准去重、quorum 达到即同事务生效并清除该迁移审批、状态变化清除陈旧审批、生效校验失败整体回滚）与 `POST …/transitions/approve`；验证：单测覆盖 quorum 边界（ANY_ONE/AT_LEAST）、重复批准、陈旧清理、回滚
- [x] 2.5 实现 `GET /api/v1/registration-process`（当前 RA 的完整流程定义，登录可读）；验证：单测/集成测试通过
- [x] 2.6 移除旧端点 `POST /administered-items/{id}/transition`；`ItemAdministrationService` 更新路径拒绝/忽略注册状态字段变更；验证：后端全量 `mvn test` 通过（含受影响的既有集成测试适配）

## 3. 前端：注册流程展示页

- [x] 3.1 引入依赖 `@vue-flow/core`；验证：`npm install` 与 `npm run build`（含 vue-tsc）通过
- [x] 3.2 新建 `views/registration-process/RegistrationProcessView.vue`：只读 Vue Flow 流程图（10 状态节点 + 迁移边，需审核边有标记）、节点悬停释义 Tooltip（Table 30 + 11179-6 §4.3.3，中文译文 + 英文原文，i18n 键 `registrationProcess.*`）、边点击详情（审核配置 + 标准依据）、图例；验证：新增 vitest 组件测试通过，页面登录可见且无编辑入口

## 4. 前端：注册项执行界面

- [x] 4.1 改造 `AdministeredItemList.vue`：状态迁移下拉改为"推进流程"面板（逐迁移展示操作名/进度/执行或批准按钮，批准后置灰），列表顶部加注册状态过滤下拉（包一层 fetchFn，后端 `status` 参数已存在）；验证：vitest 覆盖权限分支与进度展示
- [x] 4.2 `ItemRegistrationStateSection.vue` 注册状态字段改只读展示并提示迁移入口；验证：受影响的既有 vitest（ItemFormDialog/ItemRegistrationSection 等）适配后通过

## 5. 菜单与路由

- [x] 5.1 新增 `/registration-process` 路由与 routeTable 登记；"注册流程"菜单改指新页；注册模块新增"注册项"（`/administered-items`，复用既有 `nav.administeredItems` 键）；验证：`e2e/menu.spec.ts` 注册模块断言更新后 `npx playwright test e2e/menu.spec.ts` 通过

## 6. 端到端验证与文档

- [x] 6.1 新增 `e2e/registration-process.spec.ts`（mock API：流程图渲染、悬停释义、批准按钮路径、状态过滤）；验证：该套件通过
- [x] 6.2 全量验证：`mvn test`（后端）、`npx vitest run`、`npm run build`、`npx eslint`（0 error）、受影响 e2e 套件全部通过
- [x] 6.3 文档同步：README 前端页面清单（注册流程页 + 注册项）、ARCHITECTURE.md 视变化重新生成、`.superpowers-memory/` 更新；验证：文档与实现一致
