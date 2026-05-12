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

    private LocalDateTime creationDateTime;

    private LocalDateTime lastChangeDateTime;

    private String changeDescription;

    private String explanatoryComment;

    private String origin;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "registration_state_id")
    private RegistrationState registrationState;

    @OneToMany(mappedBy = "administeredItem", cascade = CascadeType.ALL)
    private List<AttachedItem> attachedItems = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "stewardship_record_id")
    private StewardshipRecord stewardshipRecord;

    @ManyToOne
    @JoinColumn(name = "registration_authority_id")
    private RegistrationAuthority registrationAuthority;

    public AdministeredItem(Item item) {
        super(item);
    }
}
