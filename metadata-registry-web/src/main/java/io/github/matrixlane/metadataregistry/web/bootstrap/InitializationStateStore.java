package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 本地初始化状态记录的读写。
 *
 * <p>与本地数据源配置同目录（{@code ~/.metadata-registry/}），原子写入（同目录
 * 临时文件 + 原子替换），容错读取（缺失或内容损坏视为无记录）。记录内容
 * MUST NOT 包含数据库口令或完整连接串。
 */
@Slf4j
@Component
public class InitializationStateStore {

    private static final String FILE_NAME = "initialization-state.yml";

    private final Path path;

    /** 按 mtime 失效的读取缓存：初始化拦截器逐请求调用，避免每请求解析 YAML。 */
    private volatile InitializationState cached;
    private volatile long cachedMtime = -1;

    public InitializationStateStore() {
        this(defaultPath());
    }

    public InitializationStateStore(Path path) {
        this.path = path;
    }

    /** 默认路径：与本地配置同目录，便于随用户目录一起备份/迁移。 */
    public static Path defaultPath() {
        return DatasourceConfigService.localConfigPath().resolveSibling(FILE_NAME);
    }

    public Path path() {
        return path;
    }

    public Optional<InitializationState> read() {
        if (!Files.exists(path)) {
            cached = null;
            cachedMtime = -1;
            return Optional.empty();
        }
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Optional.empty();
        }
        InitializationState snapshot = cached;
        if (snapshot != null && mtime == cachedMtime) {
            return Optional.of(snapshot);
        }
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> raw = new Yaml().load(in);
            if (raw == null) {
                return Optional.empty();
            }
            InitializationState parsed = parse(raw);
            cached = parsed;
            cachedMtime = mtime;
            return Optional.of(parsed);
        } catch (Exception e) {
            log.warn("初始化状态记录 {} 读取失败（按无记录处理）: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    public void write(InitializationState state) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(
                    parent == null ? Paths.get(".") : parent, ".initialization-state-", ".tmp");
            try {
                Files.writeString(temp, render(state), StandardCharsets.UTF_8);
                moveWithRetry(temp, path);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("无法写入初始化状态记录 " + path, e);
        }
        refreshCache(state);
    }

    private void refreshCache(InitializationState state) {
        long mtime = -1;
        try {
            mtime = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            // 保持缓存失效，下一次读取重新解析
        }
        cached = state;
        cachedMtime = mtime;
    }

    /**
     * 原子替换优先；Windows 上安全软件可能短暂锁住刚写出的临时文件，
     * 对瞬时失败重试，再回退到非原子移动与复制删除。
     */
    private static void moveWithRetry(Path temp, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                try {
                    Files.move(temp, target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        try {
            Files.copy(temp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(temp);
        } catch (IOException copyFailure) {
            if (last != null) {
                copyFailure.addSuppressed(last);
            }
            throw copyFailure;
        }
    }

    private InitializationState parse(Map<String, Object> raw) {
        return new InitializationState(
                asInt(raw.get("schemaVersion"), InitializationState.CURRENT_SCHEMA_VERSION),
                asBool(raw.get("datasourceConfigured"), false),
                asString(raw.get("datasourceFingerprint")),
                asBool(raw.get("initialized"), false),
                asString(raw.get("edition")),
                parseInstant(asString(raw.get("updatedAt"))));
    }

    private String render(InitializationState state) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", state.schemaVersion());
        map.put("datasourceConfigured", state.datasourceConfigured());
        if (state.datasourceFingerprint() != null) {
            map.put("datasourceFingerprint", state.datasourceFingerprint());
        }
        map.put("initialized", state.initialized());
        if (state.edition() != null) {
            map.put("edition", state.edition());
        }
        map.put("updatedAt", state.updatedAt().toString());
        return new Yaml().dump(map);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }
}
