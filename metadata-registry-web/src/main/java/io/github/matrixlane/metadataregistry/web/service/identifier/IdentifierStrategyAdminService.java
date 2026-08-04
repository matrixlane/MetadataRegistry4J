package io.github.matrixlane.metadataregistry.web.service.identifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplate;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateEngine;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.BackfillResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.NamespaceStrategyView;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.PresetResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.PreviewResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.StrategyConfigResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.ValidateFormatResponse;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.SequenceRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin operations for namespace identifier strategies: query, draft,
 * publish, disable, preview (no allocation), format validation, designer
 * presets, and sequence backfill.
 *
 * <p>Versioning reuses the existing {@link IdentifierStrategyConfig} state
 * machine: publishing disables the previous PUBLISHED config and writes a new
 * PUBLISHED row with an incremented version. Historical identifiers are never
 * rewritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IdentifierStrategyAdminService {

    /** The only standalone strategy type offered by this API (design D1). */
    public static final String STRATEGY_TYPE = "SEGMENT_TEMPLATE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IdentifierStrategyConfigRepository configRepository;
    private final NamespaceRepository namespaceRepository;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final SequenceRegistryService sequenceRegistryService;
    private final IdentifierStrategyLoader strategyLoader;

    private final SegmentTemplateEngine engine = new SegmentTemplateEngine();

    // --- query ---

    @Transactional(readOnly = true)
    public NamespaceStrategyView view(UUID namespaceId) {
        requireNamespace(namespaceId);
        List<IdentifierStrategyConfig> all = configRepository.findByNamespaceIdOrderByVersionDesc(namespaceId);
        StrategyConfigResponse published = null;
        List<StrategyConfigResponse> drafts = new ArrayList<>();
        List<StrategyConfigResponse> history = new ArrayList<>();
        for (IdentifierStrategyConfig c : all) {
            StrategyConfigResponse r = toResponse(c);
            switch (c.getStatus()) {
                case PUBLISHED -> {
                    if (published == null) {
                        published = r;
                    } else {
                        history.add(r);
                    }
                }
                case DRAFT -> drafts.add(r);
                case DISABLED -> history.add(r);
            }
        }
        return new NamespaceStrategyView(published, drafts, history);
    }

    // --- draft / publish / disable ---

    public StrategyConfigResponse saveDraft(UUID namespaceId, String configJson) {
        Namespace namespace = requireNamespace(namespaceId);
        SegmentTemplate template = parseTemplate(configJson);
        List<String> errors = engine.validate(template);
        if (!errors.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "段模板校验失败：" + String.join("；", errors));
        }
        IdentifierStrategyConfig config = new IdentifierStrategyConfig(STRATEGY_TYPE);
        config.setNamespace(namespace);
        config.setConfigJson(template.toJson());
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.DRAFT);
        config.setIsGlobalDefault(false);
        config.setVersion(nextVersion(namespaceId));
        config.setCreatedAt(LocalDateTime.now());
        return toResponse(configRepository.save(config));
    }

    /**
     * Publishes a template: validates it, disables the current PUBLISHED
     * config and writes a new PUBLISHED row with an incremented version,
     * then refreshes the runtime registry so it takes effect immediately.
     */
    public StrategyConfigResponse publish(UUID namespaceId, String configJson) {
        Namespace namespace = requireNamespace(namespaceId);
        SegmentTemplate template = parseTemplate(configJson);
        List<String> errors = engine.validate(template);
        if (!errors.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "段模板校验失败，无法发布：" + String.join("；", errors));
        }

        IdentifierStrategyConfig current = currentPublished(namespaceId);
        if (current != null) {
            current.disable();
            current.setModifiedAt(LocalDateTime.now());
            configRepository.save(current);
        }

        IdentifierStrategyConfig config = new IdentifierStrategyConfig(STRATEGY_TYPE);
        config.setNamespace(namespace);
        config.setConfigJson(template.toJson());
        config.publish();
        config.setIsGlobalDefault(false);
        config.setVersion(nextVersion(namespaceId));
        config.setCreatedAt(LocalDateTime.now());
        IdentifierStrategyConfig saved = configRepository.save(config);
        strategyLoader.refreshRegistry();
        return toResponse(saved);
    }

    /** Publishes an existing draft row. */
    public StrategyConfigResponse publishDraft(UUID namespaceId, Long configId) {
        IdentifierStrategyConfig draft = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("IdentifierStrategyConfig", configId));
        if (draft.getNamespace() == null || !namespaceId.equals(draft.getNamespace().getId())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "该配置不属于此命名空间");
        }
        return publish(namespaceId, draft.getConfigJson());
    }

    public void disable(UUID namespaceId) {
        requireNamespace(namespaceId);
        IdentifierStrategyConfig current = currentPublished(namespaceId);
        if (current != null) {
            current.disable();
            current.setModifiedAt(LocalDateTime.now());
            configRepository.save(current);
            strategyLoader.refreshRegistry();
        }
    }

    // --- preview / validate ---

    @Transactional(readOnly = true)
    public PreviewResponse preview(String configJson, String itemType, Map<String, String> variables) {
        SegmentTemplate template = parseTemplate(configJson);
        List<String> errors = engine.validate(template);
        if (!errors.isEmpty()) {
            return new PreviewResponse(null, null, errors);
        }
        try {
            String sample = engine.preview(template, itemType, variables);
            String regex = engine.deriveFormatPattern(template).pattern();
            return new PreviewResponse(sample, regex, List.of());
        } catch (SegmentTemplateException e) {
            return new PreviewResponse(null, null, List.of(e.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public ValidateFormatResponse validateFormat(String configJson, String identifier) {
        SegmentTemplate template = parseTemplate(configJson);
        boolean matches = engine.matchesFormat(template, identifier);
        if (!matches) {
            return new ValidateFormatResponse(false, Map.of(), "标识不符合模板格式");
        }
        try {
            return new ValidateFormatResponse(true, engine.parse(template, identifier), null);
        } catch (SegmentTemplateException e) {
            return new ValidateFormatResponse(true, Map.of(), e.getMessage());
        }
    }

    // --- presets ---

    @Transactional(readOnly = true)
    public List<PresetResponse> presets() {
        List<PresetResponse> result = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:identifier-presets/*.json");
            for (Resource resource : resources) {
                JsonNode node = MAPPER.readTree(resource.getInputStream().readAllBytes());
                String configJson = MAPPER.writeValueAsString(node.get("template"));
                result.add(new PresetResponse(
                        node.path("key").asText(),
                        node.path("name").asText(),
                        node.path("description").asText(),
                        configJson));
            }
        } catch (Exception e) {
            throw new BusinessException("读取标识预设库失败：" + e.getMessage());
        }
        return result;
    }

    // --- backfill ---

    /**
     * Sequence backfill: parses every existing identifier of the namespace
     * against the published template and aligns each scope's counter to the
     * highest sequence already in use, so subsequent generation never
     * collides with imported identifiers.
     */
    public BackfillResponse backfill(UUID namespaceId) {
        Namespace namespace = requireNamespace(namespaceId);
        IdentifierStrategyConfig published = currentPublished(namespaceId);
        if (published == null || !STRATEGY_TYPE.equalsIgnoreCase(published.getStrategyType())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "该命名空间没有已发布的段模板策略，无法执行序号回填");
        }
        SegmentTemplate template = parseTemplate(published.getConfigJson());

        SegmentDefinition sequenceDef = template.getSegments().stream()
                .filter(d -> d.segmentType() == SegmentType.SEQUENCE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "模板中没有顺序号段，无需回填"));
        String seqName = sequenceDef.getName();
        if (seqName == null || seqName.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "顺序号段缺少 name，无法定位解析结果中的序号值");
        }

        int parsed = 0;
        int skipped = 0;
        Map<String, Long> scopeMax = new LinkedHashMap<>();
        for (ScopedIdentifier si : scopedIdentifierRepository.findByNamespaceId(namespaceId)) {
            Map<String, String> values;
            try {
                values = engine.parse(template, si.getIdentifier());
            } catch (SegmentTemplateException e) {
                skipped++;
                continue;
            }
            parsed++;
            String seqValue = values.get(seqName);
            if (seqValue == null) {
                continue;
            }
            try {
                long seq = Long.parseLong(seqValue);
                String scopeKey = SegmentTemplateEngine.scopeKeyOf(sequenceDef, values);
                scopeMax.merge(scopeKey, seq, Math::max);
            } catch (NumberFormatException | SegmentTemplateException e) {
                log.warn("[IdentifierStrategyAdminService] Backfill skipped identifier {}: {}",
                        si.getIdentifier(), e.getMessage());
            }
        }

        long start = sequenceDef.longParam("start", 1);
        int increment = sequenceDef.intParam("increment", 1);
        int pad = sequenceDef.intParam("pad", 6);
        for (Map.Entry<String, Long> e : scopeMax.entrySet()) {
            sequenceRegistryService.alignCounter(namespace, e.getKey(), e.getValue(), start, increment, pad);
        }
        return new BackfillResponse(parsed, skipped, scopeMax);
    }

    // --- helpers ---

    private Namespace requireNamespace(UUID namespaceId) {
        return namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", namespaceId));
    }

    private IdentifierStrategyConfig currentPublished(UUID namespaceId) {
        return configRepository.findByNamespaceIdOrderByVersionDesc(namespaceId).stream()
                .filter(c -> c.getStatus() == IdentifierStrategyConfig.StrategyStatus.PUBLISHED)
                .findFirst()
                .orElse(null);
    }

    private int nextVersion(UUID namespaceId) {
        return configRepository.findByNamespaceIdOrderByVersionDesc(namespaceId).stream()
                .map(IdentifierStrategyConfig::getVersion)
                .filter(v -> v != null)
                .findFirst()
                .orElse(0) + 1;
    }

    private SegmentTemplate parseTemplate(String configJson) {
        try {
            return SegmentTemplate.fromJson(configJson);
        } catch (SegmentTemplateException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "段模板解析失败：" + e.getMessage());
        }
    }

    private StrategyConfigResponse toResponse(IdentifierStrategyConfig c) {
        return new StrategyConfigResponse(
                c.getId(), c.getStrategyType(), c.getConfigJson(), c.getVersion(),
                c.getStatus().name(), c.getIsGlobalDefault(), c.getEffectiveFrom(), c.getEffectiveUntil());
    }
}
