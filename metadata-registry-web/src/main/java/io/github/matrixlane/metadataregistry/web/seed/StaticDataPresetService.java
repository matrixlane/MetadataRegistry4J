package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 静态预置数据统一入口（wizard-static-preset-bootstrap）。
 *
 * <p>"数据库设置完成"（向导第 2 步保存数据源并重启）后，本入口在启动期把全部
 * 静态预置数据准备就绪；初始化向导后续步骤与恢复出厂设置均复用同一入口、同一
 * 顺序。预置数据值 MUST 仅存在于 SQL 脚本（2026-09-19 用户裁定），Java 侧只执行
 * 与装配，MUST NOT 复制其中的名称数据。</p>
 *
 * <p>步骤序（全部幂等，可重复执行）：</p>
 * <ol>
 *   <li>重放语言预置 SQL 脚本清单（语言代码 22 + 中文名称/地区名 22，
 *       含 zh-CN=简体中文 口径修正）——{@link LanguagePresetSeeder}；</li>
 *   <li>{@code identifier_strategy_config} 全局默认行（UUID / INTERNAL /
 *       is_global_default=1，三方言补齐）；</li>
 *   <li>占位超级管理员 {@code admin / NO_PASSWORD}（账户表非空即跳过）；</li>
 *   <li>每注册机构的标准注册流转规则调和（既有 ensureSeed 语义，不覆盖用户定制）。</li>
 * </ol>
 *
 * <p>执行顺序约束：全局默认策略行须在 {@link IdentifierStrategyLoader}（@Order(90)）
 * 装载前写入，或由本入口完成后显式刷新策略注册表（双保险）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaticDataPresetService {

    private final LanguagePresetSeeder languagePresetSeeder;
    private final RegistrationProcessService registrationProcessService;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;
    private final IdentifierStrategyLoader identifierStrategyLoader;

    /**
     * 执行全部静态预置步骤（幂等）。启动期、恢复出厂后、初始化向导 setup 清理后
     * 三处复用，顺序一致。
     */
    @Transactional
    public void presetAll() {
        languagePresetSeeder.reapplyPreset();
        ensureGlobalDefaultIdentifierStrategy();
        ensurePlaceholderAdmin();
        reconcileRegistrationTransitionRules();
        // 顺序双保险：即便本入口在 IdentifierStrategyLoader 之后执行，也保证注册表
        // 能装载到步骤 2 写入的全局默认策略。
        identifierStrategyLoader.refreshRegistry();
        log.info("静态预置数据统一入口 presetAll() 执行完成");
    }

    /**
     * {@code identifier_strategy_config} 全局默认行（UUID 兜底策略）。
     * 历史库仅 sqlserver 由 V20260527001 种过此行，sqlite/dm 全新库缺失；
     * 出厂清空后亦须恢复。幂等：已存在 is_global_default=1 行即跳过。
     * 列集合以当前实体为准（identifier_type/sequence_scope 已随 V20260727001 移除）。
     */
    private void ensureGlobalDefaultIdentifierStrategy() {
        String table = databaseEnvironment.qualifyAuto("identifier_strategy_config");
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE is_global_default = 1", Integer.class);
        if (existing != null && existing > 0) {
            log.info("identifier_strategy_config 全局默认策略已存在，跳过预置（幂等）");
            return;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try {
            jdbcTemplate.update(
                    "INSERT INTO " + table + " (strategy_type, namespace_id, config_json, version, status,"
                            + " is_global_default, created_by, created_at, effective_from) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "UUID", null, "{}", 1, "PUBLISHED", true, "system", now, now);
            log.info("已预置 identifier_strategy_config 全局默认策略（UUID）");
        } catch (org.springframework.dao.DataIntegrityViolationException
                | org.springframework.jdbc.UncategorizedSQLException e) {
            // 并发窗口：另一进程/线程在 COUNT 之后插入了全局默认行；幂等语义下视为成功
            log.info("identifier_strategy_config 全局默认策略已被并发写入，跳过（幂等）");
        }
    }

    /**
     * 占位超级管理员（首次登录免密，commit 时设置真实密码）。
     * 幂等：账户表非空即跳过（出厂流程会先写入带真实密码的 admin）。
     */
    private void ensurePlaceholderAdmin() {
        if (accountRepository.count() > 0) {
            return;
        }
        Account admin = new Account();
        admin.setUsername("admin");
        // 哨兵值为明文，与 AuthService / InitWizardService 的 NO_PASSWORD 判定一致
        admin.setPasswordHash("NO_PASSWORD");
        admin.setSysRole(SystemRole.SUPER_ADMIN);
        admin.setEnabled(true);
        accountRepository.save(admin);
        log.info("已预置占位超级管理员账号：admin / (首次登录免密)");
    }

    /**
     * 为全部存量注册机构调和播种标准注册流转规则（registration-process-designer）。
     * 联邦/向导等路径新增 RA 时仍由 ensureSeed 在创建处惰性补播兜底。
     */
    private void reconcileRegistrationTransitionRules() {
        List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
        for (RegistrationAuthority authority : authorities) {
            registrationProcessService.ensureSeed(authority.getId());
        }
        if (!authorities.isEmpty()) {
            log.info("已为 {} 个注册机构播种标准注册流程规则", authorities.size());
        }
    }
}
