package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 注册表合并的 Registrar 门控（registry-merge-strategy D5）。
 *
 * <p>迁移冲突的裁决与批准并入必须由目的方注册机构的 Registrar 以其活动
 * Registrar 身份执行；无认证（系统路径）时维持既有行为。目的方注册机构取
 * 目的方命名空间所属机构，缺失时按来源节点 RAI 匹配。
 */
@Component
@RequiredArgsConstructor
public class MergeRegistrarGate {

    private final ActiveRegistrarService activeRegistrarService;
    private final NamespaceRepository namespaceRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;

    public void assertTargetRegistrar(MergeRequest mr) {
        UUID accountId = activeRegistrarService.currentAccountIdOrNull();
        if (accountId == null) {
            return; // 系统/无认证路径
        }
        RegistrationAuthority targetRa = resolveTargetAuthority(mr);
        if (targetRa == null) {
            return;
        }
        Registrar active = activeRegistrarService.resolveForCurrentUser()
                .orElseThrow(() -> new AccessDeniedException(
                        "注册表合并的裁决与批准要求目的方注册机构的活动 Registrar 身份"));
        if (active.getRegistrationAuthority() == null
                || !targetRa.getId().equals(active.getRegistrationAuthority().getId())) {
            throw new AccessDeniedException(
                    "仅目的方注册机构的活动 Registrar 可裁决/批准注册表合并；如持有该机构身份，请先切换为活动身份");
        }
    }

    private RegistrationAuthority resolveTargetAuthority(MergeRequest mr) {
        if (mr.getTargetNamespaceId() != null) {
            Namespace ns = namespaceRepository.findById(mr.getTargetNamespaceId()).orElse(null);
            if (ns != null && ns.getRegistrationAuthority() != null) {
                return ns.getRegistrationAuthority();
            }
        }
        if (mr.getSourceNode() != null) {
            return registrationAuthorityRepository.findByRai(mr.getSourceNode().getRai()).orElse(null);
        }
        return null;
    }
}
