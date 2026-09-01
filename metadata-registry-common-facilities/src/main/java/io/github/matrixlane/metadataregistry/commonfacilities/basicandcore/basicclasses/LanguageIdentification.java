package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ISO/IEC 11179-3:2023, 6.3.7 — Language_Identification
 *
 * <p>Identifies a natural language using the BCP 47 tag system,
 * comprising language, script, region, variant, extension, and private-use subtags.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.7")
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class LanguageIdentification extends Entity {

    private String languageIdentifier;

    @Column(name = "enabled")
    private Boolean enabled = true;

    private String scriptIdentifier;

    private String geopoliticalTerritoryIdentifier;

    @ElementCollection
    @CollectionTable(name = "language_variant", joinColumns = @JoinColumn(name = "language_id"))
    @Column(name = "variant")
    private List<String> variantIdentifier = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "language_extension", joinColumns = @JoinColumn(name = "language_id"))
    @Column(name = "extension")
    private List<String> extensionIdentifier = new ArrayList<>();

    private String privateUseQualifier;

    /**
     * Builds a BCP 47 language tag from the components.
     *
     * <p>Format: language[-script][-region][-variant...][-extension...][-x-privateuse]
     */
    public String toBcp47Tag() {
        StringBuilder tag = new StringBuilder();
        if (languageIdentifier != null && !languageIdentifier.isBlank()) {
            tag.append(languageIdentifier);
        }
        if (scriptIdentifier != null && !scriptIdentifier.isBlank()) {
            tag.append("-").append(scriptIdentifier);
        }
        if (geopoliticalTerritoryIdentifier != null && !geopoliticalTerritoryIdentifier.isBlank()) {
            tag.append("-").append(geopoliticalTerritoryIdentifier);
        }
        for (String variant : variantIdentifier) {
            if (variant != null && !variant.isBlank()) {
                tag.append("-").append(variant);
            }
        }
        for (String extension : extensionIdentifier) {
            if (extension != null && !extension.isBlank()) {
                tag.append("-").append(extension);
            }
        }
        if (privateUseQualifier != null && !privateUseQualifier.isBlank()) {
            tag.append("-x-").append(privateUseQualifier);
        }
        return tag.toString();
    }

    @Override
    public String toString() {
        return languageIdentifier;
    }
}
