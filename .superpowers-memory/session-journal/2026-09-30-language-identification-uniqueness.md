# 2026-09-30 enforce-language-identification-uniqueness（语言标识自然键唯一约束 + 应用层防御式解析）

**状态：实现完成，待归档。** OpenSpec 变更 `openspec/changes/enforce-language-identification-uniqueness`（spec delta：`language-identification-uniqueness` 3 个 Requirement / 8 个 Scenario）。本次只做仓库派生方法 + 防御式解析器 + 8 个调用点迁移 + SQLite 实测 + 文档；三方言迁移脚本 `V20260926001__Enforce_language_identifier_unique.sql` 由前序会话落盘，本次核验并实测 SQLite 侧。SQL Server 真实库端到端、达梦实测留 backlog。

## 起因：2026-09-25 23:59 启动崩溃事故链

1. 外部代码（或同步脚本／手工 SQL）向 `mdr.language_identification` 插入了一行 `language_identifier` 已存在的行。该表当时**只有主键约束**，没有 `UNIQUE(language_identifier)`，所以插入成功。
2. 应用启动期，语言播种路径用 Spring Data JPA 派生方法 `findByLanguageIdentifier` 按该列解析语言，返回类型 `Optional<LanguageIdentification>` —— 隐含"至多一行"。
3. 命中重复行，Hibernate 抛 `NonUniqueResultException`。
4. 该查询发生在播种侧 Runner（`ApplicationRunner`）里，异常**终止 Spring 上下文刷新**。
5. 内嵌 Tomcat 随之优雅停机，后端 503。

关键教训是放大效应：脏数据只多了一行，但落在启动 Runner 上就从"某个功能读不到"升级成"整个后端不可用"。

## 根治（三件事，缺一不可）

### 1. DB 侧唯一约束 —— 三方言 `V20260926001__Enforce_language_identifier_unique.sql`

- SQLite / SQL Server / 达梦三份脚本，索引名统一 `uq_language_identification_identifier`。
- 走 `CREATE UNIQUE INDEX` 而非 `ALTER TABLE ... ADD CONSTRAINT`：SQLite 不支持后者，只有索引路线能让三方言 SQL 形态一致（D1）。
- 迁移**自带幂等 dedup**，不依赖人工预处理（D2）：按 `language_identifier` 分组保留 `id` 最小行 → 8/9 张 FK 引用表 `language_id` 重定向到保留行 → 删多余行 → `CREATE UNIQUE INDEX IF NOT EXISTS`。
- SQL Server / 达梦用 `ROW_NUMBER() OVER (PARTITION BY ...)` 合并 9 张引用表；SQLite 用 `MIN(id)` 相关子查询。
- 不引入 `NOT NULL` 约束（超出范围）。

### 2. 应用层统一防御式解析器 `LanguageIdentifierResolver`

`metadata-registry-web/.../service/LanguageIdentifierResolver.java`，`@Component` + `@RequiredArgsConstructor`，单个方法 `resolve(String) -> Optional<LanguageIdentification>`，走新的派生方法 `findAllByLanguageIdentifier`：

- 0 行 → `Optional.empty()`，调用方按既有"不存在"分支处理（创建或按域规则报错）
- 1 行 → 该行，**不记日志**
- 多行 → 取 **id 文本字典序最小**的行 + WARN（含 `language_identifier` 值、命中行数、保留行 id），**不抛异常**

DB 约束是终极防线，解析器是第二道防线——历史脏数据或绕过约束的写入不该让业务读取崩掉。WARN 的作用是让运维/监控发现脏数据并安排清理，而不是沉默容忍（D3 决策：宽容 + WARN，既不严格报错也不静默吞掉）。

**保留行口径必须用 `UUID.toString()` 字典序，不能用 `UUID.compareTo`**：后者把 `mostSigBits`/`leastSigBits` 各按**有符号** 64 位比较，两枚 id 符号位不同时结论与 SQL `MIN(id)`（16 字节大端的**无符号**字节序）相反。`UUID.randomUUID()` 约一半的 id 符号位为 1，所以这不是边角情形。已写进 resolver 的 Javadoc，并由 `scratch/verify-language-uniqueness/verify.py` 步骤 5 实测钉住（`compareTo(0000…, 8000…) = 1`，与文本序 `0000… < 8000…` 相反）。

### 3. 8 个业务调用点全部迁移

主源码不再有 `findByLanguageIdentifier` 的直接业务调用，只剩仓库接口声明本身（`grep -rn "findByLanguageIdentifier" metadata-registry-web/src/main/java`）。

