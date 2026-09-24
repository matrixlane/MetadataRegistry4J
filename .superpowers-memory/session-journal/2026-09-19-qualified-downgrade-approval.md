# 2026-09-19 Qualified+ 降级审核前置（qualified-downgrade-approval）

## 做了什么

- 需求缘起：核查"流向 Retired/Superseded 是否都需 Registrar 事先审核"时发现既有实现与标准不符。经 Milvus 检索 + 本地 PDF（`ISO/IEC 11179 标准原文目录\ISO_IEC 11179-6-2023.pdf`）原文核验：
  - 4.4 d)（第 9 页）："Progression … to registration status of 'Qualified' or higher shall require the sponsorship of a steward and approval of the registration authority."（Milvus 分块截断处由 PDF 补全）
  - D.4.2/D.4.3（第 35 页）：Qualified 及以上 → Retired/Superseded "changed … by the registrar … after presentation to the control committee"；Recorded 级 "without review by the control committee" 随时可改。
  - D.2.9：SUPERSEDED→RETIRED 走 Provisionally Retired，未获 control committee 批准则保留 Superseded。
- 走 superpowers-openspec-superpowers-workflow：设计 `docs/superpowers/specs/2026-09-19-qualified-downgrade-approval-design.md`、OpenSpec 变更 `qualified-downgrade-approval`（proposal/design/specs/tasks，validate 通过）、计划 `docs/superpowers/plans/2026-09-19-qualified-downgrade-approval.md`。
- 实现：`DEFAULT_APPROVAL_TRANSITIONS` 4→11 处（新增 7 条 Qualified+ 降级审核迁移）；`ensureSeed` 调和自动收敛旧库规则；直推拒绝 1120；`→SUPERSEDED` 由"ADMIN 直推"改为"Registrar quorum 批准生效"（quorum ≈ control committee，批准生效路径跳过角色矩阵为既有设计）。
- 测试：RegistrationLifecycleServiceTest / RegistrationProcessServiceTest 更新 + 新增（ensureSeed 11 处断言、M1 扩展、availableTransitions 降级视图、execute 1120）；3 个注册流程测试类 106 tests 0 failures。

## 状态

- 实现与验证完成，**待 `continue-review` 代码评审 → `continue-archive` 归档**。
- 全量 1178 tests 中 39 errors 全部为既有 SQLite 集成测试环境问题（`database in auto-commit mode`，ApplicationContext 加载失败，11 个类），已在 clean HEAD worktree 复跑代表类（OpenApiExportTest、SegmentTemplateStrategyIntegrationTest）确认同款失败，非本次引入。VERIFICATION_BASELINE.md 已记录其中 4 个类为已知环境失败。

## 重要发现（跟进）

1. **工作区有大量用户未提交改动**（ApiCodes、RoleRepository、AdministeredItemController、删除 StateTransitionRequest.java 等，HEAD=d91fd94 不含注册流程设计器全部内容）；`git stash push -- <path>` 会打出不一致中间态（编译失败），基线对照须用 `git worktree add <dir> HEAD`，且 worktree 需手动补 `.mvn/wrapper`（未入库）。
2. 运行中的开发应用（java.exe PID 99556）占用 `target/mssql-jdbc_auth-*.dll` 导致 `copy-auth-dll` 失败：可 `mv` 改名绕开（Windows 允许重命名被占用文件），构建会落新副本。
3. `target/` 下 surefire 报告 `errors="[1-9]"` 是快速定位失败类的可靠途径，避免被 `tail` 截断漏看。
4. Milvus anythingllm 元数据块约千字截断，标准条款引用必须回 PDF 原文核对页码（pdftotext 可用）。

## 当日续：CANDIDATE→RECORDED 治理加严撤销（用户裁定"与标准对齐"）

- 缘起：用户要求核实"CANDIDATE→RECORDED 标准本身不要求批准"一句。逐条复核 4.4 c)/d)（第 9 页）、4.3.3.1.3/1.4（第 7 页）、D.2.3（第 29–30 页）、B.4.1、C.2/C.3 后确认：4.4 c) 授予 submitter/steward 晋升权（唯一条件=强制属性完备），4.4 d) 批准要求明确限定 "Qualified or higher"；D.2.3 的 steward 复审/registrar 筛查是 suggested annex 实践且非前置批准闸门。
- 用户裁定：此前治理加严要求与标准不一致，一并改正，尽所能与标准对齐。
- 修改：`DEFAULT_APPROVAL_TRANSITIONS` 移除 CANDIDATE→RECORDED，默认审核点 11→**10 处**（3 向上 + 7 降级）；spec delta 改为 REMOVED+ADDED（OpenSpec 的 MODIFIED 不允许丢弃既有 scenario）；`ensureSeed` 会把旧库该规则的 requiresApproval=true 自动归位为 false。
- 测试：3 个注册流程测试类 **107 tests 0 failures**（新增"旧库加严规则归位撤销"用例；漂移归位改用 QUALIFIED→RETIRED；approve 覆盖改挂 QUALIFIED→RETIRED 的 D.4.2 批准路径）。
- 状态：仍待 `continue-review` → `continue-archive`。

