package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.service.MetadataSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataDictionaryBrowserController.class)
@AutoConfigureMockMvc(addFilters = false)
class DataDictionaryBrowserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetadataSearchService searchService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID TEST_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void browse_withPageAndSize_shouldReturnOk() throws Exception {
        AdministeredItemResponse resp = createResponse();
        Page<AdministeredItemResponse> page = new PageImpl<>(List.of(resp));
        when(searchService.search(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/data-dictionary/browser")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content[0].itemLabel").value("患者姓名"));
    }

    @Test
    void browse_withNoParams_shouldReturnOk() throws Exception {
        AdministeredItemResponse resp = createResponse();
        Page<AdministeredItemResponse> page = new PageImpl<>(List.of(resp));
        when(searchService.search(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/data-dictionary/browser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(TEST_ID.toString()));
    }

    @Test
    void browse_withKeyword_shouldReturnOk() throws Exception {
        AdministeredItemResponse resp = createResponse();
        Page<AdministeredItemResponse> page = new PageImpl<>(List.of(resp));
        when(searchService.search(anyString(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/data-dictionary/browser")
                        .param("q", "患者"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].itemLabel").value("患者姓名"));
    }

    @Test
    void browse_withTypeFilter_shouldReturnOk() throws Exception {
        AdministeredItemResponse resp = createResponse();
        Page<AdministeredItemResponse> page = new PageImpl<>(List.of(resp));
        when(searchService.search(any(), anyString(), any(), any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/data-dictionary/browser")
                        .param("type", "DataElement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].itemType").value("DataElement"));
    }

    @Test
    void browse_withStatusFilter_shouldReturnOk() throws Exception {
        AdministeredItemResponse resp = createResponse();
        Page<AdministeredItemResponse> page = new PageImpl<>(List.of(resp));
        when(searchService.search(any(), any(), any(RegistrationStatus.class), any(), any(), any(), anyBoolean(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/data-dictionary/browser")
                        .param("status", "STANDARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].registrationStatus").value("STANDARD"));
    }

    private AdministeredItemResponse createResponse() {
        return new AdministeredItemResponse(
                TEST_ID,
                UUID.randomUUID(),
                "DataElement",
                "患者姓名",
                "DE-PATIENT-NAME",
                "1.0",
                null, null, null, null,
                RegistrationStatus.STANDARD,
                LocalDateTime.now(),
                "自动生成",
                null, null, null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "初始版本",
                null);
    }
}
