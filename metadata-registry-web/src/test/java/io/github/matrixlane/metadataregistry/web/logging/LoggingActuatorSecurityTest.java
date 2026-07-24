package io.github.matrixlane.metadataregistry.web.logging;

import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.auth.JwtAuthenticationFilter;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security test for actuator loggers endpoints using a minimal Spring context.
 * Uses dummy controllers to simulate actuator endpoints and verifies
 * that the security rules from SecurityConfig are correctly applied.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = LoggingActuatorSecurityTest.TestConfig.class, properties = {
        "management.endpoints.web.exposure.include=health,info,loggers",
        "management.endpoint.loggers.enabled=true",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.jwt.secret=please-replace-with-a-long-random-string-of-at-least-32-bytes-XYZ",
        "app.jwt.expires-minutes=480",
        "app.jwt.issuer=metadata-registry"
})
@Import({
        io.github.matrixlane.metadataregistry.web.auth.SecurityConfig.class,
        io.github.matrixlane.metadataregistry.web.config.CorsConfig.class,
        JwtAuthenticationFilter.class,
        TraceIdFilter.class,
        DummyActuatorController.class
})
@AutoConfigureMockMvc
class LoggingActuatorSecurityTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    private String adminToken;
    private String registrarToken;

    @BeforeEach
    void setUp() {
        String secret = "please-replace-with-a-long-random-string-of-at-least-32-bytes-XYZ";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(3600);

        adminToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("metadata-registry")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("username", "admin")
                .claim("role", "SUPER_ADMIN")
                .claim("displayName", "Admin User")
                .signWith(key)
                .compact();

        registrarToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("metadata-registry")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("username", "registrar")
                .claim("displayName", "Registrar User")
                .signWith(key)
                .compact();

        when(jwtService.parseClaims(any())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        });
    }

    @Test
    void actuatorLoggersGet_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/loggers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorLoggersGet_shouldAllowAdmin() throws Exception {
        mockMvc.perform(get("/actuator/loggers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorLoggersGet_shouldDenyRegistrar() throws Exception {
        mockMvc.perform(get("/actuator/loggers")
                        .header("Authorization", "Bearer " + registrarToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorLoggersPost_shouldRequireAdmin() throws Exception {
        mockMvc.perform(post("/actuator/loggers/io.github.matrixlane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorLoggersPost_shouldAllowAdmin() throws Exception {
        mockMvc.perform(post("/actuator/loggers/io.github.matrixlane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void actuatorLoggersPost_shouldDenyRegistrar() throws Exception {
        mockMvc.perform(post("/actuator/loggers/io.github.matrixlane")
                        .header("Authorization", "Bearer " + registrarToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configuredLevel\":\"DEBUG\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealth_shouldBeAccessibleWithoutAuth() throws Exception {
        // Health endpoint is accessible without auth; may return 200 (UP) or 503 (DOWN)
        // depending on whether required services (e.g., database) are available in test context.
        int status = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(
                status == 200 || status == 503,
                "Expected 200 or 503, but got: " + status
        );
    }
}
