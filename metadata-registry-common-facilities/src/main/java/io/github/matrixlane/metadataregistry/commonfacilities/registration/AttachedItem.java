package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class AttachedItem extends RegisteredItem {

    @ManyToOne
    @JoinColumn(name = "administered_item_id")
    private AdministeredItem administeredItem;

    public AttachedItem(Item item) {
        super(item);
    }
}
