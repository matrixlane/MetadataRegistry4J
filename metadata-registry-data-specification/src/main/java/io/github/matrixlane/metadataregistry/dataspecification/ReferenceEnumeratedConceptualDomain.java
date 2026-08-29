
package io.github.matrixlane.metadataregistry.dataspecification;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-31:2023 §7.4.2.6 Reference_Enumerated_Conceptual_Domain。
 * §7.4.2.6.4 明确该类无附加属性（"has no additional attributes"）；
 * 其外部源定义经 domain_definition 关联（§7.4.4.3）由
 * Enumerated_Conceptual_Domain_Definition（§7.4.2.7）承载。
 */
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class ReferenceEnumeratedConceptualDomain extends EnumeratedConceptualDomain {
}
