package io.github.matrixlane.metadataregistry.itemmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * mapping_hierarchy 关联：
 * 0..1 个 superset 映射绑定 0..* 个 subset 映射，表达映射之间的层级。
 *
 * <p>每条映射至多作为一个映射的 subset（subset_mapping_id 唯一）。
 */
@Entity
@Table(name = "mapping_hierarchy",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_mh_subset", columnNames = "subset_mapping_id"))
@Getter
@Setter
@NoArgsConstructor
public class MappingHierarchy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 上层（superset）映射。
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "superset_mapping_id", nullable = false)
    private ItemMapping supersetMapping;

    /**
     * 下层（subset）映射；每条映射至多一个上层。
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subset_mapping_id", nullable = false)
    private ItemMapping subsetMapping;

    public MappingHierarchy(ItemMapping supersetMapping, ItemMapping subsetMapping) {
        this.supersetMapping = Objects.requireNonNull(supersetMapping,
                "supersetMapping must not be null");
        this.subsetMapping = Objects.requireNonNull(subsetMapping,
                "subsetMapping must not be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MappingHierarchy that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
