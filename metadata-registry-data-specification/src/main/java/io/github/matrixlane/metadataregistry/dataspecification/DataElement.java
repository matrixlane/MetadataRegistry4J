package io.github.matrixlane.metadataregistry.dataspecification;

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
public class DataElement extends Item {

    @ManyToOne
    @JoinColumn(name = "data_element_concept_id")
    private DataElementConcept dataElementConcept;

    @ManyToOne
    @JoinColumn(name = "value_domain_id")
    private ValueDomain valueDomain;

    /**
     * 精度：关联数据元值允许的小数位数。
     * ISO/IEC 11179-31:2023 §7.6.2.1.4 Table 23：precision，0..1，Integer（ISO/IEC 11179-3:2023, 6.2.5）。
     */
    private Integer precision;
}
