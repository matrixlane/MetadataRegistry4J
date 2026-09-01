package io.github.matrixlane.metadataregistry.ext.identification.segment.types;

import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTypeHandler;

/**
 * Check character computed over the concatenation of all preceding segment
 * values (digits extracted). Params: algorithm — "luhn" (default) or "mod11".
 */
public class ChecksumSegmentHandler implements SegmentTypeHandler {

    @Override
    public SegmentType type() {
        return SegmentType.CHECKSUM;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        String algorithm = def.stringParam("algorithm", "luhn");
        if (!"luhn".equalsIgnoreCase(algorithm) && !"mod11".equalsIgnoreCase(algorithm)) {
            throw new SegmentTemplateException("CHECKSUM segment 'algorithm' must be 'luhn' or 'mod11'");
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        String algorithm = def.stringParam("algorithm", "luhn");
        String digits = context.getCurrentPrefix().replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new SegmentTemplateException("CHECKSUM segment has no digits in preceding segments to check");
        }
        return "mod11".equalsIgnoreCase(algorithm) ? mod11(digits) : luhn(digits);
    }

    /** Luhn check digit for the given digit string. */
    public static String luhn(String digits) {
        int sum = 0;
        boolean doubleIt = true; // digits to the left of the check position
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return String.valueOf((10 - (sum % 10)) % 10);
    }

    /** ISO 7064-style MOD 11 check character (0-9 or X), alternating weights 2,1. */
    public static String mod11(String digits) {
        int sum = 0;
        int weight = 2;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 2 ? 1 : 2;
        }
        int check = (11 - (sum % 11)) % 11;
        return check == 10 ? "X" : String.valueOf(check);
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        String algorithm = def.stringParam("algorithm", "luhn");
        return "mod11".equalsIgnoreCase(algorithm) ? "[0-9X]" : "\\d";
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"algorithm\",\"label\":\"校验算法\",\"type\":\"select\","
                + "\"options\":[\"luhn\",\"mod11\"],\"default\":\"luhn\"}]}";
    }
}
