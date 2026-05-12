package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConceptTest {

    /**
     * Concrete test subclass of abstract {@link Concept} for testing base behaviour.
     */
    private static class TestConcept extends Concept {
    }

    @Test
    void shouldSetSourceUri() {
        Concept concept = new TestConcept();
        concept.setSourceUri("https://example.com/concept/1");
        assertThat(concept.getSourceUri()).isEqualTo("https://example.com/concept/1");
    }
}
