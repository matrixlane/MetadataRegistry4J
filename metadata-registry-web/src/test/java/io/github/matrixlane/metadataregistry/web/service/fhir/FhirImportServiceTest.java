package io.github.matrixlane.metadataregistry.web.service.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirImportError;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirImportResult;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirImportServiceTest {
    @Mock FhirResourceRepository resourceRepository;
    @Mock FhirElementRepository elementRepository;
    @Mock FhirValueSetRepository valueSetRepository;
    @Mock FhirCodeSystemRepository codeSystemRepository;
    @Captor ArgumentCaptor<FhirResource> resourceCaptor;
    @Captor ArgumentCaptor<List<FhirElement>> elementsCaptor;
    private FhirImportService importService;
    private Path testFhirDir;

    @BeforeEach
    void setUp() throws Exception {
        importService = new FhirImportService(resourceRepository, elementRepository, valueSetRepository, codeSystemRepository, new ObjectMapper());
        URL dirUrl = getClass().getClassLoader().getResource("fhir");
        assertThat(dirUrl).isNotNull();
        testFhirDir = Path.of(dirUrl.toURI());
    }

    @Test
    void importSingleFile_shouldParseStructureDefinition() throws Exception {
        when(resourceRepository.existsByUrl(any())).thenReturn(false);
        when(resourceRepository.save(any())).thenAnswer(i -> { FhirResource r = i.getArgument(0); r.setId(java.util.UUID.randomUUID()); return r; });
        FhirImportResult result = importService.importSingleFile(testFhirDir.resolve("StructureDefinition-Patient.json").toString());
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getImportedResources()).contains("Patient");
        verify(resourceRepository).save(resourceCaptor.capture());
        assertThat(resourceCaptor.getValue().getName()).isEqualTo("Patient");
        assertThat(resourceCaptor.getValue().getKind()).isEqualTo("resource");
        verify(elementRepository).saveAll(elementsCaptor.capture());
        List<FhirElement> elements = elementsCaptor.getValue();
        assertThat(elements).hasSize(5);
        assertThat(elements).extracting(FhirElement::getPath).contains("Patient","Patient.name","Patient.name.given","Patient.gender","Patient.birthDate");
        FhirElement g = elements.stream().filter(e -> "Patient.gender".equals(e.getPath())).findFirst().orElseThrow();
        assertThat(g.getBindingStrength()).isEqualTo("required");
        assertThat(g.getBindingValueSet()).contains("administrative-gender");
    }

    @Test
    void importSingleFile_shouldParseValueSet() throws Exception {
        when(valueSetRepository.existsByUrl(any())).thenReturn(false);
        FhirImportResult result = importService.importSingleFile(testFhirDir.resolve("ValueSet-administrative-gender.json").toString());
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getImportedValueSets()).contains("AdministrativeGender");
    }

    @Test
    void importSingleFile_shouldParseCodeSystem() throws Exception {
        when(codeSystemRepository.existsByUrl(any())).thenReturn(false);
        FhirImportResult result = importService.importSingleFile(testFhirDir.resolve("CodeSystem-administrative-gender.json").toString());
        assertThat(result.getErrorCount()).isEqualTo(0);
        assertThat(result.getImportedCodeSystems()).contains("AdministrativeGender");
    }

    @Test
    void importSingleFile_shouldRejectInvalidJson() {
        FhirImportResult result = importService.importSingleFile(testFhirDir.resolve("invalid-syntax.json").toString());
        assertThat(result.getErrors()).extracting(FhirImportError::getErrorType).contains("JSON_SYNTAX");
    }

    @Test
    void importSingleFile_shouldRejectMissingResourceType() {
        FhirImportResult result = importService.importSingleFile(testFhirDir.resolve("invalid-missing-resourceType.json").toString());
        assertThat(result.getErrors()).extracting(FhirImportError::getErrorType).contains("MISSING_FIELD");
    }

    @Test
    void importFromDirectory_shouldImportAllFiles() throws Exception {
        when(resourceRepository.existsByUrl(any())).thenReturn(false);
        when(resourceRepository.save(any())).thenAnswer(i -> { FhirResource r = i.getArgument(0); r.setId(java.util.UUID.randomUUID()); return r; });
        when(valueSetRepository.existsByUrl(any())).thenReturn(false);
        when(codeSystemRepository.existsByUrl(any())).thenReturn(false);
        FhirImportResult result = importService.importFromDirectory(testFhirDir.toString());
        assertThat(result.getTotalProcessed()).isPositive();
        assertThat(result.getImportedResources()).contains("Patient","Observation","HumanName");
        assertThat(result.getImportedValueSets()).contains("AdministrativeGender");
        assertThat(result.getImportedCodeSystems()).contains("AdministrativeGender");
    }

    @Test
    void importFromDirectory_shouldSkipDuplicate() {
        when(resourceRepository.existsByUrl("http://hl7.org/fhir/StructureDefinition/Patient")).thenReturn(true);
        FhirImportResult result = importService.importSingleFile(testFhirDir.resolve("StructureDefinition-Patient.json").toString());
        assertThat(result.getSuccessCount()).isEqualTo(0);
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void importFromDirectory_shouldReturnErrorForInvalidDir() {
        assertThat(importService.importFromDirectory("/nonexistent/dir").getErrors().get(0).getErrorType()).isEqualTo("INVALID_PATH");
    }

    @Test
    void importSingleFile_shouldHandleObservation() throws Exception {
        when(resourceRepository.existsByUrl(any())).thenReturn(false);
        when(resourceRepository.save(any())).thenAnswer(i -> { FhirResource r = i.getArgument(0); r.setId(java.util.UUID.randomUUID()); return r; });
        FhirImportResult result = importService.importSingleFile(testFhirDir.resolve("StructureDefinition-Observation.json").toString());
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(elementRepository).saveAll(elementsCaptor.capture());
        FhirElement s = elementsCaptor.getValue().stream().filter(e -> "Observation.status".equals(e.getPath())).findFirst().orElseThrow();
        assertThat(s.getMin()).isEqualTo(1);
        assertThat(s.isModifier()).isTrue();
    }
}