## 当日续 2：注册流程页 UI 修订 + 重新部署

- UI 文案与布局（注册流程页，`RegistrationProcessView.vue` + zh-CN/en-US）：
  ① 删除图例中三条操作提示（拖动/单击双击/悬停 1s）；② Recorded 提示语改为"Recorded 状态以前自由编辑 / Recorded 状态以后（Qualified/Standard/Preferred Standard）必须由注册机构审核通过"，并按用户要求放在图例上方（`[data-test="recorded-note"]` 独立提示条）；③ "迁移"统一改"状态迁移"（图例、抽屉标题、默认操作名、说明文案、推进流程面板 executeDialogTitle/noteLabel/defaultTransitionName 等）；④ 画布纵向 flex 填满视口（`.app-main` 为 flex:1+overflow:auto，视图根 height:100% + 卡片 flex:1），图例置底。
- 连线重叠：smoothstep offset 由固定 20 改为按目标分组扇形错开（`edgeOffset`：双向对保留 20/48 通道；顶行下行边按目标分组、组内按源 x 排序，offset=20+idx*28）。新增单测"平行下行边 path 互不相同"。
- 部署踩坑（重要）：
  ① **运行中的后端是旧构建**——代码改了但用户看到旧行为，必须 rebuild jar + restart；jar 被运行中 JVM 持锁会导致 `repackage: Unable to rename ...jar`（须先停进程再打包）。
  ② 8081 主后端用 `~/.metadata-registry/application-local.yml`（SQL Server，经 `spring.config.import` **对所有 profile 生效**）；该文件今天被并行会话加入 `flyway.schemas: mdr,ext`，导致 **personal（SQLite）profile 启动失败**：`no such table: mdr.sqlite_master`（Flyway 检查 mdr.flyway_schema_history）。修复：`application-personal.yml` 显式 `schemas: []`（profile 特定配置优先于导入配置）。
  ③ personal 后端工作目录是 `scratch/freshdb-smoke`（`--DB_URL=jdbc:sqlite:existing.db` 相对路径）；在 web 目录下启动会创建空 existing.db 然后失败。
  ④ jsdom 无 SVG getBBox：审核边 label（EdgeText）mounted 钩子的未处理 rejection 会毒化后续用例（表现为莫名其妙的 createI18n 报错）；布局类断言用免审核边规避。
