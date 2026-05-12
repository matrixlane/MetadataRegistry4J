package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Notation;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceDocumentTest {

    @Test
    void shouldSetFields() {
        ReferenceDocument rd = new ReferenceDocument();
        rd.setIdentifier("DOC-001");
        rd.setUri("https://example.com/doc1");

        DocumentType dt = new DocumentType();
        rd.setTypeDescription(dt);

        Text title = new Text();
        title.setValue("Specification");
        rd.setTitle(title);

        Notation notation = new Notation();
        notation.setValue("ISO-notation");
        rd.setNotation(notation);

        rd.getLanguage().add(new LanguageIdentification());
        rd.getProvider().add(new Organization());

        assertThat(rd.getIdentifier()).isEqualTo("DOC-001");
        assertThat(rd.getUri()).isEqualTo("https://example.com/doc1");
        assertThat(rd.getTypeDescription()).isEqualTo(dt);
        assertThat(rd.getTitle().getValue()).isEqualTo("Specification");
        assertThat(rd.getNotation().getValue()).isEqualTo("ISO-notation");
        assertThat(rd.getLanguage()).hasSize(1);
        assertThat(rd.getProvider()).hasSize(1);
    }
}
