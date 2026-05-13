package io.github.matrixlane.metadataregistry.itemmapping.enums;

/**
 * FHIR R4 ConceptMap equivalence codes for local-to-FHIR mappings.
 *
 * <p>Per FHIR R4 specification:
 * <a href="http://hl7.org/fhir/concept-map-equivalence">ConceptMap Equivalence</a>.
 *
 * <table>
 *   <tr><th>Enum</th><th>FHIR code</th><th>Description</th></tr>
 *   <tr><td>EQUIVALENT</td><td>equivalent</td><td>Semantically equivalent</td></tr>
 *   <tr><td>EQUAL</td><td>equal</td><td>Equal definition and meaning</td></tr>
 *   <tr><td>WIDER</td><td>wider</td><td>Source is broader than target</td></tr>
 *   <tr><td>NARROWER</td><td>narrower</td><td>Source is narrower than target</td></tr>
 *   <tr><td>SUBSUMES</td><td>subsumes</td><td>Target is a subtype of source</td></tr>
 *   <tr><td>SPECIALIZES</td><td>specializes</td><td>Source is a subtype of target</td></tr>
 *   <tr><td>RELATEDTO</td><td>relatedto</td><td>Related but not equivalent</td></tr>
 *   <tr><td>INEXACT</td><td>inexact</td><td>Approximately related</td></tr>
 *   <tr><td>UNMATCHED</td><td>unmatched</td><td>No match found</td></tr>
 *   <tr><td>DISJOINT</td><td>disjoint</td><td>No overlap in meaning</td></tr>
 * </table>
 */
public enum FhirMappingEquivalence {

    EQUIVALENT,
    EQUAL,
    WIDER,
    NARROWER,
    SUBSUMES,
    SPECIALIZES,
    RELATEDTO,
    INEXACT,
    UNMATCHED,
    DISJOINT;

    /**
     * Returns the FHIR R4 canonical code string for this equivalence.
     *
     * @return the lowercase code (e.g. "equivalent", "wider")
     */
    public String toCode() {
        return name().toLowerCase();
    }

    /**
     * Parses a FHIR equivalence code string into this enum.
     *
     * @param code the FHIR code (case-insensitive)
     * @return the corresponding enum value
     * @throws IllegalArgumentException if the code is unknown
     */
    public static FhirMappingEquivalence fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be null or blank");
        }
        return valueOf(code.toUpperCase());
    }
}
