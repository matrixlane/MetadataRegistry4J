package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SystemDefaultNamespaceInitializer}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>幂等性：NS 已存在时不重复创建</li>
 *   <li>新创建时属性正确设置（NamingAuthority 留空，由调和步骤挂接注册机构）</li>
 *   <li>调和：无注册机构留空、有注册机构挂接、历史系统组织改挂</li>
 *   <li>历史系统组织仅在没有引用时被移除</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SystemDefaultNamespaceInitializerTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private LanguageIdentificationRepository languageRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment databaseEnvironment;

    @InjectMocks
    private SystemDefaultNamespaceInitializer initializer;

    private void stubEnvironment() {
        when(databaseEnvironment.qualify(any())).thenAnswer(inv -> "mdr." + inv.getArgument(0));
        when(databaseEnvironment.toJdbcUuid(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- 幂等性测试 ---

    @Test
    @DisplayName("BC-08: NS 已存在时跳过创建（幂等），且未初始化时 NamingAuthority 留空")
    void whenNamespaceExists_thenSkipCreationAndLeaveAuthorityUnset() {
        Namespace existing = new Namespace();
        when(namespaceRepository.findByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX))
                .thenReturn(Optional.of(existing));
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of());
        when(organizationRepository.existsById(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID))
                .thenReturn(false);

        initializer.initializeSystemDefaultNamespace();

        verify(namespaceRepository, never()).save(any(Namespace.class));
        assertThat(existing.getNamingAuthority()).isNull();
    }

    // --- 新创建时属性正确 ---

    @Test
    @DisplayName("新创建时设置正确属性：shorthandPrefix、schemeReference；不再创建系统组织")
    void whenCreatingNewNamespace_thenSetsCorrectAttributesWithoutSystemOrg() {
        when(namespaceRepository.findByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX))
                .thenReturn(Optional.empty());
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of());
        when(organizationRepository.existsById(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID))
                .thenReturn(false);

        LanguageIdentification langEn = new LanguageIdentification();
        langEn.setLanguageIdentifier("en-US");
        when(languageRepository.findByLanguageIdentifier("en-US")).thenReturn(Optional.of(langEn));
        when(languageRepository.findByLanguageIdentifier("zh-CN")).thenReturn(Optional.empty());
        when(languageRepository.save(any(LanguageIdentification.class))).thenReturn(langEn);

        ArgumentCaptor<Namespace> nsCaptor = ArgumentCaptor.forClass(Namespace.class);

        initializer.initializeSystemDefaultNamespace();

        verify(namespaceRepository).save(nsCaptor.capture());
        Namespace saved = nsCaptor.getValue();

        assertThat(saved.getShorthandPrefix()).isEqualTo("sys:");
        assertThat(saved.getSchemeReference().getValue()).isEqualTo("urn:metadata-registry:system-default");
        assertThat(saved.getNamingAuthority()).isNull();
        assertThat(saved.getDesignations()).isNotEmpty();
        // 不再创建/保存系统组织
        verify(organizationRepository, never()).save(any(Organization.class));
    }

    // --- NamingAuthority 调和 ---

    @Test
    @DisplayName("调和：存在注册机构时 sys: 命名空间 NamingAuthority 挂接到注册机构")
    void reconcile_attachesRegistrationAuthorityWhenExists() {
        Namespace existing = new Namespace();
        when(namespaceRepository.findByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX))
                .thenReturn(Optional.of(existing));
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of(ra));
        when(organizationRepository.existsById(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID))
                .thenReturn(false);

        initializer.initializeSystemDefaultNamespace();

        assertThat(existing.getNamingAuthority()).isSameAs(ra);
        verify(namespaceRepository).save(existing);
    }

    @Test
    @DisplayName("调和：历史库 NamingAuthority 指向系统组织时改挂注册机构")
    void reconcile_repointsLegacySystemOrganization() {
        Namespace existing = new Namespace();
        Organization legacySystemOrg = new Organization();
        legacySystemOrg.setId(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID);
        existing.setNamingAuthority(legacySystemOrg);
        when(namespaceRepository.findByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX))
                .thenReturn(Optional.of(existing));
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of(ra));
        // 历史系统组织行仍存在，但改挂后已无引用 → 移除
        stubEnvironment();
        when(organizationRepository.existsById(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(contains("namespace"), eq(Integer.class), any()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("contact"), eq(Integer.class), any()))
                .thenReturn(0);

        initializer.initializeSystemDefaultNamespace();

        assertThat(existing.getNamingAuthority()).isSameAs(ra);
        // 4 条 DELETE：organization_name / organization_email / organization_phone / organization
        verify(jdbcTemplate, times(4)).update(contains("DELETE"), eq((Object) SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID));
    }

    @Test
    @DisplayName("历史系统组织仍有联系人引用时不删除")
    void legacySystemOrganization_keptWhenReferencedByContact() {
        Namespace existing = new Namespace();
        when(namespaceRepository.findByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX))
                .thenReturn(Optional.of(existing));
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of());
        stubEnvironment();
        when(organizationRepository.existsById(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(contains("namespace"), eq(Integer.class), any()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("contact"), eq(Integer.class), any()))
                .thenReturn(1);

        initializer.initializeSystemDefaultNamespace();

        verify(jdbcTemplate, never()).update(contains("DELETE"), eq((Object) SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID));
    }
}
