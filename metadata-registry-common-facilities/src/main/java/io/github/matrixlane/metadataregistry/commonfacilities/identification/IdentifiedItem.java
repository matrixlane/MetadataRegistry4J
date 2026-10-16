package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

/**
 * ISO/IEC 11179-3:2023 §7.3.1 Identified_Item。
 *
 * <p><b>本类不再是持久化实体</b>（2026-10-09，{@code administered-item-reference-documents-ui}）：
 * 受管链的物理表由 {@code item → identified_item → registered_item → administered_item}
 * 四层收敛为三层——{@code identified_item} 表取消，属性并入 {@code registered_item}。
 * 类层次保持不变（{@code RegisteredItem extends IdentifiedItem} 仍是 §9.4.1.1 声明的
 * 关系），故本类降为 {@code @MappedSuperclass}，不产生独立表。</p>
 *
 * <p>副作用：{@code scoped_identifier.identified_item_id} 的外键改指
 * {@code registered_item.id}；「已标识但未注册」这一中间态不再可物理表达
 * （{@code Registered_Item} 为抽象类，见 §9.4.1.2）——有意的产品决策，已记入 OpenSpec。</p>
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public class IdentifiedItem extends Entity {

    /**
     * 保持 LAZY + 二次 SELECT：Item 是 30+ 子类的 joined 继承，
     * 主查询若 JOIN 该关联会把全部子类表及其关联拉进同一条 SQL，超出
     * SQLite 的 200 FROM 项上限；独立 SELECT 每条远低于该上限。
     *
     * <p>列表性能优化（2026-10-11）：由 EAGER 改 LAZY。原 EAGER 使每次 RegisteredItem
     * 加载（含 Item.identifiedItem mappedBy 方无字节码增强的自动初始化）都级联多发一条
     * Item JOINED 继承查询；改 LAZY 后访问方（均在 @Transactional 内）首次访问时才加载。</p>
     */
    @OneToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "item_id")
    private Item item;

    @OneToMany(mappedBy = "identifiedItem", cascade = CascadeType.ALL)
    private List<ScopedIdentifier> scopedIdentifiers = new ArrayList<>();

    public IdentifiedItem(Item item) {
        this.item = item;
    }
}
