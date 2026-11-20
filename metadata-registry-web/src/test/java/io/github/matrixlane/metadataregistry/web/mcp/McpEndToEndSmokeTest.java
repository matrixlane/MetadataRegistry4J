package io.github.matrixlane.metadataregistry.web.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.service.ContactAccountService;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 端到端冒烟（add-mcp-server-support 任务 7.1）：真实 Spring 上下文 +
 * 内存 SQLite + 双 Connector（MCP 端口随机），经 JDK HttpClient 走真实 MCP
 * 协议（initialize → initialized → tools/list → tools/call），覆盖：
 *
 * <ul>
 *   <li>协议握手与工具清单（15 个工具）；</li>
 *   <li>只读调用 search-metadata；</li>
 *   <li>受控写入 draft-item 落库 + 受管链闭环断言（administered_item 存在、
 *       注册状态 INCOMPLETE —— 任务 5.6 口径）；</li>
 *   <li>export-data-dictionary 端到端（2026-11-20 事故回归：JOINED 根多态 JOIN
 *       超 SQLite 64 表上限曾致导出 500）；</li>
 *   <li>推断功能未启用语义（mdr.inference.enabled=false）；</li>
 *   <li>弱角色调写工具 403、业务端口访问 MCP 路径 404。</li>
 * </ul>
 *
 * <p>不使用 @Transactional：servlet 在工作线程执行，无法参与测试线程事务。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:mcp-e2e?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "app.seed.demo-data.enabled=false",
        "mdr.mcp.port=0",
        "mdr.inference.enabled=false"
})
@ActiveProfiles("test")
class McpEndToEndSmokeTest {

