package io.github.matrixlane.metadataregistry.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Seed seed = new Seed();

    @Data
    public static class Jwt {
        private String secret;
        private long expiresMinutes = 480;
        private String issuer = "metadata-registry";
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Data
    public static class Seed {
        private boolean enabled = true;
    }
}
