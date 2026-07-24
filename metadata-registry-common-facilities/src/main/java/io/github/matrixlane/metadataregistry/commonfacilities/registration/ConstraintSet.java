package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 9.4.5 — Constraint_Set
 *
 * <p>A set of constraints about zero, one or more registered items. A registration
 * authority can apply a Constraint_Set to a Registered_Item to specify the constraints
 * that should be enforced when its Administered_Item.registration_state.registration_status
 * is "Recorded" or higher.
 */
@StandardReference("ISO/IEC 11179-3:2023, 9.4.5")
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ConstraintSet extends Item {

    @Column(name = "item_shall_be_designated_indicator", nullable = false)
    private Boolean itemShallBeDesignatedIndicator = true;

    @Column(name = "item_shall_be_defined_indicator", nullable = false)
    private Boolean itemShallBeDefinedIndicator = true;

    @Column(name = "item_shall_be_classified_indicator", nullable = false)
    private Boolean itemShallBeClassifiedIndicator = true;
}
