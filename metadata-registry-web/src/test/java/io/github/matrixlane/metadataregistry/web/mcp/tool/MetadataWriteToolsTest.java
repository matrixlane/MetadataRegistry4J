package io.github.matrixlane.metadataregistry.web.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantJobService;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantTask;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitRequest;
import io.github.matrixlane.metadataregistry.web.mcp.McpTool;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.ContactAccountService;
import io.github.matrixlane.metadataregistry.web.service.ConceptualDomainService;
import io.github.matrixlane.metadataregistry.web.service.DataElementConceptService;
import io.github.matrixlane.metadataregistry.web.service.DataElementService;
import io.github.matrixlane.metadataregistry.web.service.DimensionalityService;
import io.github.matrixlane.metadataregistry.web.service.MeasureClassService;
import io.github.matrixlane.metadataregistry.web.service.ObjectClassService;
import io.github.matrixlane.metadataregistry.web.service.PropertyService;
import io.github.matrixlane.metadataregistry.web.service.UnitOfMeasureService;
import io.github.matrixlane.metadataregistry.web.service.ValueDomainService;
import io.github.matrixlane.metadataregistry.web.service.WorkspaceService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 受控写入工具（add-mcp-server-support 任务 5.x）：白名单路由、payload 校验、
 * 状态/归属守卫、工作空间委托、推断任务动态清单。
 */
class MetadataWriteToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ObjectClassService objectClassService = mock(ObjectClassService.class);
    private final PropertyService propertyService = mock(PropertyService.class);
    private final DataElementConceptService dataElementConceptService = mock(DataElementConceptService.class);
    private final ConceptualDomainService conceptualDomainService = mock(ConceptualDomainService.class);
    private final ValueDomainService valueDomainService = mock(ValueDomainService.class);
    private final DataElementService dataElementService = mock(DataElementService.class);
    private final UnitOfMeasureService unitOfMeasureService = mock(UnitOfMeasureService.class);
    private final MeasureClassService measureClassService = mock(MeasureClassService.class);
    private final DimensionalityService dimensionalityService = mock(DimensionalityService.class);
    private final WorkspaceService workspaceService = mock(WorkspaceService.class);
    private final ActiveRegistrarService activeRegistrarService = mock(ActiveRegistrarService.class);
    private final AssistantJobService jobService = mock(AssistantJobService.class);
    private final io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository administeredItemRepository =
            mock(io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository.class);

    private final DraftItemRoutes routes = new DraftItemRoutes(
            objectMapper, Validation.buildDefaultValidatorFactory().getValidator(),
            objectClassService, propertyService, dataElementConceptService, conceptualDomainService,
            valueDomainService, dataElementService, unitOfMeasureService, measureClassService,
            dimensionalityService);

    private final ContactAccountService contactAccountService = mock(ContactAccountService.class);
    private final McpTool draftTool = new DraftItemTool(routes,
            new McpDraftOwnershipPrefill(activeRegistrarService, contactAccountService));
    private final McpTool updateTool = new UpdateDraftTool(routes, administeredItemRepository, activeRegistrarService);
    private final McpTool submitTool = new SubmitDraftsTool(workspaceService, activeRegistrarService, objectMapper);
    private final McpTool requestCandidateTool = new RequestCandidateTool(workspaceService, activeRegistrarService, objectMapper);
    private final McpTool rollbackTool = new RollbackItemTool(workspaceService, activeRegistrarService);
    private final McpTool runTaskTool = new RunInferenceTaskTool(jobService, objectMapper);

    @Test
    void draftItem_whitelistRouting_shouldDelegateToMatchingService() {
        Map<String, Object> payload = Map.of("designations", List.of(Map.of("value", "体重")));
        io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse created =
                mock(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse.class);
        when(objectClassService.create(any())).thenReturn(created);

        Object result = draftTool.handle(Map.of("itemType", "objectclass", "payload", payload));
        assertThat(result).isSameAs(created);
        verify(objectClassService).create(any());
    }

    @Test
    void draftItem_typeMatching_shouldBeCaseAndSeparatorInsensitive() {
        draftTool.handle(Map.of("itemType", "Data_Element",
                "payload", Map.of("designations", List.of(Map.of("value", "v")))));
        verify(dataElementService).create(any());
    }

    @Test
    void draftItem_typeOutsideWhitelist_shouldRejectWithoutAnyWrite() {
        assertThatThrownBy(() -> draftTool.handle(Map.of("itemType", "Contact", "payload", Map.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的条目类型");
        verify(dataElementService, never()).create(any());
        verify(objectClassService, never()).create(any());
    }

    @Test
    void draftItem_malformedPayload_shouldReturn422() {
        // designations 必须是数组：结构不匹配 → 422
        assertThatThrownBy(() -> draftTool.handle(Map.of(
                "itemType", "objectclass",
                "payload", Map.of("designations", "not-a-list"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ApiCodes.VALIDATION_FAILED);
    }

    @Test
    void draftItem_withoutStewardship_shouldPrefillDefaultRegistrarOwnership() {
        // 归属预填：业务面 UI 预填默认联系人；MCP 无前端，须工具层补等价语义
        UUID accountId = UUID.randomUUID();
        UUID registrarContactId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar registrar =
                mock(io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar.class);
        when(registrar.getId()).thenReturn(registrarContactId);
        when(contactAccountService.defaultRegistrarOf(accountId)).thenReturn(java.util.Optional.of(registrar));

        org.mockito.ArgumentCaptor<io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest> captor =
                org.mockito.ArgumentCaptor.forClass(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest.class);
        when(objectClassService.create(captor.capture()))
                .thenReturn(mock(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse.class));

        draftTool.handle(Map.of("itemType", "objectclass",
                "payload", Map.of("designations", List.of(Map.of("value", "v")))));

        assertThat(captor.getValue().registration().stewardship()).isNotNull();
        assertThat(captor.getValue().registration().stewardship().contactId()).isEqualTo(registrarContactId);
    }

    @Test
    void draftItem_withExplicitStewardship_shouldRespectCallerValue() {
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        UUID explicitContact = UUID.randomUUID();
        org.mockito.ArgumentCaptor<io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest> captor =
                org.mockito.ArgumentCaptor.forClass(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest.class);
        when(objectClassService.create(captor.capture()))
                .thenReturn(mock(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse.class));

        draftTool.handle(Map.of("itemType", "objectclass", "payload", Map.of(
                "designations", List.of(Map.of("value", "v")),
                "registration", Map.of("stewardship", Map.of("contactId", explicitContact.toString())))));

        assertThat(captor.getValue().registration().stewardship().contactId()).isEqualTo(explicitContact);
    }

    @Test
    void draftItem_withoutRegistrationBlock_shouldSynthesizeDefaultForClosure() {
        // 正交链完整性：缺省 registration 时合成默认块，杜绝"裸内容链条目"
        // （2026-10-09 受管链闭环事故的不变式违反形态）
        org.mockito.ArgumentCaptor<io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest> captor =
                org.mockito.ArgumentCaptor.forClass(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest.class);
        when(objectClassService.create(captor.capture()))
                .thenReturn(mock(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse.class));

        draftTool.handle(Map.of("itemType", "objectclass",
                "payload", Map.of("designations", List.of(Map.of("value", "v")))));

        assertThat(captor.getValue().registration()).isNotNull();
        assertThat(captor.getValue().registration().namespaceId()).isNull();
    }

    @Test
    void updateDraft_allowedStatus_shouldProceed() {
        UUID itemId = UUID.randomUUID();
        when(administeredItemRepository.findMetaByItemId(itemId))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), null, null, null, null, null,
                        AdministrativeStatus.DRAFT, null, null, null, null, null}));
        io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse updated =
                mock(io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse.class);
        when(objectClassService.update(eq(itemId), any())).thenReturn(updated);

        Object result = updateTool.handle(updateArgs(itemId));
        assertThat(result).isSameAs(updated);
        verify(activeRegistrarService).assertCanModifyItem(itemId);
    }

    @Test
    void updateDraft_initialStatus_shouldProceed() {
        UUID itemId = UUID.randomUUID();
        when(administeredItemRepository.findMetaByItemId(itemId))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), null, null, null, null, null,
                        AdministrativeStatus.INITIAL, null, null, null, null, null}));
        updateTool.handle(updateArgs(itemId));
        verify(objectClassService).update(eq(itemId), any());
    }

    @Test
    void updateDraft_nonDraftStatus_shouldRejectWithoutWrite() {
        UUID itemId = UUID.randomUUID();
        when(administeredItemRepository.findMetaByItemId(itemId))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), null, null, null, null, null,
                        AdministrativeStatus.APPROVED_AS_RECORDED, null, null, null, null, null}));
        assertThatThrownBy(() -> updateTool.handle(Map.of(
                "itemId", itemId.toString(), "itemType", "objectclass", "payload", Map.of("designations", List.of()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("草稿/未完成");
        verify(objectClassService, never()).update(any(), any());
    }

    @Test
    void updateDraft_unadministeredItem_shouldReturn404() {
        UUID itemId = UUID.randomUUID();
        when(administeredItemRepository.findMetaByItemId(itemId)).thenReturn(List.of());
        assertThatThrownBy(() -> updateTool.handle(Map.of(
                "itemId", itemId.toString(), "itemType", "objectclass", "payload", Map.of("designations", List.of()))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ApiCodes.NOT_FOUND);
    }

    @Test
    void updateDraft_ownershipViolation_shouldPropagateWithoutWrite() {
        UUID itemId = UUID.randomUUID();
        when(administeredItemRepository.findMetaByItemId(itemId))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), null, null, null, null, null,
                        AdministrativeStatus.DRAFT, null, null, null, null, null}));
        org.mockito.Mockito.doThrow(new BusinessException(ApiCodes.FORBIDDEN, "无权修改该条目"))
                .when(activeRegistrarService).assertCanModifyItem(itemId);
        assertThatThrownBy(() -> updateTool.handle(Map.of(
                "itemId", itemId.toString(), "itemType", "objectclass", "payload", Map.of("designations", List.of()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权修改");
        verify(objectClassService, never()).update(any(), any());
    }

    private static Map<String, Object> updateArgs(UUID itemId) {
        return Map.of("itemId", itemId.toString(), "itemType", "objectclass",
                "payload", Map.of("designations", List.of(Map.of("value", "v"))));
    }

    @Test
    void submitDrafts_shouldParseIdsAndDelegate() {
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        submitTool.handle(Map.of("itemIds", id1 + ", " + id2));
        verify(workspaceService).submit(any(WorkspaceSubmitRequest.class), eq(accountId));
    }

    @Test
    void submitDrafts_illegalUuid_shouldReject() {
        assertThatThrownBy(() -> submitTool.handle(Map.of("itemIds", "not-a-uuid")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法条目 UUID");
        verify(workspaceService, never()).submit(any(), any());
    }

    @Test
    void requestCandidate_shouldDelegate() {
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        UUID itemId = UUID.randomUUID();
        Object result = requestCandidateTool.handle(Map.of("itemId", itemId.toString()));
        assertThat(result).isInstanceOf(Map.class);
        verify(workspaceService).requestCandidate(eq(itemId), isNull(), eq(accountId));
    }

    @Test
    void rollback_shouldDelegate() {
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        UUID itemId = UUID.randomUUID();
        rollbackTool.handle(Map.of("itemId", itemId.toString()));
        verify(workspaceService).rollback(itemId, accountId);
    }

    @Test
    void runInferenceTask_unknownTask_shouldRejectBeforeCreatingJob() {
        when(jobService.tasks()).thenReturn(List.of());
        assertThatThrownBy(() -> runTaskTool.handle(Map.of("taskId", "complete-metadata")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知任务");
        verify(jobService, never()).createJob(any(), any(), any(), any(), any());
    }

    @Test
    void runInferenceTask_knownTask_shouldCreateJob() {
        AssistantTask task = mock(AssistantTask.class);
        when(task.id()).thenReturn("complete-metadata");
        when(jobService.tasks()).thenReturn(List.of(task));
        UUID jobId = UUID.randomUUID();
        when(jobService.createJob(eq("complete-metadata"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(jobId);

        Object result = runTaskTool.handle(Map.of("taskId", "complete-metadata"));
        assertThat(result).isEqualTo(Map.of("jobId", jobId.toString()));
    }

    @Test
    void runInferenceTask_featureDisabled_shouldTranslateFromExistingLayer() {
        AssistantTask task = mock(AssistantTask.class);
        when(task.id()).thenReturn("complete-metadata");
        when(jobService.tasks()).thenReturn(List.of(task));
        when(jobService.createJob(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ApiCodes.FEATURE_NOT_ENABLED, "AI 元数据推断功能未启用"));
        assertThatThrownBy(() -> runTaskTool.handle(Map.of("taskId", "complete-metadata")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("功能未启用");
    }
}
