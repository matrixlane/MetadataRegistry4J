# Tasks: enforce-language-identification-uniqueness

## 1. 仓库派生方法与 TDD 红测试

- [ ] 1.1 `LanguageIdentificationRepository` 新增 `List<LanguageIdentification> findAllByLanguageIdentifier(String languageIdentifier)` 派生方法，验证：`./mvnw -pl metadata-registry-web -am compile` 通过
- [ ] 1.2 新增 `LanguageInitializerTest` 覆盖 `upsertLanguage` 三种情况（0 行 / 1 行 / 多行）的红测试，验证：`./mvnw -pl metadata-registry-web -am test -Dtest='LanguageInitializerTest' -DfailIfNoTests=false` 红测试全部失败

## 2. `LanguageInitializer` 防御重写与 TDD 绿测试

- [ ] 2.1 `LanguageInitializer.upsertLanguage` 改用 `findAllByLanguageIdentifier`，按"0 行 → 创建 / 1 行 → 跳过 / 多行 → 取 id 最小保留行 + WARN"分支处理，验证：`./mvnw -pl metadata-registry-web -am test -Dtest='LanguageInitializerTest' -DfailIfNoTests=false` 红测试全部转绿
- [ ] 2.2 新增 `LanguageIdentificationRepositoryTest` 覆盖 `findAllByLanguageIdentifier` 派生方法的多行场景，验证：`./mvnw -pl metadata-registry-web -am test -Dtest='LanguageIdentificationRepositoryTest' -DfailIfNoTests=false` 0 failures

## 3. 三方言迁移脚本

- [ ] 3.1 新增 `metadata-registry-web/src/main/resources/db/migration/sqlite/V20260926001__Enforce_language_identifier_unique.sql`：dedup 阶段按 `language_identifier` 分组保留 id 最小行 → 9 张 FK 引用表（`reference_document_language`、`registration_authority_doc_language`、`registry_specification_alternative_language`、`registry_specification`（列 `default_language_id`）、`definition`、`designation`、`language_extension`、`language_variant`、`ext.language_identification`）的 `language_id` 重定向 → 删多余行 → `CREATE UNIQUE INDEX uq_language_identification_identifier ON mdr.language_identification(language_identifier)`，验证：SQLite 副本库先人工 INSERT 一行重复 `id-ID`（制造脏数据）后应用迁移成功，`UNIQUE INDEX` 存在且应用后无法再 INSERT 重复行
- [ ] 3.2 新增 `metadata-registry-web/src/main/resources/db/migration/sqlserver/V20260926001__Enforce_language_identifier_unique.sql`：用 CTE + `ROW_NUMBER() OVER (PARTITION BY language_identifier ORDER BY id)` 实现 dedup，FK 引用合并同样更新 9 张表，验证：SQL Server 真实库（现状 22 行干净）应用迁移成功；备用测试副本库（人工插入 2 行重复 `id-ID`）应用迁移成功并合并 FK 引用
- [ ] 3.3 新增 `metadata-registry-web/src/main/resources/db/migration/dm/V20260926001__Enforce_language_identifier_unique.sql`：达梦方言实现，仿 SQL Server 写法（DM8 支持窗口函数），验证：达梦副本库（若可达）应用迁移成功；如 DM 环境暂不可达，tasks.md 任务 4.3 备注降级验证方案

## 4. 集成验证

- [ ] 4.1 重新打包：`./mvnw -pl metadata-registry-web -am clean package -DskipTests`，验证：BUILD SUCCESS，新 jar 时间戳更新；`unzip -p target/metadata-registry-web-0.1.0-SNAPSHOT.jar BOOT-INF/classes/db/migration/sqlserver/V20260926001__Enforce_language_identifier_unique.sql` 内容与源码一致
- [ ] 4.2 SQL Server 真实库端到端验证：通过 `start.bat` 重启后端，验证：`actuator/health` 返回 200；日志包含"内置语言标识数据初始化完成"且无 `NonUniqueResultException`；`SELECT * FROM sys.indexes WHERE name = 'uq_language_identification_identifier'` 存在该索引；尝试 `INSERT` 一行重复 `language_identifier` 被 DB 拒绝
- [ ] 4.3 SQLite 副本库验证（脏数据场景）：`scratch/` 模式下 INSERT 一行重复 `id-ID` → 应用迁移 → 验证脏数据被 dedup、`UNIQUE INDEX` 存在、再次 INSERT 重复被拒绝；后端启动后 `LanguageInitializer` 输出对应 WARN 日志
- [ ] 4.4 达梦副本库验证（如可达）：同 4.3；不可达时在 `KNOWN_FAILURES.md` / `session-journal/` 记录降级为"DM 环境待实测"作为 follow-up

## 5. 文档与 KNOWN_FAILURES 同步

- [ ] 5.1 `.superpowers-memory/KNOWN_FAILURES.md` 新增条目 "Failure Pattern: mdr.* 自然键列缺 UNIQUE 约束 + JPA findBy* 隐含唯一假设"，记录触发条件、症状、根因、检测方式、本次缓解、后续 backlog（mdr.namespace、mdr.account、mdr.organization、mdr.registration_authority 等同类表审计清单），验证：条目字段齐全（id / status / confidence / source / last_updated / review_after）
- [ ] 5.2 新建 session journal `session-journal/2026-09-26-language-identification-uniqueness.md`，记录本次故障、临时止血、根治变更、验证证据，验证：文件存在且包含 2026-09-25 故障描述与 2026-09-26 根治验证记录

## 6. OpenSpec 与全局校验

- [ ] 6.1 `openspec status --change "enforce-language-identification-uniqueness" --json` 校验，验证：`status` 全为 `"ready"` 或对应 artifact 完成
- [ ] 6.2 `openspec validate "enforce-language-identification-uniqueness"` 校验，验证：无 error / 无 critical warning
- [ ] 6.3 `bunx gitnexus@latest detect-changes --scope all --repo .` 校验，验证：无 partial / truncated
- [ ] 6.4 `./mvnw -pl metadata-registry-web -am test` 后端全量测试，验证：0 failures（既有 baseline 1971 tests + 新增 LanguageInitializerTest / LanguageIdentificationRepositoryTest 不破坏）
- [ ] 6.5 `cd metadata-registry-ui && npm run build` 前端构建，验证：本次无前端变更，build 仍应通过（如有 VTU 警告确认与本次无关）
