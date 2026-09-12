package io.github.matrixlane.metadataregistry.web.inference.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcpConnectionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AcpConnection start(FakeAcpServer server) {
        return new AcpConnection(server);
    }

    private ObjectNode params(String json) throws Exception {
        return (ObjectNode) MAPPER.readTree(json);
    }

    @Test
    void request_responsePairedById() throws Exception {
        FakeAcpServer server = new FakeAcpServer();
        try (AcpConnection conn = start(server)) {
            JsonNode result = conn.request("initialize", params("{\"protocolVersion\":1}"))
                    .get(5, TimeUnit.SECONDS);
            assertThat(result.path("agentInfo").path("name").asText()).isEqualTo("FakeAgent");
            assertThat(server.lastInitializeParams.path("protocolVersion").asInt()).isEqualTo(1);
        }
    }

    @Test
    void request_errorResponse_completesExceptionally() {
        FakeAcpServer server = new FakeAcpServer();
        server.failHandshake = true;
        try (AcpConnection conn = start(server)) {
            CompletableFuture<JsonNode> future =
                    conn.request("initialize", MAPPER.createObjectNode());
            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(AcpConnection.AcpTransportException.class)
                    .rootCause()
                    .hasMessageContaining("handshake rejected");
        }
    }

    @Test
    void notification_routedToSessionListenerBySessionId() throws Exception {
        FakeAcpServer server = new FakeAcpServer();
        try (AcpConnection conn = start(server)) {
            List<JsonNode> updates = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);
            conn.listenSession("sess-1", update -> {
                updates.add(update);
                latch.countDown();
            });
            // 借 session/new 拿真实 sessionId，再触发该 session 的 update
            JsonNode newResult = conn.request("session/new", MAPPER.createObjectNode())
                    .get(5, TimeUnit.SECONDS);
            String sessionId = newResult.path("sessionId").asText();
            conn.listenSession("sess-1", null);
            conn.listenSession(sessionId, update -> {
                updates.add(update);
                latch.countDown();
            });

            server.sendLine("{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{\"sessionId\":\""
                    + sessionId + "\",\"update\":{\"sessionUpdate\":\"agent_message_chunk\","
                    + "\"content\":{\"type\":\"text\",\"text\":\"hi\"}}}}");

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(updates).hasSize(1);
            assertThat(updates.get(0).path("content").path("text").asText()).isEqualTo("hi");
        }
    }

    @Test
    void reverseRequest_isAnsweredMethodNotFound() throws Exception {
        FakeAcpServer server = new FakeAcpServer();
        server.sendReverseRequest = true;
        try (AcpConnection conn = start(server)) {
            // 触发 initialize，server 随后发送 fs/read_text_file 反向请求
            conn.request("initialize", MAPPER.createObjectNode()).get(5, TimeUnit.SECONDS);
            long deadline = System.currentTimeMillis() + 5000;
            while (server.reverseRequestErrorResponse == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertThat(server.reverseRequestErrorResponse).isNotNull();
            assertThat(server.reverseRequestErrorResponse.path("error").path("code").asLong()).isEqualTo(-32601);
            // 反向请求不应消耗本端请求 id 序列，也不产生悬挂 future
            assertThat(conn.requestsWritten()).isEqualTo(1);
        }
    }

    @Test
    void processEof_strandsPendingRequests() throws Exception {
        FakeAcpServer server = new FakeAcpServer();
        server.hangOnPrompt = true;
        AcpConnection conn = start(server);
        conn.request("initialize", MAPPER.createObjectNode()).get(5, TimeUnit.SECONDS);
        CompletableFuture<JsonNode> stranded = conn.request("session/prompt",
                params("{\"sessionId\":\"sess-0\",\"prompt\":[]}"));
        // 等 server 收到 prompt 再杀进程，模拟崩溃
        long deadline = System.currentTimeMillis() + 5000;
        while (server.pendingPromptId == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        server.die();

        assertThatThrownBy(() -> stranded.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AcpConnection.AcpTransportException.class);
        // 等读线程处理 EOF 后连接才真正死亡
        long aliveDeadline = System.currentTimeMillis() + 5000;
        while (conn.isAlive() && System.currentTimeMillis() < aliveDeadline) {
            Thread.sleep(10);
        }
        assertThat(conn.isAlive()).isFalse();
        // 连接死亡后新请求以异常完成（写失败转为 failed future）
        assertThatThrownBy(() -> conn.request("session/new", MAPPER.createObjectNode())
                .get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AcpConnection.AcpTransportException.class);
    }

    @Test
    void unreadableLine_isIgnored() throws Exception {
        FakeAcpServer server = new FakeAcpServer();
        try (AcpConnection conn = start(server)) {
            server.sendLine("not-json-at-all");
            JsonNode result = conn.request("initialize", MAPPER.createObjectNode())
                    .get(5, TimeUnit.SECONDS);
            assertThat(result.path("agentInfo").path("name").asText()).isEqualTo("FakeAgent");
        }
    }
}
