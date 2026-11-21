# export-pagination Specification

## Purpose

注册元数据导出的请求级分页语义：page/size 可空、同有同无；SQL 方言分页下推与确定性排序；分页时 totalCount 报告全量命中数；MCP 导出工具与 REST 同步/流式路径同口径。大结果集经分批获取规避内存与单响应体压力。

## ADDED Requirements

### Requirement: 导出请求可选分页参数

系统 SHALL 接受导出请求上的可选 `page`（从 0 起）与 `size` 分页字段。两者均省略时导出行为 SHALL 与既有完全一致（不分页）。两字段 MUST 同时提供或同时省略；`page` MUST ≥ 0；`size` MUST 在 1..10000 之间。违反 SHALL 在发起任何查询前以参数校验异常拒绝。

#### Scenario: 省略分页字段行为不变

- **WHEN** 客户端不传 `page`/`size` 发起导出
- **THEN** 系统 SHALL 返回全量命中结果，不附加分页子句

#### Scenario: 仅传一个分页字段被拒绝

- **WHEN** 客户端只传 `page=0` 或只传 `size=100`
- **THEN** 系统 SHALL 拒绝并提示两字段必须同时提供或同时省略

#### Scenario: 越界分页参数被拒绝

- **WHEN** 客户端传 `page=-1` 或 `size=0` 或 `size=10001`
- **THEN** 系统 SHALL 拒绝并说明合法区间

### Requirement: 分页 SQL 方言下推

分页请求的主查询 SHALL 以 `ORDER BY ai.creation_date_time, ai.id` 保证稳定序（原排序字段不唯一），并按方言追加参数绑定（非字符串插值）的分页子句：SQL Server 用 `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`（参数 page*size, size），其余方言（SQLite/达梦）用 `LIMIT ? OFFSET ?`（参数 size, page*size）。

#### Scenario: SQLite 方言分页子句

- **WHEN** 在 SQLite 部署上以 `page=2, size=50` 请求分页导出
- **THEN** 主查询 SHALL 包含 `LIMIT ? OFFSET ?` 且绑定参数依次为 50、100

#### Scenario: SQL Server 方言分页子句

- **WHEN** 在 SQL Server 部署上以 `page=1, size=25` 请求分页导出
- **THEN** 主查询 SHALL 包含 `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY` 且绑定参数依次为 25、25

### Requirement: 分页时 totalCount 报告全量命中数

分页 JSON 导出的 `exportMetadata.totalCount` SHALL 等于全库命中数（经与主查询同 WHERE 的 COUNT 查询获得），客户端据此判定翻页终止。未分页导出 totalCount SHALL 维持现语义（= 本批条数）。越界页 SHALL 返回空 `items` 且 totalCount 不变。

#### Scenario: 首页返回页切片与全量 totalCount

- **WHEN** 库内 2156 条值域以 `page=0, size=100` 导出
- **THEN** `items` SHALL 含 100 条，`totalCount` SHALL 为 2156

#### Scenario: 越界页空数组且 totalCount 不变

- **WHEN** 以上述库以 `page=22, size=100` 导出（共 22 页）
- **THEN** `items` SHALL 为空数组，`totalCount` SHALL 仍为 2156

### Requirement: 同步导出路径分页语义一致

同步 `export()` 路径在分页时 SHALL 与流式路径同口径报告 totalCount（全量命中数）；`MAX_SYNC_EXPORT_SIZE`（10000 条）守卫 SHALL 仅约束未分页请求；`shouldUseAsyncExport` 对分页请求 SHALL 恒返回 false（页切片最大 10000 条，永不触异步阈值）。未分页行为 SHALL 完全不变。

#### Scenario: 分页同步导出不被误判超上限

- **WHEN** 库内 11329 条（超 1 万）以 `page=0, size=5` 走同步导出
- **THEN** 系统 SHALL 正常返回 5 条且 totalCount=11329，SHALL NOT 抛"超过最大限制"或建议异步

#### Scenario: 未分页超上限仍被守卫

- **WHEN** 未分页同步导出命中 10001 条
- **THEN** 系统 SHALL 抛"超过最大限制"并建议异步导出

### Requirement: MCP 工具透传分页

MCP 工具 `export-data-dictionary` SHALL 接受可选 `page`（整数，0..100000）与 `size`（整数，1..1000）参数并透传导出服务。省略时 SHALL 保持不分页（既有行为）。

#### Scenario: 工具透传分页参数

- **WHEN** 调用 `export-data-dictionary` 传 `format=json, page=1, size=100`
- **THEN** 服务 SHALL 收到 page=1/size=100 并返回对应页切片与全量 totalCount

#### Scenario: 工具越界分页参数被拒绝

- **WHEN** 调用工具传 `page=-1` 或 `size=1001`
- **THEN** 系统 SHALL 以校验错误拒绝，不发查询

### Requirement: CSV 导出同样分页

`exportCsvToStream` SHALL 应用与 JSON 相同的分页语义：每页返回一个完整 CSV 文本（含 BOM 与表头）。

#### Scenario: 分页 CSV 每页完整

- **WHEN** 以 `page=0, size=10` 请求 CSV 导出
- **THEN** 响应 SHALL 以 BOM 开头、含表头，数据行 SHALL 恰为 10 行（末页可不足）
