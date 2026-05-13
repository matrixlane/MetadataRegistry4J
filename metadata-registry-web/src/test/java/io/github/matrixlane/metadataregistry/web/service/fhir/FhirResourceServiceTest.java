package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirResourceResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValueSetResponse;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirValueSet;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirCodeSystemRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirResourceRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirValueSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirResourceServiceTest {
    @Mock FhirResourceRepository resourceRepository;
    @Mock FhirElementRepository elementRepository;
    @Mock FhirValueSetRepository valueSetRepository;
    @Mock FhirCodeSystemRepository codeSystemRepository;
    private FhirResourceService service;
    private UUID patientId, nameId, givenId;
    private FhirResource patientResource;
    private FhirElement rootElement, nameElement, givenElement;

    @BeforeEach
    void setUp() {
        service = new FhirResourceService(resourceRepository, elementRepository, valueSetRepository, codeSystemRepository);
        patientId = UUID.randomUUID(); nameId = UUID.randomUUID(); givenId = UUID.randomUUID();
        patientResource = createResource(patientId, "Patient", "resource");
        rootElement = createElement(UUID.randomUUID(), patientResource, null, "Patient");
        nameElement = createElement(nameId, patientResource, rootElement, "Patient.name");
        givenElement = createElement(givenId, patientResource, nameElement, "Patient.name.given");
    }

    @Test
    void getResource_shouldReturnResource() {
        when(resourceRepository.findById(patientId)).thenReturn(Optional.of(patientResource));
        assertThat(service.getResource(patientId).getName()).isEqualTo("Patient");
    }

    @Test
    void getResource_shouldThrowWhenNotFound() {
        when(resourceRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getResource(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getResourceWithTree_shouldReturnElementTree() {
        when(resourceRepository.findById(patientId)).thenReturn(Optional.of(patientResource));
        when(elementRepository.findByResourceIdOrderByElementPath(patientId)).thenReturn(List.of(rootElement, nameElement, givenElement));
        FhirResourceResponse r = service.getResourceWithTree(patientId);
        assertThat(r.getElements()).hasSize(1);
        assertThat(r.getElements().get(0).getChildren()).hasSize(1);
        assertThat(r.getElements().get(0).getChildren().get(0).getChildren()).hasSize(1);
    }

    @Test
    void getRootElements_shouldReturnRoots() {
        when(elementRepository.findByResourceIdAndParentIdIsNullOrderByElementPath(patientId)).thenReturn(List.of(rootElement));
        assertThat(service.getRootElements(patientId)).hasSize(1);
    }

    @Test
    void getElementChildren_shouldReturnDirectChildren() {
        when(elementRepository.findByParentIdOrderByElementPath(nameId)).thenReturn(List.of(givenElement));
        assertThat(service.getElementChildren(nameId)).hasSize(1);
    }

    @Test
    void listResources_shouldFilterByKind() {
        when(resourceRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(patientResource)));
        assertThat(service.listResources("resource", null, PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void listValueSets_shouldReturnValueSets() {
        FhirValueSet vs = new FhirValueSet(); vs.setId(UUID.randomUUID()); vs.setName("AdministrativeGender"); vs.setUrl("http://hl7.org/fhir/ValueSet/administrative-gender"); vs.setFhirVersion("R4");
        when(valueSetRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(vs)));
        assertThat(service.listValueSets(PageRequest.of(0, 20)).getContent().get(0).getName()).isEqualTo("AdministrativeGender");
    }

    private FhirResource createResource(UUID id, String name, String kind) {
        FhirResource r = new FhirResource(); r.setId(id); r.setFhirId(name); r.setName(name); r.setKind(kind); r.setFhirVersion("R4"); r.setImportedAt(LocalDateTime.now()); return r;
    }
    private FhirElement createElement(UUID id, FhirResource res, FhirElement parent, String path) {
        FhirElement e = new FhirElement(); e.setId(id); e.setResource(res); e.setParent(parent); e.setPath(path); e.setElementPath(path.contains(".") ? path.substring(path.lastIndexOf('.')+1) : path); e.setFhirVersion("R4"); e.setImportedAt(LocalDateTime.now()); return e;
    }
}
