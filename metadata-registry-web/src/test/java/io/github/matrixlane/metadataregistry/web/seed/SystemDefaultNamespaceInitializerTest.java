package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SystemDefaultNamespaceInitializer}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>幂等性：NS 已存在时不重复创建</li>
 *   <li>新创建时属性正确设置</li>
 *   <li>多语言 Designation 添加</li>
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
    private JdbcTemplate jdbcTemplate;

    @Mock
    private io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment databaseEnvironment;

    @Mock
    private ApplicationArguments args;

    @InjectMocks
    private SystemDefaultNamespaceInitializer initializer;

    // --- 幂等性测试 ---

    @Test
    @DisplayName("BC-08: NS 已存在时跳过初始化（幂等）")
    void whenNamespaceExists_thenSkipInitialization() throws Exception {
        when(namespaceRepository.existsByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX))
                .thenReturn(true);

        initializer.run(args);

        verify(namespaceRepository, never()).save(any(Namespace.class));
    }

    // --- 新创建时属性正确 ---

    @Test
    @DisplayName("新创建时设置正确属性：shorthandPrefix、schemeReference、namingAuthority")
    void whenCreatingNewNamespace_thenSetsCorrectAttributes() throws Exception {
        when(namespaceRepository.existsByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX))
                .thenReturn(false);

        LanguageIdentification langEn = new LanguageIdentification();
        langEn.setLanguageIdentifier("en-US");
        when(languageRepository.findByLanguageIdentifier("en-US")).thenReturn(Optional.of(langEn));
        when(languageRepository.findByLanguageIdentifier("zh-CN")).thenReturn(Optional.empty());
        when(languageRepository.save(any(LanguageIdentification.class))).thenReturn(langEn);
        // 首次 findById 为空触发 orElseGet 插入路径，插入后再次查询返回新建组织。
        Organization systemOrg = new Organization();
        when(organizationRepository.findById(any())).thenReturn(Optional.empty(), Optional.of(systemOrg));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<Namespace> nsCaptor = ArgumentCaptor.forClass(Namespace.class);

        initializer.run(args);

        verify(namespaceRepository).save(nsCaptor.capture());
        Namespace saved = nsCaptor.getValue();

        assertThat(saved.getShorthandPrefix()).isEqualTo("sys:");
        assertThat(saved.getSchemeReference()).isEqualTo("urn:metadata-registry:system-default");
        assertThat(saved.getNamingAuthority()).isNotNull();
        assertThat(saved.getNamingAuthority().getName().get(0).getValue()).isEqualTo("system");
        assertThat(saved.getDesignations()).isNotEmpty();
    }
}
