package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.system.FactoryResetRequest;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 恢复出厂设置 × 语言预置的端到端回归测试。
 *
 * <p>复现 2026-09-29 现场：团队库已存在 {@code uq_language_identification_identifier}
 * 唯一索引，旧出厂流程先经 JPA（{@code LanguageInitializer}）重建 22 条语言标识，再在同一
 * 事务内用 JdbcTemplate 重放 V20260919003 预置脚本。JPA 写入若未落库，预置脚本的
 * {@code WHERE NOT EXISTS} 会把空表当作待补种表再插一份，提交时 JPA 批量插入撞唯一索引，
 * 整个出厂事务回滚——表现为"点恢复出厂设置直接报数据冲突、什么也没清掉"。
 * wizard-static-preset-bootstrap 后出厂流程改走统一静态预置入口（仅 SQL 重放，无 JPA
 * 语言写入），该冲突在结构上消除，本测试保留为唯一索引约束下的出厂端到端回归。
 *
 * <p>因此本测试必须带唯一索引跑真实方言（SQLite 内存库），而不是只验证 mock 调用顺序。
 */
@SpringBootTest(properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite，
        // 使本测试与宿主机数据源配置无关（导入的配置优先级高于非 profile 默认值）。
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:factory-reset-language-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        // 用户主目录 application-local.yml 可能注入 orm-team.xml（SQL Server 专用）；
        // 内存 SQLite 测试改用 orm-sqlite.xml（EXT 物理名 ext_ 前缀）。
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml"
})
@ActiveProfiles("test")
class FactoryResetLanguagePresetIntegrationTest {

    @Autowired
    private FactoryResetService factoryResetService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemSettingService systemSettingService;

    @Test
    @DisplayName("个人版：确认短语 + 本机来源执行恢复出厂，重建 admin 为 NO_PASSWORD 哨兵；非本机来源 403 且数据保留")
    void personalFactoryReset_shouldReseedNoPasswordAndRejectNonLoopback() {
        // 非本机来源：即使确认短语正确也必须 403，且不得清除任何数据
        systemSettingService.setEdition(DeploymentEdition.PERSONAL);
        Account admin = new Account();
        admin.setUsername("personal-admin-" + UUID.randomUUID().toString().substring(0, 8));
        admin.setPasswordHash("NO_PASSWORD");
        admin.setSysRole(SystemRole.SUPER_ADMIN);
        admin.setEnabled(true);
        UUID adminId = accountRepository.save(admin).getId();

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                        adminId, new FactoryResetRequest(null, "恢复出厂设置"), "192.168.1.10"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.FORBIDDEN));
        assertThat(accountRepository.findById(adminId)).isPresent();

        // 本机来源 + 正确短语：执行成功，重建的 admin 为 NO_PASSWORD 哨兵
        assertThatCode(() -> factoryResetService.executeFactoryReset(
                        adminId, new FactoryResetRequest(null, "恢复出厂设置"), "127.0.0.1"))
                .doesNotThrowAnyException();

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM account WHERE username = 'admin'", String.class);
        assertThat(passwordHash).isEqualTo("NO_PASSWORD");
        // 个人版重新初始化不要求密码：初始化的前置数据已被 presetAll 重建
        assertThat(systemSettingService.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("恢复出厂设置：语言代码只播种一次且名称齐全（预置脚本不得与 JPA 播种重复插入）")
    void factoryResetSeedsEachLanguageExactlyOnceWithName() {
        // 与团队库现状一致：唯一索引先行，出厂流程必须在该约束下跑通。
        // 显式锁定 TEAM 版本：本测试依赖"版本为空⇒TEAM 密码分支"的语义，
        // 不接受同文件其他用例残留的 edition 状态（共享内存库，顺序无关性靠显式前提）。
        systemSettingService.setEdition(DeploymentEdition.TEAM);
        jdbcTemplate.execute("DROP INDEX IF EXISTS uq_language_identification_identifier");
        jdbcTemplate.execute("CREATE UNIQUE INDEX uq_language_identification_identifier"
                + " ON language_identification (language_identifier)");

        Account admin = new Account();
        admin.setUsername("factory-reset-admin-" + UUID.randomUUID().toString().substring(0, 8));
        admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
        admin.setSysRole(SystemRole.SUPER_ADMIN);
        admin.setEnabled(true);
        UUID adminId = accountRepository.save(admin).getId();

        assertThatCode(() -> factoryResetService.executeFactoryReset(
                        adminId, new io.github.matrixlane.metadataregistry.web.dto.system.FactoryResetRequest("Admin@123", null),
                        "127.0.0.1"))
                .doesNotThrowAnyException();

        Integer languages = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM language_identification", Integer.class);
        Integer distinctIdentifiers = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT language_identifier) FROM language_identification", Integer.class);
        Integer names = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ext_language_identification", Integer.class);

        assertThat(languages).isEqualTo(22);
        assertThat(distinctIdentifiers).isEqualTo(22);
        assertThat(names).isEqualTo(22);

        String zhName = jdbcTemplate.queryForObject(
                "SELECT e.language_name FROM ext_language_identification e"
                        + " JOIN language_identification l ON l.id = e.language_id"
                        + " WHERE l.language_identifier = 'zh-CN'", String.class);
        // 简体中文：V20260919003 播种「中文」后由 localize-language-names.sql 口径修正
        assertThat(zhName).isEqualTo("简体中文");
    }
}
