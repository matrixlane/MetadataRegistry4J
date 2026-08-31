package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class DesignationDefinitionPairing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Acceptability acceptability;

    @ManyToOne
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne
    @JoinColumn(name = "definition_id")
    private Definition definition;

    @ManyToOne
    @JoinColumn(name = "context_id")
    private Context context;
}
