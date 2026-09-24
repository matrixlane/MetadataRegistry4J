package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import io.github.matrixlane.metadataregistry.web.seed.LanguageInitializer;
import io.github.matrixlane.metadataregistry.web.seed.LanguagePresetSeeder;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 恢复出厂设置服务。
 *
 * <p>验证当前管理员密码后，清空所有业务表数据（保留 flyway 迁移历史），
 * 然后重新创建默认管理员账户并将系统初始化标志重置为 false。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactoryResetService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final SystemSettingService systemSettingService;
    private final LanguageInitializer languageInitializer;
    private final LanguagePresetSeeder languagePresetSeeder;
    private final ObjectProvider<SystemDefaultNamespaceInitializer> systemDefaultNamespaceInitializerProvider;
    private final DatabaseCleaner databaseCleaner;
    private final DatabaseEnvironment databaseEnvironment;

    /**
     * 执行恢复出厂设置。
     *
     * @param currentUserId 当前已认证用户的 ID
     * @param rawPassword   用户输入的明文密码，用于二次验证身份
     */
    @Transactional
    public void executeFactoryReset(UUID currentUserId, String rawPassword) {
        // 1. 查找当前用户
        Account currentUser = accountRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", currentUserId));

        // 2. 验证密码
        if (!passwordEncoder.matches(rawPassword, currentUser.getPasswordHash())) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED, "密码验证失败");
        }

        log.warn("用户 [{}] 正在执行恢复出厂设置", currentUser.getUsername());

        // 3. 清空所有业务表
        clearAllTables();

        // 4. 重新创建默认管理员
        reseedAdminUser();

        // 4.5 重新初始化内置语言标识数据与系统默认命名空间
        log.info("恢复出厂设置：开始重置默认语言标识...");
        languageInitializer.run(null);
        systemDefaultNamespaceInitializerProvider.ifAvailable(init -> {
            log.info("恢复出厂设置：开始重置系统默认命名空间...");
            init.run(null);
        });

        // 4.6 重新应用语言预置(出厂清空了 language_identification 标准表与同名扩展表,
        // Flyway 不会重跑已应用迁移;重放 V20260919003 脚本恢复内置语言与名称,
        // 脚本语句均带幂等守卫,与全新库/既有库共用同一份 SQL 数据源)
        log.info("恢复出厂设置：重新应用语言预置（V20260919003）...");
        languagePresetSeeder.reapplyPreset();

        // 5. 系统初始化状态自动重置
        // system_setting 表已被 clearAllTables() 清空，
        // SystemSettingService.isInitialized() 在找不到记录时返回 false，
        // 因此无需显式调用 setInitialized(false)。

        log.warn("恢复出厂设置完成");
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
     * UUID 以字符串形式绑定（三种方言的驱动均接受）。
     */
    private void reseedAdminUser() {
        UUID adminId = UUID.randomUUID();
        String encodedPassword = passwordEncoder.encode("Admin@123");

        jdbcTemplate.update(
                "INSERT INTO " + databaseEnvironment.qualifyExt("account") +
                        " (id, username, password_hash, sys_role, enabled, created_at, force_password_reset, temp_password_used) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                databaseEnvironment.toJdbcUuid(adminId), "admin", encodedPassword, "SUPER_ADMIN", true, false, false
        );

        log.info("已重新创建默认管理员账户：admin / Admin@123");
    }
}
