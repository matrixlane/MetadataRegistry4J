 package io.github.matrixlane.metadataregistry.web.controller;
 
 import com.fasterxml.jackson.databind.ObjectMapper;
 import io.github.matrixlane.metadataregistry.web.auth.JwtService;
 import io.github.matrixlane.metadataregistry.web.config.AppProperties;
 import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
 import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetRequest;
 import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetResponse;
 import io.github.matrixlane.metadataregistry.web.service.ConstraintSetService;
 import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
 import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
 import org.springframework.boot.test.mock.mockito.MockBean;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.PageImpl;
 import org.springframework.http.MediaType;
 import org.springframework.test.web.servlet.MockMvc;
 
 import java.util.List;
 import java.util.UUID;
 
 import static org.mockito.ArgumentMatchers.any;
 import static org.mockito.ArgumentMatchers.eq;
 import static org.mockito.Mockito.when;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
 import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
 
 @WebMvcTest(ConstraintSetController.class)
 @AutoConfigureMockMvc(addFilters = false)
 class ConstraintSetControllerTest {
 
     @Autowired
     private MockMvc mockMvc;
 
     @Autowired
     private ObjectMapper objectMapper;
 
     @MockBean
     private ConstraintSetService service;
 
     @MockBean
     private JwtService jwtService;
 
     @MockBean
     private AppProperties appProperties;
 
     private static final UUID CS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
     private static final UUID LANG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
 
     @Test
     void list_shouldReturnOk() throws Exception {
         when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(createResponse())));
 
         mockMvc.perform(get("/api/v1/constraint-sets"))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.content[0].id").value(CS_ID.toString()));
     }
 
     @Test
     void get_shouldReturnOk() throws Exception {
         when(service.get(CS_ID)).thenReturn(createResponse());
 
         mockMvc.perform(get("/api/v1/constraint-sets/{id}", CS_ID))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.id").value(CS_ID.toString()));
     }
 
     @Test
     void create_shouldReturnOk() throws Exception {
         ConstraintSetRequest req = new ConstraintSetRequest(
                 List.of(new TextLine(LANG_ID, "默认约束集", null, null)),
                 null, null, true, true, true);
         when(service.create(any())).thenReturn(createResponse());
 
         mockMvc.perform(post("/api/v1/constraint-sets")
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.id").value(CS_ID.toString()));
     }
 
    @Test
    void create_shouldAcceptEmptyDesignations() throws Exception {
        // ISO/IEC 11179-3:2023 §6.4：Item 不强制要求 designation；空集合法。
        ConstraintSetRequest req = new ConstraintSetRequest(List.of(), null, null, true, true, true);

        mockMvc.perform(post("/api/v1/constraint-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
 
     @Test
     void update_shouldReturnOk() throws Exception {
         ConstraintSetRequest req = new ConstraintSetRequest(
                 List.of(new TextLine(LANG_ID, "Updated", null, null)),
                 null, null, false, true, false);
         when(service.update(eq(CS_ID), any())).thenReturn(createResponse());
 
         mockMvc.perform(put("/api/v1/constraint-sets/{id}", CS_ID)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isOk());
     }
 
     @Test
     void delete_shouldReturnOk() throws Exception {
         mockMvc.perform(delete("/api/v1/constraint-sets/{id}", CS_ID))
                 .andExpect(status().isOk());
     }
 
     private ConstraintSetResponse createResponse() {
         return new ConstraintSetResponse(CS_ID, "默认约束集", true, true, true,
                 List.of(), List.of(), null);
     }
 }
