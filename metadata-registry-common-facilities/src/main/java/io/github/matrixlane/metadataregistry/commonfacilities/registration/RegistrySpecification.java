package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Table(name = "registry_specification")
@Getter
@Setter
@NoArgsConstructor
public class RegistrySpecification extends Entity {

    @Column(name = "registry_name", nullable = false)
    private String registryName;

    @Column(name = "identifier_prefix")
    private String identifierPrefix;

    @Column(name = "auto_generate_identifier", nullable = false)
    private Boolean autoGenerateIdentifier = true;

    @Column(name = "identifier_pattern")
    private String identifierPattern;

    @Column(name = "versioning_policy")
    private String versioningPolicy; // e.g. "SEMVER", "INTEGRAL"

    @Column(name = "mandatory_classification", nullable = false)
    private Boolean mandatoryClassification = false;

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage = "zh-CN";

    @Column(name = "description")
    private String description;
}

