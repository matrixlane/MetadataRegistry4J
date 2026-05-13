package io.github.matrixlane.metadataregistry.schemaextraction.javasource;

import io.github.matrixlane.metadataregistry.schemaextraction.javasource.model.JavaClassInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.javasource.model.JavaSourceExtractionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCommentExtractorTest {

    private final JavaCommentExtractor extractor = new JavaCommentExtractor();

    private static final String TEST_CLASS = """
            package demo;

            /** A demo entity. */
            public class Demo {
                /** Name field. */
                private String name;
                /** Value field. */
                private int value;
                private boolean active;
            }
            """;

    @Test
    void shouldExtractFromString() {
        Optional<JavaClassInfo> result = extractor.extractFromString(TEST_CLASS);
        assertThat(result).isPresent();
        assertThat(result.get().getClassName()).isEqualTo("Demo");
    }

    @Test
    void shouldExtractFromFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Demo.java");
        Files.writeString(file, TEST_CLASS);

        Optional<JavaClassInfo> result = extractor.extractFromFile(file);
        assertThat(result).isPresent();
    }

    @Test
    void shouldExtractFromDirectory(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Demo.java"), TEST_CLASS);
        Files.writeString(tempDir.resolve("Other.java"), """
                package demo;
                /** Other class. */
                public class Other { }
                """);

        JavaSourceExtractionResult result = extractor.extractFromDirectory(tempDir);
        assertThat(result.getClassCount()).isEqualTo(2);
    }

    @Test
    void shouldExtractFromZip() throws IOException {
        byte[] zip = createZip("Demo.java", TEST_CLASS);
        JavaSourceExtractionResult result = extractor.extractFromZip(new ByteArrayInputStream(zip));
        assertThat(result.getClassCount()).isEqualTo(1);
    }

    @Test
    void shouldComputeStatistics() {
        JavaSourceExtractionResult result = extractor.extractFromString(TEST_CLASS)
                .map(r -> JavaSourceExtractionResult.builder()
                        .classes(java.util.List.of(r))
                        .totalFilesProcessed(1)
                        .build())
                .orElseThrow();

        var stats = extractor.computeStatistics(result);
        assertThat(stats.totalClasses()).isEqualTo(1);
        assertThat(stats.commentedClasses()).isEqualTo(1);
        assertThat(stats.totalFields()).isEqualTo(3);
        assertThat(stats.commentedFields()).isEqualTo(2);
        assertThat(stats.classCommentRate()).isEqualTo(1.0);
        assertThat(stats.fieldCommentRate()).isBetween(0.66, 0.67);
        assertThat(stats.errorCount()).isZero();
    }

    @Test
    void shouldComputeZeroStatisticsForEmptyResult() {
        JavaSourceExtractionResult empty = JavaSourceExtractionResult.builder()
                .totalFilesProcessed(0)
                .build();
        var stats = extractor.computeStatistics(empty);
        assertThat(stats.classCommentRate()).isZero();
        assertThat(stats.fieldCommentRate()).isZero();
        assertThat(stats.enumCommentRate()).isZero();
        assertThat(stats.enumValueCommentRate()).isZero();
    }

    private byte[] createZip(String entryName, String content) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
