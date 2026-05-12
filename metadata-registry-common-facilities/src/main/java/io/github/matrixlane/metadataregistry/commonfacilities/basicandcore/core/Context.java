package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DefinitionContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ISO/IEC 11179-3:2023, 6.4.3 — Context
 *
 * <p>A delimited area in which terms, definitions, or other designations
 * are used with specific meanings.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.4.3")
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class Context extends Item {

    @OneToMany(mappedBy = "context", cascade = CascadeType.ALL)
    private List<DesignationContext> designationContexts = new ArrayList<>();

    @OneToMany(mappedBy = "context", cascade = CascadeType.ALL)
    private List<DefinitionContext> definitionContexts = new ArrayList<>();
}
