package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.schemaextraction.javasource.JavaCommentExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.javasource.JavaSourceParseException;
import io.github.matrixlane.metadataregistry.schemaextraction.javasource.mapping.JavaSourceMappingResult;
import io.github.matrixlane.metadataregistry.schemaextraction.javasource.mapping.JavaSourceMappingService;
import io.github.matrixlane.metadataregistry.schemaextraction.javasource.model.*;
import io.github.matrixlane.metadataregistry.web.dto.javasource.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

/**
 * Service for Java source comment extraction and mapping to Administered Items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaSourceExtractionService {

    private final JavaCommentExtractor extractor = new JavaCommentExtractor();
    private final JavaSourceMappingService mappingService = new JavaSourceMappingService();

    /**
     * Extract JavaDoc comments from source according to the request mode.
     */
    public JavaSourceExtractionResponse extract(JavaSourceExtractionRequest request) {
        JavaSourceExtractionResult result;
        switch (request.mode().toUpperCase()) {
            case "TEXT" -> {
                if (request.sourceContent() == null || request.sourceContent().isBlank()) {
                    throw new JavaSourceParseException("sourceContent is required for TEXT mode");
                }
                var opt = extractor.extractFromString(request.sourceContent());
                result = JavaSourceExtractionResult.builder()
                        .classes(opt.map(List::of).orElse(List.of()))
                        .totalFilesProcessed(1)
                        .build();
            }
            case "FILE" -> {
                if (request.sourcePath() == null || request.sourcePath().isBlank()) {
                    throw new JavaSourceParseException("sourcePath is required for FILE mode");
                }
                var opt = extractor.extractFromFile(Paths.get(request.sourcePath()));
                result = JavaSourceExtractionResult.builder()
                        .classes(opt.map(List::of).orElse(List.of()))
                        .totalFilesProcessed(1)
                        .build();
            }
            case "DIRECTORY" -> {
                if (request.sourcePath() == null || request.sourcePath().isBlank()) {
                    throw new JavaSourceParseException("sourcePath is required for DIRECTORY mode");
                }
                result = extractor.extractFromDirectory(Paths.get(request.sourcePath()));
            }
            case "ZIP" -> {
                if (request.sourceContent() == null || request.sourceContent().isBlank()) {
                    throw new JavaSourceParseException("sourceContent (Base64 encoded ZIP) is required for ZIP mode");
                }
                byte[] zipBytes = Base64.getDecoder().decode(request.sourceContent());
                result = extractor.extractFromZip(new ByteArrayInputStream(zipBytes));
            }
            default -> throw new JavaSourceParseException("Unsupported mode: " + request.mode());
        }

        return toExtractionResponse(result);
    }

    /**
     * Preview how extracted comments would map to Administered Items.
     */
    public JavaSourceMappingPreviewResponse previewMapping(JavaSourceExtractionRequest request) {
        JavaSourceExtractionResult result = extractRaw(request);
        JavaSourceMappingResult mappingResult = mappingService.mapExtractionResult(result);
        return toMappingPreviewResponse(mappingResult);
    }

    private JavaSourceExtractionResult extractRaw(JavaSourceExtractionRequest request) {
        // Internal helper to get raw result
        return switch (request.mode().toUpperCase()) {
            case "TEXT" -> {
                var opt = extractor.extractFromString(request.sourceContent());
                yield JavaSourceExtractionResult.builder()
                        .classes(opt.map(List::of).orElse(List.of()))
                        .totalFilesProcessed(1)
                        .build();
            }
            case "FILE" -> {
                var opt = extractor.extractFromFile(Paths.get(request.sourcePath()));
                yield JavaSourceExtractionResult.builder()
                        .classes(opt.map(List::of).orElse(List.of()))
                        .totalFilesProcessed(1)
                        .build();
            }
            case "DIRECTORY" -> extractor.extractFromDirectory(Paths.get(request.sourcePath()));
            case "ZIP" -> {
                byte[] zipBytes = Base64.getDecoder().decode(request.sourceContent());
                yield extractor.extractFromZip(new ByteArrayInputStream(zipBytes));
            }
            default -> throw new JavaSourceParseException("Unsupported mode: " + request.mode());
        };
    }

    private JavaSourceExtractionResponse toExtractionResponse(JavaSourceExtractionResult result) {
        List<JavaClassExtractionDto> classDtos = result.getClasses().stream()
                .map(this::toClassDto)
                .toList();

        var stats = extractor.computeStatistics(result);
        var statsDto = new ExtractionStatisticsDto(
                result.getTotalFilesProcessed(),
                stats.totalClasses(),
                stats.commentedClasses(),
                stats.classCommentRate(),
                stats.totalFields(),
                stats.commentedFields(),
                stats.fieldCommentRate(),
                stats.totalEnums(),
                stats.commentedEnums(),
                stats.enumCommentRate(),
                stats.totalEnumValues(),
                stats.commentedEnumValues(),
                stats.enumValueCommentRate(),
                stats.errorCount()
        );

        return new JavaSourceExtractionResponse(classDtos, statsDto, result.getErrors());
    }

    private JavaClassExtractionDto toClassDto(JavaClassInfo classInfo) {
        return new JavaClassExtractionDto(
                classInfo.getPackageName(),
                classInfo.getClassName(),
                classInfo.getQualifiedName(),
                classInfo.getComment(),
                classInfo.isEnum(),
                classInfo.isInterface(),
                classInfo.isDeprecated(),
                classInfo.getDeprecatedReason(),
                classInfo.getFields().stream().map(this::toFieldDto).toList(),
                classInfo.getEnumValues().stream().map(this::toEnumValueDto).toList(),
                classInfo.getSeeTags()
        );
    }

    private JavaFieldExtractionDto toFieldDto(JavaFieldInfo field) {
        return new JavaFieldExtractionDto(
                field.getFieldName(),
                field.getTypeName(),
                field.getComment(),
                field.getParamTags(),
                field.getSeeTags(),
                field.isDeprecated(),
                field.getDeprecatedReason(),
                field.getModifiers()
        );
    }

    private JavaEnumValueExtractionDto toEnumValueDto(JavaEnumValueInfo ev) {
        return new JavaEnumValueExtractionDto(
                ev.getName(),
                ev.getComment(),
                ev.isDeprecated(),
                ev.getDeprecatedReason()
        );
    }

    private JavaSourceMappingPreviewResponse toMappingPreviewResponse(JavaSourceMappingResult result) {
        return new JavaSourceMappingPreviewResponse(
                result.getDataElementConcepts().stream()
                        .map(m -> new MappedDataElementConceptPreviewDto(
                                m.getName(), m.getDescription(), m.getObjectClassName(),
                                m.getPropertyName(), m.getTableName()))
                        .toList(),
                result.getDataElements().stream()
                        .map(m -> new MappedDataElementPreviewDto(
                                m.getName(), m.getDescription(), m.getColumnName(),
                                m.getTableName(), m.isNullable()))
                        .toList(),
                result.getValueDomains().stream()
                        .map(m -> new MappedValueDomainPreviewDto(
                                m.getName(), m.getDescription(), m.getDataTypeName(),
                                m.getName()))
                        .toList(),
                result.getPermissibleValues().stream()
                        .map(m -> new MappedPermissibleValuePreviewDto(
                                m.getName(), m.getDescription(), m.getValueDomainName(),
                                m.getEnumClassName(), m.getValueMeaning()))
                        .toList(),
                result.getWarnings()
        );
    }
}
