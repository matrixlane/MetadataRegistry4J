package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖 ISO/IEC 11179-31:2023 §7.4.2 Reference_Enumerated_Conceptual_Domain 必填 permitted_values_fieldname
 * 以及 ConceptualDomainService 对 REFERENCE_ENUMERATED 的创建 / 更新 / 响应映射路径。
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
    void create_referenceEnumerated_missingFieldname_throwsBusinessException() {
        ConceptualDomainRequest request = new ConceptualDomainRequest(
                ConceptualDomainType.REFERENCE_ENUMERATED,
                null,
                "  ",
                List.of(),
                List.of(),
                List.of(),
                null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("permittedValuesFieldname");

        verify(repository, never()).save(any());
    }

    @Test
    void create_referenceEnumerated_withFieldname_persistsEntity() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> {
            ConceptualDomain e = inv.getArgument(0);
            e.setId(id);
            return e;
        });

        ConceptualDomainRequest request = new ConceptualDomainRequest(
                ConceptualDomainType.REFERENCE_ENUMERATED,
                "https://example.org/external",
                "code",
                List.of(),
                List.of(),
                List.of(),
                null);

        service.create(request);

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        ConceptualDomain saved = captor.getValue();
        assertThat(saved).isInstanceOf(ReferenceEnumeratedConceptualDomain.class);
        assertThat(((ReferenceEnumeratedConceptualDomain) saved).getPermittedValuesFieldname()).isEqualTo("code");
    }

    @Test
    void update_localToReference_clearsValueMeaningLinksAndSetsFieldname() {
        UUID id = UUID.randomUUID();
        LocalEnumeratedConceptualDomain existing = new LocalEnumeratedConceptualDomain();
        existing.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        ConceptualDomainRequest request = new ConceptualDomainRequest(
                ConceptualDomainType.REFERENCE_ENUMERATED,
                null,
                "code",
                List.of(),
                List.of(),
                List.of(),
                null);

        service.update(id, request);

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        ConceptualDomain saved = captor.getValue();
        assertThat(saved).isInstanceOf(ReferenceEnumeratedConceptualDomain.class);
        assertThat(((ReferenceEnumeratedConceptualDomain) saved).getPermittedValuesFieldname()).isEqualTo("code");
    }

    @Test
    void update_referenceToLocal_clearsFieldname() {
        UUID id = UUID.randomUUID();
        ReferenceEnumeratedConceptualDomain existing = new ReferenceEnumeratedConceptualDomain();
        existing.setId(id);
        existing.setPermittedValuesFieldname("code");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        ConceptualDomainRequest request = new ConceptualDomainRequest(
                ConceptualDomainType.LOCAL_ENUMERATED,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null);

        service.update(id, request);

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        ConceptualDomain saved = captor.getValue();
        assertThat(saved).isInstanceOf(LocalEnumeratedConceptualDomain.class);
        assertThat(((ReferenceEnumeratedConceptualDomain) existing).getPermittedValuesFieldname()).isNull();
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
                null,
                List.of(),
                List.of(),
                List.of(),
                new ItemRegistrationRequest(null, null, null, List.of(), List.of())));

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(DescribedConceptualDomain.class);
    }
}
