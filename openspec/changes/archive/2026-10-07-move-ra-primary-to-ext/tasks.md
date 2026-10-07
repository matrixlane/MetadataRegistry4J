# Tasks: move-ra-primary-to-ext

- [x] 1.1 新增 `RegistrationAuthorityExtension` 实体（ext 模块）+ 仓储 + orm-team.xml/orm-sqlite.xml 登记
- [x] 1.2 三方言迁移 V20261008001：建卫星表→搬数据→删 mdr.is_primary（守卫惯例）；真实 Flyway 重放测试（参照 RegistrationAuthorityPrimaryFlagMigrationTest 模式）
- [x] 1.3 调用点迁移：RegistrationAuthority 实体删字段；向导置位/启动回填/删除保护/deletable 计算/级联删除补删卫星行；全部测试适配（RegistrationAuthorityTest/ServiceTest/DeletionServiceIntegrationTest/RegistryContextBootstrapServiceTest/InitWizard 系列）
- [x] 1.4 全量测试 + detect_changes + 归档（归档留待提交时执行：并行会话正在 openspec/ 目录有 WIP，本次不动其文件）
