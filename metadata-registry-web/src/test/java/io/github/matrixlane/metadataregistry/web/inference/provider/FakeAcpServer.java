package io.github.matrixlane.metadataregistry.web.inference.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 假 ACP server：按真实协议从 stdin 读 NDJSON 请求并应答，供 ProcessRunner stub
 * 返回。支持握手失败、prompt 挂起、cancel 结束 prompt、握手后断线（模拟崩溃）、
 * 发送反向请求（验证客户端的 -32601 防卫应答）等行为脚本。
 */
public class FakeAcpServer implements RunningProcess {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---- 行为脚本（测试配置）----
    public String agentName = "FakeAgent";
    /** agentInfo.title；非空时优先于 name 展示。 */
    public String agentTitle;
    public String agentVersion = "1.0.0";
    public boolean failHandshake;
    /** 收到 session/prompt 后不响应（配合 cancel 或超时测试）。 */
    public boolean hangOnPrompt;
    /** 收到 session/prompt 后以 JSON-RPC 错误应答（模拟作业执行失败）。 */
    public boolean failPrompt;
    /** 作业失败时的错误消息。 */
    public String promptErrorMessage = "prompt failed";
    /** initialize 应答后立即关闭 stdout（模拟进程崩溃）。 */
    public boolean dieAfterHandshake;
    /** initialize 应答后向客户端发送 fs/read_text_file 反向请求。 */
    public boolean sendReverseRequest;
    /** 收到 session/cancel 时结束挂起的 prompt（真实 Agent 的行为）。 */
    public boolean cancelEndsPrompt = true;
    /** 每次 session/prompt 的应答文本块（依次消费）；空则直接回 stopReason。 */
    public final List<String> promptReplies = new ArrayList<>();

    // ---- 观测点 ----
    public final List<JsonNode> receivedRequests = Collections.synchronizedList(new ArrayList<>());
    public volatile boolean cancelNotificationReceived;
    public volatile boolean closeNotificationReceived;
    public volatile JsonNode reverseRequestErrorResponse;
    public volatile JsonNode lastPromptParams;
    public volatile JsonNode lastInitializeParams;

    private final PipedOutputStream stdinSink;
    private final PipedInputStream stdoutSource;
    private final PrintWriter out;
    private final Thread serverThread;
    private final CountDownLatch destroyedLatch = new CountDownLatch(1);
    private final AtomicInteger sessionCounter = new AtomicInteger();
    public volatile boolean destroyed;
    /** 挂起的 session/prompt 请求 id（hangOnPrompt 时非空），供测试轮询。 */
    public volatile String pendingPromptId;

