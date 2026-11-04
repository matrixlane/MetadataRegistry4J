package io.github.matrixlane.metadataregistry.web.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceFingerprintTest {

    private static final String[] BASELINE = {
            "sqlite",
            "jdbc:sqlite:/data/app.db",
            "org.hibernate.community.dialect.SQLiteDialect",
            "orm/orm-sqlite.xml",
            "classpath:db/migration/sqlite",
            "main",
            "main"
    };

    @Test
    void 相同输入产生相同指纹() {
        String first = DatasourceFingerprint.compute(BASELINE);
        String second = DatasourceFingerprint.compute(BASELINE);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void 任一设置变化指纹即变化() {
        String baseline = DatasourceFingerprint.compute(BASELINE);
        for (int i = 0; i < BASELINE.length; i++) {
            String[] changed = BASELINE.clone();
            changed[i] = changed[i] + "-changed";
            assertThat(DatasourceFingerprint.compute(changed))
                    .as("位置 %d 变化应改变指纹", i)
                    .isNotEqualTo(baseline);
        }
    }

    @Test
    void 首尾空白不影响指纹() {
        String[] padded = BASELINE.clone();
        padded[0] = "  sqlite  ";
        padded[3] = " orm/orm-sqlite.xml ";
        assertThat(DatasourceFingerprint.compute(padded))
                .isEqualTo(DatasourceFingerprint.compute(BASELINE));
    }

    @Test
    void 空值按空串处理() {
        String withNull = DatasourceFingerprint.compute("sqlite", null, "dialect", "orm", "loc", "sch", "def");
        String withEmpty = DatasourceFingerprint.compute("sqlite", "", "dialect", "orm", "loc", "sch", "def");
        assertThat(withNull).isEqualTo(withEmpty);
    }

    @Test
    void 指纹为定长十六进制() {
        assertThat(DatasourceFingerprint.compute(BASELINE))
                .matches("[0-9a-f]{64}");
    }
}
