package io.github.matrixlane.metadataregistry.commonfacilities.identification;

/**
 * Enumeration of supported identifier types in the MDR system.
 *
 * <p>Per WI-028-B: each ScopedIdentifier carries a type that drives strategy selection.
 * Multiple ScopedIdentifiers for the same Item in the same Namespace may coexist
 * with different types (e.g., one INTERNAL and one URN).
 *
 * <p>Resolution of OQ-028-B-1: identifierType does NOT participate in uniqueness
 * constraint. The unique constraint remains (namespace, identifier).
 * Same item in same namespace CAN have type=URN and type=OID with different identifier values.
 */
public enum IdentifierType {

    /**
     * Default internal identifier, typically auto-generated UUID.
     */
    INTERNAL("internal", "Internal identifier (default)"),

    /**
     * URN-style identifier per RFC 2141 and related specs.
     * Used for FHIR/HL7 interoperability.
     */
    URN("urn", "URN per RFC 2141"),

    /**
     * ISO OID (Object Identifier) for global identification standards.
     */
    OID("oid", "ISO Object Identifier"),

    /**
     * LOINC code identifier for clinical observations.
     */
    LOINC("loinc", "LOINC code identifier"),

    /**
     * SNOMED CT identifier for clinical terminology.
     */
    SNOMED("snomed", "SNOMED CT identifier"),

    /**
     * Custom/business-defined identifier type.
     * Requires registration through the type registry mechanism.
     */
    CUSTOM("custom", "Custom/business-defined identifier type");

    private final String code;
    private final String description;

    IdentifierType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Lookup by code string (case-insensitive).
     */
    public static IdentifierType fromCode(String code) {
        if (code == null) {
            return INTERNAL;
        }
        for (IdentifierType t : values()) {
            if (t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code)) {
                return t;
            }
        }
        return CUSTOM;
    }
}
