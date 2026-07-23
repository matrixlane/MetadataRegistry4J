package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 8.4.4 — Naming_Convention.
 *
 * <p>A naming convention provides a set of rules for constructing the sign of a designation
 * of a registry item. It is a subclass of Item and can therefore be identified, registered,
 * administered, named, defined and classified.
 */
@StandardReference("ISO/IEC 11179-3:2023, 8.4.4")
@Entity
@Getter
@Setter
@NoArgsConstructor
public class NamingConvention extends Item {

    /**
     * Rule specifying the range within which the naming convention is in effect.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "scope_rule"))
    })
    private Text scopeRule;

    /**
     * Rule identifying the authority that assigns names or enforces naming conventions, or both.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "authority_rule"))
    })
    private Text authorityRule;
}