    @Autowired
    private ServletWebServerApplicationContext webServerContext;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Autowired
    private RegistrarRepository registrarRepository;
    @Autowired
    private NamespaceRepository namespaceRepository;
    @Autowired
    private ContactAccountService contactAccountService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AdministeredItemRepository administeredItemRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository languageRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private int mcpPort;
    private int businessPort;
    private String registrarToken;
    private String plainToken;
    private UUID languageId;
    private UUID raNamespaceId;
    private UUID raId;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        discoverPorts();
        String suffix = "-" + Long.toHexString(System.nanoTime());

        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of("MCP 端到端测试机构" + suffix));
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("0000");
        rai.setOrganizationIdentifier("MCP-E2E" + suffix);
        ra.setRegistrationAuthorityIdentifier(rai);
        ra = registrationAuthorityRepository.save(ra);
        raId = ra.getId();

        Account account = new Account();
        account.setUsername("mcp-e2e" + suffix);
        account.setPasswordHash("x");
        account.setSysRole(SystemRole.SUPER_ADMIN);
        account = accountRepository.save(account);

        Registrar registrar = new Registrar(ra);
        registrar = registrarRepository.save(registrar);
        contactAccountService.bind(registrar.getId(), account.getId());

        Namespace raNamespace = new Namespace();
        raNamespace.setRegistrationAuthority(ra);
        raNamespace.setNamingAuthority(ra);
        raNamespaceId = namespaceRepository.save(raNamespace).getId();

        io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification lang =
                new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification();
        lang.setLanguageIdentifier("zh-CN");
        lang.setScriptIdentifier("Hans");
        lang.setGeopoliticalTerritoryIdentifier("CN");
        languageId = languageRepository.save(lang).getId();

        // 带 registrar 业务标志（SUPER_ADMIN 依 AuthService.isRegistrar 恒为 true）
        registrarToken = jwtService.generateToken(account, false, false, false, true);
        // 无 registrar 业务标志：用于写工具 403 断言
        plainToken = jwtService.generateToken(account, false, false, false, false);
    }

    @Test
    void fullMcpFlow_shouldPass() throws Exception {
        // 1) initialize 握手
        McpResponse init = postMcp(mcpPort, initializePayload(), registrarToken, null);
        assertThat(init.status()).isEqualTo(200);
        assertThat(init.body().path("result").path("serverInfo").path("name").asText())
                .isEqualTo(McpServerConfig.SERVER_NAME);
        String sessionId = init.sessionId();
        assertThat(sessionId).isNotBlank();

        // 0) 团队版语义：无 Bearer 凭证 → 401
        McpResponse anonymous = postMcp(mcpPort, initializePayload(), null, null);
        assertThat(anonymous.status()).isEqualTo(401);

        // 2) initialized 通知
        McpResponse notified = postMcp(mcpPort, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}""", registrarToken, sessionId);
        assertThat(notified.status()).isIn(200, 202);

        // 3) tools/list：15 个工具
        McpResponse tools = postMcp(mcpPort, rpc(2, "tools/list", null), registrarToken, sessionId);
        List<String> names = tools.body().path("result").path("tools").findValues("name").stream()
                .map(JsonNode::asText).toList();
        assertThat(names).hasSize(15);
        assertThat(names).contains("search-metadata", "draft-item", "submit-drafts",
                "run-inference-task", "get-registration-process");

        // 4) 只读调用
        McpResponse search = postMcp(mcpPort, rpc(3, "tools/call",
                "{\"name\":\"search-metadata\",\"arguments\":{\"q\":\"\"}}"), registrarToken, sessionId);
        assertThat(search.body().path("result").path("isError").asBoolean()).isFalse();

        // 5) 受控写入：起草 ObjectClass
        McpResponse draft = postMcp(mcpPort, rpc(4, "tools/call", """
                {"name":"draft-item","arguments":{"itemType":"ObjectClass",
                  "payload":{"designations":[{"languageId":"%s","value":"MCP 端到端对象类","isolatedException":true}],
                  "registration":{"namespaceId":"%s"}}}}"""
                        .formatted(languageId, raNamespaceId)), registrarToken, sessionId);
        assertThat(draft.body().path("result").path("isError").asBoolean()).isFalse();
        String createdText = draft.body().path("result").path("content").get(0).path("text").asText();
        UUID createdId = UUID.fromString(objectMapper.readTree(createdText).path("id").asText());

        // 6) 受管链闭环断言（任务 5.6 口径，评审 B2 补强）：
        //    归属正确（registration_authority_id = 当前活动 Registrar 的机构， immediateExecution 修复的实证）
        //    + 四表闭环（identified/registered/administered/scoped_identifier；JOINED 继承共享主键）
        List<Object[]> meta = administeredItemRepository.findMetaByItemId(createdId);
        assertThat(meta).isNotEmpty();
        assertThat(meta.get(0)[1]).isEqualTo(RegistrationStatus.INCOMPLETE);
        assertThat(meta.get(0)[2]).isEqualTo(raId);
        UUID administeredItemId = (UUID) meta.get(0)[0];
        assertThat(countRows("SELECT COUNT(*) FROM administered_item WHERE id = ?", administeredItemId))
                .isEqualTo(1);
        assertThat(countRows("SELECT COUNT(*) FROM registered_item WHERE id = ?", administeredItemId))
                .isEqualTo(1);
        assertThat(countRows("SELECT COUNT(*) FROM scoped_identifier WHERE identified_item_id = ?",
                administeredItemId)).isEqualTo(1);

        // 6b) 提交草稿 → submission_record 闭环（评审 B2：补齐 submit-drafts 端到端）
        McpResponse submit = postMcp(mcpPort, rpc(41, "tools/call", """
                {"name":"submit-drafts","arguments":{"itemIds":"%s"}}""".formatted(createdId)),
                registrarToken, sessionId);
        assertThat(submit.body().path("result").path("isError").asBoolean()).isFalse();
        assertThat(countRows("SELECT COUNT(*) FROM registered_item_submission_record WHERE registered_item_id = ?",
                administeredItemId)).isEqualTo(1);

        // 6c) export-data-dictionary 端到端（2026-11-20 事故回归：JOINED 根多态 JOIN 曾拼出
        //     超 SQLite 64 表上限的 SQL，导出 500；修复后走原生 SQL 标量投影）
        McpResponse export = postMcp(mcpPort, rpc(42, "tools/call",
                "{\"name\":\"export-data-dictionary\",\"arguments\":{\"format\":\"json\",\"itemTypes\":\"ObjectClass\"}}"),
                registrarToken, sessionId);
        assertThat(export.body().path("result").path("isError").asBoolean()).isFalse();
        String exportedJson = export.body().path("result").path("content").get(0).path("text").asText();
        assertThat(exportedJson).contains("\"itemType\":\"ObjectClass\"");
        assertThat(exportedJson).contains("MCP 端到端对象类");

        // 7) 推断功能未启用语义
        McpResponse inference = postMcp(mcpPort, rpc(5, "tools/call", """
                {"name":"run-inference-task","arguments":{"taskId":"complete-metadata"}}"""),
                registrarToken, sessionId);
        assertThat(inference.body().path("result").path("isError").asBoolean()).isTrue();
        assertThat(inference.body().path("result").path("content").get(0).path("text").asText())
                .contains("功能未启用");

        // 8) 弱角色（无 registrar claim）调写工具 → HTTP 403
        McpResponse forbidden = postMcp(mcpPort, rpc(6, "tools/call", """
                {"name":"draft-item","arguments":{"itemType":"ObjectClass",
                  "payload":{"designations":[{"languageId":"%s","value":"越权草稿","isolatedException":true}]}}}"""
                        .formatted(languageId)), plainToken, sessionId);
        assertThat(forbidden.status()).isEqualTo(403);

        // 9) 业务端口访问 MCP 路径 → 404（含匿名：前置守卫先于全局安全链，评审 B1）
        McpResponse business = postMcp(businessPort, initializePayload(), registrarToken, null);
        assertThat(business.status()).isEqualTo(404);
        McpResponse anonymousBusiness = postMcp(businessPort, initializePayload(), null, null);
        assertThat(anonymousBusiness.status()).isEqualTo(404);
    }

    private int countRows(String sql, UUID id) {
        // SQLite 下 UUID 主键/外键以 16 字节 BLOB 存储（typeof(id)=blob）：
        // 按大端 msb+lsb 绑定，与 Hibernate 6 UUID 二进制映射一致
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, (Object) uuidBytes(id));
        return count == null ? 0 : count;
    }

    private static byte[] uuidBytes(UUID id) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(new byte[16]);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return buffer.array();
    }

    // ---- MCP 协议客户端 ----

    private void discoverPorts() {
        TomcatWebServer server = (TomcatWebServer) webServerContext.getWebServer();
        businessPort = server.getPort();
        mcpPort = Arrays.stream(server.getTomcat().getService().findConnectors())
                .filter(c -> c.getLocalPort() != businessPort)
                .findFirst()
                .map(Connector::getLocalPort)
                .orElseThrow(() -> new IllegalStateException("MCP connector not found"));
    }

    private static String initializePayload() {
        return """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-11-25","capabilities":{},
                  "clientInfo":{"name":"mdr-e2e","version":"1.0"}}}""";
    }

    private static String rpc(int id, String method, String params) {
        return params == null
                ? "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\"}".formatted(id, method)
                : "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}".formatted(id, method, params);
    }

    private McpResponse postMcp(int port, String json, String token, String sessionId)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + McpServerConfig.MCP_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (sessionId != null) {
            builder.header("mcp-session-id", sessionId);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String body = unwrapSse(response.body());
        JsonNode tree = body.isBlank() ? null : objectMapper.readTree(body);
        return new McpResponse(response.statusCode(), tree,
                response.headers().firstValue("mcp-session-id").orElse(sessionId));
    }

    /** Streamable HTTP 可能以 SSE（data: 行）回传单条 JSON；剥壳取 JSON。 */
    private static String unwrapSse(String body) {
        if (body == null || !body.contains("data:")) {
            return body;
        }
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                return trimmed.substring("data:".length()).trim();
            }
        }
        return body;
    }

    private record McpResponse(int status, JsonNode body, String sessionId) {
    }
}
