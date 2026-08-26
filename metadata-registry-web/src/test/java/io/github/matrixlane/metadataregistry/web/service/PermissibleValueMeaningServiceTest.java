package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValue;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueMeaning;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaning;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning.PermissibleValueMeaningRequest;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueMeaningRepository;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * unify-item-identifier-version-editing：PermissibleValueMeaning 的创建/更新
 * 统一经由 ItemAdministrationService 处理可选 registration 负载。
 */
@ExtendWith(MockitoExtension.class)
class PermissibleValueMeaningServiceTest {

    @Mock
    private PermissibleValueMeaningRepository repository;
    @Mock
    private PermissibleValueRepository permissibleValueRepository;
    @Mock
    private ValueMeaningRepository valueMeaningRepository;
    @Mock
    private ItemAdministrationService itemAdministrationService;

    @InjectMocks
    private PermissibleValueMeaningService service;

    private PermissibleValue stubPv(UUID pvId) {
        PermissibleValue pv = new PermissibleValue();
        pv.setId(pvId);
        // update 路径不经过 permissibleValueRepository，宽松 stub 兼容两条路径
        org.mockito.Mockito.lenient().when(permissibleValueRepository.findById(pvId)).thenReturn(Optional.of(pv));
        when(repository.findByPermissibleValues_Id(pvId)).thenReturn(List.of());
        return pv;
    }

    private ValueMeaning stubVm(UUID vmId) {
        ValueMeaning vm = new ValueMeaning();
        vm.setId(vmId);
        when(valueMeaningRepository.findById(vmId)).thenReturn(Optional.of(vm));
        return vm;
    }

    @Test
    void create_withoutRegistration_keepsLegacyBehavior() {
        UUID pvId = UUID.randomUUID();
        UUID vmId = UUID.randomUUID();
        stubPv(pvId);
        stubVm(vmId);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PermissibleValueMeaningRequest request = new PermissibleValueMeaningRequest(
                pvId, vmId, null, null, null);
        var response = service.create(request);

        assertThat(response).isNotNull();
        // registration 为 null 时仍经由统一收口（其内部对 null 直接放行）
        verify(itemAdministrationService).ensureAdministeredItem(any(Item.class), isNull());
        verify(itemAdministrationService, never()).ensureAdministeredItem(any(Item.class), any(ItemRegistrationRequest.class));
    }

    @Test
    void create_withRegistration_delegatesToItemAdministration() {
        UUID pvId = UUID.randomUUID();
        UUID vmId = UUID.randomUUID();
        stubPv(pvId);
        stubVm(vmId);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ItemRegistrationRequest registration = new ItemRegistrationRequest(
                UUID.randomUUID(), "PVM-001", "1.0.0", null, null);
        PermissibleValueMeaningRequest request = new PermissibleValueMeaningRequest(
                pvId, vmId, null, null, registration);
        service.create(request);

        verify(itemAdministrationService).ensureAdministeredItem(any(PermissibleValueMeaning.class), eq(registration));
    }

    @Test
    void update_withRegistration_backfillsIdentifierOnLegacyItem() {
        UUID pvId = UUID.randomUUID();
        UUID vmId = UUID.randomUUID();
        stubPv(pvId);
        stubVm(vmId);
        PermissibleValueMeaning existing = new PermissibleValueMeaning();
        existing.setId(UUID.randomUUID());
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ItemRegistrationRequest registration = new ItemRegistrationRequest(
                UUID.randomUUID(), null, null, null, null);
        PermissibleValueMeaningRequest request = new PermissibleValueMeaningRequest(
                pvId, vmId, null, null, registration);
        service.update(existing.getId(), request);

        // 编辑路径同样经统一收口：历史无标识实体在保存时补录 ScopedIdentifier
        verify(itemAdministrationService).ensureAdministeredItem(eq(existing), eq(registration));
    }
}
