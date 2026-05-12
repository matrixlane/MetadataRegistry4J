package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeTest {

    @Test
    void shouldSetFields() {
        DocumentType dt = new DocumentType();
        dt.setIdentifier("PDF");
        Text desc = new Text();
        desc.setValue("Portable Document Format");
        dt.setDescription(desc);
        Sign ref = new Sign();
        ref.setValue("ISO 32000");
        dt.setSchemeReference(ref);

        assertThat(dt.getIdentifier()).isEqualTo("PDF");
        assertThat(dt.getDescription().getValue()).isEqualTo("Portable Document Format");
        assertThat(dt.getSchemeReference().getValue()).isEqualTo("ISO 32000");
    }
}
