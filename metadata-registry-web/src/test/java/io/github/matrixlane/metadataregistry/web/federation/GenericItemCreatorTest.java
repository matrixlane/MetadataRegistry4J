package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import java.time.LocalDateTime;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 迁入条目创建（registry-merge-strategy D2/D3）：RAI 匹配归属、来源命名空间
 * 副本、双命名空间双标识、重复并入防护与 MERGE 标记。
 */
@ExtendWith(MockitoExtension.class)
class GenericItemCreatorTest {

    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private ItemRepository itemRepository;
    @Mock private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock private LanguageIdentificationRepository languageRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.service.LanguageIdentifierResolver languageIdentifierResolver;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private ScopedIdentifierRepository scopedIdentifierRepository;
    @Mock private IdentifierVersionHistoryRepository identifierVersionHistoryRepository;
    @Mock private IdentifierStrategyConfigService identifierStrategyConfigService;
    @Mock private GlobalNamespaceService globalNamespaceService;
    @Mock private io.github.matrixlane.metadataregistry.web.service.ScopedIdentifierLifecycleService lifecycleService;
    @Mock private io.github.matrixlane.metadataregistry.web.service.NamespaceIdentifierSettingsService settingsService;

    @org.junit.jupiter.api.BeforeEach
    void stubSettingsDefaults() {
        org.mockito.Mockito.lenient().when(settingsService.getOrDefault(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings());
    }

    @InjectMocks
    private GenericItemCreator creator;

    private static final String RAI = "RAI-1";

    @Test
    void create_assignsDualIdentifiersAndMergeMarker() {
        UUID targetNamespaceId = UUID.randomUUID();
        Namespace targetNs = new Namespace();
        targetNs.setId(targetNamespaceId);
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Organization namingAuthority = new Organization();
        Namespace sourceCopy = new Namespace();
        sourceCopy.setId(UUID.randomUUID());

        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.of(ra));
        when(namespaceRepository.findById(targetNamespaceId)).thenReturn(Optional.of(targetNs));
        when(globalNamespaceService.resolveNamingAuthority("来源机构", ra)).thenReturn(namingAuthority);
        when(globalNamespaceService.ensureNamespaceCopy("uuid-1", "来源命名空间", namingAuthority))
                .thenReturn(sourceCopy);
        when(scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(sourceCopy.getId(), "X-1"))
                .thenReturn(false);
        when(itemAdministrationService.ensureAdministeredItem(
                any(Item.class), any(ItemRegistrationRequest.class), eq(ra)))
                .thenAnswer(inv -> {
                    Item item = inv.getArgument(0);
                    ItemRegistrationRequest req = inv.getArgument(1);
                    AdministeredItem ai = new AdministeredItem(item);
                    ai.setCreationDateTime(LocalDateTime.now());
                    ScopedIdentifier si = new ScopedIdentifier(req.identifier(), sourceCopy, ai);
                    si.setVersion(req.version());
                    ai.getScopedIdentifiers().add(si);
                    item.setIdentifiedItem(ai);
                    return ai;
                });
        when(identifierStrategyConfigService.generateIdentifier(targetNs, null)).thenReturn("TGT-001");
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem ai = creator.create(snapshot("DataElement"), targetNamespaceId, node());

        // 来源标识（identifier + version）在来源命名空间副本下原样写入
        ArgumentCaptor<ItemRegistrationRequest> reqCaptor =
                ArgumentCaptor.forClass(ItemRegistrationRequest.class);
        verify(itemAdministrationService).ensureAdministeredItem(
                any(Item.class), reqCaptor.capture(), eq(ra));
        ItemRegistrationRequest req = reqCaptor.getValue();
        assertThat(req.namespaceId()).isEqualTo(sourceCopy.getId());
        assertThat(req.identifier()).isEqualTo("X-1");
        assertThat(req.version()).isEqualTo("10");

        // 双命名空间双标识：来源副本下原样保留 + 目的方命名空间下新生成
        assertThat(ai.getScopedIdentifiers()).hasSize(2);
        ScopedIdentifier sourceSi = ai.getScopedIdentifiers().stream()
                .filter(si -> sourceCopy.getId().equals(si.getNamespace().getId())).findFirst().orElseThrow();
        assertThat(sourceSi.getIdentifier()).isEqualTo("X-1");
        assertThat(sourceSi.getVersion()).isEqualTo("10");
        ScopedIdentifier targetSi = ai.getScopedIdentifiers().stream()
                .filter(si -> targetNamespaceId.equals(si.getNamespace().getId())).findFirst().orElseThrow();
        assertThat(targetSi.getIdentifier()).isEqualTo("TGT-001");
        assertThat(targetSi.getVersion()).isEqualTo("1");

        // MERGE 标记记录迁移后目的方标识版本，供后续本地编辑检测
        ArgumentCaptor<IdentifierVersionHistory> historyCaptor =
                ArgumentCaptor.forClass(IdentifierVersionHistory.class);
        verify(identifierVersionHistoryRepository).save(historyCaptor.capture());
        IdentifierVersionHistory history = historyCaptor.getValue();
        assertThat(history.getScopedIdentifier()).isSameAs(targetSi);
        assertThat(history.getVersion()).isEqualTo("1");
        assertThat(history.getChangeSummary()).startsWith(MergeVersionSupport.MERGE_MARKER);
    }

