# 2026-10-08 mdr-standard-compliance-cleanup

## 成果

mdr schema 全量审计（90 表/312 列 对照 ISO/IEC 11179-3/31 PDF 原文，6 分部元模型提取）→ 用户 7 项决策 → OpenSpec 变更 `mdr-standard-compliance-cleanup`（方案 A，worktree `.worktrees/mdr-standard-cleanup`，分支 `feat/mdr-standard-compliance-cleanup`，4 提交）：

- 9 表更名（conceptual_domain_dec→data_element_concept_domain 等，registered_item_reference_document 保留现名、submission_record_registered_item→registered_item_submission_record 按用户调整）；
- 删 16 非标准列（namespace×4、scoped_identifier×7、rules_json、enabled、contact.account_id、dts.reference、slot.value/value_datatype、role 内联 email/phone、designation/definition.acceptability、submission_record.source），全部 0 行非空零搬迁；
- registration_transition_* 三表 ALTER SCHEMA TRANSFER 到 ext（类不迁模块，orm-team.xml 登记，Account 先例）；DatabaseEnvironment.EXT_TABLES 同步（guard 测试抓出）；
- submission_record.source 全栈移除（枚举/服务签名/DTO/前端 types+面板来源 tag）；主记录 acceptability 移除，MetadataSearchService 原生 SQL 改走 designation_context/definition_context（hasPreferred 同样）；ValueMeaning 弃冗余 @JoinTable 改 mappedBy；
- flyway_schema_history 归位 ext：外部 `~/.metadata-registry/application-local.yml` 补 `default-schema: ext`（默认 profile 运行时此前未设→Relocator 解析目标=mdr 跳过；备份在 /tmp/mdr-check/application-local.yml.bak）+ application-personal.yml 加 `default-schema: ${FLYWAY_DEFAULT_SCHEMA:}` 空值兜底（personal 冒烟验证）。

## 关键教训（重要）

1. **master HEAD 是红的**：relocate-flyway 会话的 FlywayHistoryRelocator/RelocationConfig 主类未提交，但其测试与 team yml 已入库→HEAD 编译失败。已在分支首提交 `dd394690` 补齐（内容=该会话主树 WIP 原样），其会话提交时应一致无冲突。
2. **ddl-auto 先于 Flyway 是设计（FlywayAfterJpaConfig）**：rename 类迁移在"新名表已被 ddl-auto 预建"时守卫跳过 → 旧表数据残留。解法是 V20261009002"合并去重后删旧表"（幂等，两种顺序都收敛）。SQL Server 嵌套游标在 WHILE 内经 EXEC DDL 会 16916 崩溃——用"集合式收集约束 + TOP 1/DELETE 循环"免游标。
3. **sqlite 分支必须 Java 迁移**（纯 SQL 无法表达条件 DDL；且同版本 .sql 与 Java 类并存会 duplicate version——删源文件后 target/classes 残留 .sql 需手工清）。
4. `mvn -pl web`（无 -am）会从 ~/.m2 解析 ext 旧 jar（并行会话污染）→ TypeNotPresentException；打包必须 `-am`。
5. 真实库验证流程：BACKUP DATABASE 先行（scratch/mdr-cleanup-backup/）；worktree jar + 外部 application-local.yml（spring.config.import 全局生效）即可跑团队 SQL Server 库。

## 验证

- web 全量 1452 tests：零新增失败（8F+4E 全部经基线提交 dd394690 对跑证实为既有：DatasourceConfig×1、SegmentTemplate×1、WorkspaceServiceIntegration×9、ScopedIdentifierConcurrency×1 负载敏感）；
- vitest 304/304、vue-tsc+vite build 过；MdrStandardComplianceCleanupMigrationTest 4/4（真实 Flyway 重放 sqlite Java 迁移 + 09002 静态结构）；
- 真实 SQL Server：V20261009001+09002 应用后 mdr=86 表、leftover=0、new9=9、ext=30、transition 40 行保留、designation_context 2 行并入、非标准列 0、ext.flyway_schema_history 23 行成功归位；8081 在跑（worktree jar），/api/v1/languages 200；personal profile scratch SQLite 启动 17.8s 通过。



## 代码评审（双代理，2026-10-08）

结论"需修复后通过"，修复提交 8e122507 后全绿：
- **B1（双方同指）EXT_TABLES 漏 registration_transition_rule_approver**（集合表无法经 orm.xml 登记，RA 级联删除经 qualifyAuto 打 mdr 必 500）——已补+注释。
- **M1 acceptability 原生 SQL 大写比对 vs 小写落库**（SQL Server CI 排序规则掩盖；SQLite/达梦筛选恒空）——改 LOWER()+小写绑定。
- **达梦 B1/B2**：09001 更名缺新名守卫（无 EXCEPTION 块会直接炸启动）、09002 ROWID 去重无效——均修（公共列 null-safe 比较）。dm 保持 EXCEPTION 吞咽惯例（与其他 dm 迁移一致，遗留实测）。
- **M2 09001 UNION ALL 重复入队**：经查证 Flyway 10 对**已应用版本**做 checksum 校验（改文件即启动失败，需 repair）——**sqlserver 09001/09002 保持应用时版本不可变**，该改进对全新库（无复合约束场景）非必需，记为接受风险。
- 补测试：合并路径（新旧并存去重合并）、acceptability 占位符配平+小写绑定；修 spec/设计稿计数（16→22 对）与版本号 typo；orm-team.xml 补显式 name；sqlite Java 迁移加子表回填+SQLException。
- 接受风险（记录）：sqlserver 09001 的 UNION/schema 过滤/EXEC 包裹改进未随附；旧名索引残留（Hibernate 会按新名再建，双索引仅空间放大）；ValueMeaning 删除路径 FK 不变式未动。
- 评审修复版已重部署 8081（15s 启动，API 200）。



## 合并 master（2026-10-08，提交 27921a1a）

- 并行会话 personal-no-login-registrar-default（094ab92e）先合入并占用了迁移版本段 20261009001–09004（contact/registrar 卫星）——**本变更迁移重编号 V20261009001/09002→V20261009005/09006**（sqlserver/dm/sqlite-Java + 测试常量 + 主 spec 引用全同步）。真实库已应用旧版本（历史保留），新版本幂等重放无副作用；下次部署 master 将应用对方的 09001–0904 卫星迁移。
- 冲突解决：3 个测试类（import 块取 master 侧——ContactAccountBinding 已更名 ContactExtension；OrmTeamXmlTest 计数 29=27+2）；约 160 个前端产物冲突以合并后 dist 重建整体替换。
- DatabaseEnvironment/orm-team.xml 双方增补自动合并（ext_contact/ext_registrar + transition×3）；合并后全量编译通过、关键 9 测试类 128 tests 全绿、前端 build 过；detect_changes 无 partial、LOW。
- 合并期并行会话又提交 000d514e；stash→merge→pop 模式恢复 WIP：绝大多数已被并行会话自行提交，剩余 NamingConventionList.vue（命名规范会话活跃 WIP）+ 16 个未跟踪文档/归档，stash 快照已核实冗余并 drop。

## 遗留

- 达梦 09001/09002 未实测（TRANSFER 段跳过，迁移 ext 方案单独任务）；
- FlywayHistoryRelocator 同 schema 警告文案小瑕疵（"mdr 与 mdr 并存"）——该组件属 relocate 会话 WIP，未动；
- 待 continue-review → continue-archive；合并 master 时注意 naming-convention 等并行 WIP 同文件仲裁。
