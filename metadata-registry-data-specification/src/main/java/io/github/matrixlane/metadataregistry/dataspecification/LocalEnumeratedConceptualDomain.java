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
public class LocalEnumeratedConceptualDomain extends EnumeratedConceptualDomain {

    /**
     * 只允许存在一个当前有效的值含义集合。
     */
    @OneToMany(mappedBy = "localEnumeratedConceptualDomain")
    private List<ValueMeaningSet> valueMeaningSets = new ArrayList<>();
}
