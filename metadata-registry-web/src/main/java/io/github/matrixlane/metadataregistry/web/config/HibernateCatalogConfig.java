package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 {@code hibernate.default_catalog} 锁定为数据源 URL 中配置的数据库。
 *
 * <p>SQL Server 的 JDBC 元数据在 catalog 未限定时会返回同实例内其他数据库
 * 的同名表，导致 schema migrator 误判"表已存在"、只发 ALTER 不发 CREATE。
 * 本配置从 JDBC URL 提取 databaseName 并显式设置 default_catalog，
 * 使元数据读取严格限定在当前数据库。
 */
@Component
public class HibernateCatalogConfig implements HibernatePropertiesCustomizer {

    private static final Pattern DB_NAME = Pattern.compile("databaseName=([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HibernateCatalogConfig.class);

    private final String jdbcUrl;

    public HibernateCatalogConfig(@Value("${spring.datasource.url:}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        if (jdbcUrl == null) {
            return;
        }
        Matcher m = DB_NAME.matcher(jdbcUrl);
        if (m.find() && !m.group(1).isBlank()) {
            hibernateProperties.put("hibernate.default_catalog", m.group(1).trim());
            log.info("hibernate.default_catalog 已锁定为 [{}]", m.group(1).trim());
        }
    }
}
