
package io.github.matrixlane.metadataregistry.dataspecification;

import jakarta.persistence.Column;
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
     * value_domain_meaning 在引用枚举子类型上的类型化视图(基类
     * {@link #getConceptualDomain()} 的窄化)。按 ISO/IEC 11179-31:2023
     * §7.4.5.2.1(c),Reference_Enumerated_Value_Domain 的 meaning 必须是
     * Reference_Enumerated_Conceptual_Domain;类型不一致时读取为 null。
     */
    public ReferenceEnumeratedConceptualDomain getReferencedConceptualDomain() {
        return getConceptualDomain() instanceof ReferenceEnumeratedConceptualDomain recd ? recd : null;
    }

    public void setReferencedConceptualDomain(ReferenceEnumeratedConceptualDomain referencedConceptualDomain) {
        setConceptualDomain(referencedConceptualDomain);
    }
}