    @Test
    void create_valueDomainOrConceptualDomainWithoutValidPeriod_backfillsStartToExecutionTime() {
        // enforce-valid-period D4：导入源数据缺有效日期时以执行时间补 start（表 2/表 5 兜底）
        UUID targetNamespaceId = UUID.randomUUID();
        Namespace targetNs = new Namespace();
        targetNs.setId(targetNamespaceId);
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Namespace sourceCopy = new Namespace();
        sourceCopy.setId(UUID.randomUUID());

        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.of(ra));
        when(namespaceRepository.findById(targetNamespaceId)).thenReturn(Optional.of(targetNs));
        when(globalNamespaceService.resolveNamingAuthority(any(), eq(ra)))
                .thenReturn(new Organization());
        when(globalNamespaceService.ensureNamespaceCopy(any(), any(), any()))
                .thenReturn(sourceCopy);
        when(scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(any(), any()))
                .thenReturn(false);
        when(itemAdministrationService.ensureAdministeredItem(
                any(Item.class), any(ItemRegistrationRequest.class), eq(ra)))
                .thenAnswer(inv -> {
                    Item item = inv.getArgument(0);
                    ItemRegistrationRequest req = inv.getArgument(1);
                    AdministeredItem ai = new AdministeredItem(item);
                    ai.setCreationDateTime(LocalDateTime.now());
                    ScopedIdentifier si = new ScopedIdentifier(req.identifier(), sourceCopy, ai);
                    ai.getScopedIdentifiers().add(si);
                    item.setIdentifiedItem(ai);
                    return ai;
                });
        when(identifierStrategyConfigService.generateIdentifier(any(), any())).thenReturn("TGT-001");
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        for (String itemType : List.of("DescribedValueDomain", "DescribedConceptualDomain")) {
            creator.create(snapshot(itemType), targetNamespaceId, node());
        }

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemAdministrationService, org.mockito.Mockito.times(2))
                .ensureAdministeredItem(itemCaptor.capture(), any(), eq(ra));
        for (Item item : itemCaptor.getAllValues()) {
            io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period period =
                    item instanceof io.github.matrixlane.metadataregistry.dataspecification.ValueDomain vd
                            ? vd.getValidPeriod()
                            : ((io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain) item)
                                    .getValidPeriod();
            assertThat(period).isNotNull();
            assertThat(period.getStartDateTime()).isNotNull();
        }
    }

    @Test
    void create_rejectsDuplicateIdentifierInSourceCopy() {
        UUID targetNamespaceId = UUID.randomUUID();
        Namespace targetNs = new Namespace();
        targetNs.setId(targetNamespaceId);
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Namespace sourceCopy = new Namespace();
        sourceCopy.setId(UUID.randomUUID());

        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.of(ra));
        when(namespaceRepository.findById(targetNamespaceId)).thenReturn(Optional.of(targetNs));
        when(globalNamespaceService.resolveNamingAuthority("来源机构", ra)).thenReturn(ra);
        when(globalNamespaceService.ensureNamespaceCopy("uuid-1", "来源命名空间", ra))
                .thenReturn(sourceCopy);
        when(scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(sourceCopy.getId(), "X-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> creator.create(snapshot("DataElement"), targetNamespaceId, node()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("疑似重复并入");
    }

    @Test
    void create_rejectsWhenRaiUnmatched() {
        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creator.create(snapshot("DataElement"), UUID.randomUUID(), node()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RAI 匹配的注册机构");
    }

    @Test
    void create_rejectsUnsupportedItemType() {
        assertThatThrownBy(() -> creator.create(snapshot("Nonsense"), UUID.randomUUID(), node()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不支持");
    }

    // ===== 辅助 =====

    private BundleItemSnapshot snapshot(String itemType) {
        return new BundleItemSnapshot(
                RAI + ":X-1",
                UUID.randomUUID(),
                RAI,
                "X-1",
                "10",
                10,
                new AdministeredItemExport(null, itemType, null, null, null, null, null, null,
                        null, null, null, null, null, null),
                List.of(),
                List.of(),
                "uuid-1",
                "来源命名空间",
                "来源机构"
        );
    }

    private RegistryNode node() {
        RegistryNode node = new RegistryNode();
        node.setId(UUID.randomUUID());
        node.setRai(RAI);
        return node;
    }
}
