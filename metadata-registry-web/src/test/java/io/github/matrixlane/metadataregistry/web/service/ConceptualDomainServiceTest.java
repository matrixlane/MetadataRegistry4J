package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
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
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
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
        Datetime_Period existingPeriod = new Datetime_Period();
        existingPeriod.setStartDateTime(java.time.OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC));
        existing.setValidPeriod(existingPeriod);
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
        Datetime_Period existingPeriod = new Datetime_Period();
        existingPeriod.setStartDateTime(java.time.OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC));
        existing.setValidPeriod(existingPeriod);
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
    void create_withoutValidPeriod_throwsValidationFailed() {
        // ISO/IEC 11179-31:2023 表 2 valid_period 基数为 1：缺起始时间必须拦下
        // （enforce-valid-period 取代旧「默认自创建起生效」行为；导入兜底见 D4）
        assertThatThrownBy(() -> service.create(new ConceptualDomainRequest(
                null, null, List.of(), List.of(), List.of(), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("有效日期（起始时间）");
    }

    @Test
    void update_endWithoutStart_throwsValidationFailed() {
        UUID id = UUID.randomUUID();
        DescribedConceptualDomain existing = new DescribedConceptualDomain();
        existing.setId(id);
        Datetime_Period existingPeriod = new Datetime_Period();
        existingPeriod.setStartDateTime(java.time.OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC));
        existing.setValidPeriod(existingPeriod);
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        // 提供了结束但未提供起始：拒绝（不得把既有 start 清空）
        assertThatThrownBy(() -> service.update(id, new ConceptualDomainRequest(
                null, null, List.of(), List.of(), List.of(),
                null, java.time.LocalDateTime.of(2026, 12, 31, 23, 59), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("有效日期（起始时间）");
    }

    @Test
    void update_withoutValidPeriod_keepsExistingPeriod() {
        // 更新未带有效期参数：保留既有（已回填的）有效期，不因校验受阻
        UUID id = UUID.randomUUID();
        DescribedConceptualDomain existing = new DescribedConceptualDomain();
        existing.setId(id);
        Datetime_Period existingPeriod = new Datetime_Period();
        existingPeriod.setStartDateTime(java.time.OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC));
        existing.setValidPeriod(existingPeriod);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ConceptualDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new ConceptualDomainRequest(
                null, null, List.of(), List.of(), List.of(), null, null, null));

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getValidPeriod().getStartDateTime())
                .isEqualTo(existingPeriod.getStartDateTime());
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
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                null,
                new ItemRegistrationRequest(null, null, null, List.of(), List.of())));

        ArgumentCaptor<ConceptualDomain> captor = ArgumentCaptor.forClass(ConceptualDomain.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(DescribedConceptualDomain.class);
    }
}
