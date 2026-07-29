## Why

语言标识设置列表中，目前仅展示了语言标识代码（如 `zh-CN` ）、文字代码（如 `Hans` ）和地区代码（如 `CN` ）。对于终端用户来说，这不够直观，无法快速看懂其代表的自然语言和国家/地区。
因此，需要在语言标识列表中，根据当前系统语言（Locale）动态展示这些代码对应的语言文字名称（如 `中文` ）和地区名称（如 `中国` ），以提升系统的易用性和可读性。

## What Changes

- **后端 DTO 变更**：`LanguageResponse` 中新增 `languageName` (语言文字名称) 和 `geopoliticalTerritoryName` (地区名称) 字段。
- **后端服务逻辑变更**：`LanguageIdentificationService` 中，在把 `LanguageIdentification` 实体转换为 `LanguageResponse` 时，根据请求上下文中的当前 Locale（使用 `LocaleContextHolder.getLocale()` 获取），动态解析并设置对应的本地化语言文字名称和地区名称。
- **前端页面变更**：`LanguageList.vue` 列表组件中，在原有的“语言标识”、“文字”和“地区”等列后，新增“语言名称”和“地区名称”两列，分别绑定 `languageName` 和 `geopoliticalTerritoryName`。

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `language-identification-edit`: 在语言标识列表展示中，增加基于当前系统语言的“语言名称”和“地区名称”两个字段的动态本地化展示要求。

## Impact

- **API 接口**：`/api/v1/languages` 接口返回的 DTO 数据结构新增 `languageName` 和 `geopoliticalTerritoryName` 字段。
- **前端**：`LanguageList.vue` 列表表格结构变化，增加了两个展示列。
- **国际化**：后端名称的本地化依赖请求的 Locale。
