package io.github.matrixlane.metadataregistry.web.dto.activeregistrar;

import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;

import java.util.List;

/**
 * 当前账号的活动 Registrar 身份与全部可用身份。
 */
public record ActiveRegistrarResponse(
        ActiveRegistrarService.RegistrarIdentity active,
        List<ActiveRegistrarService.RegistrarIdentity> identities) {
}
