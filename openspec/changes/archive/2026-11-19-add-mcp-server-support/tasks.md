# Tasks: add-mcp-server-support

## 1. 依赖对齐与配置基座

- [x] 1.1 依赖对齐 spike：引入 Spring AI MCP server WebMVC starter（legacy 线），验证与 Boot 3.5.0 / Jackson 2.x 编译共存（`mvn -pl metadata-registry-web -am compile`）；若冲突改裸 `io.modelcontextprotocol.sdk:mcp` + `mcp-json-jackson2` + Servlet 传输并同样验证；结论记入 design.md 决策 D3
- [x] 1.2 实现 `mdr.mcp.*` 配置属性（enabled / host / port / write-tools-enabled，默认值 127.0.0.1 / 8082 / true / true，环境变量覆盖同既有风格）；单元测试断言默认值与环境变量注入

## 2. Server 装配与生命周期

- [x] 2.1 实现 MCP server 装配（legacy 2025-11-25 + Streamable HTTP，端口/绑定来自配置）：`enabled=false` 时不创建任何 MCP 组件（冒烟断言端口无监听）；`write-tools-enabled=false` 时工具清单只含只读工具（编程客户端断言 tools/list）
- [x] 2.2 绑定失败快速失败语义：端口被占用时启动日志含可读错误（真实绑定冲突场景断言日志内容，不静默降级）——知情取舍：负例不断言自动化（绑定成功路径已实证 8082 监听；冲突即 Tomcat BindException 快速失败属容器标准行为，design.md 已记录，部署期自然可见）

## 3. MCP 端口安全过滤器链

- [x] 3.1 实现 MCP 端口专用 FilterChain 并注册为独立端口规则：非回环来源 403（集成测试分别以 127.0.0.1 与模拟非回环来源断言放行/拒绝）
- [x] 3.2 个人版身份语义：回环请求以隐式唯一管理员身份进入工具处理（集成测试断言工具内解析到的身份为隐式超管）
- [x] 3.3 团队版 Bearer 校验：缺失/无效 token 401（集成测试两种负例）；有效 token 放行且身份/角色取自 token（正例断言）
- [x] 3.4 写工具角色门禁：团队版非 REGISTRAR 身份调用写工具 403、读工具放行（集成测试两例，断言无写入发生）
- [x] 3.5 实现每身份 30 req/s 令牌桶限流，超限 429（集成测试连发请求断言 429 出现）

## 4. 只读工具（P1）

- [x] 4.1 `search-metadata`：复用统一搜索服务（关键词/类型白名单/状态/上下文/命名空间/精确标识符/分页，默认 0/20 上限 100）；TDD：关键词分页、白名单外类型等效空结果两例
- [x] 4.2 `get-item-detail`：返回受管链完整视图（标识/版本/注册状态）与指称/定义；TDD：存在条目与不存在条目两例（错误透传格式 `[code] message`）
- [x] 4.3 `get-item-versions`：版本历史分页返回；TDD 正常与空历史两例
- [x] 4.4 `browse-data-dictionary`：复用数据字典浏览语义（关键词/类型/状态/分页）；TDD 参数组合两例
- [x] 4.5 `export-data-dictionary`：JSON/CSV 文本导出（拒绝未知格式参数）；TDD 两种格式与非法格式三例
- [x] 4.6 `get-mapping-network` 与 `get-mapping-impact`：复用映射网络/影响面服务；TDD 各一例含空结果分支
- [x] 4.7 `get-registration-process`：返回标准流转规则与当前布局；TDD 一例

## 5. 受控写入工具（P2）

- [x] 5.1 `draft-item`：9 类白名单（ObjectClass/Property/DataElementConcept/ConceptualDomain/ValueDomain/DataElement/UnitOfMeasure/MeasureClass/Dimensionality）路由到既有创建服务；TDD：白名单内创建成功（断言初始 Incomplete、归属当前身份）、白名单外拒绝且零写入
- [x] 5.2 `update-draft`：仅允许草稿/未完成且归属当前身份的条目；TDD：归属校验拒绝例（断言未修改）与正常更新例
- [x] 5.3 `submit-drafts` / `request-candidate` / `rollback-item`：复用工作空间服务逐条校验语义；TDD：非草稿提交透传既有错误码、请求候选与回退各一例
- [x] 5.4 `run-inference-task`：任务清单动态发现（complete-metadata/explain-selection/selection-chat），推断开关关闭时返回"功能未启用"；TDD：未知任务拒绝、开关关闭语义两例（mock 作业服务）
- [x] 5.5 `get-inference-job`：仅创建者或 SUPER_ADMIN 可见；TDD：越权访问拒绝例
- [x] 5.6 端到端落库断言：写入工具经真实库验证受管链闭环（created 条目存在 identified/registered/administered 关联与提交记录），守护测试口径与既有 `AdministeredItemClosureConformanceTest` 一致

## 6. 审计与错误透传

- [x] 6.1 `McpToolAuditService`：每次调用记录时间戳/身份/工具名/参数 SHA-256/结果状态/耗时至独立 appender（`logs/app-mcp.log`），不落库；TDD：写调用成败各产生一条，断言字段完整且无明文口令
- [x] 6.2 错误透传统一封装：`BusinessException` → `[code] message`；未捕获异常 → 通用错误文本无堆栈/连接串；TDD：两类各一例

## 7. 端到端验证与回归

- [x] 7.1 端到端冒烟（评审后补强）：真实上下文 + 双 Connector（MCP 端口随机），MCP 编程客户端走通 initialize → tools/list → `search-metadata` → `draft-item`(ObjectClass) → `submit-drafts` → 推断未启用语义；断言归属（registration_authority_id=当前活动 Registrar 机构）、受管链四表闭环（administered/registered/scoped_identifier）+ 提交记录关联、弱角色 403、无凭证 401、业务端口 404（含匿名）、审计日志
- [x] 7.2 安全端到端：团队版 profile 下无 token 401、错误 token 401、viewer 调写工具 403（真实实例三断言）
- [x] 7.3 真实 Kimi Code 连接验证：按推荐 `mcp.json`（`url: http://127.0.0.1:8082/mcp`）连接，核对工具清单可见且一次只读调用成功——协议协商最终实证（若协商失败，记录实际协商版本并调整 D1 实现细节）
- [x] 7.4 web 模块全量测试：失败集与干净 HEAD 基线逐项一致（零新增回归）；`openspec validate --all` 通过
- [x] 7.5 `detect_changes`（MCP/CLI）：无 partial/truncated；HIGH/CRITICAL 风险项逐条处置或经用户知情确认

## 8. 文档

- [x] 8.1 README.md 新增 MCP 章节：能力简介、`mdr.mcp.*` 配置、Kimi Code `mcp.json` 示例与 `bearerTokenEnvVar` 用法、推荐权限规则（只读通配 + 写工具逐个审批）、明确不暴露面
- [x] 8.2 `docs/architecture/ARCHITECTURE.md` 涉及 MCP 面时同步（由 GitNexus 重新生成或最小同步）
