package io.github.matrixlane.metadataregistry.schemaextraction.javasource;

import io.github.matrixlane.metadataregistry.schemaextraction.javasource.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * High-level extractor that coordinates Java source parsing and produces
 * structured extraction results for comment analysis.
 *
 * <p>This class is the primary entry point for JavaDoc comment extraction
 * from Java source files, directories, or ZIP archives.
 */
public class JavaCommentExtractor {

    private static final Logger logger = LoggerFactory.getLogger(JavaCommentExtractor.class);

    private final JavaSourceParser parser;

    public JavaCommentExtractor() {
        this.parser = new JavaSourceParser();
    }

    /**
     * Extract comments from a single .java file.
     *
     * @param filePath path to the Java source file
     * @return extraction result containing class info, or empty if parsing failed
     */
    public Optional<JavaClassInfo> extractFromFile(Path filePath) {
        logger.debug("Extracting comments from file: {}", filePath);
        return parser.parseFile(filePath);
    }

    /**
     * Extract comments from all .java files under a directory.
     *
     * @param directory root directory to scan
     * @return aggregated extraction result
     */
    public JavaSourceExtractionResult extractFromDirectory(Path directory) {
        logger.info("Extracting comments from directory: {}", directory);
        JavaSourceExtractionResult result = parser.parseDirectory(directory);
        logger.info("Processed {} files, extracted {} classes", result.getTotalFilesProcessed(), result.getClassCount());
        return result;
    }

    /**
     * Extract comments from a ZIP archive of Java source files.
     *
     * @param zipInputStream input stream of the ZIP archive
     * @return aggregated extraction result
     */
    public JavaSourceExtractionResult extractFromZip(InputStream zipInputStream) {
        logger.info("Extracting comments from ZIP archive");
        JavaSourceExtractionResult result = parser.parseZipArchive(zipInputStream);
        logger.info("Processed {} files from ZIP, extracted {} classes", result.getTotalFilesProcessed(), result.getClassCount());
        return result;
    }

    /**
     * Extract comments from a raw string containing Java source code.
     *
     * @param javaSource the Java source code
     * @return extraction result, or empty if parsing failed
     */
    public Optional<JavaClassInfo> extractFromString(String javaSource) {
        return parser.parseContent(javaSource);
    }

    /**
     * Compute extraction statistics.
     *
     * @param result the extraction result
     * @return statistics map
     */
    public ExtractionStatistics computeStatistics(JavaSourceExtractionResult result) {
        int totalClasses = result.getClassCount();
        int commentedClasses = (int) result.getClasses().stream()
                .filter(c -> !c.getComment().isBlank())
                .count();
        int totalFields = result.getFieldCount();
        int commentedFields = (int) result.getClasses().stream()
                .flatMap(c -> c.getFields().stream())
                .filter(f -> !f.getComment().isBlank())
                .count();
        int totalEnums = result.getEnumCount();
        int commentedEnums = (int) result.getClasses().stream()
                .filter(JavaClassInfo::isEnum)
                .filter(c -> !c.getComment().isBlank())
                .count();
        int totalEnumValues = result.getEnumValueCount();
        int commentedEnumValues = (int) result.getClasses().stream()
                .filter(JavaClassInfo::isEnum)
                .flatMap(c -> c.getEnumValues().stream())
                .filter(ev -> !ev.getComment().isBlank())
                .count();

        return new ExtractionStatistics(
                totalClasses, commentedClasses,
                totalFields, commentedFields,
                totalEnums, commentedEnums,
                totalEnumValues, commentedEnumValues,
                result.getErrors().size()
        );
    }

    /**
     * Immutable statistics record for extraction quality assessment.
     */
    public record ExtractionStatistics(
            int totalClasses,
            int commentedClasses,
            int totalFields,
            int commentedFields,
            int totalEnums,
            int commentedEnums,
            int totalEnumValues,
            int commentedEnumValues,
            int errorCount
    ) {
        public double classCommentRate() {
            return totalClasses == 0 ? 0.0 : (double) commentedClasses / totalClasses;
        }

        public double fieldCommentRate() {
            return totalFields == 0 ? 0.0 : (double) commentedFields / totalFields;
        }

        public double enumCommentRate() {
            return totalEnums == 0 ? 0.0 : (double) commentedEnums / totalEnums;
        }

        public double enumValueCommentRate() {
            return totalEnumValues == 0 ? 0.0 : (double) commentedEnumValues / totalEnumValues;
        }
    }
}
