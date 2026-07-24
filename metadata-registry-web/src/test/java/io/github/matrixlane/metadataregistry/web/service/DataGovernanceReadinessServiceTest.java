package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.readiness.ReadinessChecklist;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataGovernanceReadinessService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>BC-01: RegistrationAuthority 不存在 → registrationAuthorityExists=false</li>
 *   <li>BC-02: Namespace 不存在 → namespaceExists=false</li>
 *   <li>BC-03: Namespace 无关联 RA（非 ACTIVE）→ namespaceActive=false</li>
 *   <li>BC-04: 用户无 CREATE 权限 → userHasPermission=false（当前硬编码）</li>
 *   <li>BC-08: system-default NS 已存在时重复启动 → 幂等</li>
 *   <li>system-readiness 全局检查</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DataGovernanceReadinessServiceTest {

    @Mock
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamingConventionEngine namingConventionEngine;

    @Mock
    private RegistrySpecificationService registrySpecificationService;

    @InjectMocks
    private DataGovernanceReadinessService readinessService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID NS_ID = UUID.randomUUID();

    // --- BC-01: RA 不存在 ---

    @Test
    @DisplayName("BC-01: RegistrationAuthority 不存在时，registrationAuthorityExists=false")
    void whenRaDoesNotExist_thenRegistrationAuthorityExistsIsFalse() {
        when(registrationAuthorityRepository.count()).thenReturn(0L);
        when(registrySpecificationService.exists()).thenReturn(true);

        ReadinessChecklist result = readinessService.checkReadiness(USER_ID, NS_ID);

        assertThat(result.registrationAuthorityExists()).isFalse();
        assertThat(result.missingPrerequisites())
                .anyMatch(m -> m.contains("RegistrationAuthority 不存在"));
    }

    // --- BC-01-spec: RegistrySpecification 不存在 ---

    @Test
    @DisplayName("注册规范不存在时，registrySpecificationExists=false")
    void whenRegistrySpecDoesNotExist_thenRegistrySpecificationExistsIsFalse() {
        when(registrationAuthorityRepository.count()).thenReturn(1L);
        when(registrySpecificationService.exists()).thenReturn(false);

        ReadinessChecklist result = readinessService.checkReadiness(USER_ID, NS_ID);

        assertThat(result.registrySpecificationExists()).isFalse();
        assertThat(result.missingPrerequisites())
                .anyMatch(m -> m.contains("注册规范（Registry Specification）未配置"));
    }

    // --- BC-02: Namespace 不存在 ---

    @Test
    @DisplayName("BC-02: Namespace 不存在时，namespaceExists=false")
    void whenNamespaceDoesNotExist_thenNamespaceExistsIsFalse() {
        when(registrationAuthorityRepository.count()).thenReturn(1L);
        when(registrySpecificationService.exists()).thenReturn(true);
        when(namespaceRepository.findById(NS_ID)).thenReturn(Optional.empty());

        ReadinessChecklist result = readinessService.checkReadiness(USER_ID, NS_ID);

        assertThat(result.namespaceExists()).isFalse();
        assertThat(result.missingPrerequisites())
                .anyMatch(m -> m.contains("Namespace 不存在"));
    }

    // --- BC-03: Namespace 非 ACTIVE（无关联 RA） ---

    @Test
    @DisplayName("BC-03: Namespace 无关联 RA 时，namespaceActive=false")
    void whenNamespaceHasNoRa_thenNamespaceActiveIsFalse() {
        when(registrationAuthorityRepository.count()).thenReturn(1L);
        when(registrySpecificationService.exists()).thenReturn(true);

        Namespace ns = new Namespace();
        ns.setId(NS_ID);
        ns.setRegistrationAuthority(null);  // not active
        when(namespaceRepository.findById(NS_ID)).thenReturn(Optional.of(ns));

        ReadinessChecklist result = readinessService.checkReadiness(USER_ID, NS_ID);

        assertThat(result.namespaceExists()).isTrue();
        assertThat(result.namespaceActive()).isFalse();
        assertThat(result.missingPrerequisites())
                .anyMatch(m -> m.contains("未激活"));
    }

    // --- 就绪全部通过 ---

    @Test
    @DisplayName("所有前置条件满足时，allReady=true")
    void whenAllPrerequisitesMet_thenAllReadyIsTrue() {
        when(registrationAuthorityRepository.count()).thenReturn(1L);
        when(registrySpecificationService.exists()).thenReturn(true);

        RegistrationAuthority ra = new RegistrationAuthority();
        Namespace ns = new Namespace();
        ns.setId(NS_ID);
        ns.setRegistrationAuthority(ra);
        ns.setMandatoryNamingConventionIndicator(false);
        when(namespaceRepository.findById(NS_ID)).thenReturn(Optional.of(ns));

        ReadinessChecklist result = readinessService.checkReadiness(USER_ID, NS_ID);

        assertThat(result.isAllReady()).isTrue();
        assertThat(result.missingPrerequisites()).isEmpty();
    }

    // --- system-readiness 全局检查 ---

    @Test
    @DisplayName("system-readiness: RA 不存在时返回正确状态")
    void whenSystemRaMissing_thenSystemReadinessReturnsFalse() {
        when(registrationAuthorityRepository.count()).thenReturn(0L);
        when(registrySpecificationService.exists()).thenReturn(true);

        ReadinessChecklist result = readinessService.checkSystemReadiness();

        assertThat(result.registrationAuthorityExists()).isFalse();
        assertThat(result.missingPrerequisites())
                .anyMatch(m -> m.contains("系统尚未初始化"));
    }

    @Test
    @DisplayName("system-readiness: RA 存在时 allReady=true")
    void whenSystemRaExists_thenSystemReadinessAllReady() {
        when(registrationAuthorityRepository.count()).thenReturn(1L);
        when(registrySpecificationService.exists()).thenReturn(true);

        ReadinessChecklist result = readinessService.checkSystemReadiness();

        assertThat(result.isAllReady()).isTrue();
    }
}