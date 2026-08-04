package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

/**
 * Registry entry for one segment type. Provides the five spec-mandated
 * elements: parameter schema (validation), generator, format-regex fragment,
 * parse fragment, and a UI panel descriptor. New segment types are added by
 * registering a new handler — the engine core never changes.
 */
public interface SegmentTypeHandler {

    /** The segment type this handler implements. */
    SegmentType type();

    /**
     * Validates the segment's parameters (the parameter schema).
     *
     * @throws SegmentTemplateException when a parameter is missing or illegal
     */
    void validateParams(SegmentDefinition def);

    /**
     * Produces this segment's contribution to the identifier.
     */
    String generate(SegmentDefinition def, SegmentContext context);

    /**
     * Regex fragment matching exactly this segment's value (ungrouped).
     * Used for full-format validation and reverse parsing.
     *
     * @throws SegmentTemplateException when no regex can be derived
     *         (e.g. a VARIABLE segment without a pattern)
     */
    String valueRegex(SegmentDefinition def);

    /**
     * JSON descriptor of the designer UI configuration panel for this type
     * (fields, labels, defaults). Frontends render their panel from it.
     */
    String uiPanelSchema();
}
