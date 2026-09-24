package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassRequest;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassResponse;
import io.github.matrixlane.metadataregistry.web.repository.DimensionalityRepository;
import io.github.matrixlane.metadataregistry.web.repository.MeasureClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.UnitOfMeasureRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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
 * MeasureClass(ISO/IEC 11179-31:2023 §7.5.2.2)管理服务的单元测试:
 * dimensionality_measure_class(§7.5.3.1)关联维护(计量类侧 0..* 弱约束:
 * null/空允许并清空)、关联按 id 整体替换、删除保护——被
 * unit_of_measure_measure_class(§7.5.3.2)引用时拒绝删除。
 */
@ExtendWith(MockitoExtension.class)
class MeasureClassServiceTest {

    @Mock private MeasureClassRepository repository;
    @Mock private UnitOfMeasureRepository unitOfMeasureRepository;
    @Mock private DimensionalityRepository dimensionalityRepository;
    @Mock private ConceptContentApplier conceptContentApplier;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;

    @InjectMocks
    private MeasureClassService service;

    @Test
    void create_withDimensionalities_persistsAll() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(MeasureClass.class))).thenAnswer(inv -> {
            MeasureClass e = inv.getArgument(0);
            e.setId(id);
            return e;
        });
        Dimensionality d1 = new Dimensionality();
        d1.setId(UUID.randomUUID());
        d1.setMeasureClasses(new ArrayList<>());
        Dimensionality d2 = new Dimensionality();
        d2.setId(UUID.randomUUID());
        d2.setMeasureClasses(new ArrayList<>());
        when(dimensionalityRepository.findAllById(any())).thenReturn(List.of(d1, d2));

        MeasureClassRequest request = new MeasureClassRequest(
                null, List.of(), List.of(), null,
                List.of(d1.getId(), d2.getId()));

        MeasureClassResponse response = service.create(request);

        assertThat(response.dimensionalities()).hasSize(2);
        assertThat(response.dimensionalities().stream().map(r -> r.id()))
                .containsExactlyInAnyOrder(d1.getId(), d2.getId());

        ArgumentCaptor<MeasureClass> savedCaptor = ArgumentCaptor.forClass(MeasureClass.class);
        verify(repository).save(savedCaptor.capture());
        MeasureClass savedEntity = savedCaptor.getValue();
        assertThat(d1.getMeasureClasses()).containsExactly(savedEntity);
        assertThat(d2.getMeasureClasses()).containsExactly(savedEntity);
        verify(dimensionalityRepository).saveAll(any());
    }

    @Test
    void create_withoutDimensionalities_doesNotTouchDimensionalities() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(MeasureClass.class))).thenAnswer(inv -> {
            MeasureClass e = inv.getArgument(0);
            e.setId(id);
            return e;
        });

        MeasureClassResponse response = service.create(
                new MeasureClassRequest(null, List.of(), List.of(), null, null));

        assertThat(response.id()).isEqualTo(id);
        verify(dimensionalityRepository, never()).findAllById(any());
        verify(dimensionalityRepository, never()).saveAll(any());
    }

    @Test
    void create_withUnknownDimensionalityId_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(dimensionalityRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(
                new MeasureClassRequest(null, List.of(), List.of(), null, List.of(unknown))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Dimensionality")
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void update_replacesDimensionalities() {
        UUID id = UUID.randomUUID();
        MeasureClass existing = new MeasureClass();
        existing.setId(id);
        Dimensionality d1 = new Dimensionality();
        d1.setId(UUID.randomUUID());
        d1.setMeasureClasses(new ArrayList<>(List.of(existing)));
        existing.setDimensionalities(new ArrayList<>(List.of(d1)));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(MeasureClass.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dimensionalityRepository.findByMeasureClasses_Id(id)).thenReturn(List.of(d1));

        Dimensionality d2 = new Dimensionality();
        d2.setId(UUID.randomUUID());
        d2.setMeasureClasses(new ArrayList<>());
        when(dimensionalityRepository.findAllById(List.of(d2.getId()))).thenReturn(List.of(d2));

        service.update(id, new MeasureClassRequest(
                null, List.of(), List.of(), null, List.of(d2.getId())));

        assertThat(existing.getDimensionalities()).containsExactly(d2);
        assertThat(d1.getMeasureClasses()).doesNotContain(existing);
        assertThat(d2.getMeasureClasses()).containsExactly(existing);
        verify(dimensionalityRepository).saveAll(any());
    }

    @Test
    void update_withEmptyDimensionalities_clearsAssociation() {
        UUID id = UUID.randomUUID();
        MeasureClass existing = new MeasureClass();
        existing.setId(id);
        Dimensionality d1 = new Dimensionality();
        d1.setId(UUID.randomUUID());
        d1.setMeasureClasses(new ArrayList<>(List.of(existing)));
        existing.setDimensionalities(new ArrayList<>(List.of(d1)));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(MeasureClass.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dimensionalityRepository.findByMeasureClasses_Id(id)).thenReturn(List.of(d1));

        service.update(id, new MeasureClassRequest(
                null, List.of(), List.of(), null, List.of()));

        assertThat(existing.getDimensionalities()).isEmpty();
        assertThat(d1.getMeasureClasses()).doesNotContain(existing);
        ArgumentCaptor<List<Dimensionality>> savedDimsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dimensionalityRepository).saveAll(savedDimsCaptor.capture());
        assertThat(savedDimsCaptor.getValue()).containsExactly(d1);
    }

    @Test
    void delete_referencedByUnitOfMeasure_throwsConflict() {
        UUID id = UUID.randomUUID();
        MeasureClass entity = new MeasureClass();
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(unitOfMeasureRepository.countByMeasureClasses_Id(id)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ApiCodes.CONFLICT))
                .hasMessageContaining("2");

        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_unreferenced_deletes() {
        UUID id = UUID.randomUUID();
        MeasureClass entity = new MeasureClass();
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(unitOfMeasureRepository.countByMeasureClasses_Id(id)).thenReturn(0L);

        service.delete(id);

        verify(repository).deleteById(id);
    }
}
