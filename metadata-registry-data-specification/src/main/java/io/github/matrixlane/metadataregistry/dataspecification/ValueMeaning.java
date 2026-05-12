package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Concept;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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
public class ValueMeaning extends Concept {

    @OneToMany(mappedBy = "valueMeaning")
    private List<ValueMeaningSet> valueMeaningSets = new ArrayList<>();

    /**
     * 只允许存在一个当前有效的允许值含义。
     */
    @ManyToMany
    @JoinTable(
            name = "value_meaning_permissible_value_meaning",
            joinColumns = @JoinColumn(name = "value_meaning_id"),
            inverseJoinColumns = @JoinColumn(name = "permissible_value_meaning_id")
    )
    private List<PermissibleValueMeaning> permissibleValueMeanings = new ArrayList<>();
}
