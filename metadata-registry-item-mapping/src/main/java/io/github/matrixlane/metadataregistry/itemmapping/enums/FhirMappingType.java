package io.github.matrixlane.metadataregistry.itemmapping.enums;

/**
 * Defines the cardinality type of a FHIR element mapping.
 *
 * <p>Per WI-012-2 — Local metadata to FHIR Element mapping:
 * <ul>
 *   <li>{@link #ONE_TO_ONE} — one local item maps to exactly one FHIR Element</li>
 *   <li>{@link #ONE_TO_MANY} — one local item maps to multiple FHIR Elements
 *        (e.g. a full-name field split into family/given)</li>
 * </ul>
 */
public enum FhirMappingType {

    /**
     * Equivalent mapping: one local administered item corresponds to
     * exactly one FHIR Element.
     */
    ONE_TO_ONE,

    /**
     * Subdivided mapping: one local administered item is decomposed into
     * multiple FHIR Elements. Requires a mapping variable to identify
     * the target component.
     */
    ONE_TO_MANY
}
