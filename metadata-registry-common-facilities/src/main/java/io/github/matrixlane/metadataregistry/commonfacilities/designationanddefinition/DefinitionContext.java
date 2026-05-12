package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@jakarta.persistence.Entity
@Table(name = "definition_context_relation")
@Getter
@Setter
@NoArgsConstructor
public class DefinitionContext {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Acceptability acceptability;

    @ManyToOne
    @JoinColumn(name = "definition_id")
    private Definition definition;

    @ManyToOne
    @JoinColumn(name = "context_id")
    private Context context;
}
