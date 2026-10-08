# 2026-10-08 personal-factory-reset-confirmation

## 起因

个人版免登录后恢复出厂设置仍强制密码确认，admin 为 `NO_PASSWORD` 哨兵 → 功能完全不可用。头脑风暴四方案（A 确认短语 / B 纯勾选 / C 本机回环边界 / D 两段式令牌），用户批准 **A + C**，并豁免后续确认、指令按 `superpowers-openspec-superpowers-workflow` 一路推进到实现。

## 关键事实（探索纠正）

- **团队版重新初始化无 NO_PASSWORD 缺口**：`InitWizardService.setup()` 已无条件 `resetSuperAdminToNoPassword()`（InitWizardService.java:133-137），注释明确兜住"出厂重建真实哈希"。头脑风暴阶段的疑点排除。
- `GlobalExceptionHandler` 对 `BusinessException` 恒 HTTP 400 + body code（密码失败 400/401 即先例）→ 短语 422、本机 403 均为 body 层错误码；AccessDeniedException 虽给真 403 但消息被固化为"无权访问此资源"，放弃。
- GitNexus impact（executeFactoryReset upstream）LOW，唯一直接调用方 SystemController。

## 实现要点

- `FactoryResetRequest(password, confirmationPhrase)` 双可空，service 按 `getEdition()` 分支强制（D4：互斥必填集不下沉 bean-validation）。清表**前**读 edition 传入 `reseedAdminUser(edition)`：PERSONAL 写 `NO_PASSWORD` 哨兵、TEAM 维持 Admin@123 哈希。
- 回环判断 `InetAddress.getByName(remoteAddr).isLoopbackAddress()`，异常按拒绝；只信 remoteAddr，不信 X-Forwarded-For。校验顺序：本机 403 → 短语 422 → 清表。
- 前端 `DangerSettingsPage`：`auth.edition` 未知时挂载请求 `/init-wizard/status` 补齐，仍未知 fail-closed 按 TEAM 渲染；短语框输入 trim 完全匹配前禁用提交。e2e 两 spec 的 status mock 无 edition 字段 → 自然走 TEAM 路径，断言零改动。
- **openapi.json 不能整表再生成**：当前库内契约含并行会话（active-registrar WIP）端点，整表生成会砍掉它们 → 改为 json/yaml 双侧外科手术式补丁（仅此 FactoryResetRequest schema 块）。

## 验证

- 后端目标类 15/15 绿（FactoryResetServiceTest 10、SystemControllerTest 3、集成 2）。全量 1489 tests，失败 11（Workspace* 10 + SegmentTemplate* 1）= CURRENT_STATE 记载的并行会话既有失败集合，零交集。
- 前端 vitest 35 文件 332/332（+5 新用例），build 过。
- openspec validate 通过；detect_changes 无 partial/truncated（全树 critical 风险归并行 WIP）。

## 教训

- **VTU 中 stub `Teleport`（任何大小写）会破坏 Element Plus ElDialog 的懒渲染**——表现为点击打开后 DOM 无任何对话框。正确姿势：不 stub，直接查询 `document.body`（ContextUsageDialog.spec 先例）。本次在此耗掉多轮定位。
- 集成测试共享 Spring 上下文 + 内存 SQLite 时，各用例靠"恢复出厂=清全表"自清洁，顺序无关性天然成立。
- 工作树混有并行 WIP 时，契约文件（openapi）只做手术式补丁，不做全量再生成。

## 评审（双代理结论：无 BLOCKER，1 MAJOR + 9 MINOR + 8 NIT）

独立评审子代理全量走查 9 文件。**MAJOR 1：`InetAddress.getByName` 对非字面量做真实 DNS 解析**——`127.0.0.1.nip.io` 这类解析到回环的主机名可绕过唯一边界，且 DNS 发生在事务内。已修复为**纯字面量判定**（IPv4 127.0.0.0/8 逐八位组 ≤255；IPv6 `::1`/全展开/IPv4 映射；null/畸形/主机名一律拒绝、零 DNS），补 9 形态拒绝 + 4 形态放行用例。其余处置：MINOR 2（TEAM 空密码 422→400/401）裁决**接受语义变更**并记入 spec 场景；MINOR 3/4 前端复用 `auth.checkInitialized(true)` 单点解析版本、proceed 前 await 消除形态切换竞态；MINOR 6 删除恒真断言改断写库；MINOR 7 补全负向分支（含控制器 X-Forwarded-For 伪造护栏用例）；MINOR 8 集成测试显式 setEdition(TEAM) 锁定顺序无关性；MINOR 9 拒绝分支补审计日志；NIT 清理（import/FQN/javadoc byte[]/autocomplete/注释）。评审期间教训：vi.mock 的 store 桩必须用 reactive 包裹，否则组件 computed 不刷新（生产为 pinia 响应式，纯 mock 假阴性）。

