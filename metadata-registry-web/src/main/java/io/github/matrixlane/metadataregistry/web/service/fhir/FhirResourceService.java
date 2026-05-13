package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirCodeSystemResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirResourceResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValueSetResponse;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirCodeSystem;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirValueSet;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirCodeSystemRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirResourceRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirValueSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FhirResourceService {

    private final FhirResourceRepository resourceRepository;
    private final FhirElementRepository elementRepository;
    private final FhirValueSetRepository valueSetRepository;
    private final FhirCodeSystemRepository codeSystemRepository;

    public Page<FhirResourceResponse> listResources(String kind, String nameFilter, Pageable pageable) {
        Specification<FhirResource> spec = Specification.where(null);
        if (kind != null && !kind.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("kind"), kind));
        }
        if (nameFilter != null && !nameFilter.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + nameFilter.toLowerCase() + "%"));
        }
        return resourceRepository.findAll(spec, pageable).map(FhirResourceResponse::from);
    }

    public FhirResourceResponse getResource(UUID id) {
        FhirResource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FhirResource", id));
        return FhirResourceResponse.from(resource);
    }

    public FhirResourceResponse getResourceWithTree(UUID id) {
        FhirResource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FhirResource", id));
        FhirResourceResponse response = FhirResourceResponse.from(resource);
        List<FhirElement> allElements = elementRepository.findByResourceIdOrderByElementPath(id);
        response.setElements(buildElementTree(allElements));
        return response;
    }

    public List<FhirElementResponse> getElementChildren(UUID parentElementId) {
        return elementRepository.findByParentIdOrderByElementPath(parentElementId).stream()
                .map(e -> toElementResponse(e, 0))
                .toList();
    }

    public List<FhirElementResponse> getRootElements(UUID resourceId) {
        return elementRepository.findByResourceIdAndParentIdIsNullOrderByElementPath(resourceId)
                .stream().map(e -> toElementResponse(e, 0))
                .toList();
    }

    public Page<FhirValueSetResponse> listValueSets(Pageable pageable) {
        return valueSetRepository.findAll(pageable).map(this::toValueSetResponse);
    }

    public Page<FhirCodeSystemResponse> listCodeSystems(Pageable pageable) {
        return codeSystemRepository.findAll(pageable).map(this::toCodeSystemResponse);
    }

    private List<FhirElementResponse> buildElementTree(List<FhirElement> elements) {
        if (elements.isEmpty()) return List.of();
        Map<UUID, List<FhirElement>> childrenMap = new HashMap<>();
        for (FhirElement e : elements) {
            UUID parentId = e.getParent() != null ? e.getParent().getId() : null;
            childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(e);
        }
        List<FhirElementResponse> tree = new ArrayList<>();
        for (FhirElement root : childrenMap.getOrDefault(null, List.of())) {
            tree.add(buildSubtree(root, childrenMap, 0));
        }
        return tree;
    }

    private FhirElementResponse buildSubtree(FhirElement element,
                                             Map<UUID, List<FhirElement>> childrenMap, int depth) {
        FhirElementResponse response = toElementResponse(element, depth);
        List<FhirElement> children = childrenMap.getOrDefault(element.getId(), List.of());
        if (!children.isEmpty()) {
            List<FhirElementResponse> childResponses = new ArrayList<>();
            for (FhirElement child : children) {
                childResponses.add(buildSubtree(child, childrenMap, depth + 1));
            }
            response.setChildren(childResponses);
        }
        return response;
    }

    private FhirElementResponse toElementResponse(FhirElement element, int depth) {
        return FhirElementResponse.builder()
                .id(element.getId())
                .parentId(element.getParent() != null ? element.getParent().getId() : null)
                .path(element.getPath())
                .elementPath(element.getElementPath())
                .elementType(element.getElementType())
                .min(element.getMin())
                .max(element.getMax())
                .shortDescription(element.getShortDescription())
                .definition(element.getDefinition())
                .bindingValueSet(element.getBindingValueSet())
                .bindingStrength(element.getBindingStrength())
                .bindingDescription(element.getBindingDescription())
                .modifier(element.isModifier())
                .summary(element.isSummary())
                .depth(depth)
                .build();
    }

    private FhirValueSetResponse toValueSetResponse(FhirValueSet vs) {
        return FhirValueSetResponse.builder()
                .id(vs.getId()).fhirId(vs.getFhirId()).url(vs.getUrl())
                .name(vs.getName()).title(vs.getTitle()).status(vs.getStatus())
                .version(vs.getVersion()).fhirVersion(vs.getFhirVersion())
                .description(vs.getDescription()).purpose(vs.getPurpose())
                .experimental(vs.isExperimental()).publisher(vs.getPublisher())
                .importedAt(vs.getImportedAt()).build();
    }

    private FhirCodeSystemResponse toCodeSystemResponse(FhirCodeSystem cs) {
        return FhirCodeSystemResponse.builder()
                .id(cs.getId()).fhirId(cs.getFhirId()).url(cs.getUrl())
                .name(cs.getName()).title(cs.getTitle()).status(cs.getStatus())
                .version(cs.getVersion()).fhirVersion(cs.getFhirVersion())
                .description(cs.getDescription()).content(cs.getContent())
                .publisher(cs.getPublisher()).importedAt(cs.getImportedAt()).build();
    }
}
