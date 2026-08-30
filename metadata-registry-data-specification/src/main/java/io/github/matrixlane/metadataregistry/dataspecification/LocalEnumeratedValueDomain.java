package io.github.matrixlane.metadataregistry.dataspecification;

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
public class LocalEnumeratedValueDomain extends EnumeratedValueDomain {

    /**
     * 只允许存在一个当前有效的允许值集。
     */
    @OneToMany(mappedBy = "localEnumeratedValueDomain")
    private List<PermissibleValueSet> permissibleValueSets = new ArrayList<>();

    /**
     * value_domain_meaning（ISO/IEC 11179-31:2023 §7.4.4.1）在本地枚举子类型上的实现：
     * 按 §7.4.5.2.1(a)，Local_Enumerated_Value_Domain 的 meaning 必须是
     * Local_Enumerated_Conceptual_Domain。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_enumerated_conceptual_domain_id")
    private LocalEnumeratedConceptualDomain localEnumeratedConceptualDomain;
}
