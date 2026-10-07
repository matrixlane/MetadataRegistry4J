package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
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
 * Subject_Mapping 关联类。
 *
 * <p>记录 Item_Mapping 与一个作为映射主体的注册条目之间的绑定；
 * 每个绑定各自携带 mapping_variable_name——转换规则（transformation_rule）
 * 中引用该条目的变量名（如 isodate 示例中 day/month/year 三主体
 * 各带变量名）。Item 与 Item_Mapping 为多对多关系：一条映射可有 1..*
 * 个主体，每个主体一行本关联类实例。
 */
@Entity
@Table(name = "subject_mapping")
@Getter
@Setter
@NoArgsConstructor
public class ItemMappingSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 所属映射（多对多 owning 侧）。
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_mapping_id", nullable = false)
    private ItemMapping itemMapping;

    /**
     * 作为映射主体的注册条目（Item，JOINED 根，必须 LAZY 加载）。
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /**
     * 转换规则中的映射变量名（mapping_variable_name，0..1）。
     */
    @Column(name = "mapping_variable_name", length = 255)
    private String mappingVariableName;

    public ItemMappingSubject(ItemMapping itemMapping, Item item, String mappingVariableName) {
        this.itemMapping = Objects.requireNonNull(itemMapping, "itemMapping must not be null");
        this.item = Objects.requireNonNull(item, "item must not be null");
        this.mappingVariableName = mappingVariableName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemMappingSubject that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
