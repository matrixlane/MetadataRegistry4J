package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Concept;
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
     * 经 Permissible_Value_Meaning 关联类（ISO/IEC 11179-31:2023 7.4.3.2）反向关联：
     * PVM 表上的 value_meaning_id 外键即绑定关系，不再使用冗余连接表。
     */
    @OneToMany(mappedBy = "valueMeaning")
    private List<PermissibleValueMeaning> permissibleValueMeanings = new ArrayList<>();
}
