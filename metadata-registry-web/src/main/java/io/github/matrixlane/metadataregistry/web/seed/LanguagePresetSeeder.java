package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 语言预置（V20260919003）重放器：恢复出厂设置后重新应用内置语言代码与名称。
 *
 * <p>恢复出厂会清空 {@code language_identification} 标准表与同名扩展表
 * （{@link io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner}
 * 清空两 schema 下所有业务表），而 Flyway 不会重跑已应用的迁移，出厂后须显式
 * 重放预置脚本，否则内置语言名称预置丢失、违反"开箱即有名称"语义。</p>
 *
 * <p>单一数据源：预置 SQL 只存在于 Flyway 迁移脚本
 * {@code db/migration/<vendor>/V20260919003__Seed_builtin_languages.sql}，
 * 本类按当前方言从 classpath 读取同一份脚本逐条执行（脚本语句均为幂等守卫的
 * INSERT...SELECT），应用代码 MUST NOT 复制其中的数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguagePresetSeeder {

    static final String SCRIPT_TEMPLATE = "classpath:db/migration/%s/V20260919003__Seed_builtin_languages.sql";

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;
    private final ResourceLoader resourceLoader;

    /**
     * 重新应用语言预置脚本（幂等，可重复调用）。
     */
    public void reapplyPreset() {
        String path = SCRIPT_TEMPLATE.formatted(vendorDir());
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("语言预置脚本不存在: " + path);
        }
        String sql = readScript(resource);
        List<String> statements = splitStatements(sql);
        for (String statement : statements) {
            jdbcTemplate.execute(statement);
        }
        log.info("已重新应用语言预置脚本 {}（{} 条语句）", path, statements.size());
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
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
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
