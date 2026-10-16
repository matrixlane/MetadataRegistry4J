package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class AdministeredItem extends RegisteredItem {

    /** ISO/IEC 11179-3:2023 表 21(Administered_Item):creation_datetime 1,必备。 */
    @jakarta.persistence.Column(nullable = false)
    private LocalDateTime creationDateTime;

    private LocalDateTime lastChangeDateTime;

    private String changeDescription;

    private String explanatoryComment;

    private String origin;

    /**
     * LAZY：owning 方 OneToOne 的 LAZY 经外键代理即可生效，无需字节码增强。
     * 列表性能优化（2026-10-11）：registration_state 的 EAGER 会让每次 identifiedItem
     * 自动初始化级联 join registration_state 表；访问方均在 @Transactional 内。
     */
    @OneToOne(fetch = jakarta.persistence.FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "registration_state_id")
    private RegistrationState registrationState;

    /**
     * ISO/IEC 11179-3:2023 9.4.3.5:Administered_Item 删除时,
     * 经 attachment 关联的 Attached_Item 须一并删除。
     */
    @OneToMany(mappedBy = "administeredItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttachedItem> attachedItems = new ArrayList<>();

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "stewardship_record_id")
    private StewardshipRecord stewardshipRecord;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "registration_authority_id")
    private RegistrationAuthority registrationAuthority;

    public AdministeredItem(Item item) {
        super(item);
    }
}
