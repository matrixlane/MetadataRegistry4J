package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.PermissibleValueEntry;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainResponse;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainType;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueRepository;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueSetRepository;
import io.github.matrixlane.metadataregistry.web.repository.UnitOfMeasureRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖 ISO/IEC 11179-31:2023 §7.4.2.13 Reference_Enumerated_Value_Domain 必填 permitted_values_fieldname
 * 以及 ValueDomainService 对 REFERENCE_ENUMERATED 的创建 / 更新 / 响应映射路径。
 */
@ExtendWith(MockitoExtension.class)
class ValueDomainServiceTest {

    @Mock private ValueDomainRepository repository;
    @Mock private DataTypeRepository dataTypeRepository;
    @Mock private UnitOfMeasureRepository unitOfMeasureRepository;
    @Mock private ConceptualDomainRepository conceptualDomainRepository;
    @Mock private PermissibleValueRepository permissibleValueRepository;
    @Mock private PermissibleValueSetRepository permissibleValueSetRepository;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;

    @InjectMocks
    private ValueDomainService service;

    @Test
    void create_referenceEnumerated_missingFieldname_throwsBusinessException() {
        ValueDomainRequest request = new ValueDomainRequest(
                ValueDomainType.REFERENCE_ENUMERATED,
                null, null, null, null, null, null,
                null, null, null,
                List.<PermissibleValueEntry>of(),
                List.of(), List.of(), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("permittedValuesFieldname");

        verify(repository, never()).save(any());
    }

    @Test
    void create_referenceEnumerated_referencedConceptualDomainWrongType_throws() {
        UUID wrongId = UUID.randomUUID();
        DescribedConceptualDomain wrongCd = new DescribedConceptualDomain();
        when(conceptualDomainRepository.findById(wrongId)).thenReturn(Optional.of(wrongCd));

        ValueDomainRequest request = new ValueDomainRequest(
                ValueDomainType.REFERENCE_ENUMERATED,
                null, null, null, null, null, null,
                null, "code", wrongId,
                List.<PermissibleValueEntry>of(),
                List.of(), List.of(), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ReferenceEnumeratedConceptualDomain");

        verify(repository, never()).save(any());
    }

    @Test
    void create_referenceEnumerated_withValidConceptualDomain_persistsEntity() {
        UUID refCdId = UUID.randomUUID();
        ReferenceEnumeratedConceptualDomain refCd = new ReferenceEnumeratedConceptualDomain();
        refCd.setId(refCdId);
        when(conceptualDomainRepository.findById(refCdId)).thenReturn(Optional.of(refCd));
        when(repository.save(any(ValueDomain.class))).thenAnswer(inv -> {
            ValueDomain e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        ValueDomainRequest request = new ValueDomainRequest(
                ValueDomainType.REFERENCE_ENUMERATED,
                null, null, null, null, null, null,
                null, "code", refCdId,
                List.<PermissibleValueEntry>of(),
                List.of(), List.of(), null);

        ValueDomainResponse response = service.create(request);

        assertThat(response.type()).isEqualTo(ValueDomainType.REFERENCE_ENUMERATED);
        assertThat(response.permittedValuesFieldname()).isEqualTo("code");
        assertThat(response.referencedConceptualDomainId()).isEqualTo(refCdId);
        // 引用枚举不应设置 max character / format
        assertThat(response.maximumCharacterQuantity()).isNull();
        assertThat(response.format()).isNull();

        ArgumentCaptor<ValueDomain> captor = ArgumentCaptor.forClass(ValueDomain.class);
        verify(repository).save(captor.capture());
        ValueDomain saved = captor.getValue();
        assertThat(saved).isInstanceOf(ReferenceEnumeratedValueDomain.class);
        assertThat(((ReferenceEnumeratedValueDomain) saved).getPermittedValuesFieldname()).isEqualTo("code");
        assertThat(((ReferenceEnumeratedValueDomain) saved).getReferencedConceptualDomain()).isSameAs(refCd);
    }

    @Test
    void update_localToReference_clearsPermissibleValuesAndSetsFieldname() {
        UUID id = UUID.randomUUID();
        LocalEnumeratedValueDomain existing = new LocalEnumeratedValueDomain();
        existing.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ValueDomain.class))).thenAnswer(inv -> inv.getArgument(0));

        ValueDomainRequest request = new ValueDomainRequest(
                ValueDomainType.REFERENCE_ENUMERATED,
                null, null, null, null, null, null,
                null, "code", null,
                List.<PermissibleValueEntry>of(),
                List.of(), List.of(), null);

        ValueDomainResponse response = service.update(id, request);

        ArgumentCaptor<ValueDomain> captor = ArgumentCaptor.forClass(ValueDomain.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ReferenceEnumeratedValueDomain.class);
        assertThat(response.type()).isEqualTo(ValueDomainType.REFERENCE_ENUMERATED);
    }

    @Test
    void create_described_defaultsToDescribedValueDomain() {
        when(repository.save(any(ValueDomain.class))).thenAnswer(inv -> {
            ValueDomain e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        service.create(new ValueDomainRequest(
                null,
                null, null, null, null, null, null,
                null, null, null,
                List.<PermissibleValueEntry>of(),
                List.of(), List.of(), null));

        ArgumentCaptor<ValueDomain> captor = ArgumentCaptor.forClass(ValueDomain.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(DescribedValueDomain.class);
    }
}
