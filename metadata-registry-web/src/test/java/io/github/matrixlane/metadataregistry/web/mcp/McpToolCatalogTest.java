package io.github.matrixlane.metadataregistry.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.mcp.audit.McpToolAuditService;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * McpToolCatalog（add-mcp-server-support）：写工具过滤、结果序列化、
 * 错误透传语义（[code] message / 通用错误不泄露内部细节）、审计记录。
 */
class McpToolCatalogTest {

    private final McpToolAuditService auditService = mock(McpToolAuditService.class);
    private final McpToolCatalog catalog = new McpToolCatalog(
            List.of(readTool(), writeTool()), new ObjectMapper(), auditService);

    private static McpTool readTool() {
        return new StubTool("search-metadata", false, Map.of());
    }

    private static McpTool writeTool() {
        return new StubTool("draft-item", true, Map.of());
    }

    @Test
    void specifications_shouldIncludeWriteOnlyWhenEnabled() {
        assertThat(catalog.specifications(true)).hasSize(2);
        assertThat(catalog.specifications(false)).hasSize(1);
        assertThat(catalog.specifications(false).get(0).tool().name()).isEqualTo("search-metadata");
    }

    @Test
    void invoke_success_shouldReturnJsonTextAndAuditOk() {
        McpSchema.CallToolResult result = catalog.invoke(readTool(), Map.of("q", "血压"));
        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).isEqualTo("{\"q\":\"血压\"}");
        verify(auditService).record(eq("search-metadata"), anyString(), anyString(), eq("OK"), anyLong());
    }

    @Test
    void invoke_businessError_shouldTranslateToCodeMessageText() {
        McpTool failing = new StubTool("submit-drafts", true, Map.of()) {
            @Override
            public Object handle(Map<String, Object> arguments) {
                throw new BusinessException(409, "条目不是草稿状态");
            }
        };
        McpSchema.CallToolResult result = catalog.invoke(failing, Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).isEqualTo("[409] 条目不是草稿状态");
        verify(auditService).record(eq("submit-drafts"), anyString(), anyString(),
                eq("BUSINESS_ERROR"), anyLong());
    }

    @Test
    void invoke_internalError_shouldReturnGenericTextWithoutLeakage() {
        McpTool failing = new StubTool("get-item-detail", false, Map.of()) {
            @Override
            public Object handle(Map<String, Object> arguments) {
                throw new IllegalStateException("jdbc:sqlserver://internal-host;password=secret");
            }
        };
        McpSchema.CallToolResult result = catalog.invoke(failing, Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).isEqualTo("[500] MCP 工具执行失败（内部错误）");
        assertThat(textOf(result)).doesNotContain("internal-host", "secret", "IllegalStateException");
        verify(auditService).record(eq("get-item-detail"), anyString(), anyString(),
                eq("INTERNAL_ERROR"), anyLong());
    }

    @Test
    void invoke_resourceNotFound_shouldTranslateTo404Text() {
        McpTool failing = new StubTool("get-item-detail", false, Map.of()) {
            @Override
            public Object handle(Map<String, Object> arguments) {
                throw new io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException(
                        "AdministeredItem", "e001a6b3-a580-438b-940c-3f9db301e498");
            }
        };
        McpSchema.CallToolResult result = catalog.invoke(failing, Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).startsWith("[404] AdministeredItem not found:");
        verify(auditService).record(eq("get-item-detail"), anyString(), anyString(),
                eq("NOT_FOUND"), anyLong());
    }

    private static String textOf(McpSchema.CallToolResult result) {
        assertThat(result.content()).hasSize(1);
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    /** 简单工具桩：回显参数（JSON 序列化路径覆盖）。包级可见以便条件装配测试复用。 */
    static class StubTool implements McpTool {

        private final String name;
        private final boolean write;
        private final Map<String, Object> schema;

        StubTool(String name, boolean write, Map<String, Object> schema) {
            this.name = name;
            this.write = write;
            this.schema = schema;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String title() {
            return name;
        }

        @Override
        public String description() {
            return "stub";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return schema;
        }

        @Override
        public boolean write() {
            return write;
        }

        @Override
        public Object handle(Map<String, Object> arguments) {
            return arguments;
        }
    }
}
