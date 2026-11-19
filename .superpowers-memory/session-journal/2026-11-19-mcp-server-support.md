# Session Journal — 2026-11-19 — MCP 协议支持实施（add-mcp-server-support）

## 成果

- **OpenSpec 四件套**（proposal/design/specs 7 需求 19 场景/tasks 31 项）+ Superpowers 设计稿与实施计划；变更名 `add-mcp-server-support`。
- **实现**：`metadata-registry-web` 新增 `mcp` 包——McpProperties（mdr.mcp.*）、McpServerConfig（SDK 2.2.0 legacy 2025-11-25 + Streamable HTTP，Servlet 注册 /mcp）、McpConnectorConfig（Tomcat 附加 Connector 绑 127.0.0.1:8082 + McpPortHolder 回填实际端口）、McpPortSecurityFilter（404 隐藏/403 回环/401/429 30rps/写工具 REGISTRAR 门禁，ObjectProvider 降级适配 @WebMvcTest 切片）、McpToolCatalog（[code] message 透传 + 内部错误防泄露 + SHA-256 审计）、audit（MDR.MCP logger → logs/app-mcp.log 独立 appender 复用 SensitiveDataMaskingEncoder）、15 个工具（8 只读 + 7 受控写入）。
- **正交链闭环守卫**（实施期发现并修复的真实缺陷）：draft-item 无 registration 块时会产出"裸内容链条目"（复现 2026-10-09 事故形态）——工具层合成默认 registration 块（namespaceId 空→活动 Registrar 推导，标识 AUTO）。
- **依赖**：官方 MCP Java SDK 2.2.0（Spring AI starter 在 Central 仅 1.0.0 弃用，D3 结论已回写 design.md）；reactor-core 保留传递依赖（排除即 NoSuchDefFoundError）。

## 验证

- MCP 测试 60+ 全绿（单测/条件装配/连接器/过滤器/读写工具）。
- **真实协议端到端冒烟**（@SpringBootTest RANDOM_PORT + 内存 SQLite + 双 Connector）：initialize 握手协商 **2025-11-25 实证**、tools/list 15 工具、search-metadata、draft-item 落库 + 受管链 INCOMPLETE 闭环、推断未启用语义、无凭证 401、弱角色 403、业务端口 404。
- **全量回归**：1710 例 14 失败/错误 = 干净 HEAD 对照组逐项一致（worktree 实证），零新增。
- detect_changes 无 partial/truncated，LOW。

## 评审（2026-11-19 双代理只读评审）

镜头 A 1 BLOCKER + 5 MAJOR + 4 MINOR；镜头 B 3 MAJOR + 5 MINOR，**全部核实处置**：immediateExecution(true) 修 SecurityContext 丢失（e2e 归属断言实证）；安全链拆前置守卫（匿名 403/404 不失真）；registration:null 守卫 + INCOMPLETE 强制；AccessDeniedException→[403]；限流桶溢出加固；写工具解析失败收紧；DTO 转换收敛 MappingDtoConverters（REST+MCP 复用）；e2e 补 submit-drafts + 四表闭环 + 匿名 404。评审驱动暴露并修复新缺陷：MCP 起草无归属预填 → McpDraftOwnershipPrefill（默认 Registrar，对齐 UI 语义）。design.md 已加评审记录节。

## 待办

- 任务 7.3 真实 Kimi Code 连接验证（需部署新 jar 到本机实例后人工验收）；2.2 绑定失败负例（部署时自然验证）。
- **未 git commit**（按变更分组提交，待用户确认）。工作树含并行会话改动，提交时需分组。

## 教训

- SDK sync server 默认把工具调用调度到 Reactor 线程——任何依赖 ThreadLocal（SecurityContext）的 handler 必须 `immediateExecution(true)`。
- @WebMvcTest 切片会实例化 @Component Filter——依赖必须走 ObjectProvider 降级（项目既有 personal 过滤器同款约定），否则 182 例级联失败。
- SQLite UUID 主键是 16 字节 BLOB，裸 JDBC 断言须按大端 msb+lsb 绑定；scoped_identifier 列名为 identified_item_id；提交关联表 registered_item_submission_record。
- 实机部署：OS 环境变量 DB_URL 不生效（DatasourceConfigService 以磁盘 application-local.yml 为准）；初始化状态文件与 config.file 同目录，隔离运行必须一并隔离；SQLite URL 不支持 `file:` 前缀。
- 限流类测试要 JIT 预热，否则测量段毛刺越过回补线；Mock 请求只设 servletPath 时 requestURI 为空，路径判定需兜底。
- 端到端断言落库时必须显式 registration 块或依赖工具的闭环守卫；TextLine 需 languageId 或 isolatedException；空 payload 被守卫拒绝（422）。
