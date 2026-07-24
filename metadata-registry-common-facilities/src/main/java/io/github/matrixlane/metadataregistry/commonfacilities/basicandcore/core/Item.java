package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ISO/IEC 11179-3:2023, 6.4.1 — Item
 *
 * <p>The abstract superclass of all registry entries.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.4.1")
@jakarta.persistence.Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class Item extends Entity {

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Slot> slots = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Designation> designations = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Definition> definitions = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
    private List<IdentifiedItem> identifiedItems = new ArrayList<>();

    public IdentifiedItem getIdentifiedItem() {
        return (identifiedItems == null || identifiedItems.isEmpty()) ? null : identifiedItems.get(0);
    }

    public void setIdentifiedItem(IdentifiedItem identifiedItem) {
        if (this.identifiedItems == null) {
            this.identifiedItems = new ArrayList<>();
        }
        this.identifiedItems.clear();
        if (identifiedItem != null) {
            this.identifiedItems.add(identifiedItem);
            identifiedItem.setItem(this);
        }
    }

    @Override
    public String toString() {
        if (designations.isEmpty()) {
            return super.toString();
        }
        return designations.get(0).toString();
    }
}
