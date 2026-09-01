package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrnGenerationStrategyTest {

    private final UrnGenerationStrategy strategy = new UrnGenerationStrategy();

    @Test
    void shouldGenerateUrnWithNamespacePrefix() {
        Namespace ns = createNamespace("My Org");
        String id = strategy.generate(ns);
        assertThat(id).startsWith("urn:my-org:");
        assertThat(id.split(":")).hasSize(3);
    }

    @Test
    void shouldUseDefaultNamespaceWhenNull() {
        String id = strategy.generate(null);
        assertThat(id).startsWith("urn:mdr:");
    }

    @Test
    void shouldUseDefaultNamespaceWhenNoDesignations() {
        Namespace ns = new Namespace();
        ns.setDesignations(List.of());
        String id = strategy.generate(ns);
        assertThat(id).startsWith("urn:mdr:");
    }

    @Test
    void shouldSanitizeSpecialCharacters() {
        Namespace ns = createNamespace("Org Name @#$%");
        String id = strategy.generate(ns);
        assertThat(id).startsWith("urn:org-name-");
    }

    @Test
    void shouldReturnCorrectType() {
        assertThat(strategy.getType()).isEqualTo("URN");
    }

    @Test
    void shouldGenerateUniqueUrns() {
        Namespace ns = createNamespace("Test");
        String id1 = strategy.generate(ns);
        String id2 = strategy.generate(ns);
        assertThat(id1).isNotEqualTo(id2);
    }

    private Namespace createNamespace(String name) {
        Namespace ns = new Namespace();
        Designation d = new Designation();
        d.setSign(Sign.of(name));
        ns.setDesignations(List.of(d));
        return ns;
    }
}
