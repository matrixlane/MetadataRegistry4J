package io.github.matrixlane.metadataregistry.web;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:reset-database-helper-test?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "app.seed.demo-data.enabled=false"
})
@ActiveProfiles("test")
public class ResetDatabaseHelperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseEnvironment databaseEnvironment;

    @Test
    @Transactional
    @Commit
    public void resetAdminPassword() {
        String encodedPassword = passwordEncoder.encode("Admin@123");
        String account = databaseEnvironment.qualify("account");
        jdbcTemplate.update("UPDATE " + account + " SET password_hash = ?, force_password_reset = 0 WHERE username = 'admin'", encodedPassword);
        System.out.println("=== Reset admin password to Admin@123 success ===");
        
        java.util.Map<String, Object> admin = jdbcTemplate.queryForMap("SELECT username, password_hash, force_password_reset, enabled FROM " + account + " WHERE username = 'admin'");
        System.out.println("DATABASE ADMIN STATE: " + admin);
    }
}
