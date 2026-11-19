# 实施计划：add-mcp-server-support

- 日期：2026-11-19
- 依据：`openspec/changes/add-mcp-server-support/`（proposal/design/specs/tasks 33 项）+ `docs/superpowers/specs/2026-11-16-mcp-support-design.md`
- 方式：主树 TDD；commit 待用户确认（按变更分组）

## 实施顺序与关键 wiring（已核实现状）

1. **依赖 spike（任务 1.1）**：`spring-ai-starter-mcp-server-webmvc`（Spring AI 1.1.x 线，Boot 3.5 兼容）+ BOM；验证编译与 Jackson 2.x 共存；冲突则切裸 `io.modelcontextprotocol.sdk:mcp` + `mcp-json-jackson2` + Servlet 传输，结论回写 design.md D3。
2. **配置基座（1.2）**：`McpProperties`（`mdr.mcp.*`），默认 127.0.0.1/8082/true/true。
3. **双连接器（2.x）**：`WebServerFactoryCustomizer<TomcatServletWebServerFactory>` 追加 Connector（绑定 mcp host/port）；Spring AI 的 MCP servlet 注册到上下文后对两个端口均可见——由 `McpPortSecurityFilter` 按 `localPort`/`path` 收敛暴露面。绑定失败由 Tomcat 启动期快速失败（可读 BindException 日志）。
4. **MCP 端口安全链（3.x）**：`McpPortSecurityFilter`（@Component OncePerRequestFilter，仅当 `localPort==mcpPort` 或 `path==mcpPath` 时生效）：
   - `path==mcpPath && localPort!=mcpPort` → 404（业务端口隐藏 MCP 端点）；
   - 非回环来源 → 403（与绑定地址无关，spec 硬约束）；
   - POST 且 JSON-RPC `tools/call` → 解析工具名（可重复读 request body 包装）：未认证 → 401；写工具无 ROLE_REGISTRAR → 403；超限 → 429（每身份内存令牌桶 30 req/s）；
   - 身份解析零成本：既有 `PersonalEditionAuthenticationFilter`（个人版隐式 SUPER_ADMIN，全局生效）与 `JwtAuthenticationFilter`（团队版 Bearer）对 MCP 端口天然适用，本过滤器只读 SecurityContext。
5. **只读工具（4.x）**：7 个 @Tool 工具类，直接注入既有服务；分页默认 0/20/上限 100。
6. **写入工具（5.x）**：draft-item 按 9 类白名单路由到既有 `*Service.create`（payload Map → DTO，jakarta Validator 编程式校验）；update-draft 加管理状态/归属前置守卫；工作空间三工具直接委托 `WorkspaceService`；推断两工具委托 `AssistantJobService`；**5.6 受管链闭环端到端断言**对齐 `AdministeredItemClosureConformanceTest` 口径。
7. **审计与错误（6.x）**：`McpToolAudit` 助手（工具方法内 try/finally 调用）写独立 logger `MDR.MCP`；logback-spring.xml 新增 `logs/app-mcp.log` appender；`McpToolException`（`[code] message`）。
8. **验证（7.x）**：编程客户端（MCP SDK legacy client）端到端冒烟 → 团队版安全三断言 → 真实 Kimi Code 连接 → web 全量回归（基线零新增）→ detect_changes 无 partial。
9. **文档（8.x）**：README MCP 章节 + 架构同步。

## TDD 约定

- 单元测试：Mock 服务的工具方法测试（参数/白名单/错误透传）；配置属性绑定测试。
- 集成测试：`@SpringBootTest` 随机端口起真实上下文 + 双连接器（MCP 端口随机化覆盖）+ RestTemplate/HttpClient 直打 MCP 端口做 FilterChain 断言（非回环 403 用 mock request 属性或 filter 直测）。
- 既有基线：web 全量失败集与干净 HEAD 逐项一致（既有 Workspace×9 等不计入新增）。
- 实施期每次编辑既有符号前跑 GitNexus `impact`；新建符号无需。

## 风险处置（对应 design.md §Risks）

- 依赖冲突 → D3 降级路径；Kimi Code 协商失败 → 记录实际版本调整实现；限流/审计偏差 → spec 场景为准。

## 验证命令

```bash
mvn -pl metadata-registry-web -am test -Dtest='*Mcp*'          # 目标测试
mvn -pl metadata-registry-web -am test                          # 模块全量回归
openspec validate add-mcp-server-support --strict
node .gitnexus/run.cjs detect-changes --scope all --repo .      # 提交前
```
