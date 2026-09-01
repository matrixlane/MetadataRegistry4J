package io.github.matrixlane.metadataregistry.dataspecification;

import lombok.NoArgsConstructor;

@jakarta.persistence.Entity
@NoArgsConstructor
public class DescribedValueDomain extends ValueDomain {

    /**
     * value_domain_meaning 在描述子类型上的类型化视图(基类
     * {@link #getConceptualDomain()} 的窄化)。按 ISO/IEC 11179-31:2023
     * §7.4.5.2.1(a),Described_Value_Domain 的 meaning 必须是
     * Described_Conceptual_Domain;类型不一致时读取为 null。
     */
    public DescribedConceptualDomain getDescribedConceptualDomain() {
        return getConceptualDomain() instanceof DescribedConceptualDomain dcd ? dcd : null;
    }

    public void setDescribedConceptualDomain(DescribedConceptualDomain describedConceptualDomain) {
        setConceptualDomain(describedConceptualDomain);
    }
}
