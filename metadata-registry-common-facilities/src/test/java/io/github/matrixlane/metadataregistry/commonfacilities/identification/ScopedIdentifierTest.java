package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedIdentifierTest {

    @Test
    void shouldSetFields() {
        Namespace ns = new Namespace();
        IdentifiedItem item = new IdentifiedItem();
        ScopedIdentifier si = new ScopedIdentifier("urn:demo:123", ns, item);
        assertThat(si.getIdentifier()).isEqualTo("urn:demo:123");
        assertThat(si.getNamespace()).isEqualTo(ns);
        assertThat(si.getIdentifiedItem()).isEqualTo(item);
    }

    @Test
    void shouldAllowVersionUpdate() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setVersion("2.0.0");
        assertThat(si.getVersion()).isEqualTo("2.0.0");
    }

    private static Namespace namespaceWithIdentifiers(String... identifiers) {
        Namespace ns = new Namespace();
        IdentifiedItem nsItem = new IdentifiedItem();
        for (String id : identifiers) {
            ScopedIdentifier nsId = new ScopedIdentifier();
            nsId.setIdentifier(id);
            nsItem.getScopedIdentifiers().add(nsId);
        }
        ns.setIdentifiedItem(nsItem);
        return ns;
    }

    @Test
    void fullExpansion_derivesWhenNamespaceHasExactlyOneIdentifier() {
        // ISO/IEC 11179-3:2023 表 12:full_expansion 仅当 Namespace 恰有一个标识符时可派生
        ScopedIdentifier si = new ScopedIdentifier("DE-0001", namespaceWithIdentifiers("MDR"), null);
        assertThat(si.getFullExpansion()).isEqualTo("MDR:DE-0001");
    }

    @Test
    void fullExpansion_isNullWhenNamespaceHasZeroOrMultipleIdentifiers() {
        assertThat(new ScopedIdentifier("DE-0001", namespaceWithIdentifiers(), null).getFullExpansion())
                .isNull();
        assertThat(new ScopedIdentifier("DE-0001", namespaceWithIdentifiers("MDR", "ALT"), null).getFullExpansion())
                .isNull();
        assertThat(new ScopedIdentifier("DE-0001", null, null).getFullExpansion()).isNull();
    }

    @Test
    void shorthandExpansion_prependsNamespacePrefix() {
        // ISO/IEC 11179-3:2023 表 12:shorthand_expansion = Namespace.shorthand_prefix + identifier
        Namespace ns = new Namespace();
        ns.setShorthandPrefix("mdr:");
        assertThat(new ScopedIdentifier("DE-0001", ns, null).getShorthandExpansion())
                .isEqualTo("mdr:DE-0001");
    }

    @Test
    void shorthandExpansion_isNullWithoutPrefix() {
        assertThat(new ScopedIdentifier("DE-0001", new Namespace(), null).getShorthandExpansion())
                .isNull();
        assertThat(new ScopedIdentifier("DE-0001", null, null).getShorthandExpansion()).isNull();
    }
}
