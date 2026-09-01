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

    /**
     * Item identification 关联(11179-3:2023 7.4.1):0..1。
     */
    @jakarta.persistence.OneToOne(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
    private IdentifiedItem identifiedItem;

    public IdentifiedItem getIdentifiedItem() {
        return identifiedItem;
    }

    public void setIdentifiedItem(IdentifiedItem identifiedItem) {
        this.identifiedItem = identifiedItem;
        if (identifiedItem != null) {
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
