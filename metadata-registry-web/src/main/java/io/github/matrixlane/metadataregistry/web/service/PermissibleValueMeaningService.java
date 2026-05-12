package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DataTimePeriod;
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
        DataTimePeriod period = new DataTimePeriod();
        period.setStartDateTime(request.validPeriodStart());
        period.setEndDateTime(request.validPeriodEnd());
        pvm.setValidPeriod(period);

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

        DataTimePeriod period = new DataTimePeriod();
        period.setStartDateTime(request.validPeriodStart());
        period.setEndDateTime(request.validPeriodEnd());
        pvm.setValidPeriod(period);

        // Find associated PV for overlap check
        UUID pvId = request.permissibleValueId();
        if (pvId == null && !pvm.getPermissibleValues().isEmpty()) {
            pvId = pvm.getPermissibleValues().get(0).getId();
        }
        if (pvId != null) {
            checkOverlap(pvId, id, request.validPeriodStart(), request.validPeriodEnd());
        }

        return toResponse(repository.save(pvm));
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

    private void checkOverlap(UUID permissibleValueId, UUID excludeId,
                               java.time.LocalDateTime start, java.time.LocalDateTime end) {
        List<PermissibleValueMeaning> existing = repository.findByPermissibleValues_Id(permissibleValueId);
        List<DataTimePeriod> periods = new ArrayList<>();
        DataTimePeriod newPeriod = new DataTimePeriod();
        newPeriod.setStartDateTime(start);
        newPeriod.setEndDateTime(end);
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
        DataTimePeriod period = pvm.getValidPeriod();
        return new PermissibleValueMeaningResponse(
                pvm.getId(),
                pv == null ? null : pv.getId(),
                pv == null ? null : ItemNamingHelper.preferredDesignation(pv),
                vm == null ? null : vm.getId(),
                vm == null ? null : ItemNamingHelper.preferredDesignation(vm),
                period == null ? null : period.getStartDateTime(),
                period == null ? null : period.getEndDateTime());
    }
}
