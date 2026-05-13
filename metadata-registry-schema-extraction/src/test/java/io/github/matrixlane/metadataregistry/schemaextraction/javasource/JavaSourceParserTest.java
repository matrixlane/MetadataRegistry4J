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

class JavaSourceParserTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    private static final String SIMPLE_CLASS = """
            package com.example;

            /**
             * Represents a patient in the hospital system.
             * @author Medical Team
             * @see Person
             */
            public class Patient {
                /** The unique identifier of the patient. */
                private Long id;

                /** Full name of the patient. */
                private String name;

                private int age;
            }
            """;

    private static final String ENUM_CLASS = """
            package com.example;

            /**
             * Gender classification for patients.
             * @deprecated Use GenderIdentity instead
             */
            public enum GenderType {
                /** Male gender. */
                MALE,
                /** Female gender. */
                FEMALE,
                /** Other gender. */
                OTHER
            }
            """;

    @Test
    void shouldParseSimpleClass() {
        Optional<JavaClassInfo> result = parser.parseContent(SIMPLE_CLASS);

        assertThat(result).isPresent();
        JavaClassInfo info = result.get();
        assertThat(info.getPackageName()).isEqualTo("com.example");
        assertThat(info.getClassName()).isEqualTo("Patient");
        assertThat(info.getQualifiedName()).isEqualTo("com.example.Patient");
        assertThat(info.getComment()).contains("Represents a patient");
        assertThat(info.isEnum()).isFalse();
        assertThat(info.getSeeTags()).containsExactly("Person");
    }

    @Test
    void shouldParseEnumClass() {
        Optional<JavaClassInfo> result = parser.parseContent(ENUM_CLASS);

        assertThat(result).isPresent();
        JavaClassInfo info = result.get();
        assertThat(info.getClassName()).isEqualTo("GenderType");
        assertThat(info.isEnum()).isTrue();
        assertThat(info.isDeprecated()).isTrue();
        assertThat(info.getDeprecatedReason()).contains("GenderIdentity");
        assertThat(info.getEnumValues()).hasSize(3);
        assertThat(info.getEnumValues().get(0).getName()).isEqualTo("MALE");
        assertThat(info.getEnumValues().get(0).getComment()).contains("Male gender");
    }

    @Test
    void shouldParseFieldsWithComments() {
        Optional<JavaClassInfo> result = parser.parseContent(SIMPLE_CLASS);

        assertThat(result).isPresent();
        JavaClassInfo info = result.get();
        assertThat(info.getFields()).hasSize(3);

        var idField = info.getFields().get(0);
        assertThat(idField.getFieldName()).isEqualTo("id");
        assertThat(idField.getTypeName()).isEqualTo("Long");
        assertThat(idField.getComment()).contains("unique identifier");

        var ageField = info.getFields().get(2);
        assertThat(ageField.getFieldName()).isEqualTo("age");
        assertThat(ageField.getComment()).isBlank();
    }

    @Test
    void shouldParseFileFromDisk(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("TestClass.java");
        Files.writeString(file, SIMPLE_CLASS);

        Optional<JavaClassInfo> result = parser.parseFile(file);

        assertThat(result).isPresent();
        assertThat(result.get().getClassName()).isEqualTo("Patient");
    }

    @Test
    void shouldParseDirectory(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Patient.java"), SIMPLE_CLASS);
        Files.writeString(tempDir.resolve("GenderType.java"), ENUM_CLASS);

        JavaSourceExtractionResult result = parser.parseDirectory(tempDir);

        assertThat(result.getClassCount()).isEqualTo(2);
        assertThat(result.getTotalFilesProcessed()).isEqualTo(2);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldParseZipArchive() throws IOException {
        byte[] zipBytes = createZipBytes("Patient.java", SIMPLE_CLASS);

        JavaSourceExtractionResult result = parser.parseZipArchive(new ByteArrayInputStream(zipBytes));

        assertThat(result.getClassCount()).isEqualTo(1);
        assertThat(result.getTotalFilesProcessed()).isEqualTo(1);
        assertThat(result.getClasses().get(0).getClassName()).isEqualTo("Patient");
    }

    @Test
    void shouldReturnEmptyForInvalidJava() {
        Optional<JavaClassInfo> result = parser.parseContent("not valid java");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleEmptyDirectory(@TempDir Path tempDir) {
        JavaSourceExtractionResult result = parser.parseDirectory(tempDir);
        assertThat(result.getClassCount()).isZero();
        assertThat(result.getTotalFilesProcessed()).isZero();
    }

    @Test
    void shouldHandleNonDirectoryPath(@TempDir Path tempDir) {
        Path file = tempDir.resolve("not-a-dir.txt");
        JavaSourceExtractionResult result = parser.parseDirectory(file);
        assertThat(result.getErrors()).isNotEmpty();
    }

    private byte[] createZipBytes(String entryName, String content) throws IOException {
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
