package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.system.FactoryResetRequest;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.seed.StaticDataPresetService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * 恢复出厂设置服务。
 *
 * <p>二次确认按部署版本差异化（personal-factory-reset-confirmation）：
 * 团队版校验当前管理员密码；个人版无密码概念，改校验固定确认短语，且仅接受
 * 本机回环来源的请求。校验通过后清空所有业务表数据（保留 flyway 迁移历史），
 * 然后按版本重建默认管理员账户（个人版为 NO_PASSWORD 哨兵）并将系统初始化
 * 标志重置为 false。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactoryResetService {

    /** 个人版二次确认的固定短语（前端同步展示该字面量）。 */
    public static final String CONFIRMATION_PHRASE = "恢复出厂设置";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final StaticDataPresetService staticDataPresetService;
    private final DatabaseCleaner databaseCleaner;
    private final DatabaseEnvironment databaseEnvironment;
    private final SystemSettingService systemSettingService;

    /**
     * 执行恢复出厂设置。
     *
     * @param currentUserId 当前已认证用户的 ID
     * @param request       二次确认载体：团队版携带当前密码，个人版携带确认短语
     * @param remoteAddr    请求来源地址（个人版仅允许本机回环来源）
     */
    @Transactional
    public void executeFactoryReset(UUID currentUserId, FactoryResetRequest request, String remoteAddr) {
        Account currentUser = accountRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", currentUserId));

        // 部署版本在清表前读取：system_setting 会被 clearAllTables() 清空
        DeploymentEdition edition = systemSettingService.getEdition();

        if (edition == DeploymentEdition.PERSONAL) {
            validatePersonalConfirmation(currentUser, request, remoteAddr);
        } else {
            validateTeamPassword(currentUser, request);
        }

        log.warn("用户 [{}] 正在执行恢复出厂设置", currentUser.getUsername());

        // 1. 清空所有业务表
        clearAllTables();

        // 2. 重建默认管理员（presetAll 的占位管理员步骤因账户非空跳过）；
        //    个人版写 NO_PASSWORD 哨兵保持"无密码概念"语义
        reseedAdminUser(edition);

        // 3. 静态预置统一入口：语言代码与中文名称、
        //    全局默认标识策略、标准注册流转规则（与启动期 / 初始化向导兜底同一顺序）
        log.info("恢复出厂设置：应用静态预置（统一入口 presetAll）...");
        staticDataPresetService.presetAll();

        // 4. 系统初始化状态自动重置
        // system_setting 表已被 clearAllTables() 清空，
        // SystemSettingService.isInitialized() 在找不到记录时返回 false，
        // 因此无需显式调用 setInitialized(false)。
        // init-phase-database-free：本地初始化状态记录同步重置为未初始化
        // （数据源确认保留，下次启动仍为完整相，由引导幂等兜底预置）。
        try {
            io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStateStore store =
                    new io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStateStore(
                            DatasourceConfigService.localConfigPath().resolveSibling("initialization-state.yml"));
            io.github.matrixlane.metadataregistry.web.bootstrap.InitializationState current =
                    store.read().orElse(io.github.matrixlane.metadataregistry.web.bootstrap.InitializationState.fresh());
            store.write(current.withInitialized(false));
        } catch (Exception e) {
            log.warn("初始化状态记录重置失败: {}", e.getMessage());
        }

        log.warn("恢复出厂设置完成");
    }

    /**
     * 个人版二次确认：本机回环来源 + 固定确认短语，均不通过则拒绝且不落库。
     */
    private void validatePersonalConfirmation(Account currentUser, FactoryResetRequest request, String remoteAddr) {
        if (!isLoopbackAddressLiteral(remoteAddr)) {
            log.warn("拒绝恢复出厂设置：用户 [{}] 来源 [{}] 非本机回环地址", currentUser.getUsername(), remoteAddr);
            throw new BusinessException(ApiCodes.FORBIDDEN, "个人版恢复出厂设置仅允许从本机发起");
        }
        String phrase = request.confirmationPhrase();
        if (phrase == null || !CONFIRMATION_PHRASE.equals(phrase.trim())) {
            log.warn("拒绝恢复出厂设置：用户 [{}] 确认短语不正确", currentUser.getUsername());
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "确认短语不正确");
        }
    }

    /**
     * 仅按 IP 字面量判定回环，绝不触发 DNS：非字面量输入（主机名、畸形值）
     * 一律拒绝——本机边界是个人版唯一的安全边界，不允许可被名字解析绕过的形态。
     * IPv4 认可 127.0.0.0/8；IPv6 认可 ::1 及其全展开形、IPv4 映射回环（::ffff:127.x）。
     */
    static boolean isLoopbackAddressLiteral(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        String addr = remoteAddr.trim();
        if (addr.isEmpty()) {
            return false;
        }
        String lower = addr.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("::ffff:")) {
            return isLoopbackAddressLiteral(addr.substring(7));
        }
        if (lower.contains(":")) {
            return lower.equals("::1") || lower.equals("0:0:0:0:0:0:0:1");
        }
        String[] octets = addr.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3
                    || !octet.chars().allMatch(Character::isDigit)
                    || Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return octets[0].equals("127");
    }

    private void validateTeamPassword(Account currentUser, FactoryResetRequest request) {
        String rawPassword = request.password();
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, currentUser.getPasswordHash())) {
            log.warn("拒绝恢复出厂设置：用户 [{}] 密码验证失败", currentUser.getUsername());
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "密码验证失败");
        }
    }

    /**
     * 动态清空所有用户表数据（保留 flyway_schema_history）。
     */
    private void clearAllTables() {
        // 方言可移植清理：JDBC 元数据发现表，外键控制按方言策略执行。
        databaseCleaner.clearTables(Set.of("flyway_schema_history"));
    }

    /**
     * 通过 JdbcTemplate 直接插入默认管理员用户。
     *
     * <p>表名按当前方言/schema 限定，时间函数使用可移植的 CURRENT_TIMESTAMP，
     * UUID 经 {@link DatabaseEnvironment#toJdbcUuid} 绑定（SQL Server 为字符串，
     * SQLite/达梦为 byte[16]，三种方言的驱动均接受）。个人版口令写
     * {@code NO_PASSWORD} 明文哨兵；团队版维持初始默认密码哈希。</p>
     */
    private void reseedAdminUser(DeploymentEdition edition) {
        UUID adminId = UUID.randomUUID();
        boolean personal = edition == DeploymentEdition.PERSONAL;
        String passwordHash = personal ? "NO_PASSWORD" : passwordEncoder.encode("Admin@123");

        jdbcTemplate.update(
                "INSERT INTO " + databaseEnvironment.qualifyExt("account") +
                        " (id, username, password_hash, sys_role, enabled, created_at, force_password_reset, temp_password_used) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                databaseEnvironment.toJdbcUuid(adminId), "admin", passwordHash, "SUPER_ADMIN", true, false, false
        );

        log.info("已重新创建默认管理员账户：admin（{}）", personal ? "NO_PASSWORD 哨兵" : "初始默认密码");
    }
}
