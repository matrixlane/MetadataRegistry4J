# legacy-language-schema-repair 验证记录

日期：2026-10-20 / 2026-10-21

## 1. 修复器单元/集成测试（真实 SQLite 内存库）

`LegacyLanguageSchemaRepairerTest`（7 用例，全部通过）：

| 用例 | 结果 |
|---|---|
| 本机代漂移（language_id+names，含 zh-CN 数据行）：重建标准 5 列、名称归位 ext（language_id==原 id）、二次 no-op | ✅ |
| enabled 列迁移 language_governance：true/false/null→coalesce(true)、守卫不重复 | ✅ |
| 端到端：空漂移表修复 → Seeder 种入 22+22（zh-CN 最终名称"简体中文"） | ✅ |
| 健康库（标准 5 列）no-op | ✅ |
| 非 SQLite 方言（SQL Server）no-op、零 SQL | ✅ |
| 唯一索引数据冲突：修复完成、重复行保留、无异常 | ✅ |
| 缺列旧代建表：5 列补全、缺失列 NULL 兜底、名称归位 | ✅ |

## 2. 全量回归（web 模块，修复器版本）

`mvnw -pl metadata-registry-web test`：**1554 用例，7F+4E**——与并行变更文档化既有基线（RegistrySpecification 1F、WorkspaceService 6F+3E、SegmentTemplate 并发锁抖动 1E，见 `.superpowers-memory/CURRENT_STATE.md`）**逐项一致，零新增回归**。

## 3. 真实遗留库旁路实证（8082 + 旧库副本，2026-10-20 23:48）

新 jar 以 `--spring.datasource.url=jdbc:sqlite:./data/metadata-registry-legacy-copy.db --server.port=8082` 启动（副本 = 当日漂移库逐字节复制件）：

```
23:48:18.804 [WARN] LegacyLanguageSchemaRepairer 检测到遗留漂移 language_identification：
      额外列 [language_id, language_name, territory_name]、缺失列 []；执行数据归位 + 表重建
23:48:18.883 [INFO] 已归位 0 行语言名称/地区名称至 ext_language_identification（空库，符合预期）
23:48:18.930 [INFO] 遗留语言表修复完成：language_identification 已重建为当前实体形状（0 行数据保留）
23:48:19.149 [INFO] LanguagePresetSeeder 已重新应用 V20260919003（44 条语句）   ← 无跳过 WARN
23:48:19.156 [INFO] LanguagePresetSeeder 已重新应用 localize-language-names.sql（1 条语句）
```

`GET :8082/api/v1/languages` → 22 条，zh-CN = 简体中文。**修复前同一副本上 44 条全部跳过（2026-10-20 19:39 启动日志实证），修复后 44+1 条全部成功。**

## 4. 本机空库 FRESH 重建实证（8081，2026-10-21 00:03）

1. `data/metadata-registry.db` 备份至 `data/metadata-registry.db.legacy-bak-20261020` 后删除（原库全空：111 表仅 account×1 + identifier_strategy_config×1）；
2. 新 jar 启动 → `FreshDatabaseInitializer` 检测到全新空库 → Flyway `Successfully baselined schema with version: 20261009012`；
3. DB 实证：`flyway_schema_history` 1 行 BASELINE@20261009012（success=1）；112 张用户表；`language_identification` 恰为标准 5 列（无遗留列）；languages=22、ext names=22；
4. `GET :8081/api/v1/languages` → 22 条（zh-CN 简体中文）；`GET /api/v1/init-wizard/status` → 200（initialized=false，向导可继续）；
5. 修复器在新库上以 debug 跳过（PRAGMA table_info 一次，无漂移命中）。

### 4.1 实施期发现并修复的关联缺陷

首次 FRESH 重建（2026-10-20 23:49，修复前 jar）实证 **`flyway_schema_history` 未生成**：`FreshDatabaseInitializer.apply()` 入口先查 `spring.flyway.enabled`、本地配置（旧格式 `~/.metadata-registry/application-local.yml`）置 false 即整体早退——与三处文档化意图矛盾（本类 javadoc"全新库必须启用 Flyway 完成基线化"、本地配置注释"全新空库不受影响"、dd8d9dbe 提交说明）。

修正：`apply()` 调整为先做 FRESH 识别，全新库无条件应用引导覆盖（强制 flyway.enabled=true 等），`flyway.enabled` 检查移至非全新库分支。`FreshDatabaseInitializerTest` 同步：`flywayDisabledIsIgnored` 改为既有库 + 关闭 → 忽略（原断言对全新库失效），新增 `flywayDisabledDoesNotBlockFreshBootstrap`（全新库 + 关闭 → 仍基线化；属性源位置按生产实际模拟：localConfig 在 systemEnvironment 之后）。8 用例全绿。

## 5. detect-changes

- CLI（`node .gitnexus/run.cjs detect-changes`）：失败——Windows ARM64 无 LadybugDB 原生二进制（与并行会话记录同一遗留）。
- MCP `detect_changes(scope=all)`：成功。**检出变更全部属于并行变更 `administered-item-reference-documents-ui` 的未签入修改**（AdministeredItemRepository.findMetaByItemId/findMetaByItemIds、ItemAdministrationService.buildItemMeta/preferPrimaryScalarIdentifier、PersonalEditionAuthenticationFilterTest、IdentifierMetadataTest，risk=critical 归该变更）；本变更文件不在索引内（索引滞后 HEAD 2 提交）。
- 本变更风险经文本搜索确认：FreshDatabaseInitializer 调用方仅 `MetadataRegistryApplication.main` 注册处；LegacyLanguageSchemaRepairer 为新增类、无既有调用方。风险 LOW。

## 6. 遗留与边界

- 修复器仅 SQLite 方言生效；SQL Server/达梦迁移建库无此漂移，no-op。
- 遗留库其余非语言表的列漂移不在本变更范围（本机已由 FRESH 重建全库归位；其他有数据遗留库的其他表漂移维持既有行为）。
- 本机旧漂移库备份保留于 `data/metadata-registry.db.legacy-bak-20261020`，确认新库稳定后可删除。
- 工作区并行变更 `administered-item-reference-documents-ui` 未签入修改未受影响；本变更签入仅含显式文件清单。
- `.superpowers-memory` 更新（CURRENT_STATE + journal）随本变更一并写入但**未单独签入**（与并行变更同等待其归档时提交）。
- README / ARCHITECTURE.md 无用户可见变化（内部启动加固），未改动。

