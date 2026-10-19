# 任务清单

> 实施前置条件：用户确认本变更 proposal / design / specs / tasks。
> 每项数据库运行验证均使用临时副本或临时数据库；不得连接或写入本机真实 SQL Server 库。

## 1. 数据源配置重启后生效

- [x] 1.1 修正配置导入/绑定优先级，使 `application-local.yml` 中的数据源配置覆盖 profile 默认值，同时保留环境变量和 personal/team profile 的预期覆盖语义。
- [x] 1.2 检查 SQLite 保存路径的父目录创建、空格/相对路径处理和 JDBC URL 组装；保证连接测试成功时新文件可创建，SQLite 凭据不落盘。
- [x] 1.3 在临时配置文件与临时 SQLite 路径上验证“保存配置 → 重启 personal profile → 实际连接所选路径”；确认默认路径未被错误复用。

## 2. 全新空库结构初始化

- [x] 2.1 实现 DDL 前的空库识别；只对没有应用表的全新库启用当前 ORM 结构创建，避免在既有 SQLite 库上触发批量元数据复合 SELECT 限制。
- [x] 2.2 保证新 SQLite 文件的父目录和文件可创建，Hibernate schema 保持未配置，Flyway 使用 `main` 作为 `default-schema` 和唯一 schema。
- [x] 2.3 固定启动顺序为：识别/创建实体结构 → Flyway 基线/迁移 → 静态预置 → 初始化向导可用。
- [x] 2.4 将新库共同基线版本更新为 SQL Server 当前头版 `20261009012`；已有 Flyway 历史不得重新基线化或改变历史校验和。
- [x] 2.5 对非空但无 Flyway 历史的数据库添加明确保护：不得静默跳至最新基线；给出可操作错误或兼容方案并保留原数据。

## 3. 迁移序列与数据库结构审查

- [x] 3.1 复核 SQL Server `20261009012` 的最终实体结构及数据迁移语义，确认空库不依赖历史数据迁移产出的业务行。
- [x] 3.2 静态比对三方言基线建表结果：SQL Server `mdr`/`ext` 分层、达梦 `MDR`/`EXT` 分层、SQLite 主库与 `ext_` 卫星表名；排除标准表/扩展列归属偏差。
- [x] 3.3 将迁移目录当前版本清单和共同基线记录在变更验证结果中；后续迁移继续要求三个方言使用 SQL Server 权威版本号及等价语义。
- [x] 3.4 确认新库初始化数据遵守内容链/受管链正交规则；保留注册项语义闭环守护断言。

## 4. 文档

- [x] 4.1 更新 README 首次启动与个人版 SQLite 数据库说明，包括默认路径、选定路径重启生效和自动结构初始化。
- [x] 4.2 检查 `docs/architecture/ARCHITECTURE.md`；若启动依赖顺序或数据库初始化流程发生实质变化，重新生成架构文档。

## 5. 验收

- [x] 5.1 在临时目录以无数据库文件的默认配置启动 personal profile；核对文件、核心 schema、Flyway 基线、静态预置和初始化状态端点。
- [x] 5.2 选择并保存另一个不存在的 SQLite 路径，重启后核对应用使用新路径，且数据库 schema 与基线完整。
- [x] 5.3 在新库完成初始化 setup/commit；通过语义查询验证已标识条目具备完整且互斥的 `Administered_Item`/`Attached_Item` 受管链。
- [x] 5.4 使用临时既有 SQLite 副本验证 Flyway 历史、行数据及迁移校验和保留；不得使用仓库内数据库作为写入目标。
- [x] 5.5 完成 GitNexus `detect-changes --scope all` 与回归评估；对 HIGH/CRITICAL 或 UNKNOWN 风险按 `AGENTS.md` 要求复核后再报告。
