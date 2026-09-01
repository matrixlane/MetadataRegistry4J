# 任务清单:MDR/EXT schema 拆分(mdr-ext-schema-split)

## 1. 机制落地

- [x] 1.1 写 `orm/orm-team.xml`(23 个 EXT 实体 `<table schema="ext"/>` 覆盖,合并模式)
- [x] 1.2 team profile 配置 `spring.jpa.mapping-resources`;`db/schema-init.sql` 增 ext schema;personal/local 注释说明 SQLite 不加载
- [x] 1.3 写一次性校验测试:解析 orm-team.xml,23 个 class 全限定名 Class.forName 可达 + 表名与实体解析一致(防类名漂移)
- [x] 1.4 EXT 实体集合表/派生表核对(@ElementCollection/@JoinTable 逐一确认无 EXT 侧集合表)

## 2. 迁移脚本 V20260901014

- [x] 2.1 sqlserver:23 表 `ALTER SCHEMA ext TRANSFER`,INFORMATION_SCHEMA 守卫幂等
- [x] 2.2 dm:守卫式搬家(CREATE AS SELECT + 约束/索引重建 + DROP),PL/SQL 守卫 + EXCEPTION 容错;EXT 模式预建与授权写部署说明
- [x] 2.3 sqlite:占位注释

## 3. 原生 SQL 核查

- [x] 3.1 排查 nativeQuery/JdbcTemplate/Statement 引用 23 张 EXT 表的位置,team 方言下改显式 `ext.` 前缀(必要时 DatabaseEnvironment 加 extQualify())

## 4. 验证与提交

- [x] 4.1 全模块编译 + 全量测试绿(1488:178/122/0/367/821,含新增 OrmTeamXmlTest)
- [x] 4.2 GitNexus detect_changes 无 partial/truncated
- [x] 4.3 openspec validate 2026-09-01-mdr-ext-schema-split --strict 通过
- [x] 4.4 审计报告 5.1/5.2 标注细粒度方案作废、MDR/EXT 落地;更新记忆 mdr-schema-naming-rules;提交
