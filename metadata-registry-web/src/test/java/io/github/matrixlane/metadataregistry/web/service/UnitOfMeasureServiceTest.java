package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.unitofmeasure.UnitOfMeasureRequest;
import io.github.matrixlane.metadataregistry.web.dto.unitofmeasure.UnitOfMeasureResponse;
import io.github.matrixlane.metadataregistry.web.repository.MeasureClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.UnitOfMeasureRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UnitOfMeasure(ISO/IEC 11179-31:2023 §7.5.2.4)管理服务的单元测试:
 * §7.5.3.2 unit_of_measure_measure_class 关联维护(计量单位侧 1..* 强约束:
 * null/空拒绝)、关联按 id 整体替换。
 */
@ExtendWith(MockitoExtension.class)
class UnitOfMeasureServiceTest {

    @Mock private UnitOfMeasureRepository repository;
    @Mock private MeasureClassRepository measureClassRepository;
    @Mock private ConceptContentApplier conceptContentApplier;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;

    @InjectMocks
    private UnitOfMeasureService service;

    @Test
    void create_withMeasureClasses_persistsAll() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(UnitOfMeasure.class))).thenAnswer(inv -> {
            UnitOfMeasure e = inv.getArgument(0);
            e.setId(id);
            return e;
        });
        MeasureClass mc1 = new MeasureClass();
        mc1.setId(UUID.randomUUID());
        MeasureClass mc2 = new MeasureClass();
        mc2.setId(UUID.randomUUID());
        when(measureClassRepository.findAllById(any())).thenReturn(List.of(mc1, mc2));

        UnitOfMeasureRequest request = new UnitOfMeasureRequest(
                null, List.of(), List.of(), null,
                List.of(mc1.getId(), mc2.getId()));

        UnitOfMeasureResponse response = service.create(request);

        assertThat(response.measureClasses()).hasSize(2);
        assertThat(response.measureClasses().stream().map(r -> r.id()))
                .containsExactlyInAnyOrder(mc1.getId(), mc2.getId());
        verify(repository).save(any(UnitOfMeasure.class));
    }

    @Test
    void create_withoutMeasureClasses_throwsValidationFailed() {
        assertThatThrownBy(() -> service.create(
                new UnitOfMeasureRequest(null, List.of(), List.of(), null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("计量单位必须至少归属一个计量类");

        assertThatThrownBy(() -> service.create(
                new UnitOfMeasureRequest(null, List.of(), List.of(), null, List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("计量单位必须至少归属一个计量类");
    }

    @Test
    void create_withUnknownMeasureClassId_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(measureClassRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(
                new UnitOfMeasureRequest(null, List.of(), List.of(), null, List.of(unknown))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_replacesMeasureClasses() {
        UUID id = UUID.randomUUID();
        UnitOfMeasure existing = new UnitOfMeasure();
        existing.setId(id);
        MeasureClass old = new MeasureClass();
        old.setId(UUID.randomUUID());
        existing.setMeasureClasses(new java.util.ArrayList<>(List.of(old)));
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(UnitOfMeasure.class))).thenAnswer(inv -> inv.getArgument(0));
        MeasureClass fresh = new MeasureClass();
        fresh.setId(UUID.randomUUID());
        when(measureClassRepository.findAllById(any())).thenReturn(List.of(fresh));

        service.update(id, new UnitOfMeasureRequest(
                null, List.of(), List.of(), null, List.of(fresh.getId())));

        assertThat(existing.getMeasureClasses()).containsExactly(fresh);
    }

    @Test
    void update_withEmptyMeasureClasses_throwsValidationFailed() {
        UUID id = UUID.randomUUID();
        UnitOfMeasure existing = new UnitOfMeasure();
        existing.setId(id);
        MeasureClass old = new MeasureClass();
        old.setId(UUID.randomUUID());
        existing.setMeasureClasses(new java.util.ArrayList<>(List.of(old)));
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(
                id, new UnitOfMeasureRequest(null, List.of(), List.of(), null, List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("计量单位必须至少归属一个计量类");
    }

    @Test
    void get_mapsMeasureClasses() {
        UUID id = UUID.randomUUID();
        UnitOfMeasure entity = new UnitOfMeasure();
        entity.setId(id);
        MeasureClass mc = new MeasureClass();
        mc.setId(UUID.randomUUID());
        entity.setMeasureClasses(List.of(mc));
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        UnitOfMeasureResponse response = service.get(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.measureClasses()).hasSize(1);
        assertThat(response.measureClasses().get(0).id()).isEqualTo(mc.getId());
    }

    @Test
    void list_mapsMeasureClassLabels() {
        UnitOfMeasure uom = new UnitOfMeasure();
        uom.setId(UUID.randomUUID());
        MeasureClass mc = new MeasureClass();
        mc.setId(UUID.randomUUID());
        uom.setMeasureClasses(List.of(mc));
        when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(uom)));

        Page<UnitOfMeasureResponse> page = service.list(null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).measureClasses().get(0).id()).isEqualTo(mc.getId());
    }
}
