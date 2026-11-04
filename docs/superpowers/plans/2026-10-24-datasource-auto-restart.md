# 数据源自动重启实施计划

日期：2026-10-24
关联设计：`docs/superpowers/specs/2026-10-24-datasource-auto-restart-design.md`
关联 OpenSpec：`openspec/changes/datasource-auto-restart/`

## 1. 实施约束

- 严格按已确认的 30 项 OpenSpec 任务实施，首版仅支持 Windows 单机个人版、项目启动器和初始化向导。
- 当前工作树含本功能依赖的未提交 `baseline-data-init-rebuild` 改动，直接从旧 HEAD 建 worktree 会得到不一致的启动行为。因此在当前工作树逐文件增量修改，持续核对 diff，不覆盖并行工作，不执行 git commit/reset/rebase。
- 所有真实重启、故障注入和数据库切换均使用 `scratch/datasource-auto-restart/`、测试端口、临时配置与副本库；不触碰真实 SQL Server 数据和用户正在使用的 SQLite 文件。
- 每个实现单元先写失败测试，再写最小实现；完成后立即运行对应测试并勾选 OpenSpec 任务。

## 2. 后端实施顺序

### 2.1 配置事务与文件协议

涉及：

- `DatasourceConfigService.java`：保留现有校验/连接测试/配置生成入口，将最终配置写入改为同目录临时文件加原子替换。
- 新增 `DatasourceRestartService.java`：能力检查、事务锁、备份、请求指纹、幂等、并发、状态查询和实际目标核对。
- 新增重启状态/验证枚举与内部文件模型；JSON 由项目现有 Jackson 处理，不增加依赖。
- 新增可替换的延迟退出执行器，生产实现关闭 Spring 上下文并退出 JVM，测试使用 mock。

关键不变式：

1. 备份先于配置修改；请求完整落盘后才允许退出。
2. 配置或请求写入失败时恢复操作前状态，不退出旧进程。
3. 同一时刻只有一个非终态事务；同 ID 同指纹幂等，同 ID 不同配置冲突。
4. 请求文件只含目标方言、规范化 JDBC URL、备份位置和散列指纹；API 状态不暴露这些内容。

### 2.2 API 与访问控制

涉及 `DatasourceConfigController.java` 和 `dto/datasource/`：

- `GET /restart-capability` → `{ supported, reasonCode }`
- `POST /config/apply` → 请求 `{ restartId, config }`，HTTP 202，响应 `{ restartId }`
- `GET /restarts/{restartId}` → `{ restartId, state, verification, reasonCode }`

复用控制器现有初始化/管理员守卫；新增 PERSONAL、字面回环和受管控制目录检查。既有 `PUT /config` 不改契约。

测试：新服务/控制器测试 + 既有 `DatasourceConfigServiceTest`；验证成功、失败、并发、敏感字段和旧行为。

## 3. Windows 启动器实施顺序

新增 `scripts/backend-supervisor.ps1`，改造 `start.bat`、`stop.bat`。

1. 启动器持有子进程，注入控制目录，普通退出无请求时不拉起。
2. 实现 `PENDING → STARTING → VERIFYING → SUCCEEDED`；轮询 `/startup/status` 后再调用重启状态接口核对 MATCHED。
3. 目标退出、180 秒超时或 MISMATCHED 时恢复备份并启动旧配置；旧配置就绪后写 `ROLLED_BACK`。
4. 恢复失败写 `RECOVERY_FAILED` 并停止重试；遗留非终态事务先恢复备份。
5. `stop.request` 在普通运行、目标启动和恢复中均优先；`start.bat` 启动时清理旧停止信号。
6. 状态和日志原子/脱敏处理，启动器只停止自有 PID。

验证：PowerShell 语法解析、隔离进程冒烟，再以真实 JAR 验证 PID 更替、成功、回滚、超时和停止。首版不引入 Pester。

## 4. 前端实施顺序

涉及：

- 新增 `src/api/datasourceRestart.ts`：API 类型和请求。
- 新增 `src/utils/datasourceRestart.ts`：UUID、会话标记、状态机和静默轮询。
- 新增 `src/views/init-wizard/DatasourceRestartProgress.vue`：真实阶段、不确定活动指示、等待时间、可访问状态宣读。
- 修改 `InitWizard.vue`：能力降级、单一下一步、刷新恢复、离开守卫例外和各终态。
- 新增对应 Vitest，不修改系统设置页。

阶段只依据受理、`/startup/status`、重启状态和核对结果推进。网络拒绝和暂时 5xx 不进入全局错误提示。会话标记不含密码/JDBC 地址；重启期间刷新和离开不得调用 `abort`。

验证：`npm test`、`npm run build`，再在真实浏览器覆盖刷新和终态。

## 5. 端到端验收顺序

