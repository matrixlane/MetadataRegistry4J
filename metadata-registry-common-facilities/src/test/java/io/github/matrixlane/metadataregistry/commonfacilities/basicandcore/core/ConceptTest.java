package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConceptTest {

    @Test
    void shouldSetSourceUri() {
        Concept concept = new Concept();
        concept.setSourceUri("https://example.com/concept/1");
        assertThat(concept.getSourceUri()).isEqualTo("https://example.com/concept/1");
    }
}
