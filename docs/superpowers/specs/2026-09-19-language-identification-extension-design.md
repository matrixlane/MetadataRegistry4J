# 语言标识扩展表设计（language-identification-extension）

日期：2026-09-19
状态：待用户确认

## 背景与动机

当前「语言名称」「地区名称」由后端在响应组装时用 JDK `Locale` API 实时计算
（`LanguageDisplayNames`，经 `LanguageIdentificationService.toResponse()`），
数据库中不存在任何名称字段。用户要求：

1. 将语言名称、地区名称持久化到独立的语言扩展表，便于未来扩展（不硬编码在代码中）；
2. 扩展表与标准 MDR 语言表**一对一**；
3. **扩展表名与主表一致（`language_identification`），仅以 schema 区分**
   （`mdr.language_identification` ↔ `ext.language_identification`），强调两者紧密关系；
4. UI 列名/表单项文案修改：`文字` → `文字标识`，`地区` → `地区标识`。

## 现有结构（已核实）

- 标准实体 `commonfacilities...basicclasses.LanguageIdentification`（表
  `language_identification`，ISO/IEC 11179-3:2023 6.3.7），字段：
  `language_identifier`（实际存完整 BCP 47 标签，如 `zh-CN`）、`script_identifier`、
  `geopolitical_territory_identifier`、`private_use_qualifier`；变体/扩展子标签在
  `language_variant` / `language_extension` 集合表。
- EXT 卫星先例 `ext.basicandcore.LanguageGovernance`（表 `language_governance`）：
  UUID 主键 + `language_id` 唯一值引用，**有意不建 JPA 关联**，读写经值键，
  缺失行有默认语义（enabled=true）。
- EXT schema 机制（openspec change `2026-09-01-mdr-ext-schema-split`）：
  - 实体注解 `@Table(name=...)` 不带 schema（SQLite 无 schema 概念）；
  - team profile 用 `orm/orm-team.xml` 对实体覆盖 `<table schema="ext"/>`；
  - `DatabaseEnvironment.EXT_TABLES` 登记裸表名 → schema 路由
    （SQLite 返回 null；SQL Server `ext`；达梦 `EXT`）；
  - Flyway 迁移按方言分目录（`db/migration/sqlserver|sqlite|dm`），
    `OrmTeamXmlTest` 守卫 orm-team.xml 与实体清单一致。
- 前端 `metadata-registry-ui/src/views/language/LanguageList.vue`：列表列
  `语言标识 / 文字 / 地区 / 语言名称 / 地区名称`，表单无名称字段。

## 设计决策

### D1. 扩展表名与主表同名，schema 区分

- team 库（SQL Server / 达梦）：`ext.language_identification`，与
  `mdr.language_identification` 同名不同 schema。
- **SQLite 冲突处理**：SQLite 无 schema，`main` 命名空间内同名表会冲突。
  方案：为 personal（SQLite）profile 新增 `orm/orm-sqlite.xml`，对该实体覆盖
  `<table name="ext_language_identification"/>`（物理名加 `ext_` 前缀），
  同时 `DatabaseEnvironment.EXT_TABLES` 同时登记
  `language_identification`（team）与 `ext_language_identification`（sqlite 物理名）
  两个路由键；Flyway sqlite 迁移建 `ext_language_identification` 表。
  → 团队库保持"同名不同 schema"语义，SQLite 以命名前缀模拟 schema。

### D2. 表结构（一对一）

新实体 `ext.basicandcore.LanguageIdentificationExtension`（卫星模式，仿
`LanguageGovernance`，不建 JPA 关联，经值键读写）：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUID PK | 生成值 |
| `language_id` | UUID, unique, not null | → `language_identification.id`，一对一 |
| `language_name` | varchar, null | 语言名称（单语，默认中文） |
| `territory_name` | varchar, null | 地区名称（单语，默认中文） |

- 缺失行语义：名称取不到时**回退到 JDK Locale 计算**（保留
  `LanguageDisplayNames` 作为兜底），旧库零数据迁移。
- 单语设计：每语言一条记录、一个语言名称 + 一个地区名称；未来如需按界面语言
  多语言显示，再加 `display_locale` 列升级为 1:N（用户已确认 1:1 起步）。

### D3. 读写链路

- 新增 `LanguageIdentificationExtensionRepository`（按 `languageId` 查找）与
  `LanguageExtensionService`（getOrDefault / 批量 map / upsert）。
- `LanguageIdentificationService.toResponse()`：扩展行存在且名称非空 → 用持久化
  值；否则 → `LanguageDisplayNames` 兜底。列表页与详情一致。
- `create` / `update`：`LanguageRequest` 增加 `languageName`、`territoryName`；
  保存时 upsert 扩展行；传空则按当前 JDK Locale 解析回填（保证开箱即有值）。

### D4. 登记与迁移

- `DatabaseEnvironment.EXT_TABLES` 增加 `"language_identification"` 与
  `"ext_language_identification"`（SQLite 物理名）。
- `orm/orm-team.xml` 增加 `<entity class="...LanguageIdentificationExtension"><table schema="ext"/></entity>`
  （OrmTeamXmlTest 同步更新）。
- 新增 `orm/orm-sqlite.xml`（personal profile `mapping-resources` 加载）覆盖表名。
- Flyway 迁移：sqlserver / dm 建 `ext.language_identification`；sqlite 建
  `ext_language_identification`。
- `LanguageInitializer`：为 22 条内置语言补种扩展行（中文名称：
  中文/中国、英语/美国、日语/日本、韩语/韩国、德语/德国、法语/法国、
  西班牙语/西班牙、西班牙语/阿根廷、西班牙语/墨西哥、葡萄牙语/巴西、
  印地语/印度、英语/印度、英语/澳大利亚、英语/南非、阿拉伯语/沙特阿拉伯、
  土耳其语/土耳其、印度尼西亚语/印度尼西亚、意大利语/意大利、俄语/俄罗斯、
  英语/英国、英语/加拿大、法语/加拿大）。

### D5. API 与前端

- `LanguageResponse` 增加 `languageName`、`territoryName`（已有同名字段，改为
  优先取持久化值）；`LanguageRequest` 增加两个可选字段。
- 前端 `LanguageList.vue`：
  - 列名 `文字` → `文字标识`，`地区` → `地区标识`（列表与表单）；
  - 表单新增「语言名称」「地区名称」两个可编辑输入项；
  - 列 `语言名称` / `地区名称` 绑定不变（后端字段名不变）。

### D6. 治理属性不变

`language_governance`（enabled）保持独立，不合并进本表（用户此前已将其剥离为
独立卫星实体；本次仅新增名称扩展）。

## 不做（Out of Scope）

- 不按界面语言存多份名称（未来如需再开 1:N 变更）。
- 不改动标准表 `language_identification` 的任何列。
- 不改动 `language_governance`。

## 验证计划

- 后端单测：扩展服务/仓储、`toResponse` 持久化值优先 + 缺失回退、create/update
  upsert、初始化器补种。
- `OrmTeamXmlTest` 清单守卫更新后通过；Flyway 三方言迁移在对应 profile 下启动验证。
- 前端 vitest（如有该页用例）+ build + eslint。
- 手工：语言页列表展示持久化名称，编辑保存后重查生效。
