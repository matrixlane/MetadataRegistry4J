package io.github.matrixlane.metadataregistry.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI metadataRegistryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MetadataRegistry4j API")
                        .description("基于 ISO/IEC 11179 的企业元数据注册系统 REST API")
                        .version("0.1.0")
                        .contact(new Contact().name("MatrixLane").url("https://github.com/matrixlane"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录 /api/v1/auth/login 后将返回 token，按 Bearer 方式携带。")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
