package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

/**
 * Thrown when a sequence scope reaches the maximum value representable with
 * its pad length. Generation must be rejected (never truncated or repeated).
 */
public class SequenceOverflowException extends SegmentTemplateException {

    public SequenceOverflowException(String message) {
        super(message);
    }
}
