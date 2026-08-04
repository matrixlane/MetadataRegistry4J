package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;

import java.util.regex.Pattern;

/** Fixed literal string, e.g. a custom prefix. Params: value (required). */
public class LiteralSegmentHandler implements SegmentTypeHandler {

    @Override
    public SegmentType type() {
        return SegmentType.LITERAL;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        String value = def.stringParam("value");
        if (value == null || value.isEmpty()) {
            throw new SegmentTemplateException("LITERAL segment requires a non-empty 'value' parameter");
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        return def.stringParam("value");
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        return Pattern.quote(def.stringParam("value"));
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"value\",\"label\":\"固定字符串\",\"type\":\"text\",\"required\":true}]}";
    }
}
