package io.github.matrixlane.metadataregistry.commonfacilities.identification;

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
public class ScopedIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String identifier;

    private String version;

    @ManyToOne
    @JoinColumn(name = "namespace_id")
    private Namespace namespace;

    @ManyToOne
    @JoinColumn(name = "identified_item_id")
    private IdentifiedItem identifiedItem;
}
