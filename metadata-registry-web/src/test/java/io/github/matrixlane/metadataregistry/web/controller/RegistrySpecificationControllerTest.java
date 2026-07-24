 package io.github.matrixlane.metadataregistry.web.controller;
 
 import com.fasterxml.jackson.databind.ObjectMapper;
 import io.github.matrixlane.metadataregistry.web.auth.JwtService;
 import io.github.matrixlane.metadataregistry.web.config.AppProperties;
 import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
 import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationRequest;
 import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationResponse;
 import io.github.matrixlane.metadataregistry.web.service.RegistrySpecificationService;
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
 
 @WebMvcTest(RegistrySpecificationController.class)
 @AutoConfigureMockMvc(addFilters = false)
 class RegistrySpecificationControllerTest {
 
     @Autowired
     private MockMvc mockMvc;
 
     @Autowired
     private ObjectMapper objectMapper;
 
     @MockBean
     private RegistrySpecificationService service;
 
     @MockBean
     private JwtService jwtService;
 
     @MockBean
     private AppProperties appProperties;
 
     private static final UUID RS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
     private static final UUID LANG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
 
     @Test
     void list_shouldReturnOk() throws Exception {
         when(service.list(any(), any())).thenReturn(new PageImpl<>(List.of(createResponse())));
 
         mockMvc.perform(get("/api/v1/registry-specifications"))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.content[0].id").value(RS_ID.toString()));
     }
 
     @Test
     void get_shouldReturnOk() throws Exception {
         when(service.get(RS_ID)).thenReturn(createResponse());
 
         mockMvc.perform(get("/api/v1/registry-specifications/{id}", RS_ID))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.id").value(RS_ID.toString()));
     }
 
     @Test
     void create_shouldReturnOk() throws Exception {
         RegistrySpecificationRequest req = createRequest();
         when(service.create(any())).thenReturn(createResponse());
 
         mockMvc.perform(post("/api/v1/registry-specifications")
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.id").value(RS_ID.toString()));
     }
 
     @Test
     void create_shouldRejectMissingName() throws Exception {
         RegistrySpecificationRequest req = new RegistrySpecificationRequest(
                 "", null, null, null, null, "Unicode UTF-8", null, null, null,
                 null, null, null, null, null, null, null,
                 List.of(new TextLine(LANG_ID, "注册规范", null, null)), null, null);
 
         mockMvc.perform(post("/api/v1/registry-specifications")
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isUnprocessableEntity());
     }
 
     @Test
     void update_shouldReturnOk() throws Exception {
         RegistrySpecificationRequest req = createRequest();
         when(service.update(eq(RS_ID), any())).thenReturn(createResponse());
 
         mockMvc.perform(put("/api/v1/registry-specifications/{id}", RS_ID)
                         .contentType(MediaType.APPLICATION_JSON)
                         .content(objectMapper.writeValueAsString(req)))
                 .andExpect(status().isOk());
     }
 
     @Test
     void delete_shouldReturnOk() throws Exception {
         mockMvc.perform(delete("/api/v1/registry-specifications/{id}", RS_ID))
                 .andExpect(status().isOk());
     }
 
     private RegistrySpecificationRequest createRequest() {
         return new RegistrySpecificationRequest(
                 "MetadataRegistry4j",
                 null, null, null, null,
                 "Unicode UTF-8",
                 null, null, null,
                 "ISO/IEC 11179-3:2023",
                 null, null, null, null, null, null,
                 List.of(new TextLine(LANG_ID, "注册规范", null, null)),
                 null, null);
     }
 
     private RegistrySpecificationResponse createResponse() {
         return new RegistrySpecificationResponse(
                 RS_ID, "注册规范", "MetadataRegistry4j", null, null,
                 null, null, List.of(), "Unicode UTF-8", List.of(), null, null,
                 null, "ISO/IEC 11179-3:2023", null, null, null, null, null, null,
                 List.of(), List.of(), null);
     }
 }
