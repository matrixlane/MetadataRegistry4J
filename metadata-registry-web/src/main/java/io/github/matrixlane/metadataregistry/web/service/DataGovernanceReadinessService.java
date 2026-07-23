package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.readiness.ReadinessChecklist;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WI-028-C Phase 2: 数据治理就绪检查服务
 *
 * <p>在创建 Item 前，系统调用 {@link #checkReadiness(UUID, UUID)} 检查所有前置条件：
 * <ul>
 *   <li>RegistrationAuthority 存在性</li>
 *   <li>Namespace 存在性</li>
 *   <li>Namespace ACTIVE 状态</li>
 *   <li>NamingConvention 有效性（当 mandatory=TRUE 时）</li>
 *   <li>用户权限</li>
 * </ul>
 *
 * <p>结果缓存 5 分钟（TTL），避免高频 DB 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "readiness", cacheManager = "cacheManager")
@Transactional(readOnly = true)
public class DataGovernanceReadinessService {

    /** 就绪检查结果缓存的 TTL（5 分钟） */
    public static final long CACHE_TTL_SECONDS = 300;

    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamingConventionEngine namingConventionEngine;

    /**
     * 检查指定用户在指定 Namespace 下的就绪状态。
     *
     * @param userId      用户 ID（可为 null，表示匿名检查）
     * @param namespaceId Namespace ID
     * @return 就绪检查清单
     * @throws ResourceNotFoundException 如果 Namespace 不存在
     */
    @Cacheable(key = "'ns:' + #namespaceId + ':user:' + #userId",
               cacheNames = "readiness")
    public ReadinessChecklist checkReadiness(UUID userId, UUID namespaceId) {
        log.debug("WI-028-C: 执行就绪检查 userId={}, namespaceId={}", userId, namespaceId);
        List<String> missing = new ArrayList<>();

        // 1. RegistrationAuthority 存在性
        boolean raExists = checkRegistrationAuthorityExists();
        if (!raExists) {
            missing.add("RegistrationAuthority 不存在，请先初始化注册机构");
        }

        // 2. Namespace 存在性
        Namespace ns = namespaceRepository.findById(namespaceId).orElse(null);
        boolean nsExists = ns != null;
        if (!nsExists) {
            missing.add("Namespace 不存在，ID: " + namespaceId);
            return buildChecklist(namespaceId, raExists, false, false, false, false, missing);
        }

        // 3. Namespace ACTIVE 状态（暂以 namingAuthority != null 判断）
        boolean nsActive = isNamespaceActive(ns);
        if (!nsActive) {
            missing.add("Namespace 未激活，请检查命名空间状态");
        }

        // 4. NamingConvention 有效性（仅当 mandatory=TRUE 时检查）
        boolean ncValid = true;
        if (Boolean.TRUE.equals(ns.getMandatoryNamingConventionIndicator())) {
            ncValid = checkNamingConventionValid(ns);
            if (!ncValid) {
                missing.add("NamingConvention 校验失败，当前 Namespace 要求符合命名规范");
            }
        }

        // 5. 用户权限（MVP 阶段暂时硬编码返回 true）
        boolean userHasPermission = checkUserHasPermission(userId, ns);

        return new ReadinessChecklist(
                namespaceId,
                raExists,
                nsExists,
                nsActive,
                ncValid,
                userHasPermission,
                missing
        );
    }

    /**
     * 检查系统是否就绪（全局 RegistrationAuthority 检查）。
     */
    @Cacheable(key = "'system'", cacheNames = "readiness")
    public ReadinessChecklist checkSystemReadiness() {
        List<String> missing = new ArrayList<>();
        boolean raExists = checkRegistrationAuthorityExists();
        if (!raExists) {
            missing.add("RegistrationAuthority 不存在，系统尚未初始化");
        }
        return new ReadinessChecklist(
                null,
                raExists,
                true,  // system-wide check, no specific namespace
                true,
                true,
                true,
                missing
        );
    }

    private boolean checkRegistrationAuthorityExists() {
        return registrationAuthorityRepository.count() > 0;
    }

    private boolean isNamespaceActive(Namespace ns) {
        // A namespace is active if it has a naming authority (ISO 11179-3 naming_authority)
        // or, for backwards compatibility during migration, a registration authority.
        return ns.getNamingAuthority() != null || ns.getRegistrationAuthority() != null;
    }

    private boolean checkNamingConventionValid(Namespace ns) {
        // 获取 Namespace 的 preferred designation 进行 NC 校验
        String preferredName = ItemNamingHelper.preferredDesignation(ns);
        if (preferredName == null || preferredName.isBlank()) {
            // 如果 Namespace 本身没有名称，无法校验，返回 false
            return false;
        }
        Sign sign = Sign.of(preferredName);
        return namingConventionEngine.isValid(sign);
    }

    /**
     * MVP 阶段：用户权限硬编码返回 true。
     * 待 WI-XXX 权限模块实现后补充真实权限检查逻辑。
     */
    private boolean checkUserHasPermission(UUID userId, Namespace ns) {
        // TODO: 接入 RBAC 权限模块，根据用户在 Namespace 下的角色判断 CREATE 权限
        return true;
    }

    private ReadinessChecklist buildChecklist(UUID namespaceId,
                                              boolean raExists,
                                              boolean nsExists,
                                              boolean nsActive,
                                              boolean ncValid,
                                              boolean userHasPermission,
                                              List<String> missing) {
        return new ReadinessChecklist(namespaceId, raExists, nsExists, nsActive,
                ncValid, userHasPermission, missing);
    }
}
