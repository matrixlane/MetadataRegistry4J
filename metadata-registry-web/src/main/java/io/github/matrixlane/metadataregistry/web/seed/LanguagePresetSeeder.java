package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 语言预置（V20260919003 + 名称口径修正）重放器：恢复出厂设置后重新应用内置语言
 * 代码与名称。
 *
 * <p>恢复出厂会清空 {@code language_identification} 标准表与同名扩展表
 * （{@link io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner}
 * 清空两 schema 下所有业务表），而 Flyway 不会重跑已应用的迁移，出厂后须显式
 * 重放预置脚本，否则内置语言名称预置丢失、违反"开箱即有名称"语义。</p>
 *
 * <p>单一数据源：预置 SQL 只存在于 Flyway 迁移脚本
 * {@code db/migration/<vendor>/V20260919003__Seed_builtin_languages.sql} 与
 * {@code db/preset/<vendor>/localize-language-names.sql}（V20260929001 迁移的活副本），
 * 本类按当前方言从 classpath 读取脚本逐条执行（脚本语句均为幂等守卫的
 * INSERT/UPDATE...SELECT），应用代码 MUST NOT 复制其中的数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguagePresetSeeder {

    static final List<String> SCRIPT_TEMPLATES = List.of(
            "classpath:db/migration/%s/V20260919003__Seed_builtin_languages.sql",
            "classpath:db/preset/%s/localize-language-names.sql");

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;
    private final ResourceLoader resourceLoader;

    /**
     * 重新应用语言预置脚本（幂等，可重复调用）。
     *
     * <p>schema 漂移容忍：远古 ddl-auto 时代建库的 {@code language_identification}
     * 表带 NOT NULL 且无默认值的 {@code language_id} 列，与预置 INSERT 的列清单不
     * 兼容（2026-10-19/20 实测：NOT NULL constraint failed 导致启动失败）。单条
     * 语句失败仅 WARN 并跳过该语句，不中断整轮预置——预置语句均带幂等守卫，跳过
     * 后既有数据原样保留，下轮启动自动重试。
     */
    public void reapplyPreset() {
        for (String template : SCRIPT_TEMPLATES) {
            String path = template.formatted(vendorDir());
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("语言预置脚本不存在: " + path);
            }
            String sql = readScript(resource);
            List<String> statements = splitStatements(sql);
            int skipped = 0;
            for (String statement : statements) {
                try {
                    jdbcTemplate.execute(statement);
                } catch (DataAccessException e) {
                    skipped++;
                    log.warn("语言预置语句执行失败（通常为既有库 schema 漂移，已跳过该语句）：{}；原因：{}",
                            abbreviate(statement), e.getMostSpecificCause().getMessage());
                }
            }
            if (skipped == 0) {
                log.info("已重新应用语言预置脚本 {}（{} 条语句）", path, statements.size());
            } else {
                log.warn("语言预置脚本 {} 完成：{} 条成功，{} 条因 schema 漂移跳过（既有库保留原数据）",
                        path, statements.size() - skipped, skipped);
            }
        }
    }

    private String abbreviate(String statement) {
        String singleLine = statement.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 117) + "...";
    }

    private String vendorDir() {
        return switch (databaseEnvironment.vendor()) {
            case SQLITE -> "sqlite";
            case DM -> "dm";
            case SQLSERVER -> "sqlserver";
        };
    }

    private String readScript(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取语言预置脚本失败", e);
        }
    }

    /** 按行拆分脚本：跳过注释行与空行，积累到以分号结尾的行为一条完整语句。 */
    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(trimmed).append('\n');
            if (trimmed.endsWith(";")) {
                statements.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            throw new IllegalStateException("语言预置脚本存在未以分号结尾的语句: " + current);
        }
        return statements;
    }
}
