package io.github.matrixlane.metadataregistry.ext.identification.segment;

/**
 * Catalog of identifier template segment types.
 */
public enum SegmentType {
    /** Fixed literal string, e.g. a custom prefix. */
    LITERAL,
    /** Abbreviation derived from the registered item's object type (DataElement -&gt; DE). */
    OBJECT_ABBREVIATION,
    /** Auto-incrementing sequence number; scope derived from the template structure. */
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
    VARIABLE
}
