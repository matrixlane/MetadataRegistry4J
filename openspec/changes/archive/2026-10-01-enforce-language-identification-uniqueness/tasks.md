# Tasks: enforce-language-identification-uniqueness

> 适配记录（2026-09-30）：原任务 1–2 锚定的 `LanguageInitializer` 已被归档变更 `wizard-static-preset-bootstrap` 删除（语言预置改走 `LanguagePresetSeeder` 纯 SQL 重放，无 JPA 查询）。启动崩溃路径虽已消失，但 `findByLanguageIdentifier`（隐含唯一假设）仍有 8 个业务调用点，风险同源。经与用户确认"继续下去"，应用层防御任务适配为统一防御式解析器 `LanguageIdentifierResolver` 并迁移全部调用点；DB 唯一约束任务不变。

## 1. 仓库派生方法与 TDD 红测试

- [x] 1.1 `LanguageIdentificationRepository` 新增 `List<LanguageIdentification> findAllByLanguageIdentifier(String languageIdentifier)` 派生方法，验证：`./mvnw -pl metadata-registry-web -am compile` 通过
- [x] 1.2 新增 `LanguageIdentifierResolverTest` 覆盖 `resolve` 三种情况（0 行返回 empty / 1 行返回该行 / 多行返回 MIN(id) 行 + WARN 不抛异常）的红测试，验证：`./mvnw -pl metadata-registry-web -am test -Dtest='LanguageIdentifierResolverTest' -DfailIfNoTests=false`（注意 `-Dtest` 多个类用逗号分隔，加号会静默匹配不到）

## 2. 防御式解析器与 TDD 绿测试

- [x] 2.1 新增 `LanguageIdentifierResolver`（`@Component`）：`resolve(String)` 用 `findAllByLanguageIdentifier`，按"0 行 → empty / 1 行 → 该行 / 多行 → MIN(id) 保留行 + WARN（含 identifier/计数/保留行 id）"分支，验证：`LanguageIdentifierResolverTest` 红测试全转绿
- [x] 2.2 迁移全部 8 个 `findByLanguageIdentifier` 业务调用点到 `LanguageIdentifierResolver.resolve`（调用点保持 `.orElseGet(...)`/`.orElseThrow(...)` 链不变）：`SystemDefaultNamespaceInitializer`、`ContextTestDataInitializer`、`MergeExecutor`、`GlobalNamespaceService`、`GenericItemCreator`、`InitWizardService`、`RegistrySpecificationService`、`RegistryContextBootstrapService`，验证：`grep -rn "findByLanguageIdentifier" metadata-registry-web/src/main/java` 仅剩 resolver/repository 内引用；调用点所属测试类全绿
- [x] 2.3 新增 `LanguageIdentificationRepositoryTest` 覆盖 `findAllByLanguageIdentifier` 派生方法的多行场景，验证：`./mvnw -pl metadata-registry-web -am test -Dtest='LanguageIdentificationRepositoryTest' -DfailIfNoTests=false` 0 failures

## 3. 三方言迁移脚本（前会话已落盘，本次核验）

- [x] 3.1 SQLite `V20260926001`：dedup 保留 MIN(id) → 8 张 FK 表重定向（`registry_specification_alternative_language` 在 SQLite 由 ddl-auto 建表，注释已声明 follow-up）→ 删多余行 → `CREATE UNIQUE INDEX uq_language_identification_identifier`，已核验内容符合
- [x] 3.2 SQL Server `V20260926001`：CTE + `ROW_NUMBER()` dedup，9 张 FK 表合并，已核验内容符合
- [x] 3.3 达梦 `V20260926001`：仿 SQL Server（ROW_NUMBER + EXECUTE IMMEDIATE），已核验内容符合；DM 环境不可达，实测记 backlog

## 4. 集成验证

- [x] 4.1 重新打包：`./mvnw -pl metadata-registry-web -am clean package -DskipTests`，验证：BUILD SUCCESS；`unzip -p target/metadata-registry-web-0.1.0-SNAPSHOT.jar BOOT-INF/classes/db/migration/sqlserver/V20260926001__Enforce_language_identifier_unique.sql` 内容与源码一致
- [x] 4.2 SQLite 副本库验证（脏数据场景）：`scratch/` 模式下 INSERT 一行重复 `id-ID` → 应用迁移 → 验证 dedup、UNIQUE INDEX 存在、再次 INSERT 重复被拒绝；启动后 WARN 路径由 `LanguageIdentifierResolverTest` 覆盖，验证：手工 SQL 核查通过
- [x] 4.3 SQL Server 真实库端到端验证：后端重启自动应用 V20260926001（注意该实例有 mdr.individual QP 8624 史，本迁移不涉及该表 DDL/DML 删除），验证：索引存在；应用运行无 `NonUniqueResultException`
- [x] 4.4 达梦记"待实测"（KNOWN_FAILURES / session journal）

## 5. 文档与 KNOWN_FAILURES 同步

- [x] 5.1 `.superpowers-memory/KNOWN_FAILURES.md` 新增条目 "Failure Pattern: mdr.* 自然键列缺 UNIQUE 约束 + JPA findBy* 隐含唯一假设"，记录触发条件、症状、根因、检测方式、本次缓解、后续 backlog（mdr.namespace、mdr.account、mdr.organization、mdr.registration_authority 等同类表审计清单），验证：条目字段齐全
- [x] 5.2 新建 session journal `session-journal/2026-09-30-language-identification-uniqueness.md`，记录 2026-09-25 故障、根治变更（含 LanguageInitializer 已删除的适配事实）、验证证据，验证：文件存在且内容完整

## 6. OpenSpec 与全局校验

- [x] 6.1 `openspec status --change "enforce-language-identification-uniqueness" --json` 校验，验证：status 全 ready
- [x] 6.2 `openspec validate "enforce-language-identification-uniqueness"` 校验，验证：无 error / 无 critical warning
- [x] 6.3 后端全量测试 `./mvnw -pl metadata-registry-web -am test`，验证：0 failures（既有 WIP 失败除外：FlywayHistoryRelocator*、WorkspaceServiceIntegrationTest、SegmentTemplateStrategyIntegrationTest、RegistrarServiceTest 既有 mock 缺陷）
- [x] 6.4 `cd metadata-registry-ui && npm run build`，验证：本次无前端变更，build 仍通过
