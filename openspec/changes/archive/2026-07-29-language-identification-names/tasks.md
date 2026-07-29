## 1. 后端实现

- [x] 1.1 修改 `LanguageResponse.java` 响应 DTO，新增 `languageName` 与 `geopoliticalTerritoryName` 字段。
- [x] 1.2 修改 `LanguageIdentificationService.java`，在 `toResponse` 方法中通过 `LocaleContextHolder.getLocale()` 获取当前上下文 Locale，使用 JDK 的 `Locale` API 解析得到本地化语言文字名称与国家/地区名称。
- [x] 1.3 编写或更新 `LanguageIdentificationServiceTest.java` 单元测试，验证在中文和英文 Locale 环境下返回的语言名称和地区名称符合预期。

## 2. 前端实现

- [x] 2.1 修改 `LanguageList.vue`，在 DataTable 表格的“语言标识”与“启用状态”之间，新增“语言名称”与“地区名称”两列。

## 3. 验证

- [x] 3.1 启动后端与前端服务，手动访问语言标识设置页面，检查在系统语言为中文的情况下，`zh-CN` 对应的语言名称显示为“中文”，地区名称显示为“中国”。
