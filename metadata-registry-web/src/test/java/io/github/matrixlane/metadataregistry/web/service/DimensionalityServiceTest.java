package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityRequest;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityResponse;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DimensionalityRepository;
import io.github.matrixlane.metadataregistry.web.repository.MeasureClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.lenient;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

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
 * Dimensionality(ISO/IEC 11179-31:2023 §7.5.2.3)管理服务的单元测试:
 * Table 22 coordinate_indicator 三态透传、dimensionality_measure_class(§7.5.3.1)
 * 关联维护、删除保护(被 conceptual_domain 引用时拒绝)。
 */
@ExtendWith(MockitoExtension.class)
class DimensionalityServiceTest {

    @Mock private DimensionalityRepository repository;
    @Mock private MeasureClassRepository measureClassRepository;
    @Mock private ConceptualDomainRepository conceptualDomainRepository;
    @Mock private ConceptContentApplier conceptContentApplier;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private SubmissionRecordRepository submissionRecordRepository;
    @Mock private DatabaseEnvironment databaseEnvironment;
    @Mock private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DimensionalityService service;

    @Test
    void create_withCoordinateTrueAndMeasureClasses_persistsAll() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(Dimensionality.class))).thenAnswer(inv -> {
            Dimensionality e = inv.getArgument(0);
            e.setId(id);
            return e;
        });
        MeasureClass mc1 = new MeasureClass();
        mc1.setId(UUID.randomUUID());
        MeasureClass mc2 = new MeasureClass();
        mc2.setId(UUID.randomUUID());
        when(measureClassRepository.findAllById(any())).thenReturn(List.of(mc1, mc2));

        DimensionalityRequest request = new DimensionalityRequest(
                null, List.of(), List.of(), null, Boolean.TRUE,
                List.of(mc1.getId(), mc2.getId()));

        DimensionalityResponse response = service.create(request);

        assertThat(response.coordinateIndicator()).isTrue();
        assertThat(response.measureClasses()).hasSize(2);
        assertThat(response.measureClasses().stream().map(r -> r.id()))
                .containsExactlyInAnyOrder(mc1.getId(), mc2.getId());
        org.mockito.Mockito.verify(repository).save(any(Dimensionality.class));
    }

    @Test
    void create_withoutCoordinateIndicator_staysNull() {
        when(repository.save(any(Dimensionality.class))).thenAnswer(inv -> inv.getArgument(0));

        DimensionalityResponse response = service.create(
                new DimensionalityRequest(null, List.of(), List.of(), null, null, List.of()));

        assertThat(response.coordinateIndicator()).isNull();
    }

    @Test
    void create_withUnknownMeasureClassId_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(measureClassRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(
                new DimensionalityRequest(null, List.of(), List.of(), null, null, List.of(unknown))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_replacesMeasureClasses() {
        UUID id = UUID.randomUUID();
        Dimensionality existing = new Dimensionality();
        existing.setId(id);
        MeasureClass old = new MeasureClass();
        old.setId(UUID.randomUUID());
        existing.setMeasureClasses(new java.util.ArrayList<>(List.of(old)));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Dimensionality.class))).thenAnswer(inv -> inv.getArgument(0));
        MeasureClass fresh = new MeasureClass();
        fresh.setId(UUID.randomUUID());
        when(measureClassRepository.findAllById(any())).thenReturn(List.of(fresh));

        service.update(id, new DimensionalityRequest(
                null, List.of(), List.of(), null, Boolean.FALSE, List.of(fresh.getId())));

        assertThat(existing.getMeasureClasses()).containsExactly(fresh);
        assertThat(existing.getCoordinateIndicator()).isFalse();
    }

    @Test
    void update_withEmptyMeasureClasses_clearsAssociation() {
        UUID id = UUID.randomUUID();
        Dimensionality existing = new Dimensionality();
        existing.setId(id);
        MeasureClass old = new MeasureClass();
        old.setId(UUID.randomUUID());
        existing.setMeasureClasses(new java.util.ArrayList<>(List.of(old)));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Dimensionality.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new DimensionalityRequest(null, List.of(), List.of(), null, null, List.of()));

        assertThat(existing.getMeasureClasses()).isEmpty();
    }

    @Test
    void delete_referencedByConceptualDomain_throwsConflict() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(new Dimensionality()));
        when(conceptualDomainRepository.countByDimensionality_Id(id)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.CONFLICT))
                .hasMessageContaining("2");

        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_unreferenced_deletes() {
        UUID id = UUID.randomUUID();
        Dimensionality entity = new Dimensionality();
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(conceptualDomainRepository.countByDimensionality_Id(id)).thenReturn(0L);

        service.delete(id);

        verify(itemAdministrationService).removeFromSubmissionRecords(entity);
        verify(repository).deleteById(id);
    }

    @Test
    void delete_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_mapsMeasureClassLabels() {
        Dimensionality d = new Dimensionality();
        d.setId(UUID.randomUUID());
        MeasureClass mc = new MeasureClass();
        mc.setId(UUID.randomUUID());
        d.setMeasureClasses(List.of(mc));
        when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(d)));

        Page<DimensionalityResponse> page = service.list(null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).measureClasses().get(0).id()).isEqualTo(mc.getId());
    }
}
