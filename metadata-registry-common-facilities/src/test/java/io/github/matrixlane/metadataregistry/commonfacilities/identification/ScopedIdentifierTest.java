package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedIdentifierTest {

    @Test
    void shouldSetFields() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifier("urn:example:123");
        si.setVersion("1.0");
        assertThat(si.getIdentifier()).isEqualTo("urn:example:123");
        assertThat(si.getVersion()).isEqualTo("1.0");
    }

    @Test
    void shouldSetNamespace() {
        ScopedIdentifier si = new ScopedIdentifier();
        Namespace ns = new Namespace();
        si.setNamespace(ns);
        assertThat(si.getNamespace()).isEqualTo(ns);
    }

    @Test
    void shouldSetIdentifiedItem() {
        ScopedIdentifier si = new ScopedIdentifier();
        IdentifiedItem ii = new IdentifiedItem();
        si.setIdentifiedItem(ii);
        assertThat(si.getIdentifiedItem()).isEqualTo(ii);
    }

    // --- WI-028-B: identifierType tests ---

    @Test
    void shouldDefaultToInternalType() {
        ScopedIdentifier si = new ScopedIdentifier();
        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.INTERNAL);
    }

    @Test
    void shouldSetIdentifierType() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifierType(IdentifierType.URN);

        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.URN);
    }

    @Test
    void shouldSupportAllIdentifierTypes() {
        ScopedIdentifier si = new ScopedIdentifier();

        for (IdentifierType type : IdentifierType.values()) {
            si.setIdentifierType(type);
            assertThat(si.getIdentifierType()).isEqualTo(type);
        }
    }

    @Test
    void shouldAllowMultipleTypesForSameItem() {
        Namespace ns = new Namespace();
        IdentifiedItem item = new IdentifiedItem();

        ScopedIdentifier si1 = new ScopedIdentifier("ID-001", IdentifierType.INTERNAL, ns, item);
        ScopedIdentifier si2 = new ScopedIdentifier("urn:example:002", IdentifierType.URN, ns, item);

        assertThat(si1.getIdentifierType()).isEqualTo(IdentifierType.INTERNAL);
        assertThat(si2.getIdentifierType()).isEqualTo(IdentifierType.URN);
        assertThat(si1.getIdentifier()).isNotEqualTo(si2.getIdentifier());
    }

    @Test
    void shouldSetEffectiveStrategyConfig() {
        ScopedIdentifier si = new ScopedIdentifier();
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);
        config.setId(1L);

        si.setEffectiveStrategyConfig(config);

        assertThat(si.getEffectiveStrategyConfig()).isEqualTo(config);
        assertThat(si.getEffectiveStrategyConfig().getId()).isEqualTo(1L);
    }

    @Test
    void shouldUseConvenienceConstructor() {
        Namespace ns = new Namespace();
        IdentifiedItem item = new IdentifiedItem();

        ScopedIdentifier si = new ScopedIdentifier("TEST-123", IdentifierType.URN, ns, item);

        assertThat(si.getIdentifier()).isEqualTo("TEST-123");
        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.URN);
        assertThat(si.getNamespace()).isEqualTo(ns);
        assertThat(si.getIdentifiedItem()).isEqualTo(item);
    }

    @Test
    void shouldDefaultToInternalTypeInConvenienceConstructor() {
        Namespace ns = new Namespace();
        IdentifiedItem item = new IdentifiedItem();

        ScopedIdentifier si = new ScopedIdentifier("ID-001", null, ns, item);

        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.INTERNAL);
    }

    @Test
    void shouldSetOidType() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifierType(IdentifierType.OID);
        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.OID);
    }

    @Test
    void shouldSetCustomType() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifierType(IdentifierType.CUSTOM);
        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.CUSTOM);
    }

    @Test
    void shouldSetSnomedType() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifierType(IdentifierType.SNOMED);
        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.SNOMED);
    }

    @Test
    void shouldSetLoincType() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifierType(IdentifierType.LOINC);
        assertThat(si.getIdentifierType()).isEqualTo(IdentifierType.LOINC);
    }
}
