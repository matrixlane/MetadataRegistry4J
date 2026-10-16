package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.system.DashboardStatsResponse;
import io.github.matrixlane.metadataregistry.web.dto.system.DashboardStatsResponse.RecentChangeItem;
import io.github.matrixlane.metadataregistry.web.dto.system.DashboardStatsResponse.StatusCount;
import io.github.matrixlane.metadataregistry.web.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "概览", description = "Dashboard 统计信息")
public class DashboardController {

    /** "近期注册项变更"时间线条数。 */
    private static final int RECENT_CHANGES_LIMIT = 8;

    /** "本周新增"的滚动时间窗口。 */
    private static final int THIS_WEEK_DAYS = 7;

    private final DataElementRepository dataElementRepository;
    private final ObjectClassRepository objectClassRepository;
    private final PropertyRepository propertyRepository;
    private final ValueDomainRepository valueDomainRepository;
    private final ConceptualDomainRepository conceptualDomainRepository;
    private final DataTypeRepository dataTypeRepository;
    private final OrganizationRepository organizationRepository;
    private final ContextRepository contextRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final DesignationRepository designationRepository;
    private final DataElementConceptRepository dataElementConceptRepository;

    @GetMapping
    @Operation(summary = "获取 Dashboard 统计数据")
    public ApiResponse<DashboardStatsResponse> getStats() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(THIS_WEEK_DAYS);
        List<RecentChangeItem> recentChanges = loadRecentChanges();
        return ApiResponse.ok(new DashboardStatsResponse(
                dataElementRepository.count(),
                objectClassRepository.count(),
                propertyRepository.count(),
                valueDomainRepository.count(),
                conceptualDomainRepository.count(),
                dataTypeRepository.count(),
                organizationRepository.count(),
                contextRepository.count(),
                administeredItemRepository.count(),
                dataElementConceptRepository.count(),
                administeredItemRepository.countCreatedAfterDataElement(weekAgo),
                administeredItemRepository.countCreatedAfterObjectClass(weekAgo),
                administeredItemRepository.countCreatedAfterProperty(weekAgo),
                administeredItemRepository.countCreatedAfterValueDomain(weekAgo),
                administeredItemRepository.countCreatedAfterConceptualDomain(weekAgo),
                administeredItemRepository.countCreatedAfterDataElementConcept(weekAgo),
                administeredItemRepository.countByCreationDateTimeAfter(weekAgo),
                loadStatusDistribution(),
                recentChanges));
    }

    private List<StatusCount> loadStatusDistribution() {
        return administeredItemRepository.countGroupedByRegistrationStatus().stream()
                .map(row -> new StatusCount(
                        row[0] == null ? null : row[0].toString(),
                        (Long) row[1]))
                .toList();
    }

    private List<RecentChangeItem> loadRecentChanges() {
        List<Object[]> rows = administeredItemRepository.findRecentChanges(
                PageRequest.of(0, RECENT_CHANGES_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> itemIds = rows.stream().map(row -> (UUID) row[0]).toList();
        Map<UUID, String> namesByItemId = new HashMap<>();
        // 查询已按条目分组 + 语言优先级排序，每组首行即首选指称
        for (Object[] row : designationRepository.findSignValuesByItemIdsOrdered(itemIds)) {
            namesByItemId.putIfAbsent((UUID) row[0], (String) row[1]);
        }
        return rows.stream()
                .map(row -> new RecentChangeItem(
                        (UUID) row[0],
                        namesByItemId.get((UUID) row[0]),
                        (LocalDateTime) row[1],
                        (String) row[2]))
                .toList();
    }
}
