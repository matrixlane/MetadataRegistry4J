package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.Embedded;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public abstract class ValueDomain extends Item {

    /** ISO/IEC 11179-31:2023 表 5(Value_Domain):datatype 1,必备。 */
    @ManyToOne(optional = false)
    @JoinColumn(name = "data_type_id", nullable = false)
    private DataType dataType;

    @Embedded
    private Datetime_Period validPeriod;

    private Integer maximumCharacterQuantity;

    private String format;

    @ManyToOne
    @JoinColumn(name = "unit_of_measure_id")
    private UnitOfMeasure unitOfMeasure;

    /**
     * value_domain_meaning(ISO/IEC 11179-31:2023 7.2.3.1 / 7.4.4.1):
     * Value_Domain 0..1 → Conceptual_Domain。子类型一致约束见 §7.4.5.2.1
     * (描述↔描述 / 本地枚举↔本地枚举 / 引用枚举↔引用枚举),由
     * association.ValueDomainConceptualDomains 在写入时强制。
     * 子类上的类型化访问方法(getDescribedConceptualDomain 等)是本关联的视图。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conceptual_domain_id")
    private ConceptualDomain conceptualDomain;

    @OneToMany(mappedBy = "valueDomain")
    private List<DataElement> dataElements = new ArrayList<>();
}
