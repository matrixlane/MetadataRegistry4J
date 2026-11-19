package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.MetadataSearchService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@code search-metadata}（add-mcp-server-support）：统一元数据搜索。
 * 语义与业务面 /api/v1/metadata/search 完全一致（同服务同参数）。
 */
@Component
public class SearchMetadataTool implements McpTool {

    private final MetadataSearchService searchService;

    public SearchMetadataTool(MetadataSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String name() {
        return "search-metadata";
    }

    @Override
    public String title() {
        return "统一元数据搜索";
    }

    @Override
    public String description() {
        return "跨所有实体类型搜索注册库（关键词/类型/注册状态/上下文/命名空间/精确标识符），"
                + "返回与业务面一致的分页条目（标识、指称、注册状态）。类型过滤超出服务白名单时自然产生空结果。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(MetadataSearchParams.schemaProperties(), null);
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        MetadataSearchParams params = MetadataSearchParams.from(arguments);
        return PageResponse.of(searchService.search(params.q(), params.type(), params.status(),
                params.contextId(), params.acceptability(), params.namespaceId(), params.exactMatch(),
                params.identifier(), params.namespaceShorthand(), params.pageable()));
    }
}
