package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirCodeSystemResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirImportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirResourceResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValueSetResponse;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirImportService;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FhirResourceController.class)
@AutoConfigureMockMvc(addFilters = false)
class FhirResourceControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private FhirImportService importService;
    @MockBean private FhirResourceService resourceService;
    @MockBean private JwtService jwtService;
    @MockBean private AppProperties appProperties;
    private static final UUID RID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void importFromDirectory_shouldReturnResult() throws Exception {
        FhirImportResult r = new FhirImportResult(); r.addImportedResource("Patient");
        when(importService.importFromDirectory("C:/fhir/pkg")).thenReturn(r);
        mockMvc.perform(post("/api/v1/fhir/import/directory").param("packageDir","C:/fhir/pkg"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedResources[0]").value("Patient"));
    }

    @Test
    void importSingleFile_shouldReturnResult() throws Exception {
        FhirImportResult r = new FhirImportResult(); r.addImportedResource("Patient");
        when(importService.importSingleFile("C:/test/Patient.json")).thenReturn(r);
        mockMvc.perform(post("/api/v1/fhir/import/file").param("filePath","C:/test/Patient.json"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.importedResources[0]").value("Patient"));
    }

    @Test
    void listResources_shouldReturnPage() throws Exception {
        Page<FhirResourceResponse> p = new PageImpl<>(List.of(resp("Patient","resource")));
        when(resourceService.listResources(any(),any(),any(PageRequest.class))).thenReturn(p);
        mockMvc.perform(get("/api/v1/fhir/resources")).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("Patient"));
    }

    @Test
    void getResource_shouldReturnResource() throws Exception {
        when(resourceService.getResource(RID)).thenReturn(resp("Patient","resource"));
        mockMvc.perform(get("/api/v1/fhir/resources/{id}",RID)).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Patient"));
    }

    @Test
    void getResource_shouldReturn404() throws Exception {
        UUID u = UUID.randomUUID();
        when(resourceService.getResource(u)).thenThrow(new ResourceNotFoundException("FhirResource",u));
        mockMvc.perform(get("/api/v1/fhir/resources/{id}",u)).andExpect(status().isNotFound());
    }

    @Test
    void getResourceTree_shouldReturnTree() throws Exception {
        FhirResourceResponse r = resp("Patient","resource");
        r.setElements(List.of(FhirElementResponse.builder().id(UUID.randomUUID()).path("Patient").children(List.of(FhirElementResponse.builder().id(UUID.randomUUID()).path("Patient.name").build())).build()));
        when(resourceService.getResourceWithTree(RID)).thenReturn(r);
        mockMvc.perform(get("/api/v1/fhir/resources/{id}/tree",RID)).andExpect(status().isOk()).andExpect(jsonPath("$.data.elements[0].children[0].path").value("Patient.name"));
    }

    @Test
    void getRootElements_shouldReturnRoots() throws Exception {
        when(resourceService.getRootElements(RID)).thenReturn(List.of(FhirElementResponse.builder().id(UUID.randomUUID()).path("Patient").build()));
        mockMvc.perform(get("/api/v1/fhir/resources/{id}/elements/roots",RID)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].path").value("Patient"));
    }

    @Test
    void getElementChildren_shouldReturnChildren() throws Exception {
        when(resourceService.getElementChildren(EID)).thenReturn(List.of(FhirElementResponse.builder().id(UUID.randomUUID()).path("Patient.name.given").build()));
        mockMvc.perform(get("/api/v1/fhir/elements/{elementId}/children",EID)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].path").value("Patient.name.given"));
    }

    @Test
    void listValueSets_shouldReturnPage() throws Exception {
        Page<FhirValueSetResponse> p = new PageImpl<>(List.of(FhirValueSetResponse.builder().id(UUID.randomUUID()).name("Admin").build()));
        when(resourceService.listValueSets(any(PageRequest.class))).thenReturn(p);
        mockMvc.perform(get("/api/v1/fhir/value-sets")).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].name").value("Admin"));
    }

    @Test
    void listCodeSystems_shouldReturnPage() throws Exception {
        Page<FhirCodeSystemResponse> p = new PageImpl<>(List.of(FhirCodeSystemResponse.builder().id(UUID.randomUUID()).name("Admin").content("complete").build()));
        when(resourceService.listCodeSystems(any(PageRequest.class))).thenReturn(p);
        mockMvc.perform(get("/api/v1/fhir/code-systems")).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].content").value("complete"));
    }

    @Test
    void listResourceKinds_shouldReturnKinds() throws Exception {
        mockMvc.perform(get("/api/v1/fhir/kinds")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("resource")).andExpect(jsonPath("$.data[3]").value("logical"));
    }

    private FhirResourceResponse resp(String name, String kind) {
        return FhirResourceResponse.builder().id(UUID.randomUUID()).fhirId(name).name(name).kind(kind).fhirVersion("R4").importedAt(LocalDateTime.now()).build();
    }
}
