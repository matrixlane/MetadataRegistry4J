package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public class IdentifiedItem extends Entity {

    /**
     * 保持 EAGER 语义但用二次 SELECT 加载：Item 是 30+ 子类的 joined 继承，
     * 主查询若 JOIN 该关联会把全部子类表及其关联拉进同一条 SQL，超出
     * SQLite 的 200 FROM 项上限；独立 SELECT 每条远低于该上限。
     */
    @OneToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "item_id")
    private Item item;

    @OneToMany(mappedBy = "identifiedItem", cascade = CascadeType.ALL)
    private List<ScopedIdentifier> scopedIdentifiers = new ArrayList<>();

    public IdentifiedItem(Item item) {
        this.item = item;
    }
}
