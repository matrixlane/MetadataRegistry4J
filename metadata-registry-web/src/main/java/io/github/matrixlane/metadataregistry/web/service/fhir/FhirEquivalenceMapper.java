package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import org.hl7.fhir.r4.model.Enumerations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps internal {@link MappingType} values to FHIR R4 {@link Enumerations.ConceptMapEquivalence} codes.
 *
 * <p>Per the equivalence mapping rules defined in WI-011-2-2:
 *
 * <table>
 *   <tr><th>本地映射类型</th><th>FHIR equivalence</th><th>说明</th></tr>
 *   <tr><td>EQUIVALENT</td><td>equivalent</td><td>语义完全等价</td></tr>
 *   <tr><td>DERIVED</td><td>wider</td><td>源项语义更宽泛，目标项是其细化</td></tr>
 *   <tr><td>AGGREGATED</td><td>narrower</td><td>源项语义更窄，目标项是其概括</td></tr>
 *   <tr><td>SUBDIVIDED</td><td>relatedto</td><td>存在关联但非严格等价，需人工判断</td></tr>
 * </table>
 *
 * <p>This mapper is stateless and thread-safe.
 */
public final class FhirEquivalenceMapper {

    private static final Logger log = LoggerFactory.getLogger(FhirEquivalenceMapper.class);

    private static final Map<MappingType, Enumerations.ConceptMapEquivalence> MAPPING;

    static {
        Map<MappingType, Enumerations.ConceptMapEquivalence> map =
                new EnumMap<>(MappingType.class);
        map.put(MappingType.EQUIVALENT, Enumerations.ConceptMapEquivalence.EQUIVALENT);
        map.put(MappingType.DERIVED, Enumerations.ConceptMapEquivalence.WIDER);
        map.put(MappingType.AGGREGATED, Enumerations.ConceptMapEquivalence.NARROWER);
        map.put(MappingType.SUBDIVIDED, Enumerations.ConceptMapEquivalence.RELATEDTO);
        MAPPING = Collections.unmodifiableMap(map);
    }

    private FhirEquivalenceMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Maps a {@link MappingType} to its corresponding FHIR {@link Enumerations.ConceptMapEquivalence}.
     *
     * @param mappingType the internal mapping type (must not be null)
     * @return the FHIR equivalence code
     * @throws IllegalArgumentException if the mapping type has no defined FHIR equivalence
     */
    public static Enumerations.ConceptMapEquivalence toFhirEquivalence(MappingType mappingType) {
        Enumerations.ConceptMapEquivalence equivalence = MAPPING.get(mappingType);
        if (equivalence == null) {
            log.warn("No FHIR equivalence defined for MappingType: {}", mappingType);
            throw new IllegalArgumentException(
                    "No FHIR equivalence mapping defined for: " + mappingType);
        }
        return equivalence;
    }

    /**
     * Returns the FHIR equivalence code as a string for use in JSON serialization.
     *
     * @param mappingType the internal mapping type
     * @return the FHIR equivalence code string
     */
    public static String toFhirEquivalenceCode(MappingType mappingType) {
        return toFhirEquivalence(mappingType).toCode();
    }

    /**
     * Returns the canonical FHIR system URI for the ConceptMap equivalence code system.
     *
     * @return "http://hl7.org/fhir/concept-map-equivalence"
     */
    public static String getEquivalenceSystem() {
        return "http://hl7.org/fhir/concept-map-equivalence";
    }

    /**
     * Returns the number of defined mapping type → equivalence entries.
     *
     * @return the count of defined mappings
     */
    public static int mappingCount() {
        return MAPPING.size();
    }
}