1. 构建实际 JAR 和前端，使用隔离控制目录、配置和测试端口通过新版 `start.bat` 启动。
2. 隔离 SQL Server 临时库 → 全新 SQLite：验证自动重启、PID 变化、实际目标、向导恢复及 `setup/commit`。
3. 可连接但结构不兼容的 SQLite：验证自动恢复旧配置和友好提示，目标文件不删除。
4. 缩短启动器超时、恢复失败、遗留事务和重启期间停止：验证有限重试和安全收敛。
5. 浏览器刷新、目标库已初始化、直接 JAR 非受管降级、系统设置页手工重启回归。

不得只以服务单元测试或模拟进程代替上述真实场景。

## 6. 验证命令与交付检查

- 后端目标测试：`./mvnw.cmd -pl metadata-registry-web -am -Dtest=<相关测试类> -Dsurefire.failIfNoSpecifiedTests=false test`
- 后端标准回归：`./mvnw.cmd -pl metadata-registry-web -am test`
- 前端：`npm test`、`npm run build`（工作目录 `metadata-registry-ui/`）
- PowerShell：使用 `[System.Management.Automation.Language.Parser]::ParseFile` 检查脚本无语法错误。
- OpenSpec：`openspec validate datasource-auto-restart --strict`，交付前再跑全量校验。
- 图谱：交付前 `detect_changes(scope=all)`，不得接受 `partial:true` 或 `truncated:true`。
- 文档：更新 README；按实际流程变化同步或重新生成架构文档。
- 交付物：确认前端静态资源和可执行 JAR 来自当前版本，检查无真实库、密码、临时配置或无关并行文件混入。

## 7. 完成后的流程

实现和 fresh verification 完成后暂停，按工作流请求用户输入 `continue-review`。代码评审确认代码、规范和证据一致后，再等待 `continue-archive`，不提前归档。

## 8. 代码评审补强轮（2026-10-25）

两份独立只读评审共报 4 项高危、11 项中危及若干低危，逐条核实后按下表处置；未采纳的意见在备注中说明理由。

| 编号 | 问题 | 处置 | 验证 |
| --- | --- | --- | --- |
| H1 | 环境变量覆盖新配置，失败提示无法定位 | 受理前比对占位符默认值与环境变量，冲突即拒 | `DatasourceRestartServiceTest` 用例 |
| H2 | 失败态丢弃会话标记，刷新后再提交撞 409 | 失败态保留标记，仅事务不存在时清除 | `InitWizard.spec.ts` 用例 |
| H3 | 事务无效/恢复失败导致系统停在停止状态 | 写入终态后以磁盘配置重新拉起后端并继续托管 | 启动器真实进程断言 C1/C2 |
| H4 | 自称受管但启动器已死；响应未写完即退出 | 校验启动器 PID 记录；由退出过滤器在响应写完后退出 | `DatasourceRestartExitFilterTest`、启动器断言 A1 |
| M2 | 代理来源可触发本机重启 | 拒绝带 `X-Forwarded-For`/`Forwarded`/`X-Real-IP` 的请求 | `DatasourceConfigControllerTest` |
| M3/M10 | 陈旧锁无法回收；误删他人进程记录 | 锁按 PID 判定陈旧；只删除归属自己的记录 | 服务端用例 + 启动器断言 A4/B2 |
| M6 | 失败态丢失事务 | 保留会话标记 | `InitWizard.spec.ts` 用例 |
| M7 | 事务不存在仍停在等待页 | 回到可编辑表单并提示"设置未提交到应用" | `InitWizard.spec.ts` 用例 |
| M8/M9 | 回滚文案用错阶段；成功路径高亮错位 | 回滚用 `RECOVERING`；成功路径停在"核对设置" | `DatasourceRestartProgress.spec.ts` 用例 |
| L15 | 互斥冲突无任何提示 | 写 `supervisor.error`，`start.bat` 检查并提示 | 启动器断言 A2 |
| L16 | 含明文口令的备份长期留存 | 超 7 天且非当前事务的备份自动删除 | 启动器断言 D1 |

未采纳：后端在服务层直接退出进程（原设计如此，但会截断响应，改为退出过滤器统一接管）；以环境变量判定是否受管（进程内环境变量无法反映启动器存活，改为 PID 记录校验）。

启动器验证使用隔离控制目录、隔离配置与桩进程（`scratch/datasource-auto-restart/keepalive.jar`），断言 9 项：正常启动、互斥标记、属主存活、属主后端不受影响、孤儿存活、孤儿被替换、旧孤儿终止、无效事务后后端仍存活且写入 `RECOVERY_FAILED`、超期备份删除。

补强轮同时修正两处缺陷：非属主启动器会误删属主的进程记录；`DateTime` 与 `DateTimeOffset` 直接比较在 PowerShell 中抛异常，导致备份清理被静默跳过（改为统一使用 UTC `DateTime` 比较）。
