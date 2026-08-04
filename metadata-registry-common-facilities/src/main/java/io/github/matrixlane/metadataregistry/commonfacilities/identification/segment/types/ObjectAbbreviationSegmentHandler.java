package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abbreviation of the registered item's object type (DataElement -&gt; DE,
 * ValueDomain -&gt; VD/CV). Params: mapping (objectType -&gt; abbreviation,
 * configurable), default (optional fallback abbreviation).
 */
public class ObjectAbbreviationSegmentHandler implements SegmentTypeHandler {

    @Override
    public SegmentType type() {
        return SegmentType.OBJECT_ABBREVIATION;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        Map<String, String> mapping = def.mapParam("mapping");
        if (mapping.isEmpty()) {
            throw new SegmentTemplateException(
                    "OBJECT_ABBREVIATION segment requires a non-empty 'mapping' parameter (object type -> abbreviation)");
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        Map<String, String> mapping = def.mapParam("mapping");
        String itemType = context.getItemType();
        String abbreviation = itemType != null ? mapping.get(itemType) : null;
        if (abbreviation == null) {
            abbreviation = def.stringParam("default");
        }
        if (abbreviation == null) {
            throw new SegmentTemplateException(
                    "OBJECT_ABBREVIATION segment has no mapping for item type '" + itemType + "' and no default");
        }
        return abbreviation;
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        Map<String, String> mapping = def.mapParam("mapping");
        List<String> values = new ArrayList<>(new java.util.LinkedHashSet<>(mapping.values()));
        String fallback = def.stringParam("default");
        if (fallback != null && !values.contains(fallback)) {
            values.add(fallback);
        }
        if (values.isEmpty()) {
            throw new SegmentTemplateException("OBJECT_ABBREVIATION segment cannot derive a regex without mapping values");
        }
        // longest first so alternation matches greedily
        values.sort(Comparator.comparingInt(String::length).reversed());
        return values.stream().map(Pattern::quote).collect(Collectors.joining("|", "(?:", ")"));
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"mapping\",\"label\":\"对象类型缩写映射\",\"type\":\"mapping\",\"required\":true},"
                + "{\"key\":\"default\",\"label\":\"默认缩写\",\"type\":\"text\",\"required\":false}]}";
    }
}
