package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Current date/time formatted with a {@link DateTimeFormatter} pattern.
 * Params: pattern (e.g. yyyy, yyyyMM, yyyyMMddHHmmss).
 *
 * <p>The derived regex maps each run of numeric pattern letters to
 * {@code \d{n}}; literal characters are quoted.
 */
public class TimestampSegmentHandler implements SegmentTypeHandler {

    private static final String NUMERIC_LETTERS = "yMdHmsS";

    @Override
    public SegmentType type() {
        return SegmentType.TIMESTAMP;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        String pattern = def.stringParam("pattern");
        if (pattern == null || pattern.isBlank()) {
            throw new SegmentTemplateException("TIMESTAMP segment requires a 'pattern' (e.g. yyyyMM)");
        }
        try {
            DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            throw new SegmentTemplateException("TIMESTAMP segment has an invalid 'pattern': " + e.getMessage());
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(def.stringParam("pattern")));
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        String pattern = def.stringParam("pattern");
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (NUMERIC_LETTERS.indexOf(c) >= 0) {
                int run = 1;
                while (i + run < pattern.length() && pattern.charAt(i + run) == c) {
                    run++;
                }
                regex.append("\\d{").append(run).append('}');
                i += run;
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return regex.toString();
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"pattern\",\"label\":\"时间格式\",\"type\":\"text\","
                + "\"placeholder\":\"yyyyMMdd\",\"required\":true}]}";
    }
}
