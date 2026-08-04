package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Separator between segments. Params: char — restricted to the predefined
 * separator character set (`.` `-` `_` `/` `:`).
 */
public class SeparatorSegmentHandler implements SegmentTypeHandler {

    /** Predefined separator character set. */
    public static final Set<String> ALLOWED_CHARS = Set.of(".", "-", "_", "/", ":");

    @Override
    public SegmentType type() {
        return SegmentType.SEPARATOR;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        String c = def.stringParam("char");
        if (c == null || c.length() != 1 || !ALLOWED_CHARS.contains(c)) {
            throw new SegmentTemplateException(
                    "SEPARATOR segment 'char' must be one of " + ALLOWED_CHARS + " (single character)");
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        return def.stringParam("char");
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        return Pattern.quote(def.stringParam("char"));
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"char\",\"label\":\"分隔符\",\"type\":\"select\","
                + "\"options\":[\".\",\"-\",\"_\",\"/\",\":\"],\"required\":true}]}";
    }
}
