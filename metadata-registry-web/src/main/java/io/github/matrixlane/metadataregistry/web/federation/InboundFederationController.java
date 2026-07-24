package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 联邦入站端点（预共享令牌认证，无 JWT）。
 *
 * <p>对端实例通过 X-Node-Id + X-Auth-Token 头访问；令牌经
 * {@link RegistryNodeService#verifyToken} 校验。
 */
@RestController
@RequestMapping("/api/v1/federation/inbound")
@RequiredArgsConstructor
@Tag(name = "联邦协作 - 入站", description = "对端实例推送与回执下载接口")
public class InboundFederationController {

    private final RegistryNodeService registryNodeService;
    private final MergeRequestService mergeRequestService;
    private final ObjectMapper objectMapper;

    @PostMapping("/merge-requests")
    @Operation(summary = "接收对端推送的合并包（HTTP 推送通道）")
    public ApiResponse<MergeRequestView> receive(@RequestHeader("X-Node-Id") UUID nodeId,
                                                 @RequestHeader("X-Auth-Token") String token,
                                                 @RequestBody String bundleJson) {
        if (!registryNodeService.verifyToken(nodeId, token)) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "联邦凭据校验失败");
        }
        try {
            Path dir = Paths.get("data", "federation", "inbound");
            Files.createDirectories(dir);
            Path bundleFile = dir.resolve("bundle-inbound-" + System.currentTimeMillis() + ".json");
            Files.writeString(bundleFile, bundleJson);
            return ApiResponse.ok(mergeRequestService.importBundle(bundleFile, "remote-push"));
        } catch (Exception e) {
            throw new BusinessException("合并包接收失败: " + e.getMessage());
        }
    }

    @GetMapping("/receipts/{mrId}")
    @Operation(summary = "下载合并回执（供源节点更新水位线）")
    public ApiResponse<String> downloadReceipt(@RequestHeader("X-Node-Id") UUID nodeId,
                                               @RequestHeader("X-Auth-Token") String token,
                                               @PathVariable UUID mrId) {
        if (!registryNodeService.verifyToken(nodeId, token)) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "联邦凭据校验失败");
        }
        Path receipt = Paths.get("data", "federation", "receipt-" + mrId + ".json");
        if (!Files.exists(receipt)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "回执不存在或合并未完成");
        }
        try {
            return ApiResponse.ok(Files.readString(receipt));
        } catch (Exception e) {
            throw new BusinessException("回执读取失败: " + e.getMessage());
        }
    }
}
