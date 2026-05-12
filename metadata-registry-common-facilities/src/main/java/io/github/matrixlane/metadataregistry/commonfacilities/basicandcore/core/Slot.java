package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
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

import java.util.UUID;

/**
 * ISO/IEC 11179-3:2023, 6.4.6 — Slot
 * <p>
 * A dynamic extension mechanism for {@link Item}, providing name-value-datatype
 * triples. The combination of an Item and a Slot name must be unique.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.4.6")
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

    private String name;

    private String value;

    /**
     * The datatype of the value, as a URI or type identifier.
     * Corresponds to the standard attribute {@code value_datatype}.
     */
    private String value_datatype;

    /**
     * Legacy field retained for backward compatibility.
     * <p>
     * DEVIATION: The standard uses {@code value_datatype}; this field predates
     * the standard-aligned naming. Migrate to {@link #value_datatype}.
     *
     * @deprecated Use {@link #value_datatype} instead.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    private String type;

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    public Slot(String name, String value, String value_datatype, Item item) {
        this.name = name;
        this.value = value;
        this.value_datatype = value_datatype;
        this.item = item;
    }
}
