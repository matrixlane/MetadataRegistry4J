package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainType;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningSetRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖 ConceptualDomainService 对 REFERENCE_ENUMERATED 的创建 / 更新 / 响应映射路径。
 * 依据 ISO/IEC 11179-31:2023 §7.4.2.6.4，Reference_Enumerated_Conceptual_Domain
 * 无附加属性（"has no additional attributes"）。
 */
@ExtendWith(MockitoExtension.class)
class ConceptualDomainServiceTest {

    @Mock private ConceptualDomainRepository repository;
    @Mock private ValueMeaningRepository valueMeaningRepository;
    @Mock private ValueMeaningSetRepository valueMeaningSetRepository;
    @Mock private ConceptContentApplier conceptContentApplier;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;

    @InjectMocks
    private ConceptualDomainService service;

    @Test
    void create_referenceEnumerated_persistsEntity() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> {
            ConceptualDomain e = inv.getArgument(0);
            e.setId(id);
            return e;
        });

        ConceptualDomainRequest request = new ConceptualDomainRequest(
                ConceptualDomainType.REFERENCE_ENUMERATED,
                "https://example.org/external",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);

        service.create(request);

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        ConceptualDomain saved = captor.getValue();
        assertThat(saved).isInstanceOf(ReferenceEnumeratedConceptualDomain.class);
    }

    @Test
    void update_localToReference_recreatesAsReferenceEnumerated() {
        UUID id = UUID.randomUUID();
        LocalEnumeratedConceptualDomain existing = new LocalEnumeratedConceptualDomain();
        existing.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        ConceptualDomainRequest request = new ConceptualDomainRequest(
                ConceptualDomainType.REFERENCE_ENUMERATED,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);

        service.update(id, request);

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        ConceptualDomain saved = captor.getValue();
        assertThat(saved).isInstanceOf(ReferenceEnumeratedConceptualDomain.class);
    }

    @Test
    void update_referenceToLocal_recreatesAsLocalEnumerated() {
        UUID id = UUID.randomUUID();
        ReferenceEnumeratedConceptualDomain existing = new ReferenceEnumeratedConceptualDomain();
        existing.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        ConceptualDomainRequest request = new ConceptualDomainRequest(
                ConceptualDomainType.LOCAL_ENUMERATED,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);

        service.update(id, request);

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        ConceptualDomain saved = captor.getValue();
        assertThat(saved).isInstanceOf(LocalEnumeratedConceptualDomain.class);
    }

    @Test
    void create_withoutValidPeriod_defaultsStartToNow() {
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new ConceptualDomainRequest(
                null, null, List.of(), List.of(), List.of(), null, null, null));

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        // ISO/IEC 11179-31:2023 表 2:valid_period 多重性 1,未提供时默认自创建起生效
        assertThat(captor.getValue().getValidPeriod()).isNotNull();
        assertThat(captor.getValue().getValidPeriod().getStartDateTime()).isNotNull();
    }

    @Test
    void create_withValidPeriod_appliesRequestValues() {
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new ConceptualDomainRequest(
                null, null, List.of(), List.of(), List.of(),
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2026, 12, 31, 23, 59),
                null));

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValidPeriod().getStartDateTime().toLocalDateTime())
                .isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(captor.getValue().getValidPeriod().getEndDateTime().toLocalDateTime())
                .isEqualTo(java.time.LocalDateTime.of(2026, 12, 31, 23, 59));
    }

    @Test
    void create_described_defaultsToDescribedConceptualDomain() {
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> {
            ConceptualDomain e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        service.create(new ConceptualDomainRequest(
                null,
                "https://example.org",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                new ItemRegistrationRequest(null, null, null, List.of(), List.of())));

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(DescribedConceptualDomain.class);
    }
}
