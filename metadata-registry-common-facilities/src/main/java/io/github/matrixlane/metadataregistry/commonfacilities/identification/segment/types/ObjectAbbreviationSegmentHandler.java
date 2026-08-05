package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.ObjectAbbreviationCatalog;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abbreviation of the registered item's object type (DataElement -&gt; DE,
 * ValueDomain -&gt; VD). Abbreviations resolve through the system-level
 * {@link ObjectAbbreviationCatalog} (user-editable JSON resource); the
 * deprecated per-segment {@code mapping} param is ignored. Param: default
 * (optional fallback when the item type is not in the catalog).
 *
 * <p>The resolved value is recorded on the context so that SEQUENCE segments
 * derive their scope from it (per-object-type counting, implicit and
 * non-overridable).
 */
public class ObjectAbbreviationSegmentHandler implements SegmentTypeHandler {

    @Override
    public SegmentType type() {
        return SegmentType.OBJECT_ABBREVIATION;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        // No required params: abbreviations come from the system catalog.
        // A legacy per-segment 'mapping' param is ignored by design.
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        String itemType = context.getItemType();
        String abbreviation = ObjectAbbreviationCatalog.getInstance().abbreviationOf(itemType);
        if (abbreviation == null) {
            abbreviation = def.stringParam("default");
        }
        if (abbreviation == null) {
            throw new SegmentTemplateException(
                    "OBJECT_ABBREVIATION segment: item type '" + itemType
                            + "' is not in the system abbreviation catalog and the segment has no 'default'");
        }
        context.recordObjectAbbreviation(abbreviation);
        return abbreviation;
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        List<String> values = new ArrayList<>(
                new java.util.LinkedHashSet<>(ObjectAbbreviationCatalog.getInstance().all().values()));
        String fallback = def.stringParam("default");
        if (fallback != null && !values.contains(fallback)) {
            values.add(fallback);
        }
        if (values.isEmpty()) {
            throw new SegmentTemplateException(
                    "OBJECT_ABBREVIATION segment cannot derive a regex: system abbreviation catalog is empty");
        }
        // longest first so alternation matches greedily
        values.sort(Comparator.comparingInt(String::length).reversed());
        return values.stream().map(Pattern::quote).collect(Collectors.joining("|", "(?:", ")"));
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"default\",\"label\":\"默认缩写（未命中系统映射表时）\",\"type\":\"text\",\"required\":false}]}";
    }
}
