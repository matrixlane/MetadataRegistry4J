package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceRegistryTest {

    @Test
    void shouldCreateWithDefaults() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.INTERNAL);

        assertThat(registry.getNamespace()).isEqualTo(ns);
        assertThat(registry.getIdentifierType()).isEqualTo(IdentifierType.INTERNAL);
        assertThat(registry.getCurrentValue()).isEqualTo(0L);
        assertThat(registry.getIncrementBy()).isEqualTo(1);
        assertThat(registry.getStartValue()).isEqualTo(1L);
        assertThat(registry.getPadLength()).isEqualTo(6);
        assertThat(registry.getPrefix()).isNull();
    }

    @Test
    void shouldPeekNextValue() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        registry.setCurrentValue(10L);
        registry.setIncrementBy(1);

        assertThat(registry.peekNext()).isEqualTo(11L);
    }

    @Test
    void shouldFormatNextValue() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        registry.setCurrentValue(0L);
        registry.setPrefix("DE");
        registry.setPadLength(6);

        assertThat(registry.formatNext()).isEqualTo("DE000001");
    }

    @Test
    void shouldFormatWithCustomPrefixAndPad() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.URN);
        registry.setCurrentValue(99L);
        registry.setPrefix("SEQ");
        registry.setPadLength(4);

        assertThat(registry.formatNext()).isEqualTo("SEQ0100");
    }

    @Test
    void shouldFormatWithoutPrefix() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        registry.setCurrentValue(5L);
        registry.setPadLength(4);

        assertThat(registry.formatNext()).isEqualTo("0006");
    }

    @Test
    void shouldDetectOverflow() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        registry.setCurrentValue(9998L);
        registry.setIncrementBy(1);
        registry.setPadLength(4);

        // pad=4 → maxValue=9999, peekNext=9999 → not yet overflow
        assertThat(registry.wouldOverflow(registry.getMaxValue())).isFalse();

        // When current=9999, peekNext=10000 → overflow
        registry.setCurrentValue(9999L);
        assertThat(registry.wouldOverflow(registry.getMaxValue())).isTrue();
    }

    @Test
    void shouldCalculateMaxValueForPadLength() {
        Namespace ns = new Namespace();

        SequenceRegistry r6 = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        r6.setPadLength(6);
        assertThat(r6.getMaxValue()).isEqualTo(999999L);

        SequenceRegistry r4 = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        r4.setPadLength(4);
        assertThat(r4.getMaxValue()).isEqualTo(9999L);

        SequenceRegistry r2 = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        r2.setPadLength(2);
        assertThat(r2.getMaxValue()).isEqualTo(99L);
    }

    @Test
    void shouldSetLastAllocatedAt() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        assertThat(registry.getLastAllocatedAt()).isNotNull();
    }

    @Test
    void shouldSetCreatedAt() {
        Namespace ns = new Namespace();
        SequenceRegistry registry = new SequenceRegistry(ns, IdentifierType.INTERNAL);
        assertThat(registry.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldSetAllIdentifierTypes() {
        Namespace ns = new Namespace();

        for (IdentifierType type : IdentifierType.values()) {
            SequenceRegistry registry = new SequenceRegistry(ns, type);
            assertThat(registry.getIdentifierType()).isEqualTo(type);
        }
    }

    @Test
    void shouldHandleNullNamespaceInConstructor() {
        SequenceRegistry registry = new SequenceRegistry(null, IdentifierType.INTERNAL);
        assertThat(registry.getNamespace()).isNull();
        assertThat(registry.getIdentifierType()).isEqualTo(IdentifierType.INTERNAL);
    }

    @Test
    void shouldUseInternalAsDefaultType() {
        SequenceRegistry registry = new SequenceRegistry(new Namespace(), null);
        assertThat(registry.getIdentifierType()).isEqualTo(IdentifierType.INTERNAL);
    }
}
