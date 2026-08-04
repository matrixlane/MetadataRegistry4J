package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.service.MetadataExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合并包构建（registry-merge-strategy）：RAI 前置、候选条目过滤、来源身份
 * 选择与命名空间映射信息富化。
 */
@ExtendWith(MockitoExtension.class)
class FederationBundleServiceTest {

    @Mock private RegistryNodeService registryNodeService;
    @Mock private SyncWatermarkRepository watermarkRepository;
    @Mock private MergeRequestRepository mergeRequestRepository;
    @Mock private AdministeredItemRepository administeredItemRepository;
    @Mock private VersionHistoryRepository versionHistoryRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private DefinitionRepository definitionRepository;
    @Mock private MetadataExportService exportService;
    @Mock private ObjectMapper objectMapper;
    @Mock private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock private GlobalNamespaceService globalNamespaceService;

    @InjectMocks
    private FederationBundleService service;

    private RegistryNode node(String rai) {
        RegistryNode node = new RegistryNode();
        node.setId(UUID.randomUUID());
        node.setRai(rai);
        return node;
    }

    @Test
    void createBundle_rejectsWhenTargetRaiUnmatched() {
        // RAI 一致性前置（D1，发起侧）：目的节点 RAI 在本实例匹配不到注册机构 → 拒绝
        RegistryNode node = node("RAI-OTHER");
        when(registryNodeService.getEnabled(node.getId())).thenReturn(node);
        when(registrationAuthorityRepository.findByRai("RAI-OTHER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBundle(node.getId(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RAI 不一致");

        verify(administeredItemRepository, never()).findAll();
        verify(mergeRequestRepository, never()).save(any());
    }

    @Test
    void collectItems_excludesNamespaceWrappers() {
        // 命名空间包装物（全局命名空间映射簿记）不是可注册内容，一律排除
        AdministeredItem normal = new AdministeredItem(new DataElement());
        AdministeredItem namespaceWrapper = new AdministeredItem(new Namespace());
        when(administeredItemRepository.findAll()).thenReturn(List.of(normal, namespaceWrapper));

        List<AdministeredItem> result = service.collectItems(null);

        assertThat(result).containsExactly(normal);
    }

    @Test
    void collectItems_incrementalAlsoExcludesNamespaceWrappers() {
        AdministeredItem normal = new AdministeredItem(new DataElement());
        AdministeredItem namespaceWrapper = new AdministeredItem(new Namespace());
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        when(administeredItemRepository.findChangedAfter(since))
                .thenReturn(List.of(normal, namespaceWrapper));

        List<AdministeredItem> result = service.collectItems(since);

        assertThat(result).containsExactly(normal);
    }

    @Test
    void collectItems_excludesRegistrySpecification() {
        // 注册规范描述注册表自身运行环境，RAI 一致即同一注册表，目的方保留
        // 一份即可，不参与迁移（design Open Questions 结论 (a)）
        AdministeredItem normal = new AdministeredItem(new DataElement());
        AdministeredItem registrySpec = new AdministeredItem(new RegistrySpecification());
        when(administeredItemRepository.findAll()).thenReturn(List.of(normal, registrySpec));

        List<AdministeredItem> result = service.collectItems(null);

        assertThat(result).containsExactly(normal);
    }

    @Test
    void toSnapshot_prefersRaOwnIdentifierAndEnrichesNamespaceMapping() {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Organization namingAuthority = new Organization();
        namingAuthority.getName().add(Sign.of("真实来源机构"));

        Namespace raNs = new Namespace();
        raNs.setId(UUID.randomUUID());
        raNs.setRegistrationAuthority(ra);
        raNs.setNamingAuthority(namingAuthority);
        addZhDesignation(raNs, "自有命名空间");

        DataElement item = new DataElement();
        item.setId(UUID.randomUUID());
        AdministeredItem ai = new AdministeredItem(item);
        ai.setId(UUID.randomUUID());
        ScopedIdentifier raSi = new ScopedIdentifier("OWN-1", raNs, ai);
        raSi.setVersion("7");
        ai.getScopedIdentifiers().add(raSi);
        // 全局命名空间下的标识不作为来源身份
        Namespace global = new Namespace();
        global.setId(UUID.randomUUID());
        global.setSchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME);
        ai.getScopedIdentifiers().add(new ScopedIdentifier("glob-1", global, ai));

        RegistryNode node = node("RAI-1");
        AdministeredItemExport export = new AdministeredItemExport(
                null, "DataElement", null, null, null, null, null, null,
                null, null, null, null, null, null);
        when(globalNamespaceService.ensureNamespaceGlobalIdentifier(raNs)).thenReturn("uuid-ns-1");
        when(exportService.exportItem(ai)).thenReturn(export);
        when(designationRepository.findByItem_Id(item.getId())).thenReturn(List.of());
        when(definitionRepository.findByItem_Id(item.getId())).thenReturn(List.of());

        BundleItemSnapshot snapshot = service.toSnapshot(ai, node, Map.of(ai.getId(), 3));

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.remoteItemKey()).isEqualTo("RAI-1:OWN-1");
        assertThat(snapshot.identifier()).isEqualTo("OWN-1");
        assertThat(snapshot.version()).isEqualTo("7");
        assertThat(snapshot.versionNumber()).isEqualTo(3);
        assertThat(snapshot.sourceNamespaceGlobalId()).isEqualTo("uuid-ns-1");
        assertThat(snapshot.sourceNamespaceName()).isEqualTo("自有命名空间");
        assertThat(snapshot.sourceNamespaceNamingAuthorityName()).isEqualTo("真实来源机构");
    }

    @Test
    void toSnapshot_fallsBackToExternalNamespaceIdentifier() {
        // 无注册机构自有命名空间标识时，取外部（非全局、非迁入副本）命名空间标识
        Namespace externalNs = new Namespace();
        externalNs.setId(UUID.randomUUID());
        externalNs.setSchemeReference("urn:example:external-standard");

        DataElement item = new DataElement();
        item.setId(UUID.randomUUID());
        AdministeredItem ai = new AdministeredItem(item);
        ai.setId(UUID.randomUUID());
        ScopedIdentifier extSi = new ScopedIdentifier("EXT-1", externalNs, ai);
        extSi.setVersion("2");
        ai.getScopedIdentifiers().add(extSi);
        // 迁入副本命名空间下的标识同样不作为来源身份
        Namespace importedNs = new Namespace();
        importedNs.setId(UUID.randomUUID());
        importedNs.setSchemeReference(GlobalNamespaceService.IMPORTED_NAMESPACE_SCHEME_PREFIX + "uuid-x");
        ai.getScopedIdentifiers().add(new ScopedIdentifier("IMP-1", importedNs, ai));

        RegistryNode node = node("RAI-1");
        when(globalNamespaceService.ensureNamespaceGlobalIdentifier(externalNs)).thenReturn("uuid-ext");
        when(exportService.exportItem(ai)).thenReturn(null);
        when(designationRepository.findByItem_Id(item.getId())).thenReturn(List.of());
        when(definitionRepository.findByItem_Id(item.getId())).thenReturn(List.of());

        BundleItemSnapshot snapshot = service.toSnapshot(ai, node, Map.of());

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.remoteItemKey()).isEqualTo("RAI-1:EXT-1");
        assertThat(snapshot.identifier()).isEqualTo("EXT-1");
    }

    @Test
    void toSnapshot_skipsItemWithoutUsableSourceIdentifier() {
        // 无标识 → 跳过；仅全局/迁入副本命名空间标识 → 同样跳过
        DataElement item = new DataElement();
        item.setId(UUID.randomUUID());
        AdministeredItem noIdentifier = new AdministeredItem(item);
        assertThat(service.toSnapshot(noIdentifier, node("RAI-1"), Map.of())).isNull();

        AdministeredItem globalOnly = new AdministeredItem(item);
        Namespace global = new Namespace();
        global.setId(UUID.randomUUID());
        global.setSchemeReference(GlobalNamespaceService.GLOBAL_NAMESPACE_SCHEME);
        globalOnly.getScopedIdentifiers().add(new ScopedIdentifier("glob-1", global, globalOnly));
        assertThat(service.toSnapshot(globalOnly, node("RAI-1"), Map.of())).isNull();
    }

    private void addZhDesignation(Namespace ns, String text) {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setId(UUID.randomUUID());
        lang.setLanguageIdentifier("zh-CN");
        Designation designation = new Designation();
        designation.setItem(ns);
        designation.setSign(Sign.of(text));
        designation.setLanguage(lang);
        ns.getDesignations().add(designation);
    }
}
