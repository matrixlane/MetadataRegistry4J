package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.Column;
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
     * 允许值的实际取值。
     *
     * <p>ISO/IEC 11179-31:2023 表 6(7.4.2.12.4):permitted_value 多重性 1,数据类型
     * Value(ISO/IEC 11179-3:2023, 6.2.13)。标准 NOTE:即使父 Item 可有多个
     * Designation.sign,permitted_value 仍为必备,以指定唯一的取值。
     */
    @Column(name = "permitted_value", nullable = false)
    private String permittedValue;

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
