# 菜单重构（注册模块重定义）+ 注册流程设计器（registration-process-designer）

日期：2026-09-18

## What Changed

- **菜单重构（已完成）**：侧边栏按 ISO 11179 元模型模块重排为 概览/数据字典/概念模块/表示模块/基础与核心模块/标识与命名模块/注册模块/分类模块/项目映射模块/系统/我的；新挂菜单：值含义、文档类型（接通既有孤儿页 DocumentTypeList）、注册流程、角色与分类项占位页；映射网络/匹配结果管理移出菜单（路由保留）。OpenSpec 主 spec `standardize-menu-layout` 已同步改写。
- **注册流程设计器（openspec change `registration-process-designer`，实现完成待评审）**：
  - 后端：`RegistrationTransitionRule`（按 RA 作用域，含审核人子表）+ `RegistrationTransitionApproval` 两实体三方言迁移（V20260918002）；`RegistrationProcessService`（ensureSeed 幂等播种 21 条标准默认规则、availableTransitions/execute/approve）；`RegistrationLifecycleService` 白名单切规则表（无 RA 条目回退静态底线）；旧 `POST /administered-items/{id}/transition` 移除（BREAKING）；编辑路径改状态抛 1124。
  - 前端：`RegistrationProcessView.vue`（Vue Flow 只读流程图 + 节点悬停标准释义（Table 30 中文译文+英文原文+11179-6 §4.3.3）+ 边详情抽屉 + 图例）；AdministeredItemList 改"推进流程"面板（执行/批准/进度）+ 状态过滤；ItemRegistrationStateSection 编辑模式只读（新建可选初始状态含 APPLICATION/HISTORICAL）；菜单注册模块增"注册项"。
  - 新依赖 `@vue-flow/core ^1.48.2`；新 API：`GET /registration-process`、`GET/POST /administered-items/{id}/transitions[/execute|/approve]`。

## Decisions

- 状态机底线 = 现行 ALLOWED_TRANSITIONS（21 条，用户确认 Incomplete/Candidate→Retired 标准允许；Application/Historical 为可标定初始状态）；默认审核点仅 RECORDED→QUALIFIED / QUALIFIED→STANDARD / STANDARD→PREFERRED_STANDARD（11179-6 §4.4 d)）。
- 审核=累积批准单操作（无驳回无发起），默认全部 Registrar + 任一人通过；批准人=该项 RA 的活动 Registrar 身份。
- 自定义 Role 定制取消，推迟（未来聚焦 administrative_status）；流程页本期只读（教学），编辑 UI 后续。
- 数据模型按每 RA 一套流程设计（当前单 RA）。
- e2e mock 必须显式处理 `/api/v1/startup/status`（ready:true）与 init-wizard/status、auth/me——catch-all 后注册优先匹配，缺了会卡启动屏或被重定向。

## Verification

- 后端 `mvn -pl metadata-registry-web -am test`：1125 通过；sqlite 迁移经内存库实测。
- 前端 vitest 182 通过；build（vue-tsc）通过；eslint 0 error。
- e2e：`menu.spec.ts` 8/8、`registration-process.spec.ts` 6/6。
- 注：菜单重构前的旧 menu.spec.ts 因启动就绪门控（startup/status）全部失败——属既有断裂，已在重写的 spec 中修复；其他旧 e2e 套件重跑时需补同样 mock。

## Next Step

- ~~`continue-review` 代码评审~~ 已完成：4 Major（无 RA 回退免审核 QUALIFIED+、遗留无状态条目表单后门、并发批准 500、dm 脚本单块吞噬）+ 6 Minor 全部修复，后端 1134 tests 绿；M1 偏差已按"有意行为"写入 design.md Risks。
- 09-19 人工验收修复：流程页 500 根因 = 运行中的后端 jar 过期（javaw 锁文件致 repackage 失败），已停旧进程（PID 73984）→ 重新打包 fat jar → 重启后端；验证：`/api/v1/registration-process` 401（端点存在）、ddl-auto 建三表、SeedInitializer 为 1 个 RA 播种成功。**注意：改动后端后 `mvn package` 前必须先停运行中的后端（Windows 文件锁）**。
- 09-19 按用户要求移除"注册项"菜单入口（`/administered-items` 页面与路由保留，仅无菜单入口）；e2e 边详情用例改 `dispatchEvent('click')` 消除 fit-view 坐标抖动。
- 09-19 二次验收修复：**迁移集经 ISO/IEC 11179-6:2023 Annex D.2 逐条核验后从 21 条纠正为 20 条**（删 INCOMPLETE/CANDIDATE→RETIRED、APPLICATION→RECORDED/RETIRED、SUPERSEDED/RETIRED→HISTORICAL；增 INCOMPLETE/CANDIDATE→HISTORICAL、RECORDED/QUALIFIED→SUPERSEDED、APPLICATION→CANDIDATE；RETIRED/HISTORICAL 无出边）——决策 1 中"Incomplete/Candidate→Retired 标准允许"经核验与 2023 版不符，已按标准纠正并记录于 design.md §2.5；ensureSeed 改为调和式。流程页节点可拖动 + 重置布局；悬停释义 Tooltip 被画布 overflow 裁剪 → 改 Teleport 浮层；节点点击释义详情抽屉。
- 09-19 三轮验收迭代：连线加方向箭头（markerEnd + smoothstep + 双向对分层通道）；单击仅选中高亮/双击开详情（边与节点一致，处理了 Vue Flow 单选交叉取消缺陷）；悬停释义需"选中+停留 1 秒"（修复"先悬停后选中无 mouseenter"时序缺陷——指针在节点上选中时补启动计时）；已取代/已退役分类纠正为生命周期状态（11179-6 Table 1，文档状态仅 Historical/Application）；界面去除一切标准出处文字（"定义"/"解释"朴素标签）；**CANDIDATE→RECORDED 纳入默认审核点（治理加严，默认审核点 3→4 处）**，ensureSeed 调和补强字段漂移归位（旧库 C→R 已自动翻转为需审核）。后端 1175 tests 绿、前端 vitest 194、e2e 16/16 稳定；后端两次重打包重启生效。
- 注意：工作区存在并行会话正在实现 src/views/role/（角色页）与 RoleController 等，未触碰；agent 曾误跑 CRLF 规范化波及约 250 文件（git 内容零差异，已 add 刷新索引，不进提交）。
- 待 `continue-archive` 归档 `registration-process-designer`。
- 256+ 未提交文件仍未提交（见 CURRENT_STATE），本次又新增约 50 文件；提交优先级最高。
