package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 遗留语言表 Schema 漂移启动期修复器（legacy-language-schema-repair）。
 *
 * <p>远古 ddl-auto 时代遗留的 SQLite 个人版库，{@code language_identification}
 * 物理表残留 {@code language_id NOT NULL UNIQUE} / {@code language_name} /
 * {@code territory_name} / {@code enabled} 等非标准列（2026-10-20 本机实证），
 * 与当前实体形状（ISO/IEC 11179-3:2023 §6.3.7，标准列仅
 * id/language_identifier/script_identifier/geopolitical_territory_identifier/
 * private_use_qualifier）不一致；语言预置脚本 V20260919003 的 INSERT 列清单
 * 不含遗留 NOT NULL 列，重放每条必失败（dd8d9dbe 的"单条 WARN 跳过"仅防崩溃，
 * 预置永远种不入）。本修复器在静态预置 Runner（@Order(10)）之前执行：
 *
 * <ol>
 *   <li>漂移签名判定：{@code PRAGMA table_info} 读取列清单，标准 5 列集合之外
 *       存在任何额外列、或标准列缺失（旧代建表），即判定漂移（覆盖已实证的三代
 *       漂移形态）；健康库与非 SQLite 方言 no-op。</li>
 *   <li>数据归位（mdr/ext 分层归位，全部 NOT EXISTS 幂等守卫）：
 *       {@code language_name}/{@code territory_name} → 卫星表
 *       {@code ext_language_identification}；{@code enabled} → 卫星表
 *       {@code language_governance}；卫星表缺失按既有迁移 DDL 补建
 *       （V20260919001 / V20260901012）。无可归宿遗留列（如 {@code language_id}）
 *       的非空值记 WARN 后随重建丢弃，不静默。</li>
 *   <li>表重建（单事务）：{@code language_identification_repair} 新表（当前实体
 *       形状）→ 动态列清单复制（缺失列 NULL 兜底）→ DROP + RENAME。各行
 *       {@code id} 原值保留——definition/designation 等 8 处按值引用
 *       language_identification.id（V20260926001 实证），id 变更即断引用。</li>
 *   <li>唯一索引尽力建立（{@code uq_language_identification_identifier}，对齐
 *       V20260926001 目标形态）：既有重复数据导致失败时仅 WARN 跳过，不阻断
 *       修复（重复行场景由 LanguageIdentifierResolver 防御式解析兜底）。</li>
 * </ol>
 *
 * <p>幂等可重入：修复后签名消失，后续启动自动跳过；单事务保证任一失败回滚、
 * 遗留库保持原状。
 */
@Slf4j
@Component
@Order(5)
public class LegacyLanguageSchemaRepairer implements ApplicationRunner {

    static final String TABLE = "language_identification";
    static final String REPAIR_TABLE = "language_identification_repair";

    /** 当前实体（ISO/IEC 11179-3:2023 §6.3.7）标准列集合（有序，INSERT 位置对应）。 */
    static final List<String> STANDARD_COLUMNS = List.of(
            "id", "language_identifier", "script_identifier",
            "geopolitical_territory_identifier", "private_use_qualifier");

    /** 已有卫星归宿的遗留列（参与数据归位，不记丢弃 WARN）。 */
    private static final Set<String> SATELLITE_MAPPED = Set.of("language_name", "territory_name", "enabled");

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;
    private final TransactionTemplate transactionTemplate;

    /**
     * 新架构模式（null 表示关闭，如集成测试自行管理 schema 的环境）。
     * 开启时数据库初始化由 DatabaseBootstrapService 统一执行，本 Runner 跳过，
     * 避免同一修复动作执行两次。
     */
    @Autowired(required = false)
    private io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapMode bootstrapMode;

