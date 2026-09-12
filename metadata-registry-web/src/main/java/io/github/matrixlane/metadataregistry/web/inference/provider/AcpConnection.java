package io.github.matrixlane.metadataregistry.web.inference.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ACP（Agent Client Protocol）stdio 连接：NDJSON 线格式，每行一个 JSON-RPC 2.0 对象。
 *
 * <p>只实现客户端子集：{@code initialize}、{@code session/new}、{@code session/prompt}
 * 请求，{@code session/cancel} / {@code session/close} 通知。请求-响应按 JSON-RPC id
 * 配对；无 id 且带 {@code method} 的对象分两类：{@code session/update} 通知按
 * {@code sessionId} 路由给注册的监听器，其余视为对端反向请求（权限、文件读写等），
 * 一律以防卫性 {@code -32601 method not found} 错误应答，避免对端挂起。
 *
 * <p>写侧单行加锁；读侧守护线程按行解析，进程 EOF 即连接死亡，未完成请求全部
 * 以异常完成，后续调用抛出 {@link AcpTransportException}。
 */
@Slf4j
public class AcpConnection implements AutoCloseable {

    /** 连接已死亡（进程 EOF / 读写失败）后的后续调用抛出此异常。 */
    public static class AcpTransportException extends IOException {
        public AcpTransportException(String message) {
            super(message);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long METHOD_NOT_FOUND = -32601;

    private final RunningProcess process;
    private final PrintWriter writer;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> sessionListeners = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private final AtomicLong requestsWritten = new AtomicLong();

    private volatile boolean alive = true;
    private volatile String deathCause;

    public AcpConnection(RunningProcess process) {
        this.process = process;
        this.writer = new PrintWriter(new OutputStreamWriter(process.stdin(), StandardCharsets.UTF_8));
        Thread reader = new Thread(this::readLoop, "mdr-acp-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** 自启动以来写出的请求/通知帧数（响应帧不计），便于测试断言写顺序。 */
    long requestsWritten() {
        return requestsWritten.get();
    }

    boolean isAlive() {
        return alive;
    }

    /** 注册某 session 的通知监听器；null 监听器为注销。 */
    void listenSession(String sessionId, Consumer<JsonNode> listener) {
        if (listener == null) {
            sessionListeners.remove(sessionId);
        } else {
            sessionListeners.put(sessionId, listener);
        }
    }

    /** 发送 JSON-RPC 请求并等待响应；连接死亡时 future 以异常完成。 */
    CompletableFuture<JsonNode> request(String method, JsonNode params) {
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("jsonrpc", "2.0");
        long id = nextId.getAndIncrement();
        frame.put("id", id);
        frame.put("method", method);
        if (params != null) {
            frame.set("params", params);
        }
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            writeLine(frame);
        } catch (AcpTransportException e) {
            pending.remove(id);
            future.completeExceptionally(e);
        }
        return future;
    }

    /** 发送 JSON-RPC 通知（无 id，无需响应）。 */
    void notify(String method, JsonNode params) {
        ObjectNode frame = MAPPER.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("method", method);
        if (params != null) {
            frame.set("params", params);
        }
        try {
            writeLine(frame);
        } catch (AcpTransportException e) {
            log.debug("ACP 通知发送失败（{}）：{}", method, e.getMessage());
        }
    }

    private void writeLine(ObjectNode frame) throws AcpTransportException {
        synchronized (writeLock) {
            if (!alive) {
                throw new AcpTransportException("ACP 连接已关闭：" + deathCause);
            }
            try {
                writer.write(MAPPER.writeValueAsString(frame));
                writer.write('\n');
                writer.flush();
                if (frame.has("method")) {
                    requestsWritten.incrementAndGet();
                }
            } catch (IOException e) {
                die("写入失败：" + e.getMessage());
                throw new AcpTransportException("ACP 帧写入失败：" + e.getMessage());
            }
        }
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.stdout(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                handleLine(line);
            }
            die("进程 stdout 已结束（EOF）");
        } catch (IOException e) {
            if (alive) {
                die("读取失败：" + e.getMessage());
            }
        }
    }

    private void handleLine(String line) {
        final JsonNode frame;
        try {
            frame = MAPPER.readTree(line);
        } catch (IOException e) {
            log.debug("忽略无法解析的 ACP 帧：{}", line);
            return;
        }
        if (frame.hasNonNull("id")) {
            long id = frame.get("id").asLong();
            CompletableFuture<JsonNode> future = pending.remove(id);
            if (future != null) {
                // 本端发出的请求的响应
                if (frame.hasNonNull("error")) {
                    JsonNode error = frame.get("error");
                    future.completeExceptionally(new AcpTransportException(
                            "ACP 请求失败：" + error.path("message").asText(error.toString())));
                } else {
                    future.complete(frame.path("result"));
                }
            } else {
                // 对端反向请求：无此能力，防卫性应答 method not found
                ObjectNode response = MAPPER.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.put("id", id);
                ObjectNode error = response.putObject("error");
                error.put("code", METHOD_NOT_FOUND);
                error.put("message", "method not found");
                try {
                    writeLine(response);
                } catch (AcpTransportException ignored) {
                    // 连接已死，放弃应答
                }
            }
            return;
        }
        if (frame.hasNonNull("method")) {
            String method = frame.get("method").asText();
            JsonNode params = frame.path("params");
            if ("session/update".equals(method)) {
                String sessionId = params.path("sessionId").asText(null);
                Consumer<JsonNode> listener = sessionId == null ? null : sessionListeners.get(sessionId);
                if (listener != null) {
                    try {
                        listener.accept(params.path("update"));
                    } catch (RuntimeException e) {
                        log.debug("ACP session/update 监听器异常", e);
                    }
                }
            } else {
                log.debug("忽略未知 ACP 通知：{}", method);
            }
        }
    }

    private void die(String cause) {
        if (!alive) {
            return;
        }
        alive = false;
        deathCause = cause;
        List<CompletableFuture<JsonNode>> stranded = List.copyOf(pending.values());
        pending.clear();
        AcpTransportException failure = new AcpTransportException("ACP 连接已终止：" + cause);
        stranded.forEach(f -> f.completeExceptionally(failure));
    }

    /** 关闭连接（尽力）：未完成请求以异常完成，进程由持有方销毁。 */
    @Override
    public void close() {
        die("closed");
    }
}