    public FakeAcpServer() {
        try {
            stdinSink = new PipedOutputStream();
            PipedInputStream stdinSource = new PipedInputStream(stdinSink);
            stdoutSource = new PipedInputStream();
            PipedOutputStream stdoutSink = new PipedOutputStream(stdoutSource);
            out = new PrintWriter(new java.io.OutputStreamWriter(stdoutSink, StandardCharsets.UTF_8));
            serverThread = new Thread(() -> serve(stdinSource), "fake-acp-server");
            serverThread.setDaemon(true);
            serverThread.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 模拟进程崩溃：关闭 stdout，客户端读到 EOF。 */
    public void die() {
        out.close();
    }

    /** 测试直接向客户端注帧（模拟 server 主动推送）。 */
    public void sendLine(String line) throws java.io.IOException {
        writeLine(line);
    }

    private void serve(InputStream stdinSource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stdinSource, StandardCharsets.UTF_8))) {
            String line;
            while (!destroyed && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                handle(line);
            }
        } catch (IOException ignored) {
            // 管道关闭即退出
        }
    }

    private void handle(String line) throws IOException {
        JsonNode frame = MAPPER.readTree(line);
        if (!frame.hasNonNull("method")) {
            if (frame.hasNonNull("id") && frame.get("id").asLong() == 99) {
                reverseRequestErrorResponse = frame;
            }
            return;
        }
        String method = frame.get("method").asText();
        JsonNode params = frame.path("params");
        receivedRequests.add(frame);
        switch (method) {
            case "initialize" -> onInitialize(frame);
            case "session/new" -> respond(frame, result -> result.put("sessionId",
                    "sess-" + sessionCounter.incrementAndGet()));
            case "session/prompt" -> onPrompt(frame, params);
            case "session/cancel" -> {
                cancelNotificationReceived = true;
                if (cancelEndsPrompt && pendingPromptId != null) {
                    String promptId = pendingPromptId;
                    pendingPromptId = null;
                    respondRaw(promptId, "{\"stopReason\":\"cancelled\"}");
                }
            }
            case "session/close" -> closeNotificationReceived = true;
            case "initialized" -> {
                // 客户端能力确认通知：无响应
            }
            default -> {
                // 未知请求：回 method not found
                ObjectNode error = MAPPER.createObjectNode();
                error.put("code", -32601);
                error.put("message", "method not found");
                respondError(frame, error);
            }
        }
    }

    private void onInitialize(JsonNode frame) throws IOException {
        lastInitializeParams = frame.path("params");
        if (failHandshake) {
            ObjectNode error = MAPPER.createObjectNode();
            error.put("code", -32000);
            error.put("message", "handshake rejected");
            respondError(frame, error);
            return;
        }
        respond(frame, result -> {
            result.put("protocolVersion", 1);
            ObjectNode agentInfo = result.putObject("agentInfo");
            agentInfo.put("name", agentName);
            if (agentTitle != null && !agentTitle.isBlank()) {
                agentInfo.put("title", agentTitle);
            }
            agentInfo.put("version", agentVersion);
        });
        if (sendReverseRequest) {
            sendLine("{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"fs/read_text_file\","
                    + "\"params\":{\"sessionId\":\"sess-0\",\"path\":\"/etc/passwd\"}}");
        }
        if (dieAfterHandshake) {
            die();
        }
    }

    private void onPrompt(JsonNode frame, JsonNode params) throws IOException {
        lastPromptParams = params;
        if (hangOnPrompt) {
            pendingPromptId = frame.get("id").asText();
            return;
        }
        if (failPrompt) {
            ObjectNode error = MAPPER.createObjectNode();
            error.put("code", -32000);
            error.put("message", promptErrorMessage);
            respondError(frame, error);
            return;
        }
        String sessionId = params.path("sessionId").asText();
        // 队列中当前全部应答文本作为本次 prompt 的多个文本块依次推送
        while (!promptReplies.isEmpty()) {
            String reply = promptReplies.remove(0);
            if (!reply.isEmpty()) {
                sendLine(chunk(sessionId, "agent_message_chunk", escapeJsonText(reply)));
            }
        }
        // 非文本块必须被忽略
        sendLine(chunk(sessionId, "agent_thought_chunk", escapeJsonText("thinking...")));
        respond(frame, result -> result.put("stopReason", "end_turn"));
    }

    private String chunk(String sessionId, String updateType, String contentJson) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{\"sessionId\":\""
                + sessionId + "\",\"update\":{\"sessionUpdate\":\"" + updateType + "\",\"content\":"
                + contentJson + "}}}";
    }

    private static String escapeJsonText(String text) {
        StringBuilder sb = new StringBuilder("{\"type\":\"text\",\"text\":");
        sb.append(MAPPER.getNodeFactory().textNode(text).toString()).append('}');
        return sb.toString();
    }

    private void respond(JsonNode request, java.util.function.Consumer<ObjectNode> resultBuilder)
            throws IOException {
        ObjectNode result = MAPPER.createObjectNode();
        resultBuilder.accept(result);
        respondRaw(request.get("id").asText(), MAPPER.writeValueAsString(result));
    }

    private void respondError(JsonNode request, ObjectNode error) throws IOException {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", request.get("id").asLong());
        response.set("error", error);
        sendLine(MAPPER.writeValueAsString(response));
    }

    private void respondRaw(String id, String resultJson) throws IOException {
        sendLine("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}");
    }

    private void writeLine(String line) throws IOException {
        synchronized (out) {
            if (destroyed) {
                return;
            }
            out.write(line);
            out.write('\n');
            out.flush();
        }
    }

    @Override
    public OutputStream stdin() {
        return stdinSink;
    }

    @Override
    public InputStream stdout() {
        return stdoutSource;
    }

    @Override
    public boolean waitFor(long timeoutMillis) throws InterruptedException {
        destroyedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public int exitValue() {
        return 0;
    }

    @Override
    public void destroyForcibly() {
        destroyed = true;
        destroyedLatch.countDown();
        try {
            stdinSink.close();
        } catch (IOException ignored) {
        }
        out.close();
    }
}
