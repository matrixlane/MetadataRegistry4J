package io.github.matrixlane.metadataregistry.itemmapping.enums;

/**
 * Defines the types of mapping relationships between administered items
 * or between an administered item and an external resource.
 *
 * <p>Mapping types follow specific mutual exclusion rules enforced at
 * the service layer:
 * <ul>
 *   <li>{@link #EQUIVALENT} is mutually exclusive with all other types</li>
 *   <li>{@link #DERIVED} is mutually exclusive with {@link #AGGREGATED}</li>
 *   <li>{@link #AGGREGATED} is mutually exclusive with {@link #SUBDIVIDED}</li>
 * </ul>
 */
public enum MappingType {

    /**
     * The subject and object are semantically equivalent representations
     * of the same concept. This is a bidirectional relationship.
     */
    EQUIVALENT,

    /**
     * The object is derived from the subject through transformation,
     * calculation, or other derivation rules. Directional: subject → object.
     */
    DERIVED,

    /**
     * The object is an aggregation/composition of the subject.
     * The subject is a component of the object. Directional: subject → object.
     */
    AGGREGATED,

    /**
     * The object is a subdivision/part of the subject.
     * The subject is decomposed into the object. Directional: subject → object.
     * This is the inverse direction of AGGREGATED.
     */
    SUBDIVIDED
}
