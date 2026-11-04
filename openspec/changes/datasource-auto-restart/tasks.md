## 1. 实施基线与影响核对

- [x] 1.1 记录当前工作树并行修改、真实启动方式和可用测试端口，建立 `scratch/` 隔离控制目录、配置与副本库；通过 `git status` 和隔离路径清单确认不会覆盖并行修改或触碰真实库。
- [x] 1.2 对实际待改的 `DatasourceConfigController`、`DatasourceConfigService.saveConfig`、向导 `nextStep`/`saveDbConfig`/离开守卫及启动脚本补充 GitNexus impact/API impact；保存结果并确认无未处理的 HIGH/CRITICAL/UNKNOWN 风险。

## 2. 后端自动应用事务

- [x] 2.1 先补能力查询与访问约束的失败测试，再实现 PERSONAL、字面回环地址、受管控制目录检查及 `GET /api/v1/datasource/restart-capability`；运行控制器/服务测试，验证非受管、TEAM、非回环均在写配置前拒绝。
- [x] 2.2 先补配置备份和原子写入失败测试，再实现原配置备份、“原配置不存在”标记及临时文件替换；以文件断言验证写入或请求创建失败可恢复原文件，原文件缺失时恢复为不存在而非空 YAML。
- [x] 2.3 先补自动应用服务测试，再实现连接测试复用、重启请求创建、客户端 UUID 请求指纹、相同请求幂等、不同配置 409、非终态并发拒绝和延迟退出执行器；运行测试验证调用顺序及任何失败均不退出旧进程。
- [x] 2.4 实现 `POST /api/v1/datasource/config/apply` 的新 DTO、HTTP 202 与统一错误响应；以 MockMvc/控制器测试验证有效请求、响应丢失后同 ID 重试、并发冲突及既有数据源守卫不被绕过。
- [x] 2.5 先补重启状态与运行配置核对测试，再实现 `GET /api/v1/datasource/restarts/{restartId}` 的终态、未知 ID、`VERIFYING` 下 MATCHED/MISMATCHED 和原因码；验证响应不包含密码、完整 JDBC 地址、备份、命令行或堆栈。
- [x] 2.6 运行 `DatasourceConfigServiceTest`、数据源控制器测试及系统设置相关回归，确认既有 `PUT /api/v1/datasource/config`、手工重启提示和连接测试行为无变化。
- [x] 2.7 切换方言时同步写入 ORM 映射资源（SQLite→`orm/orm-sqlite.xml`，SQL Server/达梦→`orm/orm-team.xml`）；以单元测试验证映射随方言切换，避免沿用 team 映射导致新库建表失败。

## 3. Windows 常驻启动器

- [x] 3.1 新增 `scripts/backend-supervisor.ps1` 的被托管进程启动、控制目录注入和请求等待；用 PowerShell 语法解析及隔离进程冒烟验证普通退出无请求时不自动拉起，且只管理自己创建的 PID。
- [x] 3.2 实现 `PENDING → STARTING → VERIFYING → SUCCEEDED` 状态推进、完整就绪探测和实际目标核对；在隔离端口真实重启测试 JAR，验证旧 PID 退出、新 PID 不同、状态原子更新且成功后清理备份。
- [x] 3.3 实现目标提前退出、默认 180 秒超时和 MISMATCHED 分支下的旧配置恢复；用结构不兼容的 SQLite 副本及缩短的测试超时验证 `ROLLED_BACK`、旧库可用和目标文件未被删除。
- [x] 3.4 实现 `RECOVERY_FAILED`、恢复窗口和遗留非终态事务恢复；通过故障注入验证不无限重启、诊断备份保留，以及启动器异常退出后再次运行先恢复旧配置。
- [x] 3.5 改造 `start.bat` 使用常驻启动器并改造 `stop.bat` 写入 `stop.request`；通过真实脚本验证启动、普通停止、重启期间停止均符合预期，不发生停止后再次拉起或误杀非自有进程。
- [x] 3.6 检查启动器事件与进程日志，验证不包含配置正文、数据库密码或敏感命令行；成功/回滚后清理备份，恢复失败仅保留必要诊断文件。

## 4. 前端进度与向导恢复

- [x] 4.1 先写失败 Vitest，再实现自动重启 API 类型、客户端 UUID 创建和会话恢复标记；验证相同操作复用 ID、终态清理标记，且 `sessionStorage` 不含密码、完整 JDBC 地址或配置备份。
- [x] 4.2 先写轮询状态机失败测试，再实现静默启动/重启状态轮询与浏览器总等待窗口；覆盖受理、网络拒绝、暂时 5xx、ready=false、ready=true、MATCHED/MISMATCHED、超时和重试，验证无全局错误 toast。
- [x] 4.3 实现数据库重启进度组件，以真实阶段、不确定活动指示和等待时间展示进度；以组件测试和构建验证重复操作禁用、可访问名称、礼貌宣读、减少动态效果及不显示伪造百分比。
- [x] 4.4 接线 `InitWizard.vue` 的能力查询和单一“下一步”入口：受管环境调用自动应用，非受管环境调用既有保存并显示手工重启；以测试验证参数未修改或测试失效时仍不保存、不重启。
- [x] 4.5 实现刷新恢复、重启期间离开守卫例外和终态处理；以测试验证刷新/离开不调用 `abort`，成功回到数据库步骤、已初始化目标进入应用、`ROLLED_BACK` 返回可编辑表单、恢复失败可重试。

## 5. 隔离环境端到端验收