- 验证：前端 vitest 全量 20 文件 195 tests 0 failures；build 通过；jar 重建（226MB，含新前端产物）；主后端 8081 重启后 SQL Server `mdr.registration_transition_rule` 实测：CANDIDATE→RECORDED requires_approval=0（ensureSeed 自动收敛生效），10 处审核点（3 向上 + 7 Qualified+ 降级）全部正确。
- 当日续 3（跨日至 09-20）：角色术语按国标 GB/T 18391.6 统一——steward=**主管者**、stewardship organization=**主管组织**、submitter=**提交组织**（依据：国标附录 C 官方译本，ictbda.com 收录）。修改 zh-CN.json 5 处（提示条、statusDesc INCOMPLETE/HISTORICAL、statusDetail INCOMPLETE/CANDIDATE）、单测与 e2e 断言；页面提示条同步按标准原文修正（此前"自由编辑/必须"表述与 D.4.1、4.4 d) 有偏差，新文案：Qualified 及以上仅原提交组织或负责主管者可编辑、修改应经控制委员会批准、推进须经主管者保荐及注册机构批准；Qualified 以下无审批门槛）。前端 195 tests 0 failures，重新部署双后端（8081/18099）正常。后端代码标识符（Steward/Stewardship 英文）保持不变。
- 当日续 4：注册流程页内容级改造（用户 6 条要求）：① 术语"状态迁移"全面改"**状态推进**"（图例/抽屉/推进流程面板，zh+en）；② statusDesc/statusDetail（中英双语）全部替换为 11179-6 §4.3.3.1.2–1.9/§4.3.3.2.2–2.3 **逐字原文**（en 为原文，zh 为直译），无引用出处标注；③ 状态抽屉新增"**内容变更要求**"章节（changeDesc，10 状态：谁可改/是否需审/发起/批准，源自 D.4.1、4.3.3.1.8/1.9），仅双击抽屉展示、悬浮层保持精简；④ 推进规则详情新增"**审核发起/审核批准**"两行（向上推进=提交组织请求+主管者保荐/注册机构批准；Qualified+ 降级=主管者与注册管理员提出/控制委员会审议后由注册管理员执行；免审核=无需审核）；⑤ 提示条改为用户指定文案"Recorded 状态以后（Qualified/Standard/Preferred Standard）：状态推进必须由注册机构批准"；⑥ 全文无引用标注。前端 196 tests 0 failures + build 通过，双后端重新部署（8081/18099）正常。
- 当日续 5：应用→候选走线修复（**边级 sourcePosition 对隐式连接点不生效**，会塌缩——须显式渲染 `<Handle>`：StatusFlowNode 增加 src-bottom/tgt-top/src-right/tgt-right 四个透明锚点，该边用 sourceHandle/targetHandle='src-right'/'tgt-right'）；去边标签"需审核"+EDGE_LANE_GAP 28→34+SUPERSEDED 扇反向弓出；图例改"无需审核的状态推进/需审核的状态推进"。
- **部署新坑（并行会话工作区）**：① web 编译依赖 item-mapping 的 `EndpointBinding`，`-pl` 单模块打包会用过期 ~/.m2 工件 → 须 `-pl metadata-registry-web -am`；② 并行会话的 `ItemMappingNetworkGraphTest` 测试源码编译不过（其重构中）→ 部署打包须 `-Dmaven.test.skip=true`；③ 其修改了已应用的 V20260920002 迁移脚本 → personal 库 Flyway checksum mismatch，已对 scratch/freshdb-smoke/existing.db 执行 checksum 修复（UPDATE flyway_schema_history）。前端 197 tests 0 failures，双后端部署正常。
- 当日续 6：连线终局方案——**全局正交走廊**：smoothstep 的水平中段恒为两端 y 中点，offset 无法分离同目标扇形线（此前"扇形 stagger"是假象）；改为全部跨行下行边（非双向对）经 buildOrthoPlan 全局分配独立水平走廊（y 按全局序、行间隙 64..220 内带宽动态收缩），源下缘/目标顶缘落点 x 按同组序错位（垂直段 x 全不同、水平段 y 全不同→任何两线不重叠）；edge.data 须保持迁移规则本体，自定义 path 存模块级 Map 经 `#edge-ortho` 插槽 BaseEdge 渲染；**拖动节点**须在 onNodesChange 应用 position 变化到 nodes 引用并重建边（否则正交 path 静态断开）。vue-tsc 在大 SFC 中对 @vue-flow/core Node 泛型联合报 TS2589 实例化深度误报，用 @ts-expect-error 精准抑制。25 用例全过，198 全量绿，双后端部署正常。
- 当日续 7：`registration-process-layout-persistence`——注册流程画布**布局持久化**（OpenSpec 变更已建并 validate）。后端 RegistrationProcessLayoutService/Controller（GET 登录可读 / PUT 仅 SUPER_ADMIN，SecurityConfig 显式规则），文件 `layout/registration-process-layout.json`（`app.registration-process-layout-file` 可配），首启从 `classpath:layout-defaults/` 播种、原子写、64KB 校验；前端三层优先级（内置默认 < 已保存布局 < 会话拖动）、Admin 专属"保存布局"按钮（全量快照：节点坐标 + 正交线 sx/laneY/tx）。**用户实际从 vite dev(5173) 浏览**（非 8081 jar），Kimi 浏览器扩展在 Chrome 不在 Edge；Edge DevTools 控制台有自 XSS 粘贴保护（需输"允许粘贴"，英文短语与 IME 键入均无效）。用户改动后布局经截图像素测量（zoom=167imgpx/180canvas）写入种子文件，部署后页面恢复确认。后端 5 新测 + 前端 28 用例（全量 201）全绿，双后端部署正常。
- 归档（2026-09-21）：`qualified-downgrade-approval` 与 `registration-process-layout-persistence` 均经 `openspec archive --yes` 归档，spec delta 已合并入 `openspec/specs/registration-process/spec.md`（"标准默认审核点（10 处）"替换旧需求；"布局持久化（Admin 保存与启动预加载）"新增）。CURRENT_STATE.md 已更新。
- 当日续 8（2026-09-21）：**布局文件 user.dir 陷阱**——`app.registration-process-layout-file` 默认相对路径按启动 CWD 解析：用户从仓库根目录重启 → 根目录 `layout/registration-process-layout.json` 被重新播种（旧模板），出现"保存后重启布局又回来了"。处置：①把用户已保存的 `metadata-registry-web/layout/…` 复制到根目录副本（运行中实例即时生效）；②`~/.metadata-registry/application-local.yml` 的 `app:` 段加**绝对路径**配置（对所有 profile/实例/CWD 生效，重启后统一）；③重启主后端实测 GET 返回用户保存值。教训：文件型持久化默认路径应避免依赖 user.dir，或在外部配置钉死绝对路径。
