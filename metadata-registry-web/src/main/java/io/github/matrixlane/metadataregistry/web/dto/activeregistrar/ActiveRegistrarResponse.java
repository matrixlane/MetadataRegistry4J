package io.github.matrixlane.metadataregistry.web.dto.activeregistrar;

import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;

import java.util.List;
import java.util.UUID;

/**
 * 当前账号的 Registrar 身份清单与默认注册人员
 * （personal-no-login-registrar-default：激活状态保存在浏览器端，
 * 服务端只返回身份列表与默认者，active 由前端计算）。
 */
public record ActiveRegistrarResponse(
        UUID defaultRegistrarId,
        List<ActiveRegistrarService.RegistrarIdentity> identities) {
}
