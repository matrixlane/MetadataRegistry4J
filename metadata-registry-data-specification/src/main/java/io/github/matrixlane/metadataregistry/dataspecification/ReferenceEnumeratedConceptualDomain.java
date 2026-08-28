
package io.github.matrixlane.metadataregistry.dataspecification;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class ReferenceEnumeratedConceptualDomain extends EnumeratedConceptualDomain {

    /**
     * 外部源中承载值含义的字段名（ISO/IEC 11179-31:2023 §7.4.2.6 必填属性 permitted_values_fieldname）。
     */
    @Column(name = "permitted_values_fieldname", nullable = false, length = 255)
    private String permittedValuesFieldname;
}
