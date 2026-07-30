 package io.github.matrixlane.metadataregistry.web.controller;
 
 import com.fasterxml.jackson.databind.ObjectMapper;
 import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
 import io.github.matrixlane.metadataregistry.web.auth.JwtService;
 import io.github.matrixlane.metadataregistry.web.config.AppProperties;
 import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
 import io.github.matrixlane.metadataregistry.web.dto.administereditem.ReferenceDocumentLinkRequest;
 import io.github.matrixlane.metadataregistry.web.service.AdministeredItemService;
 import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
 import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
 import org.springframework.boot.test.mock.mockito.MockBean;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.PageImpl;
 import org.springframework.http.MediaType;
 import org.springframework.test.web.servlet.MockMvc;
 
 import java.time.LocalDateTime;
 import java.util.List;
 import java.util.UUID;
 
 import static org.mockito.ArgumentMatchers.any;
 import static org.mockito.ArgumentMatchers.eq;
 import static org.mockito.ArgumentMatchers.isNull;
 import static org.mockito.Mockito.when;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
 import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
 
 @WebMvcTest(AdministeredItemController.class)
 @AutoConfigureMockMvc(addFilters = false)
 class AdministeredItemControllerTest {
 
     @Autowired
     private MockMvc mockMvc;
 
     @Autowired
     private ObjectMapper objectMapper;
 
     @MockBean
     private AdministeredItemService service;
 
     @MockBean
     private JwtService jwtService;
 
     @MockBean
     private AppProperties appProperties;
 
     private static final UUID AI_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
     private static final UUID DOC_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
     private static final UUID CS_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
 
     @Test
     void list_shouldReturnOk() throws Exception {
         when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(createResponse())));
 
         mockMvc.perform(get("/api/v1/administered-items"))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.content[0].id").value(AI_ID.toString()));
     }
 
     @Test
     void get_shouldReturnOk() throws Exception {
         when(service.get(AI_ID)).thenReturn(createResponse());
 
         mockMvc.perform(get("/api/v1/administered-items/{id}", AI_ID))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.id").value(AI_ID.toString()));
     }
 
     @Test
     void updateConstraintSet_shouldReturnOk() throws Exception {
         when(service.updateConstraintSet(eq(AI_ID), eq(CS_ID))).thenReturn(createResponse());
 
         mockMvc.perform(post("/api/v1/administered-items/{id}/constraint-set", AI_ID)
                         .param("constraintSetId", CS_ID.toString()))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.id").value(AI_ID.toString()));
     }
 
     @Test
     void updateConstraintSet_withoutId_shouldClearConstraintSet() throws Exception {
         when(service.updateConstraintSet(eq(AI_ID), isNull())).thenReturn(createResponse());
 
         mockMvc.perform(post("/api/v1/administered-items/{id}/constraint-set", AI_ID))
                 .andExpect(status().isOk());
     }
 
     @Test
     void addReferenceDocuments_shouldReturnOk() throws Exception {
         ReferenceDocumentLinkRequest req = new ReferenceDocumentLinkRequest(List.of(DOC_ID));
         when(service.addReferenceDocuments(eq(AI_ID), any())).thenReturn(createResponse());
 
         mockMvc.perform(post("/api/v1/administered-items/{id}/reference-documents", AI_ID)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.id").value(AI_ID.toString()));
     }
 
     @Test
     void addReferenceDocuments_withEmptyList_shouldReturnOk() throws Exception {
         ReferenceDocumentLinkRequest req = new ReferenceDocumentLinkRequest(List.of());
 
         when(service.addReferenceDocuments(eq(AI_ID), any())).thenReturn(createResponse());
         mockMvc.perform(post("/api/v1/administered-items/{id}/reference-documents", AI_ID)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isOk());
     }
 
     @Test
     void removeReferenceDocuments_shouldReturnOk() throws Exception {
         ReferenceDocumentLinkRequest req = new ReferenceDocumentLinkRequest(List.of(DOC_ID));
         when(service.removeReferenceDocuments(eq(AI_ID), any())).thenReturn(createResponse());
 
         mockMvc.perform(delete("/api/v1/administered-items/{id}/reference-documents", AI_ID)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isOk());
     }
 
     private AdministeredItemResponse createResponse() {
         return new AdministeredItemResponse(
                 AI_ID,
                 UUID.randomUUID(),
                 "DataElement",
                 "患者姓名",
                 "DE-PATIENT-NAME",
                 "1.0",
                 null, null, null, null,
                 RegistrationStatus.STANDARD,
                 LocalDateTime.now(),
                 "自动生成",
                 null, null, List.of(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                 "初始版本");
     }
 }
