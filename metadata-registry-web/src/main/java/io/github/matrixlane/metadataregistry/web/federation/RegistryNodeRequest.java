package io.github.matrixlane.metadataregistry.web.federation;

import jakarta.validation.constraints.NotBlank;

/**
 * 联邦节点登记/更新请求。
 *
 * <p>authToken 仅在创建或显式更换时提供，服务端加密存储，响应中永不回传。
 */
public record RegistryNodeRequest(
        @NotBlank(message = "节点名称不能为空")
        String name,

        String baseUrl,

        @NotBlank(message = "注册机构标识（RAI）不能为空")
        String rai,

        String authToken
) {}
