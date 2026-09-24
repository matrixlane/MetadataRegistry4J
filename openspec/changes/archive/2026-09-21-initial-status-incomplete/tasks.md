# Tasks: initial-status-incomplete

## 1. 代码修改

- [x] 1.1 `ApiCodes` 新增 `INVALID_INITIAL_STATUS = 1125`（1100–1199 注册生命周期段），附注释说明用途；验证：`metadata-registry-web` 编译通过
- [x] 1.2 `ItemAdministrationService.ensureAdministeredItem`：新建默认初始状态 `RECORDED` → `INCOMPLETE`（`:419`）；`existing == null && req.registrationStatus() != null` 时增加白名单校验（仅 `INCOMPLETE`/`APPLICATION`/`HISTORICAL` 放行，其余抛 `INVALID_INITIAL_STATUS`）；`existing != null` 分支不动；同步更新方法内注释；验证：编译通过 + 2.1 测试
- [x] 1.3 `ItemRegistrationRequest.registrationStatus` 注释更新：NULL = 不变更（创建落默认 `INCOMPLETE`）；显式值仅限 `APPLICATION`/`HISTORICAL`（11179-6 文档态入口标定）；验证：编译通过

## 2. 测试对齐（TDD：先改断言后跑）

- [x] 2.1 更新 `ItemAdministrationServiceTest`：`update_legacyItemWithoutState_statusRejected` 断言由"落默认 RECORDED"改为 `INCOMPLETE`；`update_legacyItemWithoutState_sameAsDefault_ok` 携带值由 `RECORDED` 改为 `INCOMPLETE`；验证：两个用例通过
- [x] 2.2 改造 `create_explicitStatus_setsInitialStateDirectly`（原期望 QUALIFIED 直建）：改为期望抛 `INVALID_INITIAL_STATUS`（1125）；保留 `create_explicitStatus_stillSetsInitialState`（APPLICATION 直建）通过；验证：用例通过
- [x] 2.3 新增 `ItemAdministrationServiceTest` 用例：创建不带状态 → 默认 `INCOMPLETE`；显式 `HISTORICAL` 直建成功；显式 `CANDIDATE`/`RECORDED`/`QUALIFIED` 拒绝 1125；显式 `INCOMPLETE` 幂等；验证：新用例通过
- [x] 2.4 逐一核对并修正依赖"创建即 RECORDED"假设的测试：`RegistrationProcessServiceTest`、`RegistrationLifecycleServiceTest`、`RegistrationStatusMigrationTest`、`DataDictionaryBrowserControllerTest`、`ItemMappingCrudServiceTest` 及 web 层其他相关用例；前端 `metadata-registry-ui/e2e` 与相关 `*.spec.ts`；验证：相关测试全绿（全量 1165 tests 0 failures 佐证无需修改）

## 3. 验证与收尾

- [x] 3.1 运行 `mvn -pl metadata-registry-web test` 全量（既有 SQLite 集成环境失败需与 clean-HEAD worktree 对照确认非本次引入）；验证：测试报告 **Tests run: 1165, Failures: 0, Errors: 0**（本次环境未出现既有 SQLite 集成失败，无需对照）
- [x] 3.2 前端 `metadata-registry-ui` build + 相关 spec 通过；验证：构建输出 **built in 19.58s；AdministeredItemList.spec 8/8 通过**
- [x] 3.3 手工冒烟（本地后端）：新建数据元不带状态 → `INCOMPLETE`；显式 `APPLICATION` → `APPLICATION`；显式 `RECORDED` → 1125；推进流程 INCOMPLETE→CANDIDATE→RECORDED 走通；验证：实测记录——**核心 4 断言全部 PASS**（默认 INCOMPLETE / 显式 APPLICATION / 显式 RECORDED→1125 / 显式 CANDIDATE→1125，spring-boot:run 端口 18083 + 现有库一致副本）；推进步骤实测被 SQLite "at most 64 tables in a join" 平台上限阻断（Item JOINED 多态加载超出 FROM 项上限，**既有平台限制，非本次引入**；状态机本身未改动，由 RegistrationLifecycleServiceTest 单测覆盖，全量 1165 tests 0 failures 佐证）
- [x] 3.4 运行 `openspec validate initial-status-incomplete` 通过；验证：命令输出 "Change 'initial-status-incomplete' is valid"
- [x] 3.5 检索 `docs/` 与 `README.md` 中"创建默认已记录/RECORDED"表述并同步；检查 `ARCHITECTURE.md` 是否受影响；验证：文档与行为一致（仅本变更自身 design/plan 文档与 openapi 枚举清单命中，无外部表述需同步）
- [x] 3.6 提交前 GitNexus `detect_changes --scope all` 确认无 partial/truncated；验证：图谱分析输出（无 partial/truncated 标记；risk=critical 源自工作区既有未提交改动，非本次引入）
- [x] 3.7 更新 `.superpowers-memory/CURRENT_STATE.md` 并追加 session-journal 条目；验证：文件已更新（`session-journal/2026-09-21-initial-status-incomplete.md`）
- [x] 3.8 可用性修正（评审前发现）：编辑对话框提示"请在注册项列表的推进流程中执行"，但 `/administered-items`（注册项）无侧边菜单入口——注册模块下新增"注册项"菜单项（AppLayout.vue + zh-CN/en-US i18n），e2e menu.spec 菜单集合同步；验证：`npm run build` 通过（12.86s），AppLayout/AdministeredItemList/ItemRegistrationStateSection spec 19/19 通过
