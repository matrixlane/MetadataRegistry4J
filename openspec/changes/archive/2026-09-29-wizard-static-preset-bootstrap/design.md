# Design: wizard-static-preset-bootstrap

> Superpowers 设计稿：`docs/superpowers/specs/2026-09-29-init-wizard-static-preset-design.md`（用户已确认按推荐方案 A 实施）。本文件为 OpenSpec 设计快照，语义以上述设计稿为准。

## Context

静态预置数据当前散落在 8 个载体，存在双数据源（SQL 脚本 vs Java 硬编码）、时机不一致、出厂后缺项（`identifier_strategy_config` 全局默认行）、演示数据混入初始化等问题；前端语言下拉全线显示 BCP 47 标识符。用户要求：向导"数据库设置"完成后立即预置全部静态数据，之后步骤只处理用户数据；所有语言下拉显示中文名称。

## Goals / Non-Goals

- Goals
  - 静态预置单一入口 `presetAll()`，启动 / 出厂 / 向导兜底三处复用，顺序一致、幂等。
  - 语言代码、语言中文名称、系统默认组织/命名空间、全局默认标识策略、占位超级管理员、标准注册流转规则全部纳入静态预置。
  - 向导 `setup()` 不再写静态预置；清理用户数据后静态数据完整。
  - 语言下拉显示中文名称（zh-CN=简体中文），向导文档语言选项 API 驱动。
- Non-Goals
  - Registry Context 创建时机（仍在 commit 后）。
  - 运行期数据源动态切换。
  - 当前数据库实例的修复。

## Decisions

| 决策 | 结论 | 理由 |
| --- | --- | --- |
| 预置时机 | 启动期统一预置（数据库设置保存 → 重启 → Flyway 建表 → `presetAll()`） | 与既有 Flyway + ApplicationRunner 架构一致；重启本来就是向导既有流程 |
| zh-CN 显示口径 | 「简体中文」（新增迁移 + 预置重放双通道） | BCP 47 zh-CN 隐含 Hans；用户 2026-09-29 裁定 |
| Registry Context | 归为用户配置后派生数据 | 依赖用户提交的 RA / 命名空间 |
| 演示数据 | 独立开关 `app.seed.demo-data.enabled`，默认 false | 出厂/初始化语义与演示数据隔离 |
| 数据源原则 | 预置数据值仅存于 SQL 脚本；Java 仅执行/装配 | 2026-09-19 用户裁定 |
| 向导清理 | 保留集增加两张纯静态语言表；其余照旧全清 + `presetAll()` 幂等兜底 | 最小改动保证语义正确 |

## 静态预置清单（presetAll 步骤序）

1. 重放预置 SQL 脚本清单（方言按 `DatabaseVendor` 取目录）：`V20260919003`（语言代码+名称）→ 名称口径修正语句（zh-CN=简体中文，独立资源文件）。
2. 系统组织（固定 UUID `…0001`）+ `sys:` 系统默认命名空间 + 中英双指称（JPA 幂等，存在即跳过）。
3. `identifier_strategy_config` 全局默认行（`strategy_type='UUID'`, `identifier_type='INTERNAL'`, `is_global_default=1`，幂等守卫，三方言）。
4. 占位超级管理员 `admin / NO_PASSWORD`（账户表非空即跳过）。
5. 每 RA 标准注册流转规则调和（既有 `RegistrationProcessService.ensureSeed` 语义，不覆盖用户定制规则）。

执行顺序约束：须在 `IdentifierStrategyLoader`（`@Order(90)`）装载前完成，或由入口完成后显式 `refreshRegistry()`。

## 关键流程

### 初始化向导

```
步骤1 版本选择 ── 用户输入
步骤2 数据库   ── 测试连接 → 保存配置 → 重启 ──▶ 启动期 presetAll() 全部静态数据就绪
步骤3~6       ── 仅用户数据（RA / 命名空间 / 注册规范 / 管理员密码）
setup()       ── clearBusinessDataOnly(保留 account, system_setting, flyway_schema_history,
                language_identification, ext_language_identification)
              → presetAll() 幂等兜底 → 用户数据写入
commit()      ── 管理员密码 + initialized 翻旗 + Registry Context（派生）
```

### 恢复出厂

```
clearAllTables(保留 flyway_schema_history) → reseedAdminUser(admin/Admin@123)
→ presetAll()（占位管理员步骤因账户非空跳过）
```

### 启动期

Flyway 迁移（含 V20260929001 名称口径）→ `StaticDataPresetRunner.presetAll()`（@Order 在依赖装载之前/之后按约束调整）→ `IdentifierStrategyLoader` → 其余既有 Runner。

## Risks / Mitigations

- 清理保留集收窄误伤静态表 → 保留集显式枚举 + 集成测试断言两张语言表清理后行数不变。
- 迁移与预置重放双通道漂移 → 名称口径修正语句在两个通道中保持同一份文本（迁移文件引用同一语义，测试断言出厂后 zh-CN=简体中文）。
- `LanguageInitializer` 删除后其他组件隐式依赖（`SystemDefaultNamespaceInitializer.createLanguage` 兜底、向导文档语言关联）→ 统一入口先于这些调用执行；兜底保留"缺语言则建"的防御查询但不预置名称。

## Migration / Rollback

- 新增 `V20260929001__Localize_language_names.sql`（sqlite / sqlserver / dm 各一份），仅 UPDATE zh-CN 行 `language_name`，幂等（WHERE 命中才更新），无回滚需求（值可由语言页编辑恢复）。
- 预置重放资源：`db/preset/{sqlite,sqlserver,dm}/localize-language-names.sql`（与迁移同语义，供出厂/向导兜底重放）。
- `identifier_strategy_config` 全局默认行进入预置重放清单（不出新迁移；历史 sqlserver 库已有该行不受影响，sqlite/dm 历史库缺行由启动期重放补齐）。
