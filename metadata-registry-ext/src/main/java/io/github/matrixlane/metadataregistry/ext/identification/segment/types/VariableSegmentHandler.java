package io.github.matrixlane.metadataregistry.ext.identification.segment.types;

import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTypeHandler;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Caller-supplied input variable. Params: label, pattern (validation regex,
 * required for regex derivation), pad (zero-pad to length), default,
 * required (default true), maxLength (max output length, default 32 — used
 * by the template total-length check).
 *
 * <p>The segment's {@code name} is the variable key.
 */
public class VariableSegmentHandler implements SegmentTypeHandler {

    @Override
    public SegmentType type() {
        return SegmentType.VARIABLE;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        if (def.getName() == null || def.getName().isBlank()) {
            throw new SegmentTemplateException("VARIABLE segment requires a 'name' (the variable key)");
        }
        String pattern = def.stringParam("pattern");
        if (pattern == null || pattern.isBlank()) {
            throw new SegmentTemplateException(
                    "VARIABLE segment '" + def.getName() + "' requires a 'pattern' so a format regex can be derived");
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new SegmentTemplateException(
                    "VARIABLE segment '" + def.getName() + "' has an invalid 'pattern': " + e.getMessage());
        }
        int maxLength = def.intParam("maxLength", 32);
        if (maxLength < 1 || maxLength > 255) {
            throw new SegmentTemplateException(
                    "VARIABLE segment '" + def.getName() + "' 'maxLength' must be between 1 and 255");
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        String name = def.getName();
        String label = def.stringParam("label", name);
        String value = context.getVariables().get(name);
        boolean required = def.boolParam("required", true);

        if (value == null || value.isBlank()) {
            value = def.stringParam("default");
        }
        if (value == null || value.isBlank()) {
            if (required) {
                throw new SegmentTemplateException(
                        "Missing required variable segment '" + label + "' (" + name + ")");
            }
            throw new SegmentTemplateException(
                    "Variable segment '" + label + "' (" + name + ") has no value and no default");
        }

        int pad = def.intParam("pad", 0);
        if (pad > 0 && value.length() < pad) {
            value = "0".repeat(pad - value.length()) + value;
        }

        String pattern = def.stringParam("pattern");
        if (pattern != null && !Pattern.compile(pattern).matcher(value).matches()) {
            throw new SegmentTemplateException(
                    "Variable segment '" + label + "' value '" + value + "' does not match " + pattern);
        }
        return value;
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        String pattern = def.stringParam("pattern");
        if (pattern == null || pattern.isBlank()) {
            throw new SegmentTemplateException(
                    "VARIABLE segment '" + def.getName() + "' cannot derive a format regex without a 'pattern'");
        }
        return "(?:" + pattern + ")";
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"label\",\"label\":\"提示标签\",\"type\":\"text\"},"
                + "{\"key\":\"pattern\",\"label\":\"校验正则\",\"type\":\"text\",\"required\":true},"
                + "{\"key\":\"pad\",\"label\":\"补零位数\",\"type\":\"number\",\"default\":0},"
                + "{\"key\":\"default\",\"label\":\"默认值\",\"type\":\"text\"},"
                + "{\"key\":\"required\",\"label\":\"必填\",\"type\":\"boolean\",\"default\":true},"
                + "{\"key\":\"maxLength\",\"label\":\"最大长度\",\"type\":\"number\",\"default\":32}]}";
    }
}
