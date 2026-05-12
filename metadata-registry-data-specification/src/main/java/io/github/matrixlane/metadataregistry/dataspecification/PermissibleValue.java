package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
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
public class PermissibleValue extends Item {

    /**
     * 只允许存在一个当前有效的允许值含义。
     */
    @ManyToMany
    @JoinTable(
            name = "permissible_value_pvm",
            joinColumns = @JoinColumn(name = "permissible_value_id"),
            inverseJoinColumns = @JoinColumn(name = "permissible_value_meaning_id")
    )
    private List<PermissibleValueMeaning> permissibleValueMeanings = new ArrayList<>();

    @OneToMany(mappedBy = "permissibleValue")
    private List<PermissibleValueSet> permissibleValueSets = new ArrayList<>();
}
