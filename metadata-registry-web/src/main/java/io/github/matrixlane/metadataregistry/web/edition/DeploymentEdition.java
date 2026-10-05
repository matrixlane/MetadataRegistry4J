package io.github.matrixlane.metadataregistry.web.edition;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;

import java.util.Locale;

/**
 * 部署版本（产品形态）。
 *
 * <p>PERSONAL — 个人版：单机部署，默认 SQLite，精简向导。
 * TEAM — 团队版：局域网单服务器，完整向导。
 * CLOUD — 云端多租户版：枚举占位，当前交付不可用。
 */
public enum DeploymentEdition {

    PERSONAL,
    TEAM,
    CLOUD;

    /**
     * 解析并校验版本标识。CLOUD 与未知值一律拒绝（业务校验异常）。
     */
    public static DeploymentEdition parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "部署版本不能为空");
        }
        final DeploymentEdition edition;
        try {
            edition = valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "无效的部署版本: " + value);
        }
        if (edition == CLOUD) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "云端多租户版在当前交付中不可用");
        }
        return edition;
    }
}
