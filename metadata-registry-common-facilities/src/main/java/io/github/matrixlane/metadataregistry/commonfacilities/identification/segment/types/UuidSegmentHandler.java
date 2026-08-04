package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;

import java.util.UUID;

/**
 * Random UUID as a template component. Params: form — "full" (36 chars with
 * dashes, default) or "short" (first 8 hex chars).
 */
public class UuidSegmentHandler implements SegmentTypeHandler {

    @Override
    public SegmentType type() {
        return SegmentType.UUID;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        String form = def.stringParam("form", "full");
        if (!"full".equalsIgnoreCase(form) && !"short".equalsIgnoreCase(form)) {
            throw new SegmentTemplateException("UUID segment 'form' must be 'full' or 'short'");
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        String form = def.stringParam("form", "full");
        String uuid = UUID.randomUUID().toString();
        return "short".equalsIgnoreCase(form) ? uuid.substring(0, 8) : uuid;
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        String form = def.stringParam("form", "full");
        return "short".equalsIgnoreCase(form)
                ? "[0-9a-fA-F]{8}"
                : "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"form\",\"label\":\"形式\",\"type\":\"select\","
                + "\"options\":[\"full\",\"short\"],\"default\":\"full\"}]}";
    }
}
