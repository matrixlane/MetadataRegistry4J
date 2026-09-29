package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.service.snapshot.ConceptualDomainSnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.DataElementConceptSnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.DataElementSnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.DataTypeSnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.MeasureClassSnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.ObjectClassSnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.PropertySnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.UnitOfMeasureSnapshotSupport;
import io.github.matrixlane.metadataregistry.web.service.snapshot.ValueDomainSnapshotSupport;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 工作空间回退服务：将 Recorded 及以上注册状态且处于 Draft/Rejected 的管理项
 * 恢复到最近一条 BASELINE 快照内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceRollbackService {

    private static final Set<RegistrationStatus> ROLLBACKABLE_REGISTRATION_STATUSES = Set.of(
            // ISO/IEC 11179-6:2023 4.3.3.1.3：Candidate 起即受版本控制，故回退窗口按标准字面取 Candidate 起
            RegistrationStatus.CANDIDATE,
            RegistrationStatus.RECORDED,
            RegistrationStatus.QUALIFIED,
            RegistrationStatus.STANDARD,
            RegistrationStatus.PREFERRED_STANDARD,
            RegistrationStatus.SUPERSEDED,
            RegistrationStatus.RETIRED
    );

    private final AdministeredItemRepository administeredItemRepository;
    private final VersionHistoryService versionHistoryService;
    private final AdministrativeStatusService administrativeStatusService;
    private final ItemAdministrationService itemAdministrationService;
    private final ObjectMapper objectMapper;

    private final DataElementSnapshotSupport dataElementSnapshotSupport;
    private final ValueDomainSnapshotSupport valueDomainSnapshotSupport;
    private final DataElementConceptSnapshotSupport dataElementConceptSnapshotSupport;
    private final ObjectClassSnapshotSupport objectClassSnapshotSupport;
    private final PropertySnapshotSupport propertySnapshotSupport;
    private final ConceptualDomainSnapshotSupport conceptualDomainSnapshotSupport;
    private final DataTypeSnapshotSupport dataTypeSnapshotSupport;
    private final UnitOfMeasureSnapshotSupport unitOfMeasureSnapshotSupport;
    private final MeasureClassSnapshotSupport measureClassSnapshotSupport;

    @Transactional
    public void rollback(UUID itemId) {
        AdministeredItem administeredItem = administeredItemRepository.findByItem_Id(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("AdministeredItem", itemId));
        RegistrationState state = administeredItem.getRegistrationState();
        if (state == null) {
            throw new BusinessException(ApiCodes.INVALID_ROLLBACK_STATE, "管理项无注册状态，无法回退");
        }

        RegistrationStatus registrationStatus = state.getRegistrationStatus();
        AdministrativeStatus administrativeStatus = state.getAdministrativeStatus();
        validateRollbackState(registrationStatus, administrativeStatus);

        Optional<VersionHistory> baselineOpt = versionHistoryService.findLatestBaseline(itemId);
        if (baselineOpt.isEmpty()) {
            if (administrativeStatus == AdministrativeStatus.REJECTED) {
                administrativeStatusService.clear(administeredItem);
                return;
            }
            throw new BusinessException(ApiCodes.NO_VERSION_TO_ROLLBACK, "无基线快照可回退");
        }

        VersionHistory baseline = baselineOpt.get();
        JsonNode itemJson = extractItemNode(baseline.getSnapshotJson());

        itemAdministrationService.setRestoreInProgress(true);
        try {
            dispatchRestore(itemId, baseline.getItemType(), itemJson);
        } finally {
            itemAdministrationService.setRestoreInProgress(false);
        }

        administrativeStatusService.clear(administeredItem);
    }

    private void validateRollbackState(RegistrationStatus registrationStatus,
                                       AdministrativeStatus administrativeStatus) {
        if (!ROLLBACKABLE_REGISTRATION_STATUSES.contains(registrationStatus)) {
            throw new BusinessException(ApiCodes.INVALID_ROLLBACK_STATE,
                    "当前注册状态不允许回退：" + registrationStatus);
        }
        if (administrativeStatus != AdministrativeStatus.DRAFT
                && administrativeStatus != AdministrativeStatus.REJECTED) {
            throw new BusinessException(ApiCodes.INVALID_ROLLBACK_STATE,
                    "当前管理状态不允许回退：" + administrativeStatus);
        }
    }

    @SneakyThrows
    private JsonNode extractItemNode(String snapshotJson) {
        JsonNode root = objectMapper.readTree(snapshotJson);
        JsonNode item = root.get("item");
        if (item == null) {
            throw new BusinessException(ApiCodes.INTERNAL_ERROR, "基线快照缺少 item 内容");
        }
        return item;
    }

    private void dispatchRestore(UUID itemId, String itemType, JsonNode itemJson) {
        switch (itemType) {
            case "DataElement" -> dataElementSnapshotSupport.restore(itemId, itemJson);
            case "ValueDomain" -> valueDomainSnapshotSupport.restore(itemId, itemJson);
            case "DataElementConcept" -> dataElementConceptSnapshotSupport.restore(itemId, itemJson);
            case "ObjectClass" -> objectClassSnapshotSupport.restore(itemId, itemJson);
            case "Property" -> propertySnapshotSupport.restore(itemId, itemJson);
            case "ConceptualDomain" -> conceptualDomainSnapshotSupport.restore(itemId, itemJson);
            case "DataType" -> dataTypeSnapshotSupport.restore(itemId, itemJson);
            case "UnitOfMeasure" -> unitOfMeasureSnapshotSupport.restore(itemId, itemJson);
            case "MeasureClass" -> measureClassSnapshotSupport.restore(itemId, itemJson);
            default -> throw new BusinessException(ApiCodes.INVALID_ROLLBACK_STATE,
                    "不支持的回退项类型：" + itemType);
        }
    }
}
