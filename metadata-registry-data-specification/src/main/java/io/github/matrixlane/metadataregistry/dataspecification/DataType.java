package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class DataType extends Item {

    /**
     * 数据类型的注记,用于进一步说明该数据类型。
     *
     * <p>ISO/IEC 11179-31:2023 表 8(7.4.2.14.4):annotation 0..1,
     * 数据类型 Text(ISO/IEC 11179-3:2023, 6.2.12)。
     */
    @Column(name = "annotation", length = 2000)
    private String annotation;

    @ManyToOne
    @JoinColumn(name = "data_type_scheme_id")
    private DataTypeScheme dataTypeScheme;
}
