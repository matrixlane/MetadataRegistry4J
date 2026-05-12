package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public class IdentifiedItem extends Entity {

    @OneToOne
    @JoinColumn(name = "item_id")
    private Item item;

    @OneToMany(mappedBy = "identifiedItem", cascade = CascadeType.ALL)
    private List<ScopedIdentifier> scopedIdentifiers = new ArrayList<>();

    public IdentifiedItem(Item item) {
        this.item = item;
    }
}
