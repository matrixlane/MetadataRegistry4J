# 实施计划:dbo 遗留表清理(cleanup-dbo-leftover-tables,2026-10-28)

输入:`openspec/changes/cleanup-dbo-leftover-tables/tasks.md`(7 项任务,已确认)、
`openspec/changes/cleanup-dbo-leftover-tables/design.md`(D1–D6)、
`docs/superpowers/specs/2026-10-28-dbo-leftover-cleanup-design.md`。

## 环境事实(实施前已核实)

- 三方言迁移目录现行最大版本:`sqlserver`/`dm` = V20261023001,`sqlite` = V20261009012
  → 新迁移统一编号 **V20261028001**(三方言同版本号,变更级对齐)。
- 本机 SQL Server `metadata_registry`:dbo 下 12 张业务表全部 0 行;外键仅存在于
  fhir 孤儿集合内部;`ext.interop_mapping`/`ext.version_history` 存在且 0 行。
- 本机后端运行中(PID 103548,端口 8081);无 sqlite3 CLI,Python 3.14 自带
  sqlite3 模块可用于 SQLite 脚本验证。
- Flyway 基线 20260918002 < 20261028001 → 重启即拾取新迁移。

## 步骤

1. **新增迁移脚本(任务 1.1–1.3)**
   - `sqlserver/V20261028001__Drop_dbo_leftover_tables.sql`:
     依赖序守卫 DROP(fhir_binding→fhir_element→fhir_resource/fhir_value_set/fhir_code_system
     →其余 7 张);`interop_mapping`/`version_history` 三重守卫——行数判定用
     `sys.partitions + OBJECT_ID`(避免"SELECT 引用不存在表"的编译期错误,
     与既有守卫 idiom 一致)。
   - `sqlite/V20261028001__Drop_dbo_leftover_tables.sql`:白名单
     `DROP TABLE IF EXISTS` 10 张非活表,不含 interop_mapping/version_history。
   - `dm/V20261028001__Drop_dbo_leftover_tables.sql`:idiom 同
     `V20260901015`(PL/SQL 块 + `ALL_TABLES` owner 守卫 + `EXCEPTION WHEN OTHERS`),
     每表先查 MDR 再防御性查 EXT,不触碰 INTEROP_MAPPING/VERSION_HISTORY。
2. **SQL Server 实库验证(任务 3.1/3.2 前置)**
   - 先 sqlcmd 手动执行脚本(幂等迁移允许先手工落地);再执行第二遍验证幂等 no-op;
   - 查询 `sys.tables`(dbo)确认 0 业务表,`ext` 活表仍在。
3. **SQLite 脚本验证(支撑任务 1.2)**:Python 内存库建 12 张同名表 → 执行脚本 →
   断言 10 张被删、`interop_mapping`/`version_history` 仍在。
4. **构建回归(任务 2.1)**:`mvn -pl metadata-registry-web -am test`(后台跑)。
5. **重启后端触发 Flyway(任务 3.1)**:按 PID 103548 的原启动方式重启 →
   `ext.flyway_schema_history` 出现 20261028001 且 success=1(此前手工执行过,
   迁移重跑应全守卫 no-op,顺带再验幂等)。
6. **API 冒烟(任务 3.3)**:注册项列表、版本历史、映射管理接口
   (个人版免登录,直接请求)。

## 风险控制

- 迁移在任何一步失败:事务回滚,Flyway 标记失败,修复后重跑(幂等)。
- 后端重启失败:查 `logs/app-*.log`;新迁移守卫全部为 no-op 的预期外失败
  只会来自环境漂移,与脚本无关。
- 不提交代码;提交前另按 AGENTS.md 跑 detect_changes(任务 4.1)。

## 实施中追加发现(2026-10-28)

重启验证时发现 Flyway 写历史表报 515:本库 `ext.flyway_schema_history.installed_on`
的 `DEFAULT (GETDATE())` 在某个历史时刻丢失,而现代 Flyway 的 INSERT 省略该列、依赖
默认值。经 Extended Events(`xe515` 会话)捕获完整错误与真实 INSERT 语句定位。

处置:V20261028001 脚本开头增加幂等自愈守卫(sqlserver:`sys.default_constraints`
检查 + `ADD DEFAULT (GETDATE()) FOR installed_on`;dm:同语义 PL/SQL 防御块)。
V20261028001 从未成功记录,改写安全。该发现同时解释了守卫测试
`CommonBaselineGuardTest` 在 e039f893 后变红之外的又一例"迁移旁路损伤历史表"。
