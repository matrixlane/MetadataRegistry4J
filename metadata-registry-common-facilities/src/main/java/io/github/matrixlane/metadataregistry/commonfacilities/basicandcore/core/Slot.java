package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ISO/IEC 11179-3:2023, 6.4.2.4 — Slot
 * <p>
 * A dynamic extension mechanism for {@link Item}, providing name-value-datatype
 * triples. The combination of an Item and a Slot name must be unique.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.4.2.4")
@jakarta.persistence.Entity
@Table(
        name = "slot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_slot_item_name",
                columnNames = {"item_id", "name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 表 11:name 1,必备。 */
    @Column(nullable = false)
    private String name;

    /**
     * ISO/IEC 11179-3:2023 表 11(Slot):value 1..* String (6.2.11)。
     */
    @ElementCollection
    @CollectionTable(name = "slot_value", joinColumns = @JoinColumn(name = "slot_id"))
    @Column(name = "value", nullable = false)
    private List<String> value = new ArrayList<>();

    /**
     * ISO/IEC 11179-3:2023 表 11(Slot):type 0..1 String (6.2.11)。
     * 对 slot 取值的分类,可用于(但不限于)标识取值的数据类型。
     */
    private String type;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    public Slot(String name, String value, String type, Item item) {
        this.name = name;
        if (value != null) {
            this.value.add(value);
        }
        this.type = type;
        this.item = item;
    }
}
