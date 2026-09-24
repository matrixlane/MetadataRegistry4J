package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValue;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueMeaning;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaning;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning.PermissibleValueMeaningRequest;
import io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning.PermissibleValueMeaningResponse;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueMeaningRepository;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.github.matrixlane.metadataregistry.web.util.ValidPeriodOverlapChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PermissibleValueMeaningService {

    private final PermissibleValueMeaningRepository repository;
    private final PermissibleValueRepository permissibleValueRepository;
    private final ValueMeaningRepository valueMeaningRepository;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public List<PermissibleValueMeaningResponse> listByPermissibleValue(UUID permissibleValueId) {
        List<PermissibleValueMeaning> list = repository.findByPermissibleValues_Id(permissibleValueId);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PermissibleValueMeaningResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public PermissibleValueMeaningResponse create(PermissibleValueMeaningRequest request) {
        PermissibleValue pv = permissibleValueRepository.findById(request.permissibleValueId())
                .orElseThrow(() -> new ResourceNotFoundException("PermissibleValue", request.permissibleValueId()));
        ValueMeaning vm = valueMeaningRepository.findById(request.valueMeaningId())
                .orElseThrow(() -> new ResourceNotFoundException("ValueMeaning", request.valueMeaningId()));

        // Overlap detection for same PV
        checkOverlap(pv.getId(), null, request.validPeriodStart(), request.validPeriodEnd());

        PermissibleValueMeaning pvm = new PermissibleValueMeaning();
        pvm.setValueMeaning(vm);
        Datetime_Period period = new Datetime_Period();
        period.setStartDateTime(toOffset(request.validPeriodStart()));
        period.setEndDateTime(toOffset(request.validPeriodEnd()));
        pvm.setValidPeriod(period);
        itemAdministrationService.ensureAdministeredItem(pvm, request.registration());

        pvm = repository.save(pvm);

        pv.getPermissibleValueMeanings().add(pvm);
        permissibleValueRepository.save(pv);

        return toResponse(pvm);
    }

    public PermissibleValueMeaningResponse update(UUID id, PermissibleValueMeaningRequest request) {
        PermissibleValueMeaning pvm = loadOrThrow(id);

        if (request.valueMeaningId() != null) {
            ValueMeaning vm = valueMeaningRepository.findById(request.valueMeaningId())
                    .orElseThrow(() -> new ResourceNotFoundException("ValueMeaning", request.valueMeaningId()));
            pvm.setValueMeaning(vm);
        }

        Datetime_Period period = new Datetime_Period();
        period.setStartDateTime(toOffset(request.validPeriodStart()));
        period.setEndDateTime(toOffset(request.validPeriodEnd()));
        pvm.setValidPeriod(period);

        // Find associated PV for overlap check
        UUID pvId = request.permissibleValueId();
        if (pvId == null && !pvm.getPermissibleValues().isEmpty()) {
            pvId = pvm.getPermissibleValues().get(0).getId();
        }
        if (pvId != null) {
            checkOverlap(pvId, id, request.validPeriodStart(), request.validPeriodEnd());
        }
        itemAdministrationService.ensureAdministeredItem(pvm, request.registration());

        PermissibleValueMeaning saved = repository.save(pvm);
        return toResponse(saved);
    }

    public void delete(UUID id) {
        PermissibleValueMeaning pvm = loadOrThrow(id);
        // Remove from all linked PVs
        for (PermissibleValue pv : pvm.getPermissibleValues()) {
            pv.getPermissibleValueMeanings().remove(pvm);
        }
        repository.delete(pvm);
    }

    private PermissibleValueMeaning loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermissibleValueMeaning", id));
    }

    /**
     * DTO 边界转换:前端日期时间不含时区(LocalDateTime),按系统默认时区
     * 补齐为 OffsetDateTime(11179-3:2023 6.2.3 Datetime 含时区)。
     */
    private static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static LocalDateTime toLocal(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private void checkOverlap(UUID permissibleValueId, UUID excludeId,
                               LocalDateTime start, LocalDateTime end) {
        List<PermissibleValueMeaning> existing = repository.findByPermissibleValues_Id(permissibleValueId);
        List<Datetime_Period> periods = new ArrayList<>();
        Datetime_Period newPeriod = new Datetime_Period();
        newPeriod.setStartDateTime(toOffset(start));
        newPeriod.setEndDateTime(toOffset(end));
        periods.add(newPeriod);
        for (PermissibleValueMeaning pvm : existing) {
            if (excludeId != null && excludeId.equals(pvm.getId())) continue;
            if (pvm.getValidPeriod() != null) {
                periods.add(pvm.getValidPeriod());
            }
        }
        ValidPeriodOverlapChecker.checkNoOverlaps(periods);
    }

    private PermissibleValueMeaningResponse toResponse(PermissibleValueMeaning pvm) {
        PermissibleValue pv = pvm.getPermissibleValues().isEmpty() ? null : pvm.getPermissibleValues().get(0);
        ValueMeaning vm = pvm.getValueMeaning();
        Datetime_Period period = pvm.getValidPeriod();
        return new PermissibleValueMeaningResponse(
                pvm.getId(),
                pv == null ? null : pv.getId(),
                pv == null ? null : ItemNamingHelper.preferredDesignation(pv),
                vm == null ? null : vm.getId(),
                vm == null ? null : ItemNamingHelper.preferredDesignation(vm),
                period == null ? null : toLocal(period.getStartDateTime()),
                period == null ? null : toLocal(period.getEndDateTime()));
    }
}
