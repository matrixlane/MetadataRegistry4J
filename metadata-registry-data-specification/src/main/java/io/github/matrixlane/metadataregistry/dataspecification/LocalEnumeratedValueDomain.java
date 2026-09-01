package io.github.matrixlane.metadataregistry.dataspecification;

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
public class LocalEnumeratedValueDomain extends EnumeratedValueDomain {

    /**
     * 只允许存在一个当前有效的允许值集。
     */
    @OneToMany(mappedBy = "localEnumeratedValueDomain")
    private List<PermissibleValueSet> permissibleValueSets = new ArrayList<>();

    /**
     * value_domain_meaning 在本地枚举子类型上的类型化视图(基类
     * {@link #getConceptualDomain()} 的窄化)。按 ISO/IEC 11179-31:2023
     * §7.4.5.2.1(b),Local_Enumerated_Value_Domain 的 meaning 必须是
     * Local_Enumerated_Conceptual_Domain;类型不一致时读取为 null。
     */
    public LocalEnumeratedConceptualDomain getLocalEnumeratedConceptualDomain() {
        return getConceptualDomain() instanceof LocalEnumeratedConceptualDomain lecd ? lecd : null;
    }

    public void setLocalEnumeratedConceptualDomain(LocalEnumeratedConceptualDomain localEnumeratedConceptualDomain) {
        setConceptualDomain(localEnumeratedConceptualDomain);
    }
}
