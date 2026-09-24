# 2026-09-21 新建管理项初始状态统一从 Incomplete 开始（initial-status-incomplete）

## 做了什么

- 需求缘起：用户发现新建数据元注册状态默认即"已记录"，要求查标准并统一从 Incomplete 起步。经本地 PDF（`ISO/IEC 11179 标准原文目录\ISO_IEC 11179-6-2023.pdf`）逐条核实：4.3.2 Table 1 + 4.3.3.1.2（Incomplete=生命周期入口态，最低元数据仅标识符+提交者信息，不入版本控制）/4.3.3.1.4（Recorded=全部强制属性完成并经确认）。**Milvus MCP 本会话未加载**，按 AGENTS.md 回退到本地 PDF（服务 :9091 健康、集合为 AnythingLLM 库，无独立 11179 集合）。
- 走 superpowers-openspec-superpowers-workflow 全流程：设计 `docs/superpowers/specs/2026-09-21-initial-status-incomplete-design.md`（用户选定方案 A：默认 INCOMPLETE + 显式标定收窄白名单）→ OpenSpec 变更 `initial-status-incomplete`（proposal/design/specs/tasks，validate 通过）→ 计划 `docs/superpowers/plans/2026-09-21-initial-status-incomplete.md` → TDD 实施。
- 实现（改动 4 文件）：
  - `ApiCodes` 新增 `INVALID_INITIAL_STATUS = 1125`（1100–1199 段下一空闲值）；
  - `ItemAdministrationService.ensureAdministeredItem`：默认初始状态 RECORDED → INCOMPLETE；新建显式 `registrationStatus` 白名单校验（仅 INCOMPLETE/APPLICATION/HISTORICAL，生命周期态一律 1125 拒绝）；既有条目 1124 守卫不变；常量 `ALLOWED_INITIAL_STATUSES`；
  - `ItemRegistrationRequest.registrationStatus` 注释同步。
  - 编辑前 GitNexus impact：`ensureAdministeredItem` 两重载 max risk MEDIUM（13 直接调用方），无 HIGH/CRITICAL。
- 测试（TDD：先改断言跑出 6 红 → 实现后全绿）：`ItemAdministrationServiceTest` M2 遗留路径断言改 INCOMPLETE、QUALIFIED 直建用例改期望 1125、新增 5 用例（默认 INCOMPLETE/显式 HISTORICAL/CANDIDATE/RECORDED 拒绝/INCOMPLETE 幂等）。
- 验证：web 全量 **1165 tests 0 failures**（本次环境未出现既有 SQLite 集成失败）；前端 build 19.58s + AdministeredItemList.spec 8/8；openspec validate 通过；detect_changes 无 partial/truncated；文档检索无外部表述需同步。

## 冒烟实测（scratch/initial-status-smoke/smoke.sh，端口 18083 + 现有库 sqlite 一致副本）

- 核心 4 断言全部 PASS：新建数据元（不带状态）→ **INCOMPLETE**；显式 APPLICATION → **APPLICATION**；显式 RECORDED → **1125**；显式 CANDIDATE → **1125**。
- 环境制备走真实 API：POST /registration-authorities（ensureSeed 自动播种 20 条迁移规则）→ 副本 SQL 改命名空间归属 → POST /registrars 绑定 admin（userId=account id）→ 重新登录。
- 推进步骤（INCOMPLETE→CANDIDATE→RECORDED）实测被 **SQLite "at most 64 tables in a join"** 阻断：Item JOINED 多态加载连 100+ 子类表超出 SQLite FROM 项上限（transitions/execute 500）。**既有平台限制，非本次引入**；状态机未改动，由 RegistrationLifecycleServiceTest 单测覆盖。

## 当日续：注册项菜单入口补挂（可用性修正）

- 用户问"注册项列表的推进流程在哪儿"——核实发现 `/administered-items`（注册项）路由已实现但**侧边菜单无入口**（注册模块下仅注册机构/注册规范/注册人员/注册流程），编辑对话框提示指向不可达页面；本次改动后所有新建条目从 不完整 起步，推进入口可达性成为闭环必需。
- 修正：AppLayout.vue 注册模块新增"注册项"菜单项（注册流程之后）；zh-CN/en-US 增加 `nav.administeredItem`；e2e menu.spec.ts 注册模块菜单集合断言同步加入"注册项"。
- 验证：`npm run build` 通过（12.86s）；AppLayout / AdministeredItemList / ItemRegistrationStateSection spec 19/19 通过。

## 状态

- 实现与验证完成，**待 `continue-review` 代码评审 → `continue-archive` 归档**。
- 2026-09-21 11:00 前后 8081 实例卡死事件（用户报"新建数据元保存一直不成功"）：跑旧代码的 8081 进程（08:48 启动）彻底无响应（连 /v3/api-docs 都挂起，与本次改动无关，疑似连接池/线程耗尽）。处置：终止卡死进程 + 18099 遗留实例（锁 jar 导致 repackage 失败）；发现 IDE 用 JDK 25 污染 target/classes（class v69，pom 要求 17）致增量打包炸——`clean package` 用 JDK 17 全量重打（fat jar 220MB 恢复）；新实例已跑新代码（健康 200，连真实 SQL Server 库）。**注意：真实库 admin 密码非 Admin@123**（SQLite 副本才是）；未用 emergency-code 验证（副作用=强制改密），最终 UI 验证留给用户。

## 重要发现（跟进）

1. **冒烟/多实例启动的正确姿势**：用户有两个 javaw 常驻 `target/*.jar`（8081 等），spring-boot repackage 会因 jar 被锁失败；多实例用 `mvn spring-boot:run` + **`SPRING_APPLICATION_JSON` 环境变量**（优先级仅次于命令行；`-Dspring-boot.run.arguments` 逗号串在 Windows Git Bash 会被当成单值、`jvmArguments` 带空格传参会被 JVM 当成单 token）。用户主目录有导入配置显式设置 SQL Server 数据源，会整体遮蔽 application.yml 的 `${DB_URL:...}` 占位行——env 占位符方式对其无效。
2. **Git Bash 经 `$(...)` 传中文 JSON 会坏 UTF-8**（400 请求体格式错误）；冒烟载荷用纯 ASCII。
3. 创建 Administered_Item 的完整前置链（active-registrar-ownership）：账号需 Registrar 身份（contact_account_binding→registrar→RA）；命名空间需归属该 RA（API 仅有 InitWizard 设置归属，冒烟用副本 SQL UPDATE）；指称/定义需 languageId + 上下文或 isolatedException；命名空间有命名规范时 designation 值须匹配 charset/regex。
4. **SQLite 64 表 join 上限会击穿推进类端点**（transitions/execute 500）：Item JOINED 继承 + EAGER 关联在多态加载时连 100+ 表。建议录入 KNOWN_FAILURES；SQL Server 无此限制。
5. `metadata_registry.db`（SQLite）是旧开发残留：无注册机构、无 Registrar、29 个命名空间全为外部——真实数据在用户主目录导入配置指向的 SQL Server 库。
