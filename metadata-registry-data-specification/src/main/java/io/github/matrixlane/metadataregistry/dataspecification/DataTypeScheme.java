package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class DataTypeScheme extends Item {

    /**
     * ISO/IEC 11179-31:2023 表 11(Datatype_Scheme):scheme_reference 0..1 Reference_Document。
     * (Figure 5 与该表矛盾处,以表 11 为准。)
     */
    @ManyToOne
    @JoinColumn(name = "scheme_reference_id")
    private ReferenceDocument schemeReference;

    @OneToMany(mappedBy = "dataTypeScheme")
    private List<DataType> dataTypes = new ArrayList<>();
}
