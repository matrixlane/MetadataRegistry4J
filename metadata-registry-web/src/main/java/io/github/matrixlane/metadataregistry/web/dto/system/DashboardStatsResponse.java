package io.github.matrixlane.metadataregistry.web.dto.system;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 概览页统计数据（{@code GET /api/v1/dashboard}）。
 *
 * <p>前 8 个计数字段与接口历史响应的 Map key 同名；{@code administeredItems} 为受管项总数，
 * {@code *CreatedThisWeek} 为近 7 天创建数（"本周"按滚动 7 天窗口计），
 * {@code registrationStatusDistribution} 为注册状态分组计数（{@code status} 为
 * {@code RegistrationStatus} 枚举名，缺失状态为 {@code null}），
 * {@code recentChanges} 为按最近变更时间倒序的注册项变更。</p>
 */
public record DashboardStatsResponse(
        long dataElements,
        long objectClasses,
        long properties,
        long valueDomains,
        long conceptualDomains,
        long dataTypes,
        long organizations,
        long contexts,
        long administeredItems,
        long dataElementConcepts,
        long dataElementsCreatedThisWeek,
        long objectClassesCreatedThisWeek,
        long propertiesCreatedThisWeek,
        long valueDomainsCreatedThisWeek,
        long conceptualDomainsCreatedThisWeek,
        long dataElementConceptsCreatedThisWeek,
        long administeredItemsCreatedThisWeek,
        List<StatusCount> registrationStatusDistribution,
        List<RecentChangeItem> recentChanges) {

    public record StatusCount(String status, long count) {
    }

    public record RecentChangeItem(UUID itemId, String name, LocalDateTime changedAt, String changeDescription) {
    }
}
