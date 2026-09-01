package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Concept;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public abstract class ConceptualDomain extends Concept {

    /**
     * 概念域的有效期。
     *
     * <p>ISO/IEC 11179-31:2023 表 2(7.4.2.1.4):valid_period 多重性 1,数据类型
     * Datetime_Period(ISO/IEC 11179-3:2023, 6.3.10)。注册机构可自行决定该时间
     * 是概念域在注册系统中生效的时间,还是其所描述概念在现实世界中的有效时间;
     * 两端 datetime 各自 0..1,但至少指定其一(6.3.10 约束)。
     */
    @Embedded
    private Datetime_Period validPeriod;

    /**
     * 概念域的量纲。
     *
     * <p>ISO/IEC 11179-31:2023 7.4.2.1(图 5):dimensionality 0..1,
     * 关联 Dimensionality 类(7.5.2.3)。
     */
    @ManyToOne
    @JoinColumn(name = "dimensionality_id")
    private Dimensionality dimensionality;

    @ManyToMany
    @JoinTable(
            name = "conceptual_domain_dec",
            joinColumns = @JoinColumn(name = "conceptual_domain_id"),
            inverseJoinColumns = @JoinColumn(name = "data_element_concept_id")
    )
    private List<DataElementConcept> dataElementConcepts = new ArrayList<>();
}