- [x] 5.1 构建实际后端 JAR 与前端产物，在 `scratch/` 用测试配置、测试端口和新版 `start.bat` 启动；通过浏览器确认受管能力可用，应用未就绪期间不显示业务错误。
- [x] 5.2 从隔离 SQL Server 临时库切换到全新 SQLite，真实执行“保存 → 自动重启 → 数据库准备 → 配置核对 → 回到向导 → setup/commit”；核对 PID、实际 SQLite 路径、初始化结果和完整受管链，全过程无手工重启。
- [x] 5.3 用可连接但结构不兼容的 SQLite 副本执行真实失败切换；在浏览器确认自动恢复旧配置、显示友好回滚提示、旧库可继续操作且失败目标文件保留。
- [x] 5.4 分别验证缩短超时、恢复失败和重启期间主动停止；确认页面不会永久无操作等待，启动器不无限重启，停止后无受管后端残留。
- [x] 5.5 在真实浏览器重启期间刷新页面，验证进度恢复、无 `abort`、无密码进入会话存储；再以已初始化 SQLite 为目标验证成功后进入正常应用而非重复初始化。
- [x] 5.6 以直接 JAR 的非受管方式和系统设置页分别执行原保存流程，确认仍显示手工重启要求且不会发生自动退出。

## 6. 文档、回归与交付核对

- [x] 6.1 更新 README 的新版启动器、自动应用、失败恢复和首版支持范围说明；核对文档不再把初始化向导统一描述为只能手工重启。（架构文档重新生成见 6.6）
- [x] 6.2 运行 Web 相关测试及项目标准 Maven 回归，保存结果；任何失败都与既有基线逐项比较，确认本变更零新增失败后才可继续。
- [x] 6.3 运行 UI Vitest、类型检查和生产构建，并同步实际交付所需静态资源与 JAR；检查构建产物和真实页面均来自本变更版本。
- [x] 6.4 运行 `openspec validate datasource-auto-restart --strict`、全量 OpenSpec 校验和 `detect_changes(scope=all)`；确认全部有效且无 `partial:true`/`truncated:true`，变更范围不包含真实库、密钥或无关并行修改。
- [x] 6.5 按能力 delta 逐项核对任务与真实证据，确认用户原始 SQL Server → SQLite 场景、自动回滚、刷新恢复、非受管兼容和主动停止均端到端通过后再提交评审。
- [x] 6.6 重新生成架构文档并同步 `docs/architecture/`：因 Kimi 通道与 codex 通道共用同一份周配额（LLM 429），改用 minimax 通道（`https://api.minimaxi.com/v1`）生成 24 页；同步映射为 `overview.md → ARCHITECTURE.md`、其余模块页 → `docs/architecture/modules/`（`docs.md` 沿用历次约定不纳入），18 个模块页刷新、新增 5 页（含记录新启动器流程的 `root.md`）；仅 `metadata-registry-common-facilities-src.md` 本轮生成失败，保留上一轮版本。生成结束后已把 `~/.gitnexus/config.json` 恢复为原 Kimi 配置。

## 7. 代码评审补强（2026-10-25）

两份独立只读评审（后端/前端、启动器）共报 4 高危 + 11 中危 + 若干低危，逐条核实后全部处置并补充验证。

- [x] 7.1 受理前检查环境变量覆盖：比对配置占位符默认值与环境变量实际值，冲突时在写入配置前拒绝并指明变量名（`DatasourceRestartService.describeTarget`/`environmentConflicts`）。
- [x] 7.2 退出时机改为响应写出之后：新增 `DatasourceRestartExitFilter`，在过滤器链结束且响应为 202 时 flush 输出并调度退出；服务层不再自行退出进程。
- [x] 7.3 能力判定增加启动器存活校验：读取 `supervisor.pid.json` 核对 PID、进程名与可执行路径，环境变量不再作为唯一依据。
- [x] 7.4 陈旧锁回收：事务锁记录持有者 PID，进程已消失的锁可被回收，避免功能永久不可用。
- [x] 7.5 终态语义收敛：`RECOVERY_FAILED` 保留请求文件（诊断与遗留事务恢复的输入），仅 `SUCCEEDED`/`ROLLED_BACK` 删除；状态查询在仅有请求文件时返回 `PENDING` 而非 404。
- [x] 7.6 拒绝代理来源的自动应用：`/datasource/config/apply` 与状态查询拒绝带 `X-Forwarded-For`/`Forwarded`/`X-Real-IP` 的请求。
- [x] 7.7 启动器：事务无效/恢复失败/自身异常时保持后端可用；启动时无条件清理遗留后端；未获得互斥体时写 `supervisor.error` 且不触碰属主记录；只删除归属自己的 PID 记录；超 7 天的非当前事务备份自动清理。
- [x] 7.8 启动器脚本 ASCII 化：`start.bat`/`stop.bat` 去除中文注释（cmd 按本地代码页解码 UTF-8 会吞行），`call :label` 改为传两个参数（传含逗号参数会整体丢失）。
- [x] 7.9 前端：失败态保留会话标记，仅事务不存在时清除并回到可编辑表单；初始化状态判定改用 `refreshInitialized()`（取不到新值返回 null，不误跳首页）；回滚阶段用 `RECOVERING`；成功路径高亮停在"核对设置"。
- [x] 7.10 规范与文档同步：三份 OpenSpec 规范补充本轮语义并通过 `--strict` 校验；设计文档、计划、README 同步补强轮决策与新环境变量说明。
- [x] 7.11 回归与真实进程验证：后端 web 模块 1601 例（9F+4E，与改动前基线完全一致，零新增）、前端 405/405、启动器真实进程 9 项断言全通过（含真实进程验证中暴露的两个缺陷：非属主实例误删属主记录、`DateTime` 与 `DateTimeOffset` 比较抛异常导致备份清理被静默跳过）。
