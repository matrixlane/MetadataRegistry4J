package io.github.matrixlane.metadataregistry.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 契约导出。默认仅校验 /v3/api-docs 可用；
 * 传入 -Dopenapi.export=true 时将契约写入 docs/technical/api/openapi.json。
 *
 * <p>配置模式与 CaseInsensitiveEnumPersistenceTest 一致：切断用户主目录
 * application-local.yml 导入（其可能激活 team profile 引入 orm-team.xml 映射），
 * 锁定内存 SQLite，使测试与宿主机配置无关。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:openapi-export-test?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "app.seed.demo-data.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAvailableAndExportable() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains("\"openapi\"");

        if (Boolean.getBoolean("openapi.export")) {
            Path out = Path.of(System.getProperty("user.dir"))
                    .resolve("../docs/technical/api/openapi.json")
                    .normalize();
            Files.createDirectories(out.getParent());
            Files.writeString(out, json);
        }
    }
}
