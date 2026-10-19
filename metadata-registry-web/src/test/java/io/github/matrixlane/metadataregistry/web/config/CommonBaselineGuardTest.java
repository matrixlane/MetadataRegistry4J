package io.github.matrixlane.metadataregistry.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共同基线版本守卫（sqlite-empty-db-bootstrap 任务 3.3）。
 *
 * <p>新库共同基线以 SQL Server 当前最终迁移版本为权威；本测试静态扫描三个方言
 * 迁移目录的最大版本号，与 FreshDatabaseInitializer 的基线常量比对，
 * 防止后续新增迁移时三方言版本序列漂移（新增迁移 MUST 三方言同版号）。
 */
class CommonBaselineGuardTest {

    private static final Path MIGRATION_ROOT = Path.of("src/main/resources/db/migration");

    @Test
    void threeDialectsShareMaxVersionEqualToFreshBaseline() throws IOException {
        long sqlServerMax = maxVersion("sqlserver");
        long sqliteMax = maxVersion("sqlite");
        long dmMax = maxVersion("dm");

        assertThat(sqliteMax).as("SQLite 最大版本应与 SQL Server 共同基线一致").isEqualTo(sqlServerMax);
        assertThat(dmMax).as("达梦最大版本应与 SQL Server 共同基线一致").isEqualTo(sqlServerMax);

        long baseline = Long.parseLong(FreshDatabaseInitializer.FRESH_BASELINE_VERSION);
        assertThat(baseline).as("共同新库基线必须等于三方言当前最大版本（以 SQL Server 为权威）")
                .isEqualTo(sqlServerMax);
    }

    private long maxVersion(String dialect) throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_ROOT.resolve(dialect))) {
            return files
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*"))
                    .map(name -> Long.parseLong(name.substring(1, name.indexOf("__"))))
                    .max(Long::compareTo)
                    .orElseThrow(() -> new IllegalStateException("迁移目录为空: " + dialect));
        }
    }
}
