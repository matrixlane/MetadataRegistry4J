package io.github.matrixlane.metadataregistry.web.service.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.*;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating FHIR Profile (StructureDefinition) suggestions (WI-012-3).
 *
 * <p>Produces a draft differential StructureDefinition based on:
 * <ul>
 *   <li>Uncovered required elements → flagged with mustSupport + min cardinality</li>
 *   <li>Covered elements → binding/strength suggestions</li>
 *   <li>Local extra fields → Extension definitions</li>
 * </ul>
 *
 * <p>The output is explicitly marked as draft and requires human review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FhirProfileSuggestionService {

    private final FhirResourceRepository resourceRepository;
    private final FhirElementRepository elementRepository;
    private final FhirComplianceService complianceService;
    private final ObjectMapper objectMapper;

    /**
     * Generates a draft FHIR Profile (StructureDefinition) suggestion.
     *
     * @param request profile generation request
     * @return profile suggestion response with JSON and metadata
     */
    public ProfileSuggestionResponse generateProfile(ProfileSuggestionRequest request) {
        UUID resourceId = request.getResourceId();
        log.info("Generating profile suggestion for resourceId={}", resourceId);

        FhirResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("FhirResource", resourceId));

        // Base the generation on a compliance report if provided, otherwise run a fresh check
        ComplianceReportResponse report;
        if (request.getReportId() != null) {
            report = complianceService.getReport(request.getReportId());
        } else {
            report = complianceService.checkCompliance(resourceId);
        }

        List<FhirElement> allElements = elementRepository.findByResourceIdOrderByElementPath(resourceId);
        Map<UUID, FhirElement> elementMap = allElements.stream()
                .collect(Collectors.toMap(FhirElement::getId, e -> e));

        // Build StructureDefinition JSON
        ObjectNode profile = objectMapper.createObjectNode();
        profile.put("resourceType", "StructureDefinition");
        profile.put("id", resource.getName() + "-local-draft");
        profile.put("url", buildProfileUrl(request, resource));
        profile.put("name", resource.getName() + "LocalDraft");
        profile.put("title", resource.getTitle() != null ? resource.getTitle() + " (Local Draft)" : resource.getName() + " (Local Draft)");
        profile.put("status", "draft");
        profile.put("date", LocalDateTime.now().toLocalDate().toString());
        profile.put("publisher", "MetadataRegistry4j Auto-Generated");
        profile.put("description", "自动生成的 FHIR Profile 草稿建议。需经人工审核后方可使用。\nBased on gap analysis of local metadata mappings.");
        profile.put("kind", resource.getKind() != null ? resource.getKind() : "resource");
        profile.put("abstract", false);
        profile.put("type", resource.getType() != null ? resource.getType() : resource.getName());
        profile.put("baseDefinition", resource.getUrl() != null ? resource.getUrl() : "http://hl7.org/fhir/StructureDefinition/" + resource.getName());
        profile.put("derivation", "constraint");

        // Differential element array
        ArrayNode differential = profile.putArray("differential");

        // Element 0: root element (must exist in differential)
        ObjectNode rootElem = differential.addObject();
        rootElem.put("path", resource.getName());
        rootElem.put("short", resource.getName() + " with local constraints");
        rootElem.put("definition", "Locally constrained " + resource.getName() + " profile.");

        // 1. Mark uncovered required elements as mustSupport with min=1
        if (report.getUncoveredElements() != null) {
            for (ElementGap gap : report.getUncoveredElements()) {
                if (gap.isRequired()) {
                    ObjectNode elem = differential.addObject();
                    elem.put("path", gap.getFhirPath());
                    elem.put("min", 1);
                    elem.put("mustSupport", true);
                    elem.put("short", "[REQUIRED - UNMAPPED] " + (gap.getShortDescription() != null ? gap.getShortDescription() : ""));
                    if (gap.getElementType() != null) {
                        ArrayNode types = elem.putArray("type");
                        ObjectNode t = types.addObject();
                        t.put("code", gap.getElementType());
                    }
                }
            }
        }

        // 2. For covered elements, suggest binding if present in original
        if (report.getCoveredElements() != null) {
            for (ElementCoverage coverage : report.getCoveredElements()) {
                FhirElement original = elementMap.get(coverage.getFhirElementId());
                if (original != null && original.getBindingValueSet() != null) {
                    ObjectNode elem = differential.addObject();
                    elem.put("path", coverage.getFhirPath());
                    ObjectNode binding = elem.putObject("binding");
                    binding.put("strength", "preferred");
                    binding.put("valueSet", original.getBindingValueSet());
                    binding.put("description", original.getBindingDescription() != null ? original.getBindingDescription() : "Suggested binding from local mapping");
                }
            }
        }

        // 3. Suggest Extensions for local extra fields
        List<ExtensionSuggestion> extensionSuggestions = new ArrayList<>();
        if (request.isIncludeExtensions() && report.getLocalExtraFields() != null) {
            for (LocalExtraField extra : report.getLocalExtraFields()) {
                String extUrl = extra.getSuggestedExtensionUrl() != null
                        ? extra.getSuggestedExtensionUrl()
                        : "http://example.org/fhir/StructureDefinition/" + sanitize(extra.getLocalItemName());

                // Add an extension slice to the differential (simplified)
                ObjectNode elem = differential.addObject();
                elem.put("path", resource.getName() + ".extension");
                elem.put("slicing", objectMapper.createObjectNode()
                        .put("discriminator", "url")
                        .put("rules", "open"));
                elem.put("short", "Extensions for local extra fields");

                // Add the specific extension element
                ObjectNode extElem = differential.addObject();
                extElem.put("path", resource.getName() + ".extension:" + sanitize(extra.getLocalItemName()));
                extElem.put("sliceName", sanitize(extra.getLocalItemName()));
                extElem.put("min", 0);
                extElem.put("max", "1");
                extElem.put("type", objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("code", "Extension")));
                ObjectNode extType = extElem.putObject("type");
                ArrayNode extTypeArr = extType.putArray("code");
                extTypeArr.add("Extension");

                extensionSuggestions.add(ExtensionSuggestion.builder()
                        .localItemId(extra.getLocalItemId())
                        .localItemName(extra.getLocalItemName())
                        .localItemType(extra.getLocalItemType())
                        .suggestedUrl(extUrl)
                        .suggestedFhirType(mapLocalTypeToFhir(extra.getLocalItemType()))
                        .reason("本地额外字段，建议通过 Extension 方式纳入 Profile")
                        .build());
            }
        }

        String profileJson = profile.toPrettyString();

        List<String> warnings = new ArrayList<>();
        warnings.add("此 Profile 为草稿级建议，仅包含 differential 层面的基础约束。");
        warnings.add("未涉及复杂的 slicing、invariant 或 constraint 叠加逻辑。");
        warnings.add("所有 mustSupport 和 binding 建议需经 FHIR 实施专家审核确认。");
        if (report.getCoverageMetrics() != null && report.getCoverageMetrics().getCoveragePercentage() < 80.0) {
            warnings.add("当前映射覆盖率较低 (" + report.getCoverageMetrics().getCoveragePercentage() + "%)，生成的 Profile 可能不完整。");
        }

        UUID profileId = UUID.randomUUID();
        log.info("Profile suggestion generated for resourceId={}: {} bytes JSON, {} extensions",
                resourceId, profileJson.length(), extensionSuggestions.size());

        return ProfileSuggestionResponse.builder()
                .profileId(profileId)
                .resourceId(resourceId)
                .status("DRAFT")
                .profileJson(profileJson)
                .suggestedExtensions(extensionSuggestions.isEmpty() ? null : extensionSuggestions)
                .warnings(warnings)
                .build();
    }

    private String buildProfileUrl(ProfileSuggestionRequest request, FhirResource resource) {
        if (request.getBaseProfileUrl() != null && !request.getBaseProfileUrl().isBlank()) {
            return request.getBaseProfileUrl().replaceAll("/+$", "") + "-local-draft";
        }
        return "http://example.org/fhir/StructureDefinition/" + resource.getName() + "-local-draft";
    }

    private String sanitize(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9]", "");
    }

    private String mapLocalTypeToFhir(String localType) {
        if (localType == null) return "string";
        return switch (localType.toLowerCase()) {
            case "boolean", "bool" -> "boolean";
            case "integer", "int", "number" -> "integer";
            case "decimal", "real", "float", "double" -> "decimal";
            case "date" -> "date";
            case "datetime", "timestamp" -> "dateTime";
            case "time" -> "time";
            case "uri", "url", "oid", "uuid" -> "uri";
            case "code", "coding" -> "code";
            default -> "string";
        };
    }
}
