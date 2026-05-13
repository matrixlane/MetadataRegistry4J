package io.github.matrixlane.metadataregistry.itemmapping.quality;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingSchemeRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for evaluating mapping quality and generating quality reports.
 *
 * <p>Evaluates four dimensions:
 * <ul>
 *   <li>Coverage: mapped target elements / total target elements</li>
 *   <li>Completeness: description and transformation rule fill rates</li>
 *   <li>Consistency: symmetry violations, type conflicts, cycle risks</li>
 *   <li>Risk identification: low-confidence or incomplete mappings</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MappingQualityService {

    private final ItemMappingRepository mappingRepository;
    private final MappingSchemeRepository schemeRepository;
    private final ItemMappingService mappingService;

    // Thresholds
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.6;
    private static final double HIGH_RISK_DESCRIPTION_RATE = 0.3;

    public MappingQualityService(ItemMappingRepository mappingRepository,
                                 MappingSchemeRepository schemeRepository,
                                 ItemMappingService mappingService) {
        this.mappingRepository = mappingRepository;
        this.schemeRepository = schemeRepository;
        this.mappingService = mappingService;
    }

    /**
     * Generates a quality report for a mapping scheme.
     *
     * @param schemeId the UUID of the mapping scheme
     * @return the quality report
     * @throws IllegalArgumentException if the scheme does not exist
     */
    public MappingQualityReport generateReport(UUID schemeId) {
        MappingScheme scheme = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping scheme not found: " + schemeId));

        List<ItemMapping> mappings = mappingRepository.findByMappingSchemeId(schemeId);

        // Compute metrics
        MappingQualityMetrics metrics = computeMetrics(schemeId, mappings);

        // Detect issues
        List<QualityIssue> issues = detectIssues(mappings);

        // Identify high-risk mappings
        List<UUID> highRiskMappings = identifyHighRiskMappings(mappings, issues);

        // Generate improvement tips
        List<String> tips = generateImprovementTips(metrics, issues, highRiskMappings.size());

        return new MappingQualityReport(
                schemeId,
                scheme.getName(),
                metrics,
                highRiskMappings,
                issues,
                tips,
                LocalDateTime.now().toString()
        );
    }

    /**
     * Computes quality metrics for a mapping scheme.
     *
     * @param schemeId the scheme UUID
     * @param mappings the list of mappings (pre-loaded)
     * @return the quality metrics
     */
    MappingQualityMetrics computeMetrics(UUID schemeId, List<ItemMapping> mappings) {
        long totalMappings = mappings.size();

        if (totalMappings == 0) {
            return new MappingQualityMetrics(0.0, 0.0, 0.0, 100.0, 0.0, 0, 0, 0, 0);
        }

        // Coverage: distinct object item IDs / total mappings is a proxy for now
        // In a real scenario we'd need the total target standard elements count
        // For V1 we approximate: count unique object items as "mapped elements"
        // and total mappings as "target elements" proxy when no external count is available
        long mappedElements = mappings.stream()
                .map(m -> m.getObject().getItemId())
                .filter(id -> id != null)
                .distinct()
                .count();

        // If we can't determine true target count, use mappedElements as both
        // (in a real system, targetElements would come from the target standard)
        long targetElements = Math.max(mappedElements, totalMappings);
        double coverageScore = targetElements == 0 ? 0.0
                : (mappedElements * 100.0 / targetElements);

        // Description rate
        long withDescription = mappings.stream()
                .filter(m -> m.getDescription() != null && !m.getDescription().isBlank())
                .count();
        double descriptionRate = (withDescription * 100.0) / totalMappings;

        // Transformation rule rate (only for mappings that typically need it)
        long transformRelevant = mappings.stream()
                .filter(m -> m.getMappingType() == MappingType.DERIVED
                        || m.getMappingType() == MappingType.AGGREGATED
                        || m.getMappingType() == MappingType.SUBDIVIDED)
                .count();
        long withTransformation = mappings.stream()
                .filter(m -> m.getTransformationRule() != null && !m.getTransformationRule().isBlank())
                .count();
        double transformationRate = transformRelevant == 0 ? 100.0
                : (withTransformation * 100.0 / transformRelevant);

        // Consistency score: 100 - (issues / total * 100)
        List<QualityIssue> issues = detectIssues(mappings);
        int issueCount = issues.size();
        double consistencyScore = Math.max(0.0, 100.0 - (issueCount * 100.0 / totalMappings));

        // Overall score: weighted average
        double overallScore = (coverageScore * 0.25)
                + (descriptionRate * 0.25)
                + (transformationRate * 0.20)
                + (consistencyScore * 0.30);

        return new MappingQualityMetrics(
                coverageScore,
                descriptionRate,
                transformationRate,
                consistencyScore,
                overallScore,
                totalMappings,
                mappedElements,
                targetElements,
                issueCount
        );
    }

    /**
     * Detects quality issues in a list of mappings.
     *
     * <p>V1 consistency rules:
     * <ol>
     *   <li>Symmetry: if A→B is EQUIVALENT, B→A should also be EQUIVALENT (or flagged)</li>
     *   <li>Type mutual exclusion: same pair cannot have multiple conflicting types</li>
     *   <li>Cycle detection: directional mappings forming A→B→C→...→A loops</li>
     *   <li>Missing description</li>
     *   <li>Missing transformation rule for DERIVED/AGGREGATED/SUBDIVIDED</li>
     * </ol>
     *
     * @param mappings the mappings to analyze
     * @return list of detected issues
     */
    List<QualityIssue> detectIssues(List<ItemMapping> mappings) {
        List<QualityIssue> issues = new ArrayList<>();

        // Build lookup maps for efficiency
        Map<Pair, List<ItemMapping>> pairMap = new HashMap<>();
        for (ItemMapping m : mappings) {
            UUID subjectId = m.getSubject().getItemId();
            UUID objectId = m.getObject().getItemId();
            if (subjectId == null || objectId == null) {
                continue;
            }

            // Store both ordered and unordered pairs
            Pair ordered = new Pair(subjectId, objectId);
            pairMap.computeIfAbsent(ordered, k -> new ArrayList<>()).add(m);
        }

        Set<String> processedPairs = new HashSet<>();
        Set<UUID> cycleReportedMappings = new HashSet<>();

        for (ItemMapping m : mappings) {
            UUID sid = m.getSubject().getItemId();
            UUID oid = m.getObject().getItemId();
            if (sid == null || oid == null) {
                continue;
            }

            // 1. Symmetry check for EQUIVALENT
            if (m.getMappingType() == MappingType.EQUIVALENT) {
                Pair reverse = new Pair(oid, sid);
                List<ItemMapping> reverseMappings = pairMap.getOrDefault(reverse, List.of());
                boolean hasEquivalentReverse = reverseMappings.stream()
                        .anyMatch(rm -> rm.getMappingType() == MappingType.EQUIVALENT);

                String pairKey = sid + "-" + oid;
                String reverseKey = oid + "-" + sid;

                if (!hasEquivalentReverse && !processedPairs.contains(reverseKey)) {
                    issues.add(new QualityIssue(
                            m.getId(),
                            IssueSeverity.MEDIUM,
                            "SYMMETRY_VIOLATION",
                            "Equivalent mapping from " + sid + " to " + oid +
                                    " lacks a reverse equivalent mapping.",
                            m.getMappingType()
                    ));
                }
                processedPairs.add(pairKey);
            }

            // 2. Type mutual exclusion check (within same ordered pair)
            Pair ordered = new Pair(sid, oid);
            List<ItemMapping> sameOrdered = pairMap.getOrDefault(ordered, List.of());
            if (sameOrdered.size() > 1) {
                Set<MappingType> types = sameOrdered.stream()
                        .map(ItemMapping::getMappingType)
                        .collect(Collectors.toSet());
                boolean conflictAdded = false;
                for (MappingType t1 : types) {
                    for (MappingType t2 : types) {
                        if (!conflictAdded && t1 != t2 && ItemMappingService.areMutuallyExclusive(t1, t2)) {
                            issues.add(new QualityIssue(
                                    m.getId(),
                                    IssueSeverity.HIGH,
                                    "TYPE_CONFLICT",
                                    "Mutually exclusive mapping types " + t1 + " and " + t2 +
                                            " exist between " + sid + " and " + oid,
                                    m.getMappingType()
                            ));
                            conflictAdded = true;
                            break;
                        }
                    }
                }
            }

            // 3. Missing description
            if (m.getDescription() == null || m.getDescription().isBlank()) {
                issues.add(new QualityIssue(
                        m.getId(),
                        IssueSeverity.LOW,
                        "MISSING_DESCRIPTION",
                        "Mapping has no description.",
                        m.getMappingType()
                ));
            }

            // 4. Missing transformation rule for relevant types
            if ((m.getMappingType() == MappingType.DERIVED
                    || m.getMappingType() == MappingType.AGGREGATED
                    || m.getMappingType() == MappingType.SUBDIVIDED)
                    && (m.getTransformationRule() == null || m.getTransformationRule().isBlank())) {
                issues.add(new QualityIssue(
                        m.getId(),
                        IssueSeverity.MEDIUM,
                        "MISSING_TRANSFORMATION_RULE",
                        "Mapping of type " + m.getMappingType() + " lacks a transformation rule.",
                        m.getMappingType()
                ));
            }
        }

        // 5. Cycle detection for directional mappings
        issues.addAll(detectCycles(mappings, cycleReportedMappings));

        return issues;
    }

    /**
     * Detects cycles in the directional mapping graph.
     *
     * <p>Uses DFS with three-color marking. Only considers internal-to-internal
     * mappings with non-EQUIVALENT types (as EQUIVALENT mappings are bidirectional
     * and not subject to cycle detection per WI-011-1).
     *
     * @param mappings              all mappings to analyze
     * @param alreadyReported       mappings already reported as part of a cycle
     * @return list of cycle quality issues
     */
    List<QualityIssue> detectCycles(List<ItemMapping> mappings, Set<UUID> alreadyReported) {
        List<QualityIssue> issues = new ArrayList<>();

        // Build adjacency list for directional (non-EQUIVALENT) internal mappings
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        Map<String, UUID> edgeToMapping = new HashMap<>();

        for (ItemMapping m : mappings) {
            if (m.getMappingType() == MappingType.EQUIVALENT) {
                continue;
            }
            UUID sid = m.getSubject().getItemId();
            UUID oid = m.getObject().getItemId();
            if (sid == null || oid == null) {
                continue;
            }
            adjacency.computeIfAbsent(sid, k -> new HashSet<>()).add(oid);
            edgeToMapping.put(sid + "->" + oid, m.getId());
        }

        if (adjacency.isEmpty()) {
            return issues;
        }

        // DFS cycle detection with three colors: WHITE(0), GRAY(1), BLACK(2)
        Map<UUID, Integer> color = new HashMap<>();
        Map<UUID, UUID> parent = new HashMap<>();

        for (UUID node : adjacency.keySet()) {
            color.putIfAbsent(node, 0);
        }

        for (UUID start : new ArrayList<>(adjacency.keySet())) {
            if (color.getOrDefault(start, 0) != 0) {
                continue;
            }

            Deque<UUID> stack = new ArrayDeque<>();
            stack.push(start);
            color.put(start, 1);

            while (!stack.isEmpty()) {
                UUID current = stack.peek();
                Set<UUID> neighbors = adjacency.getOrDefault(current, Set.of());
                boolean allNeighborsVisited = true;

                for (UUID neighbor : neighbors) {
                    int neighborColor = color.getOrDefault(neighbor, 0);
                    if (neighborColor == 1) {
                        // Found a back edge -> cycle
                        UUID mappingId = edgeToMapping.get(current + "->" + neighbor);
                        if (mappingId != null && !alreadyReported.contains(mappingId)) {
                            alreadyReported.add(mappingId);
                            issues.add(new QualityIssue(
                                    mappingId,
                                    IssueSeverity.HIGH,
                                    "CYCLE_DETECTED",
                                    "Mapping forms part of a directional cycle: " +
                                            current + " → " + neighbor + ". " +
                                            "Review to ensure semantic correctness.",
                                    null
                            ));
                        }
                    } else if (neighborColor == 0) {
                        allNeighborsVisited = false;
                        color.put(neighbor, 1);
                        parent.put(neighbor, current);
                        stack.push(neighbor);
                        break; // Process neighbor depth-first
                    }
                }

                if (allNeighborsVisited) {
                    color.put(current, 2);
                    stack.pop();
                }
            }
        }

        return issues;
    }

    /**
     * Identifies high-risk mappings based on issues and completeness.
     */
    List<UUID> identifyHighRiskMappings(List<ItemMapping> mappings, List<QualityIssue> issues) {
        Map<UUID, List<QualityIssue>> issuesByMapping = issues.stream()
                .collect(Collectors.groupingBy(QualityIssue::mappingId));

        return mappings.stream()
                .filter(m -> {
                    List<QualityIssue> mappingIssues = issuesByMapping.getOrDefault(m.getId(), List.of());
                    boolean hasHighSeverity = mappingIssues.stream()
                            .anyMatch(i -> i.severity() == IssueSeverity.HIGH);
                    boolean hasNoDescription = m.getDescription() == null || m.getDescription().isBlank();
                    boolean hasConflict = mappingIssues.stream()
                            .anyMatch(i -> i.issueType().equals("TYPE_CONFLICT") || i.issueType().equals("SYMMETRY_VIOLATION"));

                    return hasHighSeverity || (hasNoDescription && hasConflict);
                })
                .map(ItemMapping::getId)
                .toList();
    }

    List<String> generateImprovementTips(MappingQualityMetrics metrics,
                                         List<QualityIssue> issues,
                                         int highRiskCount) {
        List<String> tips = new ArrayList<>();

        if (metrics.totalMappings() == 0) {
            tips.add("No mappings found in this scheme. Start by adding mappings to generate quality insights.");
            return tips;
        }

        if (metrics.coverageScore() < 50.0) {
            tips.add("Coverage is low (" + metrics.coverageScore() + "%). Consider mapping more target standard elements.");
        }

        if (metrics.descriptionRate() < 80.0) {
            tips.add("Many mappings lack descriptions (" + metrics.descriptionRate() + "%). Adding descriptions improves maintainability.");
        }

        if (metrics.transformationRate() < 80.0) {
            tips.add("Transformation rules are incomplete (" + metrics.transformationRate() + "%). DERIVED/AGGREGATED/SUBDIVIDED mappings should have rules.");
        }

        if (metrics.consistencyScore() < 90.0) {
            tips.add("Consistency issues detected (score: " + metrics.consistencyScore() + "%). Review symmetry and type conflicts.");
        }

        long symmetryIssues = issues.stream()
                .filter(i -> i.issueType().equals("SYMMETRY_VIOLATION"))
                .count();
        if (symmetryIssues > 0) {
            tips.add("Found " + symmetryIssues + " symmetry violation(s). Ensure EQUIVALENT mappings are bidirectional.");
        }

        long typeConflicts = issues.stream()
                .filter(i -> i.issueType().equals("TYPE_CONFLICT"))
                .count();
        if (typeConflicts > 0) {
            tips.add("Found " + typeConflicts + " type conflict(s). A pair should not have mutually exclusive mapping types.");
        }

        long cycleIssues = issues.stream()
                .filter(i -> i.issueType().equals("CYCLE_DETECTED"))
                .count();
        if (cycleIssues > 0) {
            tips.add("Found " + cycleIssues + " directional cycle(s). Cycles in mapping graphs may indicate semantic errors.");
        }

        if (highRiskCount > 0) {
            tips.add(highRiskCount + " mapping(s) flagged as high-risk. Prioritize review of these mappings.");
        }

        if (tips.isEmpty()) {
            tips.add("No significant issues found. Maintain current mapping quality practices.");
        }

        return tips;
    }

    private record Pair(UUID a, UUID b) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pair p)) return false;
            return (a.equals(p.a) && b.equals(p.b));
        }

        @Override
        public int hashCode() {
            return 31 * a.hashCode() + b.hashCode();
        }
    }
}
