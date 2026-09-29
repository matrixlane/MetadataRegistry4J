# language-identification-edit 增量规格

## ADDED Requirements

### Requirement: 语言名称中文显示口径
语言中文名称的预置口径 SHALL 以 SQL 预置脚本为唯一数据源。`zh-CN` 行的 `language_name` MUST 为「简体中文」（BCP 47 `zh-CN` 隐含脚本 `Hans`，简体中文为该标签在中文语境下的通行称谓；用户裁定 2026-09-29）。既有库 MUST 通过迁移 `V20260929001__Localize_language_names.sql`（SQLite / SQL Server / 达梦三份，语义一致）一次性更新；恢复出厂与初始化向导兜底 MUST 通过预置重放脚本清单中的名称口径修正语句保持同一取值，两条通道不得漂移。

#### Scenario: 全新库 zh-CN 显示简体中文
- **WHEN** 全新数据库完成迁移与静态预置
- **THEN** `ext_language_identification` 中 zh-CN 行 `language_name` 为「简体中文」、`territory_name` 为「中国」

#### Scenario: 既有库应用迁移后更新
- **WHEN** 既有库（`language_name` 为「中文」）应用 `V20260929001`
- **THEN** zh-CN 行 `language_name` 更新为「简体中文」，其余 21 行保持不变

#### Scenario: 出厂兜底与迁移同语义
- **WHEN** 恢复出厂设置或初始化向导 `setup` 清理后触发预置重放
- **THEN** zh-CN 行 `language_name` 为「简体中文」（与迁移通道同一份语句语义）

### Requirement: 语言下拉中文显示
所有语言相关下拉菜单（初始化向导"文档语言"、多语言文本编辑器、指称编辑器、定义编辑器、注册规范设置的默认语言与备选语言、条目表单的语言展示）SHALL 以 `language_name`（缺失时回退 `language_identifier`）作为显示项，`value` / 提交值 MUST 仍为 BCP 47 语言标识符，保证提交格式不变。中文名称 MUST 参与下拉搜索过滤。初始化向导"文档语言"的选项 MUST 由 `GET /api/v1/languages` 驱动，MUST NOT 使用前端硬编码语言数组；预填值仍为 `zh-CN`，显示「简体中文」。

#### Scenario: 有名称时下拉显示中文
- **WHEN** 语言扩展表存在 `language_name` 的行被渲染为下拉选项
- **THEN** 选项显示 `language_name`（如「简体中文」「英语」），选中值仍为对应 BCP 47 标识符

#### Scenario: 缺失名称时回退标识符
- **WHEN** 某语言行无 `language_name`（用户未填写且无预置）
- **THEN** 下拉显示其 `language_identifier`，不出现空白选项

#### Scenario: 中文名可搜索
- **WHEN** 用户在下拉中输入"简体"或"英语"
- **THEN** 匹配到 `language_name` 包含该词的行

#### Scenario: 向导文档语言预填
- **WHEN** 管理员进入初始化向导注册机构步骤
- **THEN** 文档语言默认选中 `zh-CN`，显示项为「简体中文」而非 `zh-CN` 字符串

### Requirement: 语言页编辑提示与解析语义一致
`LanguageList.vue` 编辑弹窗中语言名称 / 地区名称输入框的提示文案 MUST 与后端"不做运行时解析"的语义一致：留空时列表与下拉 SHALL 显示语言标识符，MUST NOT 暗示存在自动回填行为。

#### Scenario: 留空保存后的显示
- **WHEN** 管理员将某语言名称清空并保存
- **THEN** 语言页该列与所有下拉显示该行的 `language_identifier`，提示文案不出现"自动回填"表述
