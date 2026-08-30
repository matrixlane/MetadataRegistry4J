package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ISO/IEC 11179-31:2023 中 data_element_meaning（数据元→数据元概念）与
 * data_element_domain（数据元→值域）关联的下界均为 0（可选），
 * 这些用例锁定后端对该可选性的支持。
 */
@ExtendWith(MockitoExtension.class)
class DataElementServiceTest {

    @Mock private DataElementRepository repository;
    @Mock private DataElementConceptRepository dataElementConceptRepository;
    @Mock private ValueDomainRepository valueDomainRepository;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;

    @InjectMocks
    private DataElementService service;

    @Test
    void create_withoutConceptAndValueDomain_succeedsWithNullAssociations() {
        DataElementRequest request = new DataElementRequest(null, null, null, List.of(), null, null);
        when(repository.save(any(DataElement.class))).thenAnswer(inv -> inv.getArgument(0));

        DataElementResponse response = service.create(request);

        ArgumentCaptor<DataElement> captor = ArgumentCaptor.forClass(DataElement.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDataElementConcept()).isNull();
        assertThat(captor.getValue().getValueDomain()).isNull();
        assertThat(response.dataElementConceptId()).isNull();
        assertThat(response.valueDomainId()).isNull();
        verifyNoInteractions(dataElementConceptRepository, valueDomainRepository);
    }

    @Test
    void create_withUnknownConceptId_throwsNotFound() {
        UUID decId = UUID.randomUUID();
        when(dataElementConceptRepository.findById(decId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new DataElementRequest(decId, null, null, List.of(), null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withPrecision_roundTripsValue() {
        // ISO/IEC 11179-31:2023 §7.6.2.1.4 Table 23：precision，0..1，Integer，
        // 关联数据元值允许的小数位数
        DataElementRequest request = new DataElementRequest(null, null, 2, List.of(), null, null);
        when(repository.save(any(DataElement.class))).thenAnswer(inv -> inv.getArgument(0));

        DataElementResponse response = service.create(request);

        ArgumentCaptor<DataElement> captor = ArgumentCaptor.forClass(DataElement.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPrecision()).isEqualTo(2);
        assertThat(response.precision()).isEqualTo(2);
    }

    @Test
    void update_withNullPrecision_clearsExistingValue() {
        UUID id = UUID.randomUUID();
        DataElement entity = new DataElement();
        entity.setPrecision(4);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(DataElement.class))).thenAnswer(inv -> inv.getArgument(0));

        DataElementResponse response = service.update(id,
                new DataElementRequest(null, null, null, List.of(), null, null));

        assertThat(entity.getPrecision()).isNull();
        assertThat(response.precision()).isNull();
    }

    @Test
    void update_withNullConceptAndValueDomain_clearsExistingAssociations() {
        UUID id = UUID.randomUUID();
        DataElement entity = new DataElement();
        entity.setDataElementConcept(new DataElementConcept());
        entity.setValueDomain(new ValueDomain());
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(DataElement.class))).thenAnswer(inv -> inv.getArgument(0));

        DataElementResponse response = service.update(id,
                new DataElementRequest(null, null, null, List.of(), null, null));

        assertThat(entity.getDataElementConcept()).isNull();
        assertThat(entity.getValueDomain()).isNull();
        assertThat(response.dataElementConceptId()).isNull();
        assertThat(response.valueDomainId()).isNull();
    }
}
