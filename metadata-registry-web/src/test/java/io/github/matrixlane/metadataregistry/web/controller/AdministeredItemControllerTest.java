 package io.github.matrixlane.metadataregistry.web.controller;
 
 import com.fasterxml.jackson.databind.ObjectMapper;
 import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
 import io.github.matrixlane.metadataregistry.web.auth.JwtService;
 import io.github.matrixlane.metadataregistry.web.config.AppProperties;
 import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
 import io.github.matrixlane.metadataregistry.web.dto.administereditem.ReferenceDocumentLinkRequest;
 import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
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
     private ActiveRegistrarService activeRegistrarService;
 
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
 
 
    @Test
    void transitions_shouldReturnOk() throws Exception {
        when(service.availableTransitions(eq(AI_ID), isNull())).thenReturn(List.of(
                new io.github.matrixlane.metadataregistry.web.dto.registration.AvailableTransitionView(
                        RegistrationStatus.RETIRED, null, false, 0, 1, true, false, false)));

        mockMvc.perform(get("/api/v1/administered-items/{id}/transitions", AI_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].to").value("RETIRED"))
                .andExpect(jsonPath("$.data[0].canExecute").value(true));
    }

    @Test
    void executeTransition_shouldReturnOk() throws Exception {
        when(service.executeTransition(eq(AI_ID), eq(RegistrationStatus.RETIRED), eq("退役"), isNull()))
                .thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/administered-items/{id}/transitions/execute", AI_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\": \"RETIRED\", \"note\": \"退役\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(AI_ID.toString()));
    }

    @Test
    void approveTransition_shouldReturnOk() throws Exception {
        when(service.approveTransition(eq(AI_ID), eq(RegistrationStatus.QUALIFIED), isNull()))
                .thenReturn(new io.github.matrixlane.metadataregistry.web.dto.registration.ApprovalResultResponse(
                        true, List.of()));

        mockMvc.perform(post("/api/v1/administered-items/{id}/transitions/approve", AI_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\": \"QUALIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executed").value(true));
    }

    @Test
    void rejectTransition_shouldReturnOk() throws Exception {
        when(service.rejectTransition(eq(AI_ID), eq("信息不足"), isNull()))
                .thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/administered-items/{id}/transitions/reject", AI_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"信息不足\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(AI_ID.toString()));
    }

    @Test
    void requestQualification_shouldReturnOk() throws Exception {
        when(service.requestQualification(eq(AI_ID), isNull()))
                .thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/administered-items/{id}/request-qualification", AI_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(AI_ID.toString()));
    }

    @Test
    void oldTransitionEndpoint_shouldBeRemoved() throws Exception {
        // 旧 POST /{id}/transition 已移除：不再有任何处理器承接（MockMvc 无过滤器时
        // 未匹配路由返回 500，生产经 /error 返回 404；此处只断言不再可用）
        int status = mockMvc.perform(post("/api/v1/administered-items/{id}/transition", AI_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\": \"RETIRED\"}"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
                .isNotEqualTo(200)
                .isNotEqualTo(201);
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
                 "初始版本",
                 null,
                 null);
    }
}
