package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
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

@StandardReference("ISO/IEC 11179-3:2023, 8.5.2")
@jakarta.persistence.Entity
@Table(name = "designation_context", indexes = {
        @jakarta.persistence.Index(name = "idx_designation_context_designation_id", columnList = "designation_id"),
        @jakarta.persistence.Index(name = "idx_designation_context_context_id", columnList = "context_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DesignationContext {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Acceptability acceptability;

    @ManyToOne
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne
    @JoinColumn(name = "context_id")
    private Context context;
}

