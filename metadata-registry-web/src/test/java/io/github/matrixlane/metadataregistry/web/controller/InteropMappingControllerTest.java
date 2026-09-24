package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.itemmapping.entity.InteropMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import io.github.matrixlane.metadataregistry.itemmapping.service.InteropMappingResultService;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.interopemapping.ManualMapRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InteropMappingController.class)
@AutoConfigureMockMvc(addFilters = false)
class InteropMappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InteropMappingResultService interopMappingResultService;

    @MockBean
    private io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataElementRepository interopDataElementRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    // ── Helpers ─────────────────────────────────────────────────────

    private InteropMapping createMapping(UUID id, InteropMappingStatus status, BigDecimal score) {
        InteropMapping mapping = new InteropMapping(
                UUID.randomUUID(),
                UUID.randomUUID(),
                score,
                "NAME_SIMILARITY",
                status,
                "test reason",
                UUID.randomUUID()
        );
        mapping.setId(id);
        mapping.setVersion(1L);
        return mapping;
    }

    // ── List Tests ──────────────────────────────────────────────────

    @Test
    void list_shouldReturnPageOfMappings() throws Exception {
        UUID datasetId = UUID.randomUUID();
        InteropMapping mapping = createMapping(UUID.randomUUID(), InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        Page<InteropMapping> page = new PageImpl<>(List.of(mapping));

        when(interopMappingResultService.listMappings(eq(datasetId), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/interop-mappings")
                        .param("datasetId", datasetId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].status", is("PENDING")));
    }

    @Test
    void get_shouldReturnMapping() throws Exception {
        UUID id = UUID.randomUUID();
        InteropMapping mapping = createMapping(id, InteropMappingStatus.CONFIRMED, BigDecimal.valueOf(0.90));

        when(interopMappingResultService.getMapping(id)).thenReturn(mapping);

        mockMvc.perform(get("/api/v1/interop-mappings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }

    // ── Confirm / Reject Tests ──────────────────────────────────────

    @Test
    void confirm_shouldReturnConfirmedMapping() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping mapping = createMapping(id, InteropMappingStatus.CONFIRMED, BigDecimal.valueOf(0.90));

        when(interopMappingResultService.confirmMapping(id, userId)).thenReturn(mapping);

        mockMvc.perform(put("/api/v1/interop-mappings/{id}/confirm", id)
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }

    @Test
    void reject_shouldReturnRejectedMapping() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping mapping = createMapping(id, InteropMappingStatus.REJECTED, BigDecimal.valueOf(0.50));

        when(interopMappingResultService.rejectMapping(id, userId)).thenReturn(mapping);

        mockMvc.perform(put("/api/v1/interop-mappings/{id}/reject", id)
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));
    }

    // ── Manual Map Tests ────────────────────────────────────────────

    @Test
    void manualMap_shouldReturnRemappedMapping() throws Exception {
        UUID id = UUID.randomUUID();
        UUID newLocalItemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping mapping = createMapping(id, InteropMappingStatus.CONFIRMED, BigDecimal.valueOf(0.85));
        mapping.setLocalItemId(newLocalItemId);

        when(interopMappingResultService.manualMap(id, newLocalItemId, userId)).thenReturn(mapping);

        mockMvc.perform(put("/api/v1/interop-mappings/{id}/manual-map", id)
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ManualMapRequest(newLocalItemId)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.localItemId", is(newLocalItemId.toString())));
    }

    // ── Undo Tests ──────────────────────────────────────────────────

    @Test
    void undo_shouldReturnRestoredMapping() throws Exception {
        UUID id = UUID.randomUUID();
        InteropMapping mapping = createMapping(id, InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));

        when(interopMappingResultService.undoMapping(id)).thenReturn(mapping);

        mockMvc.perform(delete("/api/v1/interop-mappings/{id}/undo", id)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    // ── Mark Unmatched Tests ────────────────────────────────────────

    @Test
    void markUnmatched_shouldReturnUnmatchedMapping() throws Exception {
        UUID id = UUID.randomUUID();
        InteropMapping mapping = createMapping(id, InteropMappingStatus.UNMATCHED, BigDecimal.valueOf(0.00));

        when(interopMappingResultService.markUnmatched(id)).thenReturn(mapping);

        mockMvc.perform(put("/api/v1/interop-mappings/{id}/mark-unmatched", id)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("UNMATCHED")));
    }

    // ── Batch Tests ─────────────────────────────────────────────────

    @Test
    void countPending_shouldReturnCount() throws Exception {
        UUID datasetId = UUID.randomUUID();
        when(interopMappingResultService.countPendingByDataset(datasetId)).thenReturn(42L);

        mockMvc.perform(get("/api/v1/interop-mappings/batch/pending-count")
                        .param("datasetId", datasetId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", is(42)));
    }

    @Test
    void batchConfirm_shouldReturnBatchOperation() throws Exception {
        UUID datasetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        when(interopMappingResultService.countPendingByDataset(datasetId)).thenReturn(5L);
        when(interopMappingResultService.batchConfirm(datasetId, userId)).thenReturn(batchId);

        mockMvc.perform(post("/api/v1/interop-mappings/batch-confirm")
                        .param("datasetId", datasetId.toString())
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchOperationId", is(batchId.toString())))
                .andExpect(jsonPath("$.data.affectedCount", is(5)))
                .andExpect(jsonPath("$.data.operationType", is("CONFIRM")));
    }

    @Test
    void batchReject_shouldReturnBatchOperation() throws Exception {
        UUID datasetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        when(interopMappingResultService.countPendingByDataset(datasetId)).thenReturn(3L);
        when(interopMappingResultService.batchReject(datasetId, userId)).thenReturn(batchId);

        mockMvc.perform(post("/api/v1/interop-mappings/batch-reject")
                        .param("datasetId", datasetId.toString())
                        .param("userId", userId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount", is(3)))
                .andExpect(jsonPath("$.data.operationType", is("REJECT")));
    }

    @Test
    void undoBatch_shouldReturnUndoResult() throws Exception {
        UUID batchId = UUID.randomUUID();

        when(interopMappingResultService.undoBatchOperation(batchId)).thenReturn(5);

        mockMvc.perform(post("/api/v1/interop-mappings/batch-undo")
                        .param("batchOperationId", batchId.toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount", is(5)))
                .andExpect(jsonPath("$.data.operationType", is("UNDO")));
    }

    // ── Quality Score Tests ─────────────────────────────────────────

    @Test
    void qualityScore_shouldReturnScore() throws Exception {
        UUID datasetId = UUID.randomUUID();
        var result = new InteropMappingResultService.QualityScoreResult(
                datasetId, 100, 70, 20, 10, 80.0);

        when(interopMappingResultService.calculateQualityScore(datasetId)).thenReturn(result);

        mockMvc.perform(get("/api/v1/interop-mappings/quality-score")
                        .param("datasetId", datasetId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount", is(100)))
                .andExpect(jsonPath("$.data.matchedCount", is(70)))
                .andExpect(jsonPath("$.data.pendingCount", is(20)))
                .andExpect(jsonPath("$.data.unmatchedCount", is(10)))
                .andExpect(jsonPath("$.data.score", is(80.0)))
                .andExpect(jsonPath("$.data.matchedRate", is(70.0)))
                .andExpect(jsonPath("$.data.pendingRate", is(20.0)))
                .andExpect(jsonPath("$.data.unmatchedRate", is(10.0)));
    }

    // ── Empty State Tests ───────────────────────────────────────────

    @Test
    void emptyCheck_shouldReturnEmptyState() throws Exception {
        UUID datasetId = UUID.randomUUID();

        when(interopMappingResultService.isDatasetEmpty(datasetId)).thenReturn(true);
        when(interopMappingResultService.hasEmptyLocalMetadata(datasetId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/interop-mappings/empty-check")
                        .param("datasetId", datasetId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isEmpty", is(true)))
                .andExpect(jsonPath("$.data.hasEmptyLocalMetadata", is(false)))
                .andExpect(jsonPath("$.data.message").exists());
    }
}
