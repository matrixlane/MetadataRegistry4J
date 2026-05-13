package io.github.matrixlane.metadataregistry.schemaextraction.javasource.mapping;

import io.github.matrixlane.metadataregistry.schemaextraction.javasource.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceMappingServiceTest {

    private final JavaSourceMappingService mappingService = new JavaSourceMappingService();

    @Test
    void shouldMapClassToDataElementConcept() {
        JavaClassInfo classInfo = JavaClassInfo.builder()
                .packageName("com.example")
                .className("Patient")
                .qualifiedName("com.example.Patient")
                .comment("A patient in the hospital system")
                .isEnum(false)
                .fields(List.of())
                .build();

        JavaSourceMappingResult result = mappingService.mapClass(classInfo);

        assertThat(result.getDataElementConcepts()).hasSize(1);
        var dec = result.getDataElementConcepts().get(0);
        assertThat(dec.getName()).isEqualTo("Patient");
        assertThat(dec.getDescription()).contains("hospital system");
        assertThat(dec.getObjectClassName()).isEqualTo("Patient");
        assertThat(dec.getPropertyName()).isEqualTo("attributes");
    }

    @Test
    void shouldMapClassWithoutComment() {
        JavaClassInfo classInfo = JavaClassInfo.builder()
                .className("EmptyComment")
                .qualifiedName("EmptyComment")
                .comment("")
                .isEnum(false)
                .fields(List.of())
                .build();

        JavaSourceMappingResult result = mappingService.mapClass(classInfo);

        assertThat(result.getDataElementConcepts()).hasSize(1);
        assertThat(result.getDataElementConcepts().get(0).getDescription())
                .contains("derived from Java class");
    }

    @Test
    void shouldMapFieldsToDataElements() {
        JavaFieldInfo field1 = JavaFieldInfo.builder()
                .fieldName("patientName")
                .typeName("String")
                .comment("Full name of the patient")
                .modifiers(List.of("private"))
                .build();

        JavaFieldInfo field2 = JavaFieldInfo.builder()
                .fieldName("age")
                .typeName("int")
                .comment("")
                .modifiers(List.of("private", "final"))
                .build();

        JavaClassInfo classInfo = JavaClassInfo.builder()
                .className("Patient")
                .qualifiedName("com.example.Patient")
                .comment("Patient class")
                .fields(List.of(field1, field2))
                .build();

        JavaSourceMappingResult result = mappingService.mapClass(classInfo);

        assertThat(result.getDataElements()).hasSize(2);
        var de1 = result.getDataElements().get(0);
        assertThat(de1.getName()).isEqualTo("patientName");
        assertThat(de1.getDescription()).contains("Full name");
        assertThat(de1.isNullable()).isTrue();

        var de2 = result.getDataElements().get(1);
        assertThat(de2.getName()).isEqualTo("age");
        assertThat(de2.isNullable()).isFalse();
    }

    @Test
    void shouldMapEnumToValueDomainAndPermissibleValues() {
        JavaEnumValueInfo ev1 = JavaEnumValueInfo.builder()
                .name("MALE")
                .comment("Male gender")
                .build();

        JavaEnumValueInfo ev2 = JavaEnumValueInfo.builder()
                .name("FEMALE")
                .comment("Female gender")
                .build();

        JavaClassInfo enumInfo = JavaClassInfo.builder()
                .className("GenderType")
                .qualifiedName("com.example.GenderType")
                .comment("Gender classification")
                .isEnum(true)
                .enumValues(List.of(ev1, ev2))
                .fields(List.of())
                .build();

        JavaSourceMappingResult result = mappingService.mapClass(enumInfo);

        assertThat(result.getValueDomains()).hasSize(1);
        var vd = result.getValueDomains().get(0);
        assertThat(vd.getName()).isEqualTo("GenderType");
        assertThat(vd.getDescription()).contains("Gender classification");
        assertThat(vd.getDataTypeName()).isEqualTo("STRING");

        assertThat(result.getPermissibleValues()).hasSize(2);
        var pv1 = result.getPermissibleValues().get(0);
        assertThat(pv1.getName()).isEqualTo("MALE");
        assertThat(pv1.getValueDomainName()).isEqualTo("GenderType");
    }

    @Test
    void shouldMapExtractionResult() {
        JavaClassInfo class1 = JavaClassInfo.builder()
                .className("Class1")
                .qualifiedName("Class1")
                .comment("First class")
                .fields(List.of())
                .build();

        JavaClassInfo class2 = JavaClassInfo.builder()
                .className("Class2")
                .qualifiedName("Class2")
                .comment("Second class")
                .fields(List.of())
                .build();

        JavaSourceExtractionResult extractionResult = JavaSourceExtractionResult.builder()
                .classes(List.of(class1, class2))
                .totalFilesProcessed(2)
                .build();

        JavaSourceMappingResult result = mappingService.mapExtractionResult(extractionResult);

        assertThat(result.getDataElementConcepts()).hasSize(2);
    }

    @Test
    void shouldPropagateErrorsAsWarnings() {
        JavaSourceExtractionResult extractionResult = JavaSourceExtractionResult.builder()
                .classes(List.of())
                .errors(List.of("Parse error 1", "Parse error 2"))
                .totalFilesProcessed(0)
                .build();

        JavaSourceMappingResult result = mappingService.mapExtractionResult(extractionResult);

        assertThat(result.getWarnings()).containsExactly("Parse error 1", "Parse error 2");
    }
}
