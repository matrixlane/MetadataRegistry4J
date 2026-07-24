package io.github.matrixlane.metadataregistry.web.logging;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Minimal Spring Boot configuration for {@link LoggingActuatorSecurityTest}.
 * Excludes data-source auto-configuration so the test can run without a database.
 *
 * <p>Deliberately NOT annotated with @Configuration/@SpringBootConfiguration:
 * this class lives under the application's component-scan base package, and a
 * stereotype annotation would make the main application context pick it up.
 * Its @EnableAutoConfiguration(exclude=...) would then join the shared
 * auto-configuration group and exclude DataSource/JPA/Flyway for every
 * full-context test boot. It is only used via explicit
 * {@code classes = LoggingActuatorSecurityTestConfig.class} reference, which
 * processes it in "lite" configuration mode without a stereotype.
 */
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
})
class LoggingActuatorSecurityTestConfig {
}
