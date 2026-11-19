package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.MetadataSearchService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@code browse-data-dictionary}（add-mcp-server-support）：数据字典浏览。
 * 业务面 /api/v1/data-dictionary/browser 委托至统一搜索服务，本工具同语义。
 */
@Component
public class BrowseDataDictionaryTool implements McpTool {

    private final MetadataSearchService searchService;

    public BrowseDataDictionaryTool(MetadataSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String name() {
        return "browse-data-dictionary";
    }

    @Override
    public String title() {
        return "数据字典浏览";
    }

    @Override
    public String description() {
        return "浏览数据字典：统一列出已注册元数据项（可按关键词/类型/注册状态/上下文过滤，分页）。"
                + "参数与 search-metadata 一致。";
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
