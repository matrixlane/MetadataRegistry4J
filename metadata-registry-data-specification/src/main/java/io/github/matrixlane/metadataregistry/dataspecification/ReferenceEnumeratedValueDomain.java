
package io.github.matrixlane.metadataregistry.dataspecification;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class ReferenceEnumeratedValueDomain extends EnumeratedValueDomain {

    /**
     * 外部源中承载许可值的字段名（ISO/IEC 11179-31:2023 §7.4.2.13 必填属性 permitted_values_fieldname）。
     */
    @Column(name = "permitted_values_fieldname", nullable = false, length = 255)
    private String permittedValuesFieldname;

    /**
     * 该引用枚举值域所关联的外部源概念域（典型如 ISO 3166、HL7 v3 codeSystem 等代码集标识）。
     * ISO/IEC 11179-31:2023 §7.4.4.4 推荐绑定一个 Reference_Enumerated_Conceptual_Domain；本期业务上可空。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referenced_conceptual_domain_id")
    private ReferenceEnumeratedConceptualDomain referencedConceptualDomain;
}
