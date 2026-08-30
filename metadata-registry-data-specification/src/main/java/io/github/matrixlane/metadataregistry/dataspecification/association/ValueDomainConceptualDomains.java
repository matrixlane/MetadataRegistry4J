package io.github.matrixlane.metadataregistry.dataspecification.association;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;

/**
 * value_domain_meaning（ISO/IEC 11179-31:2023 §7.4.4.1）的统一读写入口。
 * <p>
 * 标准将 value_domain_meaning 定义在基类 Value_Domain 上（0..* Value_Domain ↔ 0..1
 * Conceptual_Domain），并通过 §7.4.5.2.1 约束子类型一致：
 * <ul>
 *   <li>Described_Value_Domain ↔ Described_Conceptual_Domain</li>
 *   <li>Local_Enumerated_Value_Domain ↔ Local_Enumerated_Conceptual_Domain</li>
 *   <li>Reference_Enumerated_Value_Domain ↔ Reference_Enumerated_Conceptual_Domain</li>
 * </ul>
 * 实现上该关联由三个子类字段承载，本工具类提供基类视角的统一访问并在写入时
 * 强制执行上述子类型一致性。
 */
public final class ValueDomainConceptualDomains {

    private ValueDomainConceptualDomains() {
    }

    /**
     * 读取值域当前关联的概念域；无关联或未知子类型时返回 null。
     */
    public static ConceptualDomain get(ValueDomain valueDomain) {
        if (valueDomain instanceof DescribedValueDomain dvd) {
            return dvd.getDescribedConceptualDomain();
        }
        if (valueDomain instanceof LocalEnumeratedValueDomain levd) {
            return levd.getLocalEnumeratedConceptualDomain();
        }
        if (valueDomain instanceof ReferenceEnumeratedValueDomain revd) {
            return revd.getReferencedConceptualDomain();
        }
        return null;
    }

    /**
     * 建立值域到概念域的关联，强制 §7.4.5.2.1 子类型一致性。
     */
    public static void set(ValueDomain valueDomain, ConceptualDomain conceptualDomain) {
        if (valueDomain == null || conceptualDomain == null) {
            throw new MetadataAssociationValidationException("值域与概念域均不能为空");
        }
        if (valueDomain instanceof DescribedValueDomain dvd) {
            if (!(conceptualDomain instanceof DescribedConceptualDomain dcd)) {
                throw inconsistent(valueDomain, conceptualDomain);
            }
            dvd.setDescribedConceptualDomain(dcd);
            return;
        }
        if (valueDomain instanceof LocalEnumeratedValueDomain levd) {
            if (!(conceptualDomain instanceof LocalEnumeratedConceptualDomain lecd)) {
                throw inconsistent(valueDomain, conceptualDomain);
            }
            levd.setLocalEnumeratedConceptualDomain(lecd);
            return;
        }
        if (valueDomain instanceof ReferenceEnumeratedValueDomain revd) {
            if (!(conceptualDomain instanceof ReferenceEnumeratedConceptualDomain recd)) {
                throw inconsistent(valueDomain, conceptualDomain);
            }
            revd.setReferencedConceptualDomain(recd);
            return;
        }
        throw new MetadataAssociationValidationException(
                "不支持的 ValueDomain 子类型：%s".formatted(valueDomain.getClass().getSimpleName()));
    }

    /**
     * 解除值域当前的概念域关联（若有）。
     */
    public static void clear(ValueDomain valueDomain) {
        if (valueDomain instanceof DescribedValueDomain dvd) {
            dvd.setDescribedConceptualDomain(null);
            return;
        }
        if (valueDomain instanceof LocalEnumeratedValueDomain levd) {
            levd.setLocalEnumeratedConceptualDomain(null);
            return;
        }
        if (valueDomain instanceof ReferenceEnumeratedValueDomain revd) {
            revd.setReferencedConceptualDomain(null);
        }
    }

    private static MetadataAssociationValidationException inconsistent(ValueDomain vd, ConceptualDomain cd) {
        return new MetadataAssociationValidationException(
                "ISO/IEC 11179-31:2023 §7.4.5.2.1 要求值域与概念域子类型一致：%s 不能关联 %s"
                        .formatted(vd.getClass().getSimpleName(), cd.getClass().getSimpleName()));
    }
}
