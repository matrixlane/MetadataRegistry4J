## Context

语言标识（Language Identification）用于标识元数据定义或指称所采用的自然语言。虽然系统在启动时自动初始化了 G20 常用的 22 种语言（如 `zh-CN`、`en-US`、`ja-JP` 等），但在前端管理页面上，目前只展示了代码形式的“语言标识”（例如 `zh-CN`）、“文字”（例如 `Hans`）和“地区”（例如 `CN`）。

这导致非技术背景的业务人员在使用本系统时，无法一眼看出这些标识具体对应的是什么语言和国家/地区，需要系统在此基础之上展示清晰易懂的本地化名称（例如：语言名称“中文”，地区名称“中国”）。

## Goals / Non-Goals

**Goals:**
- 在语言标识管理界面表格中，新增“语言名称”和“地区名称”两列。
- 服务端解析现有的语言标识代码，根据当前请求的系统语言（Locale）返回本地化解析后的名称。
- 支持在系统切换为不同语言（如中文或英文）时，该列能够动态显示为当前系统语言对应的文本（如“中文”/“中国” 或 “Chinese”/“China”）。

**Non-Goals:**
- 不改变数据库中语言标识的存储结构，不新增任何数据库字段。
- 不增加额外的人工翻译字典维护逻辑，完全依赖 JDK 自带的 `java.util.Locale` 本地化名称提取机制。
- 不涉及除语言标识列表页之外的其它语言展示优化。

## Decisions

### 1. 采用后端动态解析与 DTO 增强方案
- **决策内容**：在后端 `LanguageResponse` 中新增 `languageName` 与 `geopoliticalTerritoryName` 字段。并在 `LanguageIdentificationService.toResponse()` 方法中通过 JDK 提供的 `java.util.Locale` API 动态计算解析出本地化名称。
- **解析算法实现**：
  1. 使用 `org.springframework.context.i18n.LocaleContextHolder.getLocale()` 获取当前请求的 Locale 上下文。
  2. 对于语言名称：使用 `Locale.forLanguageTag(entity.getLanguageIdentifier()).getDisplayLanguage(currentLocale)` 进行获取。对于 `zh-CN`，其 `getDisplayLanguage` 会自动返回“中文”或“Chinese”。
  3. 对于地区名称：若 `geopoliticalTerritoryIdentifier` 存在，使用 `new Locale.Builder().setRegion(entity.getGeopoliticalTerritoryIdentifier()).build().getDisplayCountry(currentLocale)` 获取。对于 `CN`，其 `getDisplayCountry` 会自动返回“中国”或“China”。
- **对比前端解析（Intl.DisplayNames）**：
  - 前端解析需要浏览器具有较新的 API 支持（`Intl.DisplayNames` 虽然在现代浏览器中支持较好，但在旧版本或某些特定环境下可能有兼容问题），且如果解析失败，容错逻辑较多。
  - 后端直接使用 JDK 标准类库，在 Spring Boot 环境中天然与 Accept-Language 请求头结合，稳定且易于编写单元测试。

### 2. 前端列表组件绑定
- **决策内容**：在 `LanguageList.vue` 的 `DataTablePage` 中，原有的“文字”和“地区”两列之间（或之后）新增两个 `el-table-column`，分别对应新字段 `languageName` (语言名称) 和 `geopoliticalTerritoryName` (地区名称)。

## Risks / Trade-offs

- **[Risk]** 部分非标准语言标识可能无法被 `Locale.forLanguageTag` 正确解析。
  - **Mitigation**: 在解析前进行非空校验，若解析出的显示名称与原标识相同或为空，可降级显示原代码标识，以保证不报错。
- **[Risk]** 当 `LanguageResponse` 字段增多时，可能在编辑/更新时传回后端，但后端接收的是 `LanguageRequest`。
  - **Mitigation**: `LanguageRequest` 中并未定义这两个新增字段，更新操作不会受这两个展示字段的影响，前后端契约保持清晰。