    @Autowired
    public LegacyLanguageSchemaRepairer(JdbcTemplate jdbcTemplate,
                                        DataSource dataSource,
                                        DatabaseEnvironment databaseEnvironment) {
        this(jdbcTemplate, databaseEnvironment, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    /** 测试直达构造：显式传入事务模板（生产路径经上方注入构造自建 DataSource 事务）。 */
    LegacyLanguageSchemaRepairer(JdbcTemplate jdbcTemplate,
                                 DatabaseEnvironment databaseEnvironment,
                                 TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseEnvironment = databaseEnvironment;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapMode != null && bootstrapMode.enabled()) {
            log.debug("新架构模式下数据库初始化由引导服务统一执行，跳过遗留修复 Runner");
            return;
        }
        repairIfNeeded();
    }

    /** 修复入口：启动期 Runner 与数据库初始化引导共用（幂等，可重复调用）。 */
    public void repairIfNeeded() {
        if (databaseEnvironment.vendor() != DatabaseVendor.SQLITE) {
            log.debug("非 SQLite 方言（{}），跳过遗留语言表漂移修复", databaseEnvironment.vendor());
            return;
        }
        List<String> columns = tableColumns(TABLE);
        if (columns.isEmpty()) {
            log.debug("表 {} 不存在，跳过遗留漂移修复", TABLE);
            return;
        }
        Set<String> extras = new LinkedHashSet<>(columns);
        extras.removeAll(STANDARD_COLUMNS);
        Set<String> missing = new LinkedHashSet<>(STANDARD_COLUMNS);
        missing.removeAll(columns);
        if (extras.isEmpty() && missing.isEmpty()) {
            log.debug("表 {} 结构符合当前实体形状，无需修复", TABLE);
            return;
        }
        log.warn("检测到遗留漂移 {}：额外列 {}、缺失列 {}；执行数据归位 + 表重建",
                TABLE, extras, missing);
        transactionTemplate.executeWithoutResult(status -> repair(columns, extras));
        log.info("遗留语言表修复完成：{} 已重建为当前实体形状（{} 行数据保留）",
                TABLE, countRows());
    }

    private void repair(List<String> columns, Set<String> extras) {
        // 遗留 SQLite 库无声明式 FK；此 pragma 仅防御，且经事务绑定在同一连接执行。
        jdbcTemplate.execute("PRAGMA foreign_keys=OFF");
        warnUnmappedLegacyData(extras);
        migrateNameColumns(columns);
        migrateEnabledColumn(columns);
        rebuild(columns);
        createUniqueIndex();
    }

    private void warnUnmappedLegacyData(Set<String> extras) {
        for (String column : extras) {
            if (SATELLITE_MAPPED.contains(column)) {
                continue;
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE " + column + " IS NOT NULL", Integer.class);
            if (count != null && count > 0) {
                log.warn("遗留列 {} 有 {} 行非空数据，语义无法映射到当前模型，将随表重建丢弃", column, count);
            }
        }
    }

    private void migrateNameColumns(List<String> columns) {
        boolean hasName = columns.contains("language_name");
        boolean hasTerritory = columns.contains("territory_name");
        if (!hasName && !hasTerritory) {
            return;
        }
        ensureExtTable();
        String nameExpr = hasName ? "li.language_name" : "NULL";
        String territoryExpr = hasTerritory ? "li.territory_name" : "NULL";
        int migrated = jdbcTemplate.update(
                "INSERT INTO ext_language_identification (id, language_id, language_name, territory_name) "
                        + "SELECT randomblob(16), li.id, " + nameExpr + ", " + territoryExpr + " "
                        + "FROM language_identification li "
                        + "WHERE NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = li.id)");
        log.info("已归位 {} 行语言名称/地区名称至 ext_language_identification", migrated);
    }

    private void migrateEnabledColumn(List<String> columns) {
        if (!columns.contains("enabled")) {
            return;
        }
        ensureGovernanceTable();
        int migrated = jdbcTemplate.update(
                "INSERT INTO language_governance (id, language_id, enabled) "
                        + "SELECT randomblob(16), li.id, coalesce(li.enabled, true) "
                        + "FROM language_identification li "
                        + "WHERE NOT EXISTS (SELECT 1 FROM language_governance g WHERE g.language_id = li.id)");
        log.info("已归位 {} 行 enabled 至 language_governance", migrated);
    }

    /** 按 V20260919001 DDL 补建语言名称卫星表（幂等）。 */
    private void ensureExtTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ext_language_identification ("
                + "id blob not null, language_id blob not null,"
                + " language_name varchar(255), territory_name varchar(255), primary key (id))");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_liext_language"
                + " ON ext_language_identification (language_id)");
    }

    /** 按 V20260901012 DDL 补建语言治理卫星表（幂等）。 */
    private void ensureGovernanceTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS language_governance ("
                + "id blob not null, language_id blob not null,"
                + " enabled boolean not null default true, primary key (id))");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_lg_language ON language_governance (language_id)");
    }

    private void rebuild(List<String> columns) {
        List<String> selectExprs = STANDARD_COLUMNS.stream()
                .map(c -> columns.contains(c) ? c : "NULL")
                .toList();
        jdbcTemplate.execute("CREATE TABLE " + REPAIR_TABLE + " ("
                + "id blob not null, language_identifier varchar(255), script_identifier varchar(255),"
                + " geopolitical_territory_identifier varchar(255), private_use_qualifier varchar(255),"
                + " primary key (id))");
        jdbcTemplate.update("INSERT INTO " + REPAIR_TABLE
                + " (id, language_identifier, script_identifier,"
                + " geopolitical_territory_identifier, private_use_qualifier) "
                + "SELECT " + String.join(", ", selectExprs) + " FROM " + TABLE);
        jdbcTemplate.execute("DROP TABLE " + TABLE);
        jdbcTemplate.execute("ALTER TABLE " + REPAIR_TABLE + " RENAME TO " + TABLE);
    }

    private void createUniqueIndex() {
        try {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_language_identification_identifier"
                    + " ON " + TABLE + "(language_identifier)");
        } catch (DataAccessException e) {
            log.warn("唯一索引 uq_language_identification_identifier 建立失败（既有重复数据），跳过：{}",
                    e.getMostSpecificCause() != null
                            ? e.getMostSpecificCause().getMessage()
                            : e.getMessage());
        }
    }

    private List<String> tableColumns(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        return rows.stream().map(row -> (String) row.get("name")).toList();
    }

    private int countRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        return count != null ? count : 0;
    }
}