| 文件 | 原调用 |
| --- | --- |
| `web/seed/SystemDefaultNamespaceInitializer.java` | `createLanguage` |
| `web/seed/ContextTestDataInitializer.java` | `findOrCreateLanguage` |
| `web/federation/MergeExecutor.java` | `resolveLanguageId` |
| `web/federation/GlobalNamespaceService.java` | `addDesignation` |
| `web/federation/GenericItemCreator.java` | `resolveLanguage` |
| `web/service/InitWizardService.java` | 文档语言 `documentation_language_identifier` 循环 |
| `web/service/RegistrySpecificationService.java` | `defaultLanguageId` |
| `web/service/RegistryContextBootstrapService.java` | `findOrCreateLanguage` |

迁移只把查询入口换成 `languageIdentifierResolver.resolve(x)`，**后续 `.orElseGet(...)` / `.map(...::getId).orElse(null)` 链一律不动**，调用点语义零变化。`languageRepository` 字段全部保留（`save` 仍在用）。

## 重要适配事实：`LanguageInitializer` 已被删除

原任务锚定的防御目标是 `LanguageInitializer`（`@Order(1)` `ApplicationRunner`，22 条语言代码硬编码）。归档变更 `wizard-static-preset-bootstrap`（2026-09-29）把语言预置改为 `LanguagePresetSeeder` 纯 SQL 重放（`V20260919003` + `localize-language-names.sql`），并**删除了 `LanguageInitializer`**。于是：

- 启动崩溃的那条具体路径已经消失（预置不再有 JPA 语言写入，也就不会触发 `NonUniqueResultException`）。
- 但 8 个业务调用点的 `findByLanguageIdentifier` **仍在**，风险同源——任何一个业务请求走到重复行仍会炸，只是范围从"全站 503"缩小为"该请求 500"。
- DB 唯一约束的任务不变（脏数据仍会被外部写入产生，且历史库已经带着重复行）。

经与用户确认"继续下去"，应用层防御目标从"改 `LanguageInitializer`"改为"新增统一解析器 + 迁移全部调用点"。

## 验证

### TDD 红 → 绿

