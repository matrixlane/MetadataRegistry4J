# 2026-10-20 administered-item-reference-documents-ui 收尾（回归修复 + 全量复测）

## 背景

续 2026-10-17 已签入的 1bd2699d（后端/受管链闭环+表合并）与 c70019b5（前端参考文档页签）。
tasks.md 全未勾，本次核对实际状态、修复对基线新引入的回归、完成第 7 组提交前检查的大部分。

## 环境事故（重要，影响复现）

- 本机环境已变：无 SQL Server（1433 不通），`~/.metadata-registry/application-local.yml` 指向空 SQLite 库；
  `db-baseline/`（gitignore）本机缺失。真实库相关验证组（1.3 / 2.4 / 3.4 / 5.7 / 5.8 / 6.x）本机无法执行。
- **仓库被第二个并行会话占用**（sqlite-empty-db-bootstrap，5b9d8c59 于本会话期间 06:10 提交）：
  对方反复 `mvn -pl metadata-registry-web -am clean package -DskipTests`，与本会话构建互踩
  target/ 与 .m2，曾造成 NoClassDefFoundError 风暴、编译产物消失、JVM 变孤儿等连环假象。
  教训：**该仓库可能多 Agent 并发，验证必须在与主 checkout 隔离的 git worktree 中进行**。
- Windows ARM64：gitnexus CLI 原生二进制不可用（npm 无 win-arm64 预编译、源码不随包发布），
  detect-changes 本会话未能运行（AGENTS.md 强制项，记遗留）。

## 基线比对方法

git worktree 5a5453ee（10-09 基线）与 master+修复 各跑一次全量/定向 surefire，按类逐项比对：
- 5a5453ee：RegistrySpecificationServiceTest 1F、WorkspaceServiceIntegrationTest 6F+3E（PersonalEdition 6/6 绿、WorkspaceRollback 7/7 绿）。
- c70019b5（本变更提交点）：新增 PersonalEditionAuthenticationFilterTest 6/6 失败、WorkspaceRollbackServiceIntegrationTest 2 失败。

## 修复内容（主仓库工作区，master=5b9d8c59 之上）

1. **AdministeredItemRepository.findMetaByItemId / findMetaByItemIds**（本变更 design D1' 引入）：
   select 列表中 `ai.stewardshipRecord.id / .contact.id / .organization.id` 被 Hibernate 生成为
   stewardship_record 的 **INNER JOIN**（且 contact/organization 链一并内联），对无主管记录的
   受管项**整行丢行** → meta 全 null（registrationStatus/administrativeStatus 等全读不到）。
   改为显式 `LEFT JOIN ai.stewardshipRecord sr / sr.contact c / sr.organization o`，列序不变。
   实证：update() 后 update registration_state 绑定 'draft' 已落库，但投影 select 因内联 join 无行返回。
2. **ItemAdministrationService 批量标识符路径空值防护**（随①暴露的潜伏 NPE，10-17 注释自述"在途重构"）：
   - `preferPrimaryScalarIdentifier`：两处 filter 补 `r[4] != null`（对齐实体版 preferPrimaryIdentifier 的
     `s.getNamespace() != null` 语义）；`Map.of()`（immutable MapN）`.get(null)` 直接 NPE。
   - `buildItemMeta` 批量分支：`preload.namespacesById().get(nsId)` 前判 `nsId == null`。
   修复前批量路径因①整体不可达，故 10-17 未爆；①修复后 ItemSortMvc(4F)、ItemKeyword(8E) 立即爆 NPE，修复后全绿。
3. **PersonalEditionAuthenticationFilterTest 重写**：过滤器在 1bd2699d 改为个人版一律装载隐式主体并传
   AuthorizationHiddenRequest 包装（AGENTS.md 已写明的设计），测试仍按旧行为断言（原始 request 透传 +
   getHeader stub）。按新语义重写 6 例（含 stale bearer 仍装载且隐藏令牌、未初始化也走包装）。
4. **IdentifierMetadataTest**：1bd2699d 给 VersioningPolicy 增 FREE_TEXT 未同步测试（ext 模块 136 用例 1 失败）。
5. **tasks.md**：按实测勾选 1.1-1.2/2.1-2.3/3.1-3.3/4.1-4.4/5.1-5.6/7.2/7.3/7.4，记录阻塞项与证据。

## 验证

- worktree（master 5b9d8c59 + 上述修复）全量 web：**1546 用例 7F+4E，170/170 报告齐**。
  剩余失败=既有基线：RegistrySpecificationServiceTest 1F、WorkspaceServiceIntegrationTest 6F+3E（与 5a5453ee 基线逐项一致）、SegmentTemplateStrategyIntegrationTest 1E（SQLITE_LOCKED_SHAREDCACHE 并发锁抖动，master 无修复时亦同）。
- 四组合修复类孤立复测全绿：ItemSort 4/4、ItemKeyword 9/9、WorkspaceRollback 8/8、PersonalEdition 6/6。
- 前端 vitest 371 用例全绿（2 例在构建并发期 5.8s 超时抖动，孤立重跑 36/36 过，判环境性）。
- openspec validate 本变更 valid；--all 75 过 1 失败为 default-registrar-auto-activation（既有，与本次无关）。
- 个人版空库端到端：应用可启动（个人版隐式主体，无需登录）。

## 遗留

- 1.3/2.4/3.4/5.7/5.8/6.x：待真实 SQL Server 库恢复后执行（需用户确认真实库写入）。
- 7.1 detect-changes：待 gitnexus 在 Windows ARM64 可用（或换 x64 Node 模拟环境，已验证可行但未装）。
- 7.5 归档：本变更 spec 尚未并入主 specs、changes 目录未归档（建议组 6 完成后一并做）。
- 未提交：上述修复在主仓库工作区未签入（detect-changes 强制项暂不可跑，由用户裁定提交时机）。
