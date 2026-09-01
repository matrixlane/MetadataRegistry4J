package io.github.matrixlane.metadataregistry.ext.identification.segment;

/**
 * Thrown when a segment template is structurally invalid, cannot be
 * generated from, or cannot derive a format regex / parser.
 */
public class SegmentTemplateException extends RuntimeException {

    public SegmentTemplateException(String message) {
        super(message);
    }

    public SegmentTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
