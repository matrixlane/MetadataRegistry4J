package io.github.matrixlane.metadataregistry.ext.identification.segment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An identifier template: an ordered list of segments, persisted as JSON in
 * {@code IdentifierStrategyConfig.configJson}.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SegmentTemplate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<SegmentDefinition> segments = new ArrayList<>();

    public SegmentTemplate(List<SegmentDefinition> segments) {
        this.segments = segments != null ? segments : new ArrayList<>();
    }

    /** Parses a template from its persisted JSON form. */
    public static SegmentTemplate fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new SegmentTemplateException("Segment template JSON is empty");
        }
        try {
            SegmentTemplate template = MAPPER.readValue(json, SegmentTemplate.class);
            if (template.segments == null || template.segments.isEmpty()) {
                throw new SegmentTemplateException("Segment template must contain at least one segment");
            }
            return template;
        } catch (SegmentTemplateException e) {
            throw e;
        } catch (Exception e) {
            throw new SegmentTemplateException("Invalid segment template JSON: " + e.getMessage(), e);
        }
    }

    /** Serializes this template to its persisted JSON form. */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new SegmentTemplateException("Cannot serialize segment template: " + e.getMessage(), e);
        }
    }
}
