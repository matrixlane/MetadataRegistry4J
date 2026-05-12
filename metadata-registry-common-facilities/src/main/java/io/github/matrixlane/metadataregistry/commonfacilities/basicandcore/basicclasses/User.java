package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Project-specific extension: system user for authentication and authorization.
 *
 * <p>DEVIATION: This class is not defined in ISO/IEC 11179-3:2023. It is retained
 * as a project-specific extension to support multi-tenant user management.
 */
@StandardReference(value = "ISO/IEC 11179-3:2023",
        deviation = "Not defined in the standard. Project-specific extension for authentication.")
@jakarta.persistence.Entity
@Table(name = "registry_user")
@Getter
@Setter
@NoArgsConstructor
public class User extends Entity {

    @Column(nullable = false)
    private String objectId;

    @Column(nullable = false)
    private String tenautId;

    @Column(nullable = false)
    private String name;

    private String email;
}
