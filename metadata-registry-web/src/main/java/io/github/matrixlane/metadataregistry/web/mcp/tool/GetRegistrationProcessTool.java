package io.github.matrixlane.metadataregistry.web.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationProcessResponse;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessLayoutService;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code get-registration-process}（add-mcp-server-support）：标准注册流转规则
 * 与当前流程画布布局（只读；布局修改不暴露）。
 */
@Component
public class GetRegistrationProcessTool implements McpTool {

    private final RegistrationProcessService registrationProcessService;
    private final RegistrationProcessLayoutService layoutService;

    public GetRegistrationProcessTool(RegistrationProcessService registrationProcessService,
                                      RegistrationProcessLayoutService layoutService) {
        this.registrationProcessService = registrationProcessService;
        this.layoutService = layoutService;
    }

    @Override
    public String name() {
        return "get-registration-process";
    }

    @Override
    public String title() {
        return "注册流程规则";
    }

    @Override
    public String description() {
        return "读取标准注册流转规则（状态机/审核点）与当前注册流程画布布局。只读工具。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemas.object(Map.of(), null);
    }

    @Override
    public Object handle(Map<String, Object> arguments) {
        RegistrationProcessResponse process = registrationProcessService.getCurrentProcess();
        JsonNode layout = layoutService.read();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("process", process);
        result.put("layout", layout);
        return result;
    }
}
