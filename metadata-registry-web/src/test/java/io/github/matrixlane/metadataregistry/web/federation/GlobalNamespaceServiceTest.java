package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierGenerationStrategyRegistry;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全局命名空间与命名空间跨实例映射（registry-merge-strategy 任务 5.3）。
 */
@ExtendWith(MockitoExtension.class)
class GlobalNamespaceServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private AdministeredItemRepository administeredItemRepository;
    @Mock private ScopedIdentifierRepository scopedIdentifierRepository;
    @Mock private IdentifierStrategyConfigRepository identifierStrategyConfigRepository;
    @Mock private LanguageIdentificationRepository languageRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.service.LanguageIdentifierResolver languageIdentifierResolver;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.service.ScopedIdentifierLifecycleService lifecycleService;
    @Mock private io.github.matrixlane.metadataregistry.web.service.NamespaceIdentifierSettingsService settingsService;

    @InjectMocks
    private GlobalNamespaceService service;

    @BeforeEach
    void setUp() {
        lenient().when(languageIdentifierResolver.resolve(any()))
                .thenAnswer(inv -> {
                    LanguageIdentification lang = new LanguageIdentification();
                    lang.setId(UUID.randomUUID());
                    lang.setLanguageIdentifier(inv.getArgument(0));
                    return Optional.of(lang);
                });
        lenient().when(namespaceRepository.save(any(Namespace.class)))
                .thenAnswer(inv -> {
                    Namespace ns = inv.getArgument(0);
                    if (ns.getId() == null) {
                        ns.setId(UUID.randomUUID());
                    }
                    return ns;
                });
        lenient().when(administeredItemRepository.save(any(AdministeredItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(administeredItemRepository.findByItem_Id(any()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        IdentifierGenerationStrategyRegistry.getInstance().clearCache();
    }

    @Test
    void ensureGlobalNamespace_createsOnce_thenReusesSingleton() {
        Namespace global = null;
        when(namespaceRepository.findBySchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME))
                .thenReturn(Optional.empty());

        global = service.ensureGlobalNamespace();

        assertThat(global.getSchemeReference().getValue()).isEqualTo(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME);
        assertThat(global.getShorthandPrefix()).isEqualTo(GlobalNamespaceService.GLOBAL_NAMESPACE_PREFIX);
        assertThat(global.getRegistrationAuthority()).isNull();
        assertThat(global.getDesignations()).anyMatch(d -> "全局命名空间".equals(d.getSign().getValue()));

        // UUID 标识策略
        ArgumentCaptor<IdentifierStrategyConfig> configCaptor = ArgumentCaptor.forClass(IdentifierStrategyConfig.class);
        verify(identifierStrategyConfigRepository).save(configCaptor.capture());
        assertThat(configCaptor.getValue().getStrategyType()).isEqualTo("UUID");
        assertThat(configCaptor.getValue().getNamespace()).isSameAs(global);

        // 再次调用：复用既有单例，绝不新建第二个
        when(namespaceRepository.findBySchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME))
                .thenReturn(Optional.of(global));
        Namespace again = service.ensureGlobalNamespace();
        assertThat(again).isSameAs(global);
        verify(namespaceRepository, times(1)).save(any(Namespace.class));
    }

    @Test
    void ensureNamespaceGlobalIdentifier_wrapsNamespaceAndAssignsUuid_thenReuses() {
        Namespace global = new Namespace();
        global.setId(UUID.randomUUID());
        global.setSchemeReference(Sign.of(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME));
        when(namespaceRepository.findBySchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME))
                .thenReturn(Optional.of(global));

        Namespace contentNs = new Namespace();
        contentNs.setId(UUID.randomUUID());
        when(scopedIdentifierRepository.findByItemId(contentNs.getId())).thenReturn(List.of());

        String globalId = service.ensureNamespaceGlobalIdentifier(contentNs);

        assertThat(globalId).isNotNull();
        assertThat(UUID.fromString(globalId)).isNotNull(); // UUID 策略

        // 包装物以 Administered_Item 承载，标识挂在全局命名空间下
        ArgumentCaptor<AdministeredItem> wrapperCaptor = ArgumentCaptor.forClass(AdministeredItem.class);
        verify(administeredItemRepository).save(wrapperCaptor.capture());
        AdministeredItem wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getItem()).isSameAs(contentNs);
        assertThat(wrapper.getRegistrationAuthority()).isNull();
        assertThat(wrapper.getScopedIdentifiers()).hasSize(1);
        ScopedIdentifier si = wrapper.getScopedIdentifiers().get(0);
        assertThat(si.getIdentifier()).isEqualTo(globalId);
        assertThat(si.getNamespace()).isSameAs(global);

        // 已有标识时复用，不重复生成
        when(scopedIdentifierRepository.findByItemId(contentNs.getId())).thenReturn(List.of(si));
        String again = service.ensureNamespaceGlobalIdentifier(contentNs);
        assertThat(again).isEqualTo(globalId);
        verify(administeredItemRepository, times(1)).save(any(AdministeredItem.class));
    }

    @Test
    void ensureNamespaceCopy_createsCopyWithSameUuid_thenReuses() {
        Namespace global = new Namespace();
        global.setId(UUID.randomUUID());
        global.setSchemeReference(Sign.of(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME));
        when(namespaceRepository.findBySchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME))
                .thenReturn(Optional.of(global));
        when(scopedIdentifierRepository.findByNamespaceIdAndIdentifier(global.getId(), "uuid-src-1"))
                .thenReturn(Optional.empty());

        RegistrationAuthority sourceRa = new RegistrationAuthority();
        sourceRa.setId(UUID.randomUUID());

        Namespace copy = service.ensureNamespaceCopy("uuid-src-1", "来源命名空间", sourceRa);

        assertThat(copy.getSchemeReference().getValue())
                .isEqualTo(GlobalNamespaceService.IMPORTED_NAMESPACE_SCHEME_PREFIX + "uuid-src-1");
        assertThat(copy.getRegistrationAuthority()).isNull(); // 副本不关联注册机构
        assertThat(copy.getNamingAuthority()).isSameAs(sourceRa); // naming_authority 记录来源机构
        assertThat(copy.getDesignations()).anyMatch(d -> "来源命名空间".equals(d.getSign().getValue()));

        // 副本的全局标识与来源方一致（同一 UUID）
        ArgumentCaptor<AdministeredItem> wrapperCaptor = ArgumentCaptor.forClass(AdministeredItem.class);
        verify(administeredItemRepository).save(wrapperCaptor.capture());
        ScopedIdentifier si = wrapperCaptor.getValue().getScopedIdentifiers().get(0);
        assertThat(si.getIdentifier()).isEqualTo("uuid-src-1");
        assertThat(si.getNamespace()).isSameAs(global);

        // 再次迁移同一来源命名空间：按共享 UUID 命中副本，不重复创建
        AdministeredItem existingWrapper = wrapperCaptor.getValue();
        when(scopedIdentifierRepository.findByNamespaceIdAndIdentifier(global.getId(), "uuid-src-1"))
                .thenReturn(Optional.of(si));
        Namespace again = service.ensureNamespaceCopy("uuid-src-1", "来源命名空间", sourceRa);
        assertThat(again).isSameAs(copy);
        verify(namespaceRepository, times(1)).save(any(Namespace.class));
        assertThat(existingWrapper.getItem()).isSameAs(copy);
    }

    @Test
    void ensureNamespaceCopy_multipleSources_shareOneGlobalNamespace() {
        Namespace global = new Namespace();
        global.setId(UUID.randomUUID());
        global.setSchemeReference(Sign.of(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME));
        when(namespaceRepository.findBySchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME))
                .thenReturn(Optional.of(global));
        when(scopedIdentifierRepository.findByNamespaceIdAndIdentifier(any(), any()))
                .thenReturn(Optional.empty());

        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());

        Namespace copyA = service.ensureNamespaceCopy("uuid-src-A", "来源A", ra);
        Namespace copyB = service.ensureNamespaceCopy("uuid-src-B", "来源B", ra);

        assertThat(copyA.getId()).isNotEqualTo(copyB.getId());

        // 两个副本的 Scoped_Identifier 挂在同一个全局命名空间下，identifier 各异
        ArgumentCaptor<AdministeredItem> wrapperCaptor = ArgumentCaptor.forClass(AdministeredItem.class);
        verify(administeredItemRepository, times(2)).save(wrapperCaptor.capture());
        List<AdministeredItem> wrappers = wrapperCaptor.getAllValues();
        assertThat(wrappers.get(0).getScopedIdentifiers().get(0).getNamespace()).isSameAs(global);
        assertThat(wrappers.get(1).getScopedIdentifiers().get(0).getNamespace()).isSameAs(global);
        assertThat(wrappers.get(0).getScopedIdentifiers().get(0).getIdentifier()).isEqualTo("uuid-src-A");
        assertThat(wrappers.get(1).getScopedIdentifiers().get(0).getIdentifier()).isEqualTo("uuid-src-B");

        // 全程只复用同一个全局命名空间，从未新建
        verify(namespaceRepository, never()).save(argThat(ns ->
                GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME.equals(ns.getSchemeReference().getValue())));
    }

    @Test
    void findNamespaceByGlobalId_locatesCopyViaSharedUuid() {
        Namespace global = new Namespace();
        global.setId(UUID.randomUUID());
        global.setSchemeReference(Sign.of(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME));
        when(namespaceRepository.findBySchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME))
                .thenReturn(Optional.of(global));

        Namespace copy = new Namespace();
        copy.setId(UUID.randomUUID());
        AdministeredItem wrapper = new AdministeredItem(copy);
        ScopedIdentifier si = new ScopedIdentifier("uuid-x", global, wrapper);
        when(scopedIdentifierRepository.findByNamespaceIdAndIdentifier(global.getId(), "uuid-x"))
                .thenReturn(Optional.of(si));

        assertThat(service.findNamespaceByGlobalId("uuid-x")).containsSame(copy);
        assertThat(service.findNamespaceByGlobalId(null)).isEmpty();
        assertThat(service.findNamespaceByGlobalId("")).isEmpty();
    }
}
