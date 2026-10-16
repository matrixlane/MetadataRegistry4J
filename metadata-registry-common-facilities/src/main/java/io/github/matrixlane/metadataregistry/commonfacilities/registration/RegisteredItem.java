package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class RegisteredItem extends IdentifiedItem {

    /**
     * LAZY：RegisteredItem 是 Item 子类 JOINED 继承根的受管链中间层，ConstraintSet 自身
     * 也继承 Item——默认 EAGER 会让每次 identifiedItem 加载（含 Item.identifiedItem
     * mappedBy 方 LAZY OneToOne 无字节码增强的自动初始化）级联 join constraint_set + item
     * 子类树。列表实测单次 ~37ms/12+ 表 join（2026-10-11 列表性能优化），改 LAZY 后
     * 只剩类型判别 3 表。访问方均在 @Transactional 内，无 LazyInitializationException 风险。
     */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "constraint_set_id")
    private ConstraintSet constraintSet;

    @ManyToMany
    @JoinTable(
            name = "registered_item_reference_document",
            joinColumns = @JoinColumn(name = "registered_item_id"),
            inverseJoinColumns = @JoinColumn(name = "reference_document_id")
    )
    private List<ReferenceDocument> referenceDocuments = new ArrayList<>();

    @ManyToMany(mappedBy = "registeredItems")
    private List<SubmissionRecord> submissionRecords = new ArrayList<>();

    public RegisteredItem(Item item) {
        super(item);
    }
}
