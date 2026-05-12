package io.github.matrixlane.metadataregistry.datasetregistration.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportErrorTest {

    @Test
    void shouldCreateWithRowAndMessage() {
        ImportError err = new ImportError(5, "Missing required field");
        assertThat(err.getRow()).isEqualTo(5);
        assertThat(err.getColumn()).isEqualTo(-1);
        assertThat(err.getMessage()).isEqualTo("Missing required field");
        assertThat(err.getRawValue()).isNull();
    }

    @Test
    void shouldCreateWithRowColumnAndMessage() {
        ImportError err = new ImportError(5, 2, "Invalid format", "ABC123");
        assertThat(err.getRow()).isEqualTo(5);
        assertThat(err.getColumn()).isEqualTo(2);
        assertThat(err.getMessage()).isEqualTo("Invalid format");
        assertThat(err.getRawValue()).isEqualTo("ABC123");
    }

    @Test
    void shouldCreateWithRowMessageAndRawValue() {
        ImportError err = new ImportError(5, "Invalid value", "BAD_VALUE");
        assertThat(err.getRow()).isEqualTo(5);
        assertThat(err.getColumn()).isEqualTo(-1);
        assertThat(err.getRawValue()).isEqualTo("BAD_VALUE");
    }
}
