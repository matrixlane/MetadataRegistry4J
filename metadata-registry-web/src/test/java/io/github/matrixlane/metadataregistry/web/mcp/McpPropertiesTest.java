package io.github.matrixlane.metadataregistry.web.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpProperties 绑定契约（add-mcp-server-support）：前缀 mdr.mcp、
 * kebab-case write-tools-enabled 映射、默认值。
 */
class McpPropertiesTest {

    private static McpProperties bind(Map<String, String> props) {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(props);
        return new Binder(source).bind("mdr.mcp", McpProperties.class).get();
    }

    @Test
    void defaults_shouldBeLoopback8082AndEnabled() {
        McpProperties properties = new McpProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getPort()).isEqualTo(8082);
        assertThat(properties.isWriteToolsEnabled()).isTrue();
    }

    @Test
    void kebabCaseWriteToolsEnabled_shouldBind() {
        McpProperties properties = bind(Map.of(
                "mdr.mcp.enabled", "false",
                "mdr.mcp.host", "0.0.0.0",
                "mdr.mcp.port", "9082",
                "mdr.mcp.write-tools-enabled", "false"));
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getHost()).isEqualTo("0.0.0.0");
        assertThat(properties.getPort()).isEqualTo(9082);
        assertThat(properties.isWriteToolsEnabled()).isFalse();
    }
}
