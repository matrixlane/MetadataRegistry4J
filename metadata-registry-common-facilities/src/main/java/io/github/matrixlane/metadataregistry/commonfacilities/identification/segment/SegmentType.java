package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

/**
 * Catalog of identifier template segment types.
 *
 * <p>RANDOM is optional per spec; all others are mandatory.
 */
public enum SegmentType {
    /** Fixed literal string, e.g. a custom prefix. */
    LITERAL,
    /** Abbreviation derived from the registered item's object type (DataElement -&gt; DE). */
    OBJECT_ABBREVIATION,
    /** Auto-incrementing sequence number, optionally scoped to other segments. */
    SEQUENCE,
    /** Random UUID, full or short form. */
    UUID,
    /** Separator character, restricted to a predefined character set. */
    SEPARATOR,
    /** Formatted current date/time. */
    TIMESTAMP,
    /** Check digit(s) computed over the concatenation of preceding segments. */
    CHECKSUM,
    /** Caller-supplied input variable (e.g. major/minor category). */
    VARIABLE,
    /** Fixed-length random characters (optional type). */
    RANDOM
}
