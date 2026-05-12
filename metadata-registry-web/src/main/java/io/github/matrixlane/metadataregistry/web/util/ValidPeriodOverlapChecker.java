package io.github.matrixlane.metadataregistry.web.util;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DataTimePeriod;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;

import java.time.LocalDateTime;
import java.util.List;

public class ValidPeriodOverlapChecker {

    public static void checkNoOverlaps(List<DataTimePeriod> periods) {
        if (periods == null || periods.size() < 2) {
            return;
        }
        for (int i = 0; i < periods.size(); i++) {
            DataTimePeriod a = periods.get(i);
            for (int j = i + 1; j < periods.size(); j++) {
                DataTimePeriod b = periods.get(j);
                if (overlaps(a, b)) {
                    throw new BusinessException("有效时间段存在重叠");
                }
            }
        }
    }

    private static boolean overlaps(DataTimePeriod a, DataTimePeriod b) {
        LocalDateTime aStart = a.getStartDateTime();
        LocalDateTime aEnd = a.getEndDateTime();
        LocalDateTime bStart = b.getStartDateTime();
        LocalDateTime bEnd = b.getEndDateTime();

        // Open-ended periods: if both ends are null, they always overlap
        // If one end is null, it extends to infinity
        if (aStart == null && bStart == null) {
            return true;
        }
        if (aStart == null || bStart == null) {
            // One side open-ended start -- treat as overlapping if the bounded side hasn't ended before the other starts
            // Simplified: if either has no boundaries, they overlap
            return !(aEnd != null && bEnd != null && aEnd.isBefore(bStart != null ? bStart : aEnd.plusSeconds(1)) && bEnd.isBefore(aStart != null ? aStart : bEnd.plusSeconds(1)));
        }

        // Standard interval overlap: [aStart, aEnd] and [bStart, bEnd]
        // They overlap if aStart <= bEnd AND bStart <= aEnd
        // Handle null ends as +infinity
        boolean aBeforeB = aEnd != null && aEnd.isBefore(bStart);
        boolean bBeforeA = bEnd != null && bEnd.isBefore(aStart);
        return !aBeforeB && !bBeforeA;
    }
}