- 红：先写 `LanguageIdentifierResolverTest`（`@ExtendWith(MockitoExtension)`，mock `LanguageIdentificationRepository`，用 logback `ListAppender` 抓 WARN），跑 `./mvnw -pl metadata-registry-web -am test -Dtest='LanguageIdentifierResolverTest'` → `BUILD FAILURE`，`找不到符号: 类 LanguageIdentifierResolver`（定位到测试第 45 行的 `@InjectMocks` 字段）。
- 绿：实现 resolver 后同命令 → `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
- 4 个用例覆盖：0 行→empty 且无 WARN / 1 行→该行且无 WARN / 多行（乱序注入）→id 字典序最小行 + WARN 含 identifier/行数/保留行 id 且不抛异常 / 多行后其他标识符解析照常推进。

### 回归

- `./mvnw -pl metadata-registry-web -am test -Dtest=LanguageIdentifierResolverTest,LanguageIdentificationRepositoryTest` → `Tests run: 6, Failures: 0`。
- 8 个调用点所属测试类（实际类名：`ContextTestDataInitializer` 无独立测试）：`SystemDefaultNamespaceInitializerTest`、`RegistrySpecificationServiceTest`、`RegistryContextBootstrapServiceTest`、`GlobalNamespaceServiceTest`、`GenericItemCreatorTest`、`MergeExecutorStrategyTest`（无 `MergeExecutorTest`）、`InitWizardServiceIntegrationTest`，外加 `StaticDataPresetServiceTest`、`FactoryResetLanguagePresetIntegrationTest` → **60 tests, 0 failures**。`InitWizardServiceIntegrationTest` 15 个全绿，**无既有失败**。
- 二阶受影响（间接引用上述类）：`ItemAdministrationServiceTest`、`NamespaceGateIntegrationTest`、`OrganizationServiceTest`、`FederationBundleServiceTest`、`InitWizardControllerTest`、`RegistrySpecificationControllerTest`、`DataGovernanceReadinessServiceTest`、`ResetDatabaseHelperTest` → **94 tests, 0 failures**。
- 5 个单测类需把 `languageRepository.findByLanguageIdentifier` 的 stub 改为 `languageIdentifierResolver.resolve` 并补 `@Mock LanguageIdentifierResolver`：`SystemDefaultNamespaceInitializerTest`、`GlobalNamespaceServiceTest`、`RegistrySpecificationServiceTest`、`GenericItemCreatorTest`、`MergeExecutorStrategyTest`。

### SQLite 脏数据场景实测（`scratch/verify-language-uniqueness/verify.py`）

在 `metadata-registry-web/metadata_registry.db` 的**副本**上跑（真实库只读，脚本结束删临时库、保留脚本；标准库 `sqlite3`，无 `sqlite3` CLI 依赖）：向 `ar-SA` 插一行 `id=ffffffff-…-ffffffffffff` 的脏数据并在 8 张引用表挂上指向该脏行的引用 → 逐语句执行迁移（切出 10 条）→ **28 项校验全通过**：

- 重复组只剩 1 行，保留行 = `MIN(id)` = `0653e3e3-181d-4f4c-afd3-0f7c5bf35ee5`；脏行已删；全表无重复 `language_identifier`。
- 8 张引用表（`definition`、`designation`、`ext_language_identification`、`language_extension`、`language_variant`、`reference_document_language`、`registration_authority_doc_language`、`registry_specification.default_language_id`）引用全部重定向到保留行，且**无悬空引用**。
- `PRAGMA index_list` 可见 `uq_language_identification_identifier`，`unique=1`，索引列 `['language_identifier']`；此后再次 INSERT 重复行报 `UNIQUE constraint failed: language_identification.language_identifier`（`IntegrityError`）。
- 真实库里 `ext_language_identification` 不存在（该库建库早于 V20260919001），脚本为其补了最小 schema 以让 8 条 UPDATE 全部走到，输出里明确标注为"合成表"，不冒充真实数据。

### 打包内容核验

`./mvnw -pl metadata-registry-web -am package -DskipTests` → BUILD SUCCESS；`unzip -p` 出的 `db/migration/{sqlite,sqlserver,dm}/V20260926001__Enforce_language_identifier_unique.sql` 与源码**逐字节一致**（5051 / 6459 / 3946 bytes）。

## 踩坑记录

- **`-Dtest` 假绿 + 并发会话干扰叠加**：中途一次 `-Dtest=A,B` 只跑了 B 没跑 A。排查发现是另一个会话正在改 `Account.java`（移除非标准字段 `avatar`）写到中间态，`AuthServiceAvatarTest` 编译失败 → 整个 test 编译中断 → surefire 拿旧的 `test-classes` 跑出一半用例并 BUILD SUCCESS。**判据是日志里的 `Running` 行与 `Tests run:` 计数，不是 BUILD 结果**；并发会话下每次都要核对这两个。
- **JUnit 局部变量不能被 lambda 捕获**：红测试里用 `assertThatCode(() -> resolved = ...)` 报"局部变量必须 final"，改用 `AtomicReference`。
- **Windows 侧 `unzip` 的 jar 内路径不带 `BOOT-INF/classes/` 前缀**（该模块 `package` 产出的是普通 jar），按 Spring Boot 惯例拼路径会得到空文件，`diff` 静默判为 DIFFERS。

## 遗留 / backlog

1. **SQL Server 真实库端到端未验**：后端重启会自动应用 `V20260926001`。注意该实例有 `mdr.individual` QP 8624 历史，本迁移不涉及该表的 DDL/DML 删除。验完确认索引存在且运行期无 `NonUniqueResultException`。
2. **达梦未实测**（无实例，沿用先例）：脚本仿 SQL Server（`ROW_NUMBER` + `EXECUTE IMMEDIATE`），窗口函数兼容性留待有实例时确认。
3. **SQLite 侧 follow-up**：`registry_specification_alternative_language` 在 SQLite 下由 Hibernate `ddl-auto=update` 运行时创建，迁移不主动 UPDATE 它；若该表存在且其 `language_id` 指向被丢弃的孤儿行，lazy load 会报 `EntityNotFoundException`。场景极少见、不影响本次启动阻塞修复，脚本注释已声明。
4. **同类表审计**（见 `KNOWN_FAILURES.md` 新增条目 `failure-2026-09-30-natural-key-without-unique-constraint`）：`mdr.namespace`（`shorthand_prefix`）、`mdr.account`（`username`）、`mdr.organization`、`mdr.registration_authority`、`mdr.identifier_strategy_config`——按用户"只修报告的"原则留待报告再修。
5. 本次未跑后端**全量**测试（任务 6.3），只跑了全部直接与二阶受影响类；并发 WIP（`FlywayHistoryRelocator*`、`WorkspaceServiceIntegrationTest`、`SegmentTemplateStrategyIntegrationTest`、`RegistrarServiceTest`）红不属本次变更，归档前需在干净树上再跑一次全量。
