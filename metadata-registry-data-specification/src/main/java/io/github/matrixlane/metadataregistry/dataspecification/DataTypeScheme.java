package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
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

    private String reference;

    @OneToMany(mappedBy = "dataTypeScheme")
    private List<DataType> dataTypes = new ArrayList<>();
}