## 遗留

- 未提交：主树并行 WIP（mdr-cleanup 124 文件暂存、remove-system-default-namespace 会话正在主树删改 SystemDefaultNamespaceInitializer——评审验证期曾致全模块测试编译中断）需用户按变更分组提交；本变更文件清单见 tasks.md。
- 个人版 NAS/局域网直连接入时恢复出厂仅本机可发起（方案 C 有意取舍，已入 spec）。

## 部署事故与修复（当日 19:54-20:11）

用户报"恢复出厂两次都回到原界面"。三层叠加根因，全部非功能代码缺陷：
1. **运行实例（17:44 启动的胖 jar）logback-classic/core 版本错配**——任何带 throwable 的日志调用线程即崩（`NoClassDefFoundError: ThrowableProxy`），19:54 两次 factory-reset 处理过程中日志崩溃 → 返回损坏响应 → 数据库未清 → initialized 仍为 true → 前端跳 /init-wizard 被守卫弹回 = "回到原界面"。
2. **18:49 磁盘 jar 是瘦 jar**（无 BOOT-INF/lib，4MB）——并行会话在应用运行中执行 package，Windows 文件锁致 repackage rename 失败留下瘦 jar。
3. **前端 dist 未同步 static**——static 里还是 06:51 旧 chunk（密码框），即使后端正常也会 422。

修复：taskkill 旧进程（解除 jar 锁）→ `mvnw package -DskipTests` 出 222MB 健康胖 jar（logback 1.5.18 配对、含新代码、static 已同步 dist）→ 启动新实例 → 实测：错误短语 422「确认短语不正确」+ 审计日志落盘 + password-only 同样 422（个人版密码不构成凭据）+ 状态未变（探针零副作用）+ 前端入口 chunk 指向新 DangerSettingsPage。

**教训：交付"用户以 jar 运行"的功能必须完成最后一公里**——dist→static 同步、杀旧进程解除文件锁后再 package、验证胖 jar 健康（BOOT-INF/lib 配对、新代码常量、static chunk hash）、重启实例实测端点。缺一不可，本次三缺。

## 追加微调（当日 20:2x，豁免 OpenSpec 的文案调整）

用户要求初始化向导第 6 步"管理员密码"更名"管理员账户"，个人版提示改为"个人版无需设置账户，将使用内置账户默认登录"。改动仅 InitWizard.vue（步骤条标签/final-step 类判定/注释/个人版面板文案）+ e2e 注释；团队版"设置超级管理员密码"面板标题保留。已走完整交付链：vitest 331 过（332→331 系并行会话改 ItemRegistrationSection.spec 减 1 用例，非本次）、build、dist→static、杀进程重打 jar、启动验证在线 chunk 含新文案。

## 追加微调②（当日 20:40，豁免 OpenSpec 的文案调整）

第 6 步个人版提示改为"个人版无需设置管理员账户，将使用内置账户默认登录，完成初始化即可直接进入应用"；提交按钮"完成初始化并启用系统"→"完成初始化"（e2e 三处点击选择器同步）。**部署教训：后台命令行内嵌 `&`（java 启动）会把整条 && 链整体转入后台，任务外壳 9 秒退出时整条链被杀——必须分成"构建+打包"与"启动"两个任务**。另：用户已于 20:38 前成功完成恢复出厂（status initialized:false）——本次改造的实战验证闭环。vitest 331/331、build、dist→static、胖 jar 20:39、启动实测在线 chunk 含新文案。
- 追加教训③：以 Bash 后台任务方式启动的 java 常驻进程会被任务外壳回收（运行 337s 后被杀、日志无异常即进程消失）——**后台服务必须经 Start-Process javaw 完全脱离 shell 启动**（PID 20984，日志 app-restart-20261008g.log）。
