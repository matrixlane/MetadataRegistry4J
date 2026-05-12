package io.github.matrixlane.metadataregistry.datasetregistration.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportResultTest {

    @Test
    void shouldStartEmpty() {
        ImportResult r = new ImportResult();
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getTotalRows()).isZero();
        assertThat(r.getImportedRows()).isZero();
        assertThat(r.getErrorRows()).isZero();
        assertThat(r.getErrors()).isEmpty();
        assertThat(r.getDurationMs()).isZero();
    }

    @Test
    void shouldTrackErrors() {
        ImportResult r = new ImportResult();
        r.addError(new ImportError(5, "Missing identifier"));
        r.addError(new ImportError(6, "Invalid format"));

        assertThat(r.getErrorRows()).isEqualTo(2);
        assertThat(r.getErrors()).hasSize(2);
        assertThat(r.getErrors().get(0).getMessage()).isEqualTo("Missing identifier");
        assertThat(r.getErrors().get(1).getRow()).isEqualTo(6);
    }

    @Test
    void shouldMarkSuccessWithIdAndName() {
        java.util.UUID id = java.util.UUID.randomUUID();
        ImportResult r = new ImportResult();
        r.markSuccess(id, "Test Dataset");

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getDataSetId()).isEqualTo(id);
        assertThat(r.getDataSetName()).isEqualTo("Test Dataset");
    }

    @Test
    void isFullySuccessfulShouldReturnTrueWhenNoErrors() {
        ImportResult r = new ImportResult();
        r.setImportedRows(10);
        assertThat(r.isFullySuccessful()).isTrue();
    }

    @Test
    void isFullySuccessfulShouldReturnFalseWhenErrorsExist() {
        ImportResult r = new ImportResult();
        r.setImportedRows(8);
        r.addError(new ImportError(5, "Error"));
        assertThat(r.isFullySuccessful()).isFalse();
    }

    @Test
    void isFullySuccessfulShouldReturnFalseWhenNoRows() {
        ImportResult r = new ImportResult();
        assertThat(r.isFullySuccessful()).isFalse();
    }
}
