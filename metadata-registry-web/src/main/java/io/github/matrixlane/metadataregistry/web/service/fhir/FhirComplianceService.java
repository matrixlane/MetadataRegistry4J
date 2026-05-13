package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.*;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for FHIR compliance gap analysis (WI-012-3).
 *
 * <p>Compares locally mapped AdministeredItems against a target FHIR Resource's
 * complete Element list to produce:
 * <ul>
 *   <li>Covered / uncovered element inventories</li>
 *   <li>Local extra fields inventory</li>
 *   <li>Type compatibility summary</li>
 *   <li>Coverage metrics</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FhirComplianceService {

    private final FhirResourceRepository resourceRepository;
    private final FhirElementRepository elementRepository;
    private final AdministeredItemRepository administeredItemRepository;

    // In-memory report cache for MVP (non-persistent)
    private final Map<UUID, ComplianceReportResponse> reportCache = new HashMap<>();

    // Simple FHIR-to-local type compatibility map
    private static final Map<String, Set<String>> TYPE_COMPATIBILITY_MAP;
    static {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("string", Set.of("string", "text", "StringType", "String", "TEXT"));
        map.put("boolean", Set.of("boolean", "BooleanType", "Boolean", "BOOLEAN"));
        map.put("integer", Set.of("integer", "IntegerType", "Integer", "INT", "NUMBER"));
        map.put("decimal", Set.of("decimal", "DecimalType", "Decimal", "NUMBER", "REAL"));
        map.put("date", Set.of("date", "DateType", "Date", "DATE"));
        map.put("datetime", Set.of("datetime", "dateTime", "DateTimeType", "DateTime", "DATETIME"));
        map.put("time", Set.of("time", "TimeType", "Time", "TIME"));
        map.put("uri", Set.of("uri", "UrlType", "URI", "URL", "OID", "UUID"));
        map.put("code", Set.of("code", "CodeType", "Code", "CODE", "STRING"));
        map.put("coding", Set.of("coding", "Coding", "CODE"));
        map.put("codeableconcept", Set.of("codeableconcept", "CodeableConcept", "CODING", "CODE"));
        map.put("quantity", Set.of("quantity", "Quantity", "NUMBER", "INTEGER", "DECIMAL"));
        map.put("period", Set.of("period", "Period", "DATETIME", "DATE"));
        map.put("range", Set.of("range", "Range", "NUMBER", "INTEGER", "DECIMAL"));
        map.put("ratio", Set.of("ratio", "Ratio", "NUMBER", "INTEGER", "DECIMAL"));
        map.put("reference", Set.of("reference", "Reference", "URI", "URL"));
        map.put("identifier", Set.of("identifier", "Identifier", "STRING", "URI"));
        map.put("humanname", Set.of("humanname", "HumanName", "STRING", "TEXT"));
        map.put("address", Set.of("address", "Address", "STRING", "TEXT"));
        map.put("contactpoint", Set.of("contactpoint", "ContactPoint", "STRING", "URI"));
        map.put("annotation", Set.of("annotation", "Annotation", "STRING", "TEXT"));
        map.put("attachment", Set.of("attachment", "Attachment", "STRING", "URI"));
        map.put("markdown", Set.of("markdown", "MarkdownType", "STRING", "TEXT"));
        map.put("uuid", Set.of("uuid", "UUID", "URI", "STRING"));
        map.put("id", Set.of("id", "ID", "STRING"));
        map.put("oid", Set.of("oid", "OID", "URI", "STRING"));
        map.put("positiveint", Set.of("positiveint", "PositiveInt", "INTEGER", "NUMBER"));
        map.put("unsignedint", Set.of("unsignedint", "UnsignedInt", "INTEGER", "NUMBER"));
        map.put("base64binary", Set.of("base64binary", "Base64Binary", "STRING", "BINARY"));
        map.put("instant", Set.of("instant", "Instant", "DATETIME", "DATE"));
        TYPE_COMPATIBILITY_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Performs gap analysis for a target FHIR Resource.
     *
     * @param resourceId the FHIR Resource ID
     * @return compliance report
     */
    public ComplianceReportResponse checkCompliance(UUID resourceId) {
        return checkCompliance(resourceId, null);
    }

    /**
     * Performs gap analysis for a target FHIR Resource.
     *
     * @param resourceId the FHIR Resource ID
     * @param schemeId   optional mapping scheme to scope local items
     * @return compliance report
     */
    public ComplianceReportResponse checkCompliance(UUID resourceId, UUID schemeId) {
        log.info("Starting compliance check for resourceId={}, schemeId={}", resourceId, schemeId);

        FhirResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("FhirResource", resourceId));

        List<FhirElement> allElements = elementRepository.findByResourceIdOrderByElementPath(resourceId);
        if (allElements.isEmpty()) {
            throw new IllegalArgumentException("Resource has no elements: " + resourceId);
        }

        // Exclude root element (e.g., "Patient") from coverage metrics since it's the resource itself
        List<FhirElement> childElements = allElements.stream()
                .filter(e -> e.getParent() != null)
                .toList();

        // Fetch all local items that are referenced by any element of this resource
        Set<UUID> mappedLocalItemIds = childElements.stream()
                .map(FhirElement::getAdministeredItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, AdministeredItem> localItemMap = administeredItemRepository.findAllById(mappedLocalItemIds)
                .stream().collect(Collectors.toMap(AdministeredItem::getId, ai -> ai));

        // Build covered / uncovered / extra lists
        List<ElementCoverage> coveredElements = new ArrayList<>();
        List<ElementGap> uncoveredElements = new ArrayList<>();
        List<TypeIncompatibility> incompatibleDetails = new ArrayList<>();

        int requiredTotal = 0;
        int requiredCovered = 0;

        for (FhirElement element : childElements) {
            boolean isRequired = element.getMin() != null && element.getMin() > 0;
            if (isRequired) {
                requiredTotal++;
            }

            if (element.getAdministeredItemId() != null) {
                AdministeredItem localItem = localItemMap.get(element.getAdministeredItemId());
                String localItemName = extractItemName(localItem);
                String localType = extractLocalType(localItem);
                boolean typeCompatible = checkTypeCompatibility(element.getElementType(), localType);

                coveredElements.add(ElementCoverage.builder()
                        .fhirElementId(element.getId())
                        .fhirPath(element.getPath())
                        .elementType(element.getElementType())
                        .shortDescription(element.getShortDescription())
                        .localItemId(element.getAdministeredItemId())
                        .localItemName(localItemName)
                        .localItemType(localType)
                        .typeCompatible(typeCompatible)
                        .compatibilityNote(typeCompatible ? "类型兼容" : "类型可能不兼容: FHIR=" + element.getElementType() + ", 本地=" + localType)
                        .build());

                if (isRequired) {
                    requiredCovered++;
                }

                if (!typeCompatible && localType != null) {
                    incompatibleDetails.add(TypeIncompatibility.builder()
                            .fhirElementId(element.getId())
                            .fhirPath(element.getPath())
                            .fhirType(element.getElementType())
                            .localItemId(element.getAdministeredItemId())
                            .localItemName(localItemName)
                            .localType(localType)
                            .reason("FHIR类型 '" + element.getElementType() + "' 与本地类型 '" + localType + "' 可能不兼容")
                            .build());
                }
            } else {
                uncoveredElements.add(ElementGap.builder()
                        .fhirElementId(element.getId())
                        .fhirPath(element.getPath())
                        .elementType(element.getElementType())
                        .shortDescription(element.getShortDescription())
                        .min(element.getMin())
                        .max(element.getMax())
                        .required(isRequired)
                        .summary(element.isSummary())
                        .priority(isRequired ? "HIGH" : (element.isSummary() ? "MEDIUM" : "LOW"))
                        .build());
            }
        }

        // Local extra fields: mapped items that are NOT mapped to this resource
        List<LocalExtraField> localExtraFields = findLocalExtraFields(resourceId, mappedLocalItemIds, localItemMap);

        // Coverage metrics
        int total = childElements.size();
        int covered = coveredElements.size();
        int uncovered = uncoveredElements.size();
        double coveragePct = total > 0 ? (double) covered / total * 100 : 0.0;
        double requiredCoveragePct = requiredTotal > 0 ? (double) requiredCovered / requiredTotal * 100 : 0.0;

        CoverageMetrics metrics = CoverageMetrics.builder()
                .totalElements(total)
                .coveredElements(covered)
                .uncoveredElements(uncovered)
                .localExtraFields(localExtraFields.size())
                .coveragePercentage(round2(coveragePct))
                .requiredElementCount(requiredTotal)
                .coveredRequiredCount(requiredCovered)
                .requiredCoveragePercentage(round2(requiredCoveragePct))
                .build();

        // Type compatibility summary
        int typeChecked = coveredElements.stream().filter(ec -> ec.getLocalItemType() != null).toList().size();
        int compatible = coveredElements.stream().filter(ElementCoverage::getTypeCompatible).toList().size();
        int unknown = coveredElements.size() - typeChecked;

        TypeCompatibilitySummary typeSummary = TypeCompatibilitySummary.builder()
                .totalChecked(typeChecked)
                .compatibleCount(compatible)
                .incompatibleCount(incompatibleDetails.size())
                .unknownCount(unknown)
                .incompatibleDetails(incompatibleDetails.isEmpty() ? null : incompatibleDetails)
                .build();

        // Warnings
        List<String> warnings = new ArrayList<>();
        if (coveragePct < 50.0) {
            warnings.add("映射覆盖率低于50%，建议优先完成核心字段的映射。");
        }
        if (requiredCoveragePct < 100.0 && requiredTotal > 0) {
            warnings.add("存在未映射的必填元素(required)，可能影响 FHIR 资源实例的有效性验证。");
        }
        if (!incompatibleDetails.isEmpty()) {
            warnings.add("检测到 " + incompatibleDetails.size() + " 处类型不兼容，建议复核映射关系。");
        }

        UUID reportId = UUID.randomUUID();
        ComplianceReportResponse report = ComplianceReportResponse.builder()
                .reportId(reportId)
                .resourceId(resourceId)
                .resourceName(resource.getName())
                .resourceUrl(resource.getUrl())
                .generatedAt(LocalDateTime.now())
                .status("DRAFT")
                .coverageMetrics(metrics)
                .typeCompatibilitySummary(typeSummary)
                .coveredElements(coveredElements)
                .uncoveredElements(uncoveredElements)
                .localExtraFields(localExtraFields)
                .warnings(warnings.isEmpty() ? null : warnings)
                .build();

        reportCache.put(reportId, report);
        log.info("Compliance check completed for resourceId={}: {} covered, {} uncovered, {} extra",
                resourceId, covered, uncovered, localExtraFields.size());
        return report;
    }

    /**
     * Retrieves a previously generated compliance report by ID.
     */
    public ComplianceReportResponse getReport(UUID reportId) {
        ComplianceReportResponse report = reportCache.get(reportId);
        if (report == null) {
            throw new ResourceNotFoundException("ComplianceReport", reportId);
        }
        return report;
    }

    // ---- Internal helpers ----

    private List<LocalExtraField> findLocalExtraFields(UUID resourceId,
                                                        Set<UUID> mappedToThisResource,
                                                        Map<UUID, AdministeredItem> localItemMap) {
        // All local items mapped to ANY FHIR element
        List<UUID> allMappedIds = elementRepository.findAllMappedAdministeredItemIds();

        // Items mapped to OTHER resources (not this one)
        Set<UUID> extraIds = allMappedIds.stream()
                .filter(id -> !mappedToThisResource.contains(id))
                .collect(Collectors.toSet());

        // Also include items mapped to this resource that are duplicates (same local item mapped to multiple elements)
        // We don't count those as "extra" - they're just over-mapped

        List<LocalExtraField> extras = new ArrayList<>();
        for (UUID id : extraIds) {
            AdministeredItem item = localItemMap.get(id);
            if (item == null) {
                // Fetch if not in cache
                item = administeredItemRepository.findById(id).orElse(null);
            }
            if (item != null) {
                String name = extractItemName(item);
                String type = extractLocalType(item);
                extras.add(LocalExtraField.builder()
                        .localItemId(id)
                        .localItemName(name)
                        .localItemType(type)
                        .reason("该本地数据元已映射到其他 FHIR Resource 的元素，但未映射到当前 Resource。")
                        .suggestedExtensionUrl("http://example.org/fhir/StructureDefinition/" + sanitizeForUrl(name))
                        .build());
            }
        }
        return extras;
    }

    private String extractItemName(AdministeredItem item) {
        if (item == null) return null;
        Item i = item.getItem();
        if (i == null) return null;
        // Try to get designation name
        if (i.getDesignations() != null && !i.getDesignations().isEmpty()) {
            return i.getDesignations().stream()
                    .filter(d -> d.getSign() != null && d.getSign().getValue() != null && !d.getSign().getValue().isBlank())
                    .map(d -> d.getSign().getValue())
                    .findFirst().orElse(i.getClass().getSimpleName());
        }
        return i.getClass().getSimpleName();
    }

    private String extractLocalType(AdministeredItem item) {
        if (item == null) return null;
        Item i = item.getItem();
        if (i instanceof DataElement de) {
            ValueDomain vd = de.getValueDomain();
            if (vd != null && vd.getDataType() != null) {
                // DataType extends Item, try to get its designation
                return extractItemName(new AdministeredItem(vd.getDataType()));
            }
        }
        return i != null ? i.getClass().getSimpleName() : null;
    }

    private boolean checkTypeCompatibility(String fhirType, String localType) {
        if (fhirType == null || localType == null) {
            return true; // unknown = not flagged as incompatible
        }
        String fhirLower = fhirType.toLowerCase();
        String localLower = localType.toLowerCase();

        Set<String> compatibleLocals = TYPE_COMPATIBILITY_MAP.get(fhirLower);
        if (compatibleLocals != null) {
            return compatibleLocals.contains(localLower);
        }
        // Fallback: exact match or substring
        return fhirLower.equals(localLower) || localLower.contains(fhirLower) || fhirLower.contains(localLower);
    }

    private String sanitizeForUrl(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9]", "");
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
