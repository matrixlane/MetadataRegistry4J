package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Fixed-length random characters (optional segment type). Params: length
 * (default 8), alphabet (default digits + uppercase letters).
 */
public class RandomSegmentHandler implements SegmentTypeHandler {

    private static final String DEFAULT_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public SegmentType type() {
        return SegmentType.RANDOM;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        int length = def.intParam("length", 8);
        if (length < 1 || length > 64) {
            throw new SegmentTemplateException("RANDOM segment 'length' must be between 1 and 64");
        }
        String alphabet = def.stringParam("alphabet", DEFAULT_ALPHABET);
        if (alphabet.isEmpty()) {
            throw new SegmentTemplateException("RANDOM segment 'alphabet' must not be empty");
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        int length = def.intParam("length", 8);
        String alphabet = def.stringParam("alphabet", DEFAULT_ALPHABET);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        int length = def.intParam("length", 8);
        String alphabet = def.stringParam("alphabet", DEFAULT_ALPHABET);
        StringBuilder charClass = new StringBuilder("[");
        for (char c : alphabet.toCharArray()) {
            if (c == '\\' || c == ']' || c == '[' || c == '^' || c == '-') {
                charClass.append('\\');
            }
            charClass.append(c);
        }
        return charClass.append(']').append('{').append(length).append('}').toString();
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"length\",\"label\":\"长度\",\"type\":\"number\",\"default\":8},"
                + "{\"key\":\"alphabet\",\"label\":\"字符集\",\"type\":\"text\"}]}";
    }
}
