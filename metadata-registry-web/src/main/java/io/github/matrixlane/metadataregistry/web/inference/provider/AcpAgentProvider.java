package io.github.matrixlane.metadataregistry.web.inference.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.matrixlane.metadataregistry.web.inference.InferenceException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 本地 Coding Agent ACP 推断提供方（常驻进程，JSON-RPC over stdio/NDJSON）。
 *
 * <p>每个 Agent 一个实例（{@link #id()} 即 Agent id）。进程懒启动并在首次使用时完成
 * {@code initialize} 握手（clientCapabilities 不含 fs/终端能力）；进程意外退出后
 * 下一次使用自动重建并重新握手，不影响其它 Agent。每次协作作业经
 * {@code session/new}（独立临时工作目录、空 MCP 列表）→ {@code session/prompt}（单
 * 文本块）→ 累积 {@code session/update} 中 {@code agent_message_chunk} 文本 →
 * 响应完成（记录 stopReason）→ 尽力 {@code session/close}。作业取消发送
 * {@code session/cancel} 通知，若 5s 内未结束则销毁进程；作业超时沿用
 * {@code mdr.inference.cli.timeout-seconds} 语义并取消该作业。
 *
 * <p>同一进程多作业并发：ACP server 按会话隔离，不加串行锁；若未来发现对端
 * 不支持并发会话，可在 {@link #infer} 上加 synchronized 回退为串行。
 */
@Slf4j
public class AcpAgentProvider implements MetadataInferenceProvider {

    static final long CANCEL_GRACE_MILLIS = 5_000;
    static final long HANDSHAKE_TIMEOUT_MILLIS = 10_000;
    static final int PROTOCOL_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String id;
    private final String displayName;
    private final List<String> command;
    private final int timeoutSeconds;
    private final String workDirBase;
    private final ProcessRunner processRunner;

    private final Object processLock = new Object();
    private final Map<UUID, String> jobSessions = new ConcurrentHashMap<>();

    private volatile AcpConnection connection;
    private volatile RunningProcess process;
    private volatile String agentInfo;
    private volatile boolean processFailed;

    public AcpAgentProvider(String id, String displayName, List<String> command,
                            int timeoutSeconds, String workDirBase, ProcessRunner processRunner) {
        this.id = id;
        this.displayName = displayName;
        this.command = List.copyOf(command);
        this.timeoutSeconds = timeoutSeconds;
        this.workDirBase = workDirBase;
        this.processRunner = processRunner;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ProviderStatus status() {
        try {
            AcpConnection conn = ensureConnection();
            return new ProviderStatus(id, displayName, true, agentInfo == null ? "ACP 可用" : agentInfo);
        } catch (InferenceException e) {
            return new ProviderStatus(id, displayName, false, "Agent 不可用：" + e.getMessage());
        }
    }

    @Override
    public ProviderStatus probe() {
        destroyProcess();
        return status();
    }

    @Override
    public InferenceResult infer(InferenceRequest request) {
        AcpConnection conn = ensureConnection();
        Path sessionDir = createSessionDirectory();
        String sessionId = null;
        try {
            sessionId = sessionNew(conn, sessionDir);
            String promptSessionId = sessionId;
            StringBuilder accumulated = new StringBuilder();
            conn.listenSession(promptSessionId, update -> accumulateText(update, accumulated));

            ObjectNode params = MAPPER.createObjectNode();
            params.put("sessionId", promptSessionId);
            ObjectNode textBlock = params.putArray("prompt").addObject();
            textBlock.put("type", "text");
            textBlock.put("text", request.prompt());

            CompletableFuture<JsonNode> promptFuture = conn.request("session/prompt", params);
            jobSessions.put(request.jobId(), promptSessionId);
            JsonNode result;
            try {
                result = promptFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                cancel(conn, request.jobId(), promptSessionId);
                throw new InferenceException("ACP 作业超时（" + timeoutSeconds + "s），已发送 session/cancel", e);
            } catch (ExecutionException e) {
                throw new InferenceException("ACP 作业失败：" + rootMessage(e), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel(conn, request.jobId(), promptSessionId);
                throw new InferenceException("等待 ACP 作业时被中断", e);
            }
            String stopReason = result.path("stopReason").asText("end_turn");
            log.debug("ACP 作业完成：agent={} session={} stopReason={}", id, promptSessionId, stopReason);
            return new InferenceResult(accumulated.toString());
        } finally {
            jobSessions.remove(request.jobId());
            conn.listenSession(sessionId, null);
            if (sessionId != null) {
                sessionClose(conn, sessionId);
            }
            deleteQuietly(sessionDir);
        }
    }

    @Override
    public boolean cancel(UUID jobId) {
        String sessionId = jobSessions.get(jobId);
        if (sessionId == null) {
            return false;
        }
        AcpConnection conn = connection;
        if (conn == null) {
            return false;
        }
        cancel(conn, jobId, sessionId);
        return true;
    }

    /** 发送 session/cancel 通知；对端 5s 内未自行结束时销毁常驻进程。 */
    private void cancel(AcpConnection conn, UUID jobId, String sessionId) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("sessionId", sessionId);
        conn.notify("session/cancel", params);
        Thread reaper = new Thread(() -> {
            try {
                Thread.sleep(CANCEL_GRACE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (jobSessions.containsKey(jobId) && connection == conn) {
                log.warn("ACP 作业取消后 5s 未结束，销毁 agent={} 的常驻进程", id);
                destroyProcess();
            }
        }, "mdr-acp-cancel-reaper");
        reaper.setDaemon(true);
        reaper.start();
    }

    /** 懒启动常驻进程并完成 initialize 握手；崩溃后由本方法重建。 */
    private AcpConnection ensureConnection() {
        AcpConnection conn = connection;
        if (conn != null && conn.isAlive() && !processFailed) {
            return conn;
        }
        synchronized (processLock) {
            conn = connection;
            if (conn != null && conn.isAlive() && !processFailed) {
                return conn;
            }
            destroyProcessQuietly();
            try {
                Path workDir = Path.of(workDirBase).toAbsolutePath().normalize();
                Files.createDirectories(workDir);
                RunningProcess started = processRunner.start(command, workDir);
                try {
                    AcpConnection created = new AcpConnection(started);
                    JsonNode result = created.request("initialize", initializeParams())
                            .get(HANDSHAKE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    JsonNode info = result.path("agentInfo");
                    String name = info.path("title").isTextual()
                            ? info.path("title").asText() : info.path("name").asText("");
                    String version = info.path("version").asText("");
                    agentInfo = (name + " " + version).trim();
                    created.notify("initialized", MAPPER.createObjectNode());
                    this.process = started;
                    this.connection = created;
                    this.processFailed = false;
                    log.info("ACP 进程就绪：agent={} {}（命令：{}）", id, agentInfo, String.join(" ", command));
                    return created;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    started.destroyForcibly();
                    throw new InferenceException("ACP initialize 握手时被中断", e);
                } catch (Exception e) {
                    started.destroyForcibly();
                    throw handshakeFailure(e);
                }
            } catch (IOException e) {
                throw new InferenceException("启动 ACP 进程失败（" + String.join(" ", command) + "）："
                        + e.getMessage(), e);
            }
        }
    }

    private InferenceException handshakeFailure(Exception e) {
        if (e instanceof InferenceException ie) {
            return ie;
        }
        Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
        if (cause instanceof TimeoutException) {
            return new InferenceException("ACP initialize 握手超时（10s），Agent 未响应");
        }
        String message = cause == null ? e.getMessage() : cause.getMessage();
        return new InferenceException("ACP initialize 握手失败：" + message, cause);
    }

    private ObjectNode initializeParams() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = params.putObject("clientCapabilities");
        ObjectNode fs = capabilities.putObject("fs");
        fs.put("readTextFile", false);
        fs.put("writeTextFile", false);
        return params;
    }

    private String sessionNew(AcpConnection conn, Path sessionDir) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("cwd", sessionDir.toAbsolutePath().toString());
        params.putArray("mcpServers");
        try {
            JsonNode result = conn.request("session/new", params)
                    .get(HANDSHAKE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            String sessionId = result.path("sessionId").asText(null);
            if (sessionId == null || sessionId.isBlank()) {
                throw new InferenceException("ACP session/new 响应缺少 sessionId");
            }
            return sessionId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InferenceException("等待 ACP session/new 时被中断", e);
        } catch (ExecutionException e) {
            throw new InferenceException("ACP session/new 失败：" + rootMessage(e), e.getCause());
        } catch (TimeoutException e) {
            throw new InferenceException("ACP session/new 超时（10s）");
        }
    }

    private void sessionClose(AcpConnection conn, String sessionId) {
        try {
            ObjectNode params = MAPPER.createObjectNode();
            params.put("sessionId", sessionId);
            conn.notify("session/close", params);
        } catch (RuntimeException e) {
            log.debug("ACP session/close 通知失败：agent={} session={}", id, sessionId);
        }
    }

    /** 只累积 agent_message_chunk 的文本块；忽略 thought / tool_call / plan 等。 */
    private void accumulateText(JsonNode update, StringBuilder accumulated) {
        if (!"agent_message_chunk".equals(update.path("sessionUpdate").asText())) {
            return;
        }
        JsonNode content = update.path("content");
        if (!"text".equals(content.path("type").asText())) {
            return;
        }
        String text = content.path("text").asText("");
        if (!text.isEmpty()) {
            synchronized (accumulated) {
                accumulated.append(text);
            }
        }
    }

    private Path createSessionDirectory() {
        try {
            Path base = Path.of(workDirBase).toAbsolutePath().normalize();
            Files.createDirectories(base);
            return Files.createTempDirectory(base, "acp-");
        } catch (IOException e) {
            throw new InferenceException("创建 ACP 会话工作目录失败：" + e.getMessage(), e);
        }
    }

    private String rootMessage(ExecutionException e) {
        Throwable cause = e.getCause();
        return cause == null || cause.getMessage() == null ? e.getMessage() : cause.getMessage();
    }

    /** 销毁常驻进程（取消未完成作业、关闭连接、杀进程）。下次使用自动重建。 */
    public void destroyProcess() {
        synchronized (processLock) {
            destroyProcessQuietly();
        }
    }

    @Override
    public void destroy() {
        destroyProcess();
    }

    private void destroyProcessQuietly() {
        AcpConnection conn = connection;
        connection = null;
        RunningProcess proc = process;
        process = null;
        processFailed = true;
        if (conn != null) {
            conn.close();
        }
        if (proc != null) {
            try {
                proc.destroyForcibly();
            } catch (RuntimeException e) {
                log.debug("销毁 ACP 进程失败：agent={}", id, e);
            }
        }
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            log.debug("清理 ACP 会话目录残留文件失败：{}", file);
                        }
                    });
        } catch (IOException e) {
            log.debug("清理 ACP 会话目录失败：{}", dir, e);
        }
    }
}
