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
}
