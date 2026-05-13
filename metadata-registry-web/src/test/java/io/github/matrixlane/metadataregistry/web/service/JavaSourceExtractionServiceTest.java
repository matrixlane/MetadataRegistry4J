package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.schemaextraction.javasource.JavaSourceParseException;
import io.github.matrixlane.metadataregistry.web.dto.javasource.*;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class JavaSourceExtractionServiceTest {

    private final JavaSourceExtractionService service = new JavaSourceExtractionService();

    private static final String TEST_CLASS = """
            package demo;

            /** A demo entity. */
            public class Demo {
                /** Name field. */
                private String name;
                /** Value field. */
                private int value;
            }
            """;

    @Test
    void shouldExtractFromTextMode() {
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("TEXT", TEST_CLASS, null);

        JavaSourceExtractionResponse response = service.extract(request);

        assertThat(response.classes()).hasSize(1);
        assertThat(response.classes().get(0).className()).isEqualTo("Demo");
        assertThat(response.statistics().totalClasses()).isEqualTo(1);
        assertThat(response.statistics().totalFields()).isEqualTo(2);
    }

    @Test
    void shouldRejectEmptyTextMode() {
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("TEXT", "", null);

        assertThatThrownBy(() -> service.extract(request))
                .isInstanceOf(JavaSourceParseException.class)
                .hasMessageContaining("sourceContent is required");
    }

    @Test
    void shouldRejectMissingPathForFileMode() {
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("FILE", null, null);

        assertThatThrownBy(() -> service.extract(request))
                .isInstanceOf(JavaSourceParseException.class)
                .hasMessageContaining("sourcePath is required");
    }

    @Test
    void shouldRejectMissingPathForDirectoryMode() {
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("DIRECTORY", null, null);

        assertThatThrownBy(() -> service.extract(request))
                .isInstanceOf(JavaSourceParseException.class)
                .hasMessageContaining("sourcePath is required");
    }

    @Test
    void shouldRejectUnsupportedMode() {
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("UNSUPPORTED", null, null);

        assertThatThrownBy(() -> service.extract(request))
                .isInstanceOf(JavaSourceParseException.class)
                .hasMessageContaining("Unsupported mode");
    }

    @Test
    void shouldExtractFromZipMode() {
        byte[] zipBytes = createZipBytes("Demo.java", TEST_CLASS);
        String base64Zip = Base64.getEncoder().encodeToString(zipBytes);
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("ZIP", base64Zip, null);

        JavaSourceExtractionResponse response = service.extract(request);

        assertThat(response.classes()).hasSize(1);
        assertThat(response.classes().get(0).className()).isEqualTo("Demo");
    }

    @Test
    void shouldPreviewMapping() {
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("TEXT", TEST_CLASS, null);

        JavaSourceMappingPreviewResponse response = service.previewMapping(request);

        assertThat(response.dataElementConcepts()).hasSize(1);
        assertThat(response.dataElements()).hasSize(2);
    }

    @Test
    void shouldHandleClassWithEnumInPreview() {
        String enumClass = """
                package demo;
                /** Status enum. */
                public enum Status {
                    /** Active status. */
                    ACTIVE,
                    /** Inactive status. */
                    INACTIVE
                }
                """;
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("TEXT", enumClass, null);

        JavaSourceMappingPreviewResponse response = service.previewMapping(request);

        assertThat(response.valueDomains()).hasSize(1);
        assertThat(response.permissibleValues()).hasSize(2);
    }

    private byte[] createZipBytes(String entryName, String content) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
