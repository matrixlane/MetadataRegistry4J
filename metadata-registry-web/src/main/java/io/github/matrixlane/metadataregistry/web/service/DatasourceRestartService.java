package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceApplyRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartAcceptedResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartCapabilityResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartStatusResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import io.github.matrixlane.metadataregistry.web.startup.StartupReadinessIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DatasourceRestartService {

    private static final String REQUEST_FILE = "restart-request.json";
    private static final String STATUS_FILE = "restart-status.json";
    private static final String LOCK_FILE = "restart.lock";
    private static final String SUPERVISOR_PID_FILE = "supervisor.pid.json";
    private static final Set<String> TERMINAL_STATES = Set.of(
            "SUCCEEDED", "ROLLED_BACK", "RECOVERY_FAILED");

    private final DatasourceConfigService datasourceConfigService;
    private final DatabaseEnvironment databaseEnvironment;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final StartupReadinessIndicator readinessIndicator;

    public DatasourceRestartService(
            DatasourceConfigService datasourceConfigService,
            DatabaseEnvironment databaseEnvironment,
            Environment environment,
            ObjectMapper objectMapper,
            StartupReadinessIndicator readinessIndicator) {
        this.datasourceConfigService = datasourceConfigService;
        this.databaseEnvironment = databaseEnvironment;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.readinessIndicator = readinessIndicator;
    }

    public DatasourceRestartCapabilityResponse capability(String remoteAddress) {
        String reasonCode = unsupportedReason(remoteAddress);
        return new DatasourceRestartCapabilityResponse(reasonCode == null, reasonCode);
    }

    public DatasourceRestartAcceptedResponse apply(DatasourceApplyRequest request, String remoteAddress) {
        assertSupported(remoteAddress);
        String edition = request.config().edition();
        if (edition == null || edition.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "自动应用需要明确指定部署版本");
        }
        if (DeploymentEdition.parse(edition) != DeploymentEdition.PERSONAL) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "自动应用仅支持个人版初始化向导");
        }

        Path control = controlDirectory();
        UUID restartId = request.restartId();
        String fingerprint = fingerprint(request);
        Path lock = control.resolve(LOCK_FILE);
        boolean lockAcquired = false;
        try {
            DatasourceRestartAcceptedResponse existing = existingTransaction(control, restartId, fingerprint);
            if (existing != null) {
                return existing;
            }
            acquireLock(lock);
            lockAcquired = true;
            existing = existingTransaction(control, restartId, fingerprint);
            if (existing != null) {
                return existing;
            }
            discardFinishedTransactionFiles(control);

            Path localConfig = datasourceConfigService.localConfig();
            Path backup = control.resolve("backups").resolve(restartId + ".yml");
            boolean previousMissing = !Files.exists(localConfig);
            boolean backupReady = false;
            try {
                if (!previousMissing) {
                    Files.createDirectories(backup.getParent());
                    atomicCopy(localConfig, backup);
                }
                backupReady = true;

                datasourceConfigService.saveConfig(request.config());
                RestartRequest restartRequest = describeTarget(
                        request, localConfig, backup, previousMissing, fingerprint);
                writeJson(control.resolve(REQUEST_FILE), restartRequest);
                writeJson(control.resolve(STATUS_FILE), new RestartStatus(
                        restartId.toString(), "PENDING", null, fingerprint, Instant.now().toString()));
                return new DatasourceRestartAcceptedResponse(restartId);
            } catch (Exception ex) {
                if (backupReady) {
                    restoreAfterFailure(localConfig, backup, previousMissing, control, restartId, ex);
                }
                if (ex instanceof BusinessException businessException) {
                    throw businessException;
                }
                if (ex instanceof DatasourceRestartConflictException conflictException) {
                    throw conflictException;
                }
                throw new BusinessException(ApiCodes.INTERNAL_ERROR, "自动应用准备失败，已恢复原数据源配置");
            }
        } catch (FileAlreadyExistsException ex) {
            throw new DatasourceRestartConflictException("另一个数据源重启事务正在准备中，请稍后重试");
        } catch (IOException ex) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "无法准备数据源自动应用，请稍后重试");
        } finally {
            if (lockAcquired) {
                try {
                    Files.deleteIfExists(lock);
                } catch (IOException ignored) {
                }
            }
        }
    }

    DatasourceRestartStatusResponse status(UUID restartId) {
        return status(restartId, "127.0.0.1");
    }

    public DatasourceRestartStatusResponse status(UUID restartId, String remoteAddress) {
        if (!isLoopback(remoteAddress)) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "重启状态仅允许从本机查询");
        }
        if (!isPersonalProfile()) {
            throw new BusinessException(ApiCodes.FEATURE_NOT_ENABLED, "当前部署版本不支持数据源自动重启");
        }
        try {
            Path control = controlDirectory();
            RestartStatus status = readStatus(control);
            if (status == null || !restartId.toString().equals(status.restartId())) {
                RestartRequest request = readRequest(control);
                if (request != null && restartId.toString().equals(request.restartId())) {
                    // 事务已受理但状态文件尚未落盘：视为进行中，而不是"不存在"。
                    return new DatasourceRestartStatusResponse(
                            restartId.toString(), "PENDING", "PENDING", null);
                }
                throw new ResourceNotFoundException("数据源重启事务不存在: " + restartId);
            }
            String verification = switch (status.state()) {
                case "SUCCEEDED" -> "MATCHED";
                case "ROLLED_BACK" -> "CONFIG_MISMATCH".equals(status.reasonCode())
                        ? "MISMATCHED" : "NOT_REQUIRED";
                case "RECOVERY_FAILED" -> "NOT_REQUIRED";
                case "VERIFYING" -> verifyCurrentTarget(control);
                default -> "PENDING";
            };
            return new DatasourceRestartStatusResponse(
                    status.restartId(), status.state(), verification, status.reasonCode());
        } catch (IOException ex) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "无法读取数据源重启状态");
        }
    }

    private String verifyCurrentTarget(Path control) throws IOException {
        if (!readinessIndicator.isReady()) {
            return "PENDING";
        }
        RestartRequest request = readRequest(control);
        if (request == null) {
            return "PENDING";
        }
        DatabaseVendor actualVendor = databaseEnvironment.vendor();
        String actualUrl = environment.getProperty("spring.datasource.url");
        String expectedUrl = request.expectedJdbcUrl();
        boolean vendorMatches = actualVendor != null && request.expectedVendor() != null
                && actualVendor.name().equalsIgnoreCase(request.expectedVendor());
        boolean urlMatches = actualUrl != null && expectedUrl != null
                && actualUrl.trim().equals(expectedUrl.trim());
        return vendorMatches && urlMatches ? "MATCHED" : "MISMATCHED";
    }

    /**
     * 依据保存后的配置推导本次重启的目标，并拦截"环境变量会覆盖新配置"的情况。
     *
     * <p>保存时 URL、方言、schema、Flyway 目录均以 {@code ${ENV:默认值}} 形式落盘，
     * 而环境变量优先级高于本地配置文件：若机器上设置了 DB_URL 等变量，重启后
     * 应用仍会使用旧目标，用户只会看到"未能应用，已恢复原设置"这种无法定位的提示。
     * 这里在受理前直接识别冲突并给出可操作的错误。
     */
    private RestartRequest describeTarget(DatasourceApplyRequest request, Path localConfig, Path backup,
                                          boolean previousMissing, String fingerprint) throws IOException {
        Map<String, Object> saved = readYaml(localConfig);
        List<String> conflicts = environmentConflicts(saved);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "环境变量 " + String.join("、", conflicts)
                            + " 会覆盖本次数据源设置，请先清除该环境变量再试");
        }
        String expectedUrl = placeholderDefault(saved, "spring", "datasource", "url");
        String expectedVendor = DatabaseVendor.resolve(request.config().vendor(), null)
                .name().toLowerCase(Locale.ROOT);
        return new RestartRequest(
                request.restartId().toString(), expectedVendor, expectedUrl,
                backup.toAbsolutePath().normalize().toString(), previousMissing,
                fingerprint, Instant.now().toString());
    }

    /** 保存配置中受环境变量影响的键及其环境变量名。 */
    private static final List<String[]> ENV_BACKED_SETTINGS = List.of(
            new String[]{"spring", "datasource", "url", "DB_URL"},
            new String[]{"spring", "jpa", "properties", "hibernate", "dialect", "HIBERNATE_DIALECT"},
            new String[]{"spring", "jpa", "properties", "hibernate", "default_schema", "DB_DEFAULT_SCHEMA"},
            new String[]{"spring", "flyway", "locations", "DB_VENDOR"},
            new String[]{"spring", "flyway", "schemas", "FLYWAY_SCHEMAS"},
            new String[]{"spring", "flyway", "default-schema", "FLYWAY_DEFAULT_SCHEMA"});

    private List<String> environmentConflicts(Map<String, Object> saved) {
        List<String> conflicts = new ArrayList<>();
        for (String[] setting : ENV_BACKED_SETTINGS) {
            String envKey = setting[setting.length - 1];
            Object raw = nestedValue(saved, Arrays.copyOf(setting, setting.length - 1));
            if (!(raw instanceof String value) || !value.startsWith("${") || !value.endsWith("}")) {
                continue;
            }
            String inner = value.substring(2, value.length() - 1);
            int separator = inner.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String defaultValue = inner.substring(separator + 1);
            String envValue = environment.getProperty(envKey);
            if (envValue != null && !envValue.equals(defaultValue)) {
                conflicts.add(envKey);
            }
        }
        return conflicts;
    }

    private String placeholderDefault(Map<String, Object> saved, String... path) {
        Object raw = nestedValue(saved, path);
        if (raw instanceof String value) {
            if (value.startsWith("${") && value.endsWith("}")) {
                String inner = value.substring(2, value.length() - 1);
                int separator = inner.indexOf(':');
                if (separator > 0) {
                    return inner.substring(separator + 1);
                }
            }
            return value;
        }
        throw new BusinessException(ApiCodes.INTERNAL_ERROR, "保存后的数据源配置缺少 JDBC 地址");
    }

    private Object nestedValue(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private Map<String, Object> readYaml(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            Map<String, Object> loaded = new Yaml().load(input);
            return loaded == null ? new LinkedHashMap<>() : loaded;
        }
    }

    /**
     * 获取事务锁。锁文件记录持有者 PID：进程被强杀后残留的锁必须能被识别为陈旧，
     * 否则此后每次自动应用都会命中"事务正在进行中"，功能将永久不可用。
     */
    private void acquireLock(Path lock) throws IOException {
        try {
            Files.createFile(lock);
        } catch (FileAlreadyExistsException ex) {
            if (!isStaleLock(lock)) {
                throw ex;
            }
            Files.deleteIfExists(lock);
            Files.createFile(lock);
        }
        Files.writeString(lock, Long.toString(ProcessHandle.current().pid()));
    }

    private boolean isStaleLock(Path lock) {
        try {
            String content = Files.readString(lock).trim();
            if (content.isEmpty()) {
                return true;
            }
            long pid = Long.parseLong(content);
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) == false;
        } catch (Exception ex) {
            return true;
        }
    }

    /**
     * 清理上一个已结束事务的请求文件。RECOVERY_FAILED 的请求文件必须保留：
     * 它指向为诊断保留的旧配置备份，也是启动器"遗留事务先恢复"路径的输入。
     */
    private void discardFinishedTransactionFiles(Path control) throws IOException {
        RestartStatus status = readStatus(control);
        if (status == null || !TERMINAL_STATES.contains(status.state())) {
            return;
        }
        if ("RECOVERY_FAILED".equals(status.state())) {
            return;
        }
        Files.deleteIfExists(control.resolve(REQUEST_FILE));
    }

    private DatasourceRestartAcceptedResponse existingTransaction(
            Path control, UUID restartId, String fingerprint) throws IOException {
        RestartStatus status = readStatus(control);
        RestartRequest request = readRequest(control);
        if (status != null && restartId.toString().equals(status.restartId())) {
            String existingFingerprint = status.fingerprint() != null
                    ? status.fingerprint()
                    : request != null ? request.fingerprint() : null;
            assertSameFingerprint(fingerprint, existingFingerprint);
            return new DatasourceRestartAcceptedResponse(restartId);
        }
        if (request != null && restartId.toString().equals(request.restartId())) {
            assertSameFingerprint(fingerprint, request.fingerprint());
            return new DatasourceRestartAcceptedResponse(restartId);
        }
        if (status != null && !TERMINAL_STATES.contains(status.state())) {
            throw new DatasourceRestartConflictException("另一个数据源重启事务正在进行中");
        }
        if (request != null && status == null) {
            throw new DatasourceRestartConflictException("另一个数据源重启事务正在进行中");
        }
        return null;
    }

    private void assertSameFingerprint(String requested, String existing) {
        if (!requested.equals(existing)) {
            throw new DatasourceRestartConflictException("重启标识已用于其他数据源配置");
        }
    }

    private void assertSupported(String remoteAddress) {
        String reasonCode = unsupportedReason(remoteAddress);
        if (reasonCode == null) {
            return;
        }
        if ("LOOPBACK_REQUIRED".equals(reasonCode)) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "自动应用仅允许从本机发起");
        }
        throw new BusinessException(ApiCodes.FEATURE_NOT_ENABLED,
                "当前启动方式不支持自动应用，请使用项目启动器或按原流程手工重启");
    }

    private String unsupportedReason(String remoteAddress) {
        if (!isLoopback(remoteAddress)) {
            return "LOOPBACK_REQUIRED";
        }
        if (!isPersonalProfile()) {
            return "PERSONAL_REQUIRED";
        }
        if (!Boolean.parseBoolean(environment.getProperty("MDR_RESTART_SUPERVISED"))) {
            return "SUPERVISOR_REQUIRED";
        }
        if (!isSupervisorAlive()) {
            // 环境变量可能在启动器被强杀后仍然留在后端进程里：此时后端会自称受管，
            // 但退出后没有任何进程负责拉起它。必须以启动器进程是否真实存活为准。
            return "SUPERVISOR_REQUIRED";
        }
        try {
            Path control = controlDirectory();
            return Files.isDirectory(control) && Files.isWritable(control)
                    ? null : "CONTROL_DIR_UNAVAILABLE";
        } catch (RuntimeException ex) {
            return "CONTROL_DIR_UNAVAILABLE";
        }
    }

    private boolean isPersonalProfile() {
        String profiles = environment.getProperty("spring.profiles.active");
        if (profiles == null || profiles.isBlank()) {
            return false;
        }
        for (String profile : profiles.split(",")) {
            if ("personal".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验常驻启动器是否真的还活着：读取启动器写入的 PID 记录，核对进程存在、
     * 进程名一致（PID 可能被系统复用）。记录缺失或不可解析一律视为"没有启动器"。
     */
    private boolean isSupervisorAlive() {
        try {
            Path record = controlDirectory().resolve(SUPERVISOR_PID_FILE);
            if (!Files.exists(record)) {
                return false;
            }
            Map<String, Object> data = readJsonObject(record);
            Object pidValue = data.get("pid");
            if (!(pidValue instanceof Number pidNumber) || pidNumber.longValue() <= 0) {
                return false;
            }
            Object nameValue = data.get("processName");
            ProcessHandle handle = ProcessHandle.of(pidNumber.longValue()).orElse(null);
            if (handle == null || !handle.isAlive()) {
                return false;
            }
            if (nameValue instanceof String expectedName && !expectedName.isBlank()) {
                String commandLine = handle.info().commandLine().orElse("");
                String actualName = simpleExecutableName(commandLine);
                if (!actualName.isEmpty() && !actualName.equalsIgnoreCase(expectedName)) {
                    // PID 已被同名以外的程序复用：记录属于旧的启动器。
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 从命令行取可执行文件的简单名（去目录与扩展名），与记录中的进程名比对。 */
    private String simpleExecutableName(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "";
        }
        int space = commandLine.indexOf(' ');
        String first = space > 0 ? commandLine.substring(0, space) : commandLine;
        int separator = Math.max(first.lastIndexOf('\\'), first.lastIndexOf('/'));
        if (separator >= 0) {
            first = first.substring(separator + 1);
        }
        if (first.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            first = first.substring(0, first.length() - 4);
        }
        return first;
    }

    private Map<String, Object> readJsonObject(Path file) throws IOException {
        return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private Path controlDirectory() {
        String configured = environment.getProperty("MDR_RESTART_CONTROL_DIR");
        if (configured == null || configured.isBlank()) {
            throw new BusinessException(ApiCodes.FEATURE_NOT_ENABLED, "当前后端未由受支持启动器托管");
        }
        Path path = Paths.get(configured).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "无法创建数据源重启控制目录");
        }
        return path;
    }

    private boolean isLoopback(String address) {
        if (address == null) {
            return false;
        }
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        if ("::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring("::ffff:".length());
        }
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            if (first != 127) {
                return false;
            }
            for (String part : parts) {
                if (part.isEmpty() || part.length() > 3) {
                    return false;
                }
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String fingerprint(DatasourceApplyRequest request) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(request.config());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "无法计算数据源配置指纹");
        }
    }

    /**
     * 读取控制文件。控制文件由启动器以"临时文件 + 原子替换"写入，理论上不会读到
     * 半截内容；但目标文件被占用或被清理工具短暂持锁时仍可能读失败。
     * 这里重试若干次，仍失败则按"文件不存在"处理——让调用方按"事务尚未就绪"
     * 继续等待，而不是把一次瞬时 IO 抖动升级成 500/404 终态。
     */
    private RestartRequest readRequest(Path control) throws IOException {
        Path path = control.resolve(REQUEST_FILE);
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!Files.exists(path)) {
                return null;
            }
            try {
                return objectMapper.readValue(
                        Files.readString(path, StandardCharsets.UTF_8), RestartRequest.class);
            } catch (IOException ex) {
                if (attempt == 2) {
                    return null;
                }
                sleep(50);
            }
        }
        return null;
    }

    private RestartStatus readStatus(Path control) throws IOException {
        Path path = control.resolve(STATUS_FILE);
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!Files.exists(path)) {
                return null;
            }
            try {
                return objectMapper.readValue(
                        Files.readString(path, StandardCharsets.UTF_8), RestartStatus.class);
            } catch (IOException ex) {
                if (attempt == 2) {
                    return null;
                }
                sleep(50);
            }
        }
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeJson(Path target, Object value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, objectMapper.writeValueAsString(value), StandardCharsets.UTF_8);
            atomicMove(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void atomicCopy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            atomicMove(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void restoreAfterFailure(
            Path localConfig, Path backup, boolean previousMissing,
            Path control, UUID restartId, Exception original) {
        try {
            if (previousMissing) {
                Files.deleteIfExists(localConfig);
            } else {
                atomicCopy(backup, localConfig);
            }
            Files.deleteIfExists(backup);
            deleteIfTransactionMatches(control, restartId);
        } catch (Exception restoreFailure) {
            original.addSuppressed(restoreFailure);
        }
    }

    private void deleteIfTransactionMatches(Path control, UUID restartId) throws IOException {
        RestartRequest request = readRequest(control);
        if (request != null && restartId.toString().equals(request.restartId())) {
            Files.deleteIfExists(control.resolve(REQUEST_FILE));
        }
        RestartStatus status = readStatus(control);
        if (status != null && restartId.toString().equals(status.restartId())) {
            Files.deleteIfExists(control.resolve(STATUS_FILE));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestartRequest(
            String restartId,
            String expectedVendor,
            String expectedJdbcUrl,
            String backupPath,
            boolean previousConfigMissing,
            String fingerprint,
            String createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestartStatus(
            String restartId,
            String state,
            String reasonCode,
            String fingerprint,
            String updatedAt
    ) {}
}
