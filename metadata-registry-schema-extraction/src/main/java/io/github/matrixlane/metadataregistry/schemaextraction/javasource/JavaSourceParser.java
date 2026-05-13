package io.github.matrixlane.metadataregistry.schemaextraction.javasource;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import io.github.matrixlane.metadataregistry.schemaextraction.javasource.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses Java source files and extracts structured comment information.
 *
 * <p>Handles individual .java files, directory trees, and ZIP archives.
 * Uses JavaParser for robust AST-based extraction without requiring compilation.
 */
public class JavaSourceParser {

    private static final Logger logger = LoggerFactory.getLogger(JavaSourceParser.class);

    private final JavaParser parser;

    public JavaSourceParser() {
        this.parser = new JavaParser();
    }

    /**
     * Parse a single Java source file.
     */
    public Optional<JavaClassInfo> parseFile(Path filePath) {
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return parseContent(content);
        } catch (IOException e) {
            logger.warn("Failed to read file: {}", filePath, e);
            return Optional.empty();
        }
    }

    /**
     * Parse Java source content from a string.
     */
    public Optional<JavaClassInfo> parseContent(String content) {
        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return Optional.empty();
        }
        CompilationUnit cu = result.getResult().get();
        return cu.findFirst(TypeDeclaration.class)
                .map(type -> extractClassInfo(type, cu.getPackageDeclaration()
                        .map(pd -> pd.getName().asString())
                        .orElse("")));
    }

    /**
     * Parse all .java files under a directory recursively.
     */
    public JavaSourceExtractionResult parseDirectory(Path directory) {
        List<JavaClassInfo> classes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int[] fileCount = {0};

        if (!Files.isDirectory(directory)) {
            return JavaSourceExtractionResult.builder()
                    .errors(List.of("Not a directory: " + directory))
                    .totalFilesProcessed(0)
                    .build();
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        fileCount[0]++;
                        parseFile(p).ifPresentOrElse(
                                classes::add,
                                () -> errors.add("Failed to parse: " + p)
                        );
                    });
        } catch (IOException e) {
            errors.add("Directory walk failed: " + e.getMessage());
        }

        return JavaSourceExtractionResult.builder()
                .classes(classes)
                .errors(errors)
                .totalFilesProcessed(fileCount[0])
                .build();
    }

    /**
     * Parse a ZIP archive containing Java source files.
     */
    public JavaSourceExtractionResult parseZipArchive(InputStream zipInputStream) {
        List<JavaClassInfo> classes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int[] fileCount = {0};

        try (ZipInputStream zis = new ZipInputStream(zipInputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".java")) {
                    continue;
                }
                fileCount[0]++;
                final String entryName = entry.getName();
                // Read entry content
                StringBuilder sb = new StringBuilder();
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                }
                parseContent(sb.toString()).ifPresentOrElse(
                        classes::add,
                        () -> errors.add("Failed to parse ZIP entry: " + entryName)
                );
                zis.closeEntry();
            }
        } catch (IOException e) {
            errors.add("ZIP parsing failed: " + e.getMessage());
        }

        return JavaSourceExtractionResult.builder()
                .classes(classes)
                .errors(errors)
                .totalFilesProcessed(fileCount[0])
                .build();
    }

    private JavaClassInfo extractClassInfo(TypeDeclaration<?> type, String packageName) {
        String qualifiedName = packageName.isEmpty()
                ? type.getNameAsString()
                : packageName + "." + type.getNameAsString();

        JavaClassInfo.JavaClassInfoBuilder builder = JavaClassInfo.builder()
                .packageName(packageName)
                .className(type.getNameAsString())
                .qualifiedName(qualifiedName)
                .comment(extractCommentText(type))
                .isEnum(type instanceof EnumDeclaration)
                .isInterface(type instanceof ClassOrInterfaceDeclaration && ((ClassOrInterfaceDeclaration) type).isInterface())
                .seeTags(extractSeeTags(type))
                .typeParameters(extractTypeParameters(type));

        extractDeprecatedInfo(type).ifPresent(info -> {
            builder.deprecated(true);
            builder.deprecatedReason(info);
        });

        if (type instanceof EnumDeclaration enumDecl) {
            builder.enumValues(extractEnumValues(enumDecl));
        }

        builder.fields(extractFields(type));

        return builder.build();
    }

    private String extractCommentText(NodeWithJavadoc<?> node) {
        return node.getJavadoc()
                .map(Javadoc::toText)
                .map(this::cleanCommentText)
                .orElse("");
    }

    private String cleanCommentText(String text) {
        if (text == null) {
            return "";
        }
        return text.lines()
                .map(line -> line.replaceFirst("^\\s*\\*?\\s?", ""))
                .filter(line -> !line.trim().startsWith("@"))
                .collect(Collectors.joining(" "))
                .trim()
                .replaceAll("\\s+", " ");
    }

    private List<String> extractSeeTags(NodeWithJavadoc<?> node) {
        return node.getJavadoc()
                .map(jd -> jd.getBlockTags().stream()
                        .filter(tag -> tag.getType() == JavadocBlockTag.Type.SEE)
                        .map(JavadocBlockTag::getContent)
                        .map(content -> content.toText())
                        .toList())
                .orElse(List.of());
    }

    private Optional<String> extractDeprecatedInfo(NodeWithJavadoc<?> node) {
        return node.getJavadoc()
                .flatMap(jd -> jd.getBlockTags().stream()
                        .filter(tag -> tag.getType() == JavadocBlockTag.Type.DEPRECATED)
                        .findFirst()
                        .map(tag -> tag.getContent().toString().trim()));
    }

    private List<JavaEnumValueInfo> extractEnumValues(EnumDeclaration enumDecl) {
        return enumDecl.getEntries().stream()
                .map(entry -> {
                    JavaEnumValueInfo.JavaEnumValueInfoBuilder builder = JavaEnumValueInfo.builder()
                            .name(entry.getNameAsString())
                            .comment(extractCommentText(entry));
                    extractDeprecatedInfo(entry).ifPresent(reason -> {
                        builder.deprecated(true);
                        builder.deprecatedReason(reason);
                    });
                    return builder.build();
                })
                .toList();
    }

    private List<JavaFieldInfo> extractFields(TypeDeclaration<?> type) {
        return type.getFields().stream()
                .flatMap(fieldDecl -> fieldDecl.getVariables().stream()
                        .map(var -> {
                            JavaFieldInfo.JavaFieldInfoBuilder builder = JavaFieldInfo.builder()
                                    .fieldName(var.getNameAsString())
                                    .typeName(fieldDecl.getElementType().asString())
                                    .comment(extractCommentText(fieldDecl))
                                    .paramTags(extractParamTags(fieldDecl))
                                    .seeTags(extractSeeTags(fieldDecl))
                                    .modifiers(fieldDecl.getModifiers().stream()
                                            .map(m -> m.getKeyword().asString())
                                            .toList());
                            extractDeprecatedInfo(fieldDecl).ifPresent(reason -> {
                                builder.deprecated(true);
                                builder.deprecatedReason(reason);
                            });
                            return builder.build();
                        }))
                .toList();
    }

    private List<String> extractParamTags(NodeWithJavadoc<?> node) {
        return node.getJavadoc()
                .map(jd -> jd.getBlockTags().stream()
                        .filter(tag -> tag.getType() == JavadocBlockTag.Type.PARAM)
                        .map(JavadocBlockTag::getContent)
                        .map(Object::toString)
                        .toList())
                .orElse(List.of());
    }

    private List<String> extractTypeParameters(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration ci && !ci.getTypeParameters().isEmpty()) {
            return ci.getTypeParameters().stream()
                    .map(tp -> tp.getNameAsString())
                    .toList();
        }
        return List.of();
    }
}
