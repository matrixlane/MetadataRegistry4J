package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DataTimePeriod;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaning;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaningSet;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainType;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningEntry;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningResponse;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningSetRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.github.matrixlane.metadataregistry.web.util.ValidPeriodOverlapChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConceptualDomainService {

    private final ConceptualDomainRepository repository;
    private final ValueMeaningRepository valueMeaningRepository;
    private final ValueMeaningSetRepository valueMeaningSetRepository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<ConceptualDomainResponse> list(String keyword, Pageable pageable) {
        Specification<ConceptualDomain> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConceptualDomainResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ConceptualDomainResponse create(ConceptualDomainRequest request) {
        ConceptualDomainType type = request.type() == null ? ConceptualDomainType.DESCRIBED : request.type();
        ConceptualDomain entity = switch (type) {
            case LOCAL_ENUMERATED -> new LocalEnumeratedConceptualDomain();
            case DESCRIBED -> new DescribedConceptualDomain();
        };
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ConceptualDomain saved = repository.save(entity);
        if (saved instanceof LocalEnumeratedConceptualDomain lecd) {
            applyValueMeanings(lecd, request.valueMeanings());
        }
        return toResponse(saved);
    }

    public ConceptualDomainResponse update(UUID id, ConceptualDomainRequest request) {
        ConceptualDomain entity = loadOrThrow(id);
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ConceptualDomain saved = repository.save(entity);
        if (saved instanceof LocalEnumeratedConceptualDomain lecd) {
            applyValueMeanings(lecd, request.valueMeanings());
        }
        return toResponse(saved);
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ConceptualDomain", id);
        }
        repository.deleteById(id);
    }

    private ConceptualDomain loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", id));
    }

    private void applyValueMeanings(LocalEnumeratedConceptualDomain lecd, List<ValueMeaningEntry> entries) {
        cleanupValueMeanings(lecd);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        // Overlap detection
        List<DataTimePeriod> periods = new ArrayList<>();
        for (ValueMeaningEntry entry : entries) {
            DataTimePeriod p = new DataTimePeriod();
            p.setStartDateTime(entry.validPeriodStart());
            p.setEndDateTime(entry.validPeriodEnd());
            periods.add(p);
        }
        ValidPeriodOverlapChecker.checkNoOverlaps(periods);

        for (ValueMeaningEntry entry : entries) {
            ValueMeaning vm = new ValueMeaning();
            itemAdministrationService.applyDesignations(vm, entry.designations());
            itemAdministrationService.applyDefinitions(vm, entry.definitions());
            vm = valueMeaningRepository.save(vm);

            ValueMeaningSet vms = new ValueMeaningSet();
            vms.setValueMeaning(vm);
            vms.setLocalEnumeratedConceptualDomain(lecd);
            DataTimePeriod period = new DataTimePeriod();
            period.setStartDateTime(entry.validPeriodStart());
            period.setEndDateTime(entry.validPeriodEnd());
            vms.setValidPeriod(period);
            valueMeaningSetRepository.save(vms);
        }
    }

    private void cleanupValueMeanings(LocalEnumeratedConceptualDomain lecd) {
        List<ValueMeaningSet> existing = valueMeaningSetRepository
                .findByLocalEnumeratedConceptualDomainId(lecd.getId());
        for (ValueMeaningSet vms : existing) {
            ValueMeaning vm = vms.getValueMeaning();
            valueMeaningSetRepository.delete(vms);
            if (vm != null) {
                valueMeaningRepository.delete(vm);
            }
        }
    }

    private ConceptualDomainResponse toResponse(ConceptualDomain entity) {
        ConceptualDomainType type = ConceptualDomainType.DESCRIBED;
        List<ValueMeaningResponse> vmResponses = Collections.emptyList();
        if (entity instanceof LocalEnumeratedConceptualDomain lecd) {
            type = ConceptualDomainType.LOCAL_ENUMERATED;
            vmResponses = mapValueMeanings(lecd);
        }
        return new ConceptualDomainResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                type,
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                vmResponses,
                itemAdministrationService.buildItemMeta(entity));
    }

    private List<ValueMeaningResponse> mapValueMeanings(LocalEnumeratedConceptualDomain lecd) {
        List<ValueMeaningSet> sets = valueMeaningSetRepository
                .findByLocalEnumeratedConceptualDomainId(lecd.getId());
        if (sets.isEmpty()) {
            return Collections.emptyList();
        }
        List<ValueMeaningResponse> out = new ArrayList<>(sets.size());
        for (ValueMeaningSet vms : sets) {
            ValueMeaning vm = vms.getValueMeaning();
            if (vm == null) continue;
            DataTimePeriod period = vms.getValidPeriod();
            out.add(new ValueMeaningResponse(
                    vm.getId(),
                    ItemNamingHelper.preferredDesignation(vm),
                    itemAdministrationService.mapDesignations(vm),
                    itemAdministrationService.mapDefinitions(vm),
                    period == null ? null : period.getStartDateTime(),
                    period == null ? null : period.getEndDateTime(),
                    itemAdministrationService.buildItemMeta(vm)));
        }
        return out;
    }
}
