package io.github.matrixlane.metadataregistry.schemaextraction.openapi;

import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiExtractionResult;
import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiPropertyInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiSchemaInfo;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Extracts data models from a parsed OpenAPI document and maps them
 * to metadata registry concepts (DataElementConcept, DataElement, ValueDomain).
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>OpenAPI Schema → DataElementConcept (via ObjectClass + Property)</li>
 *   <li>Schema Property → DataElement</li>
 *   <li>enum constraint → EnumeratedValueDomain + PermissibleValue</li>
 *   <li>$ref → reference relationship</li>
 * </ul>
 */
public class OpenApiSchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSchemaExtractor.class);

    private static final Map<String, String> OPENAPI_TO_DATA_TYPE_MAP = Map.ofEntries(
            Map.entry("string", "StringType"),
            Map.entry("integer", "IntegerType"),
            Map.entry("number", "IntegerType"),
            Map.entry("boolean", "BooleanType"),
            Map.entry("array", "Array"),
            Map.entry("object", "Object")
    );

    private static final Map<String, String> FORMAT_TO_DATA_TYPE_MAP = Map.ofEntries(
            Map.entry("int32", "IntegerType"),
            Map.entry("int64", "IntegerType"),
            Map.entry("float", "IntegerType"),
            Map.entry("double", "IntegerType"),
            Map.entry("date", "Date"),
            Map.entry("date-time", "Datetime"),
            Map.entry("password", "StringType"),
            Map.entry("byte", "StringType"),
            Map.entry("binary", "StringType"),
            Map.entry("email", "EmailAddress")
    );

    /**
     * Extracts all schemas from the parsed OpenAPI document.
     *
     * @param openAPI the parsed OpenAPI model
     * @return extraction result with mapped metadata registry concepts
     */
    public OpenApiExtractionResult extract(OpenAPI openAPI) {
        long startTime = System.currentTimeMillis();

        OpenApiExtractionResult.OpenApiExtractionResultBuilder resultBuilder = OpenApiExtractionResult.builder();

        if (openAPI.getInfo() != null) {
            resultBuilder.documentTitle(openAPI.getInfo().getTitle());
            resultBuilder.documentVersion(openAPI.getInfo().getVersion());
            resultBuilder.documentDescription(openAPI.getInfo().getDescription());
        }
        resultBuilder.openApiVersion(openAPI.getOpenapi() != null ? openAPI.getOpenapi() : "2.0 (converted)");

        List<OpenApiSchemaInfo> schemas = new ArrayList<>();
        List<String> globalWarnings = new ArrayList<>();
        List<String> unresolvedRefs = new ArrayList<>();
        int totalProperties = 0;
        int totalEnumValues = 0;

        Map<String, Schema> allSchemas = resolveAllSchemas(openAPI);

        if (allSchemas.isEmpty()) {
            globalWarnings.add("No schemas found in components/schemas or definitions");
        }

        for (var entry : allSchemas.entrySet()) {
            String schemaName = entry.getKey();
            Schema schema = entry.getValue();

            try {
                OpenApiSchemaInfo schemaInfo = extractSchema(schemaName, schema, allSchemas, unresolvedRefs);
                schemas.add(schemaInfo);
                totalProperties += schemaInfo.getProperties().size();
                totalEnumValues += schemaInfo.getProperties().stream()
                        .mapToInt(p -> p.getEnumValues() != null ? p.getEnumValues().size() : 0)
                        .sum();
            } catch (Exception e) {
                log.warn("Failed to extract schema '{}': {}", schemaName, e.getMessage());
                globalWarnings.add("Failed to extract schema '" + schemaName + "': " + e.getMessage());
            }
        }

        long extractionTime = System.currentTimeMillis() - startTime;

        resultBuilder.schemas(schemas);
        resultBuilder.warnings(globalWarnings);
        resultBuilder.unresolvedReferences(unresolvedRefs);
        resultBuilder.totalSchemas(schemas.size());
        resultBuilder.totalProperties(totalProperties);
        resultBuilder.totalEnumValues(totalEnumValues);
        resultBuilder.extractionTimeMs(extractionTime);

        return resultBuilder.build();
    }

    /**
     * Resolves all schemas from both OpenAPI 3.0 components/schemas and Swagger 2.0 definitions.
     */
    private Map<String, Schema> resolveAllSchemas(OpenAPI openAPI) {
        Map<String, Schema> allSchemas = new LinkedHashMap<>();

        // OpenAPI 3.0: components/schemas
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            allSchemas.putAll(openAPI.getComponents().getSchemas());
        }

        // Swagger 2.0 converted: definitions (some parsers place them here after conversion)
        // The swagger-parser v3 converts Swagger 2.0 to OpenAPI 3.0, so definitions are typically
        // moved to components/schemas. This is a safety net.

        return allSchemas;
    }

    private OpenApiSchemaInfo extractSchema(String schemaName, Schema schema,
                                            Map<String, Schema> allSchemas, List<String> unresolvedRefs) {
        OpenApiSchemaInfo.OpenApiSchemaInfoBuilder builder = OpenApiSchemaInfo.builder();
        builder.name(schemaName);
        builder.description(schema.getDescription());
        builder.title(schema.getTitle());
        builder.type(schema.getType());
        builder.referencePath("#/components/schemas/" + schemaName);
        builder.suggestedObjectClassName(toObjectClassName(schemaName));

        List<OpenApiPropertyInfo> properties = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> requiredFields = schema.getRequired() != null ? schema.getRequired() : Collections.emptyList();
        builder.requiredFields(requiredFields);

        // Handle composed schemas (allOf, oneOf, anyOf)
        if (schema instanceof ComposedSchema composedSchema) {
            properties.addAll(extractComposedSchema(composedSchema, schemaName, allSchemas, unresolvedRefs, requiredFields));
        }

        // Handle regular object properties
        Map<String, Schema> props = getPropertiesMap(schema);
        if (props != null) {
            for (String propName : props.keySet()) {
                Schema propSchema = props.get(propName);
                OpenApiPropertyInfo propInfo = extractProperty(propName, propSchema, requiredFields.contains(propName),
                        allSchemas, unresolvedRefs);
                properties.add(propInfo);
            }
        }

        // Handle additionalProperties as a special case
        if (schema.getAdditionalProperties() instanceof Schema additionalSchema) {
            OpenApiPropertyInfo additionalProp = extractProperty("additionalProperties", additionalSchema, false,
                    allSchemas, unresolvedRefs);
            additionalProp.setName("additionalProperties");
            additionalProp.getWarnings().add("Schema uses additionalProperties; mapped as generic field");
            properties.add(additionalProp);
        }

        // Handle array items at schema level (rare, but some schemas define arrays at top level)
        if (schema instanceof ArraySchema arraySchema && arraySchema.getItems() != null) {
            warnings.add("Schema is defined as an array type; extracted properties are from item schema");
            Schema itemSchema = arraySchema.getItems();
            Map<String, Schema> itemProps = getPropertiesMap(itemSchema);
            if (itemProps != null) {
                for (Map.Entry<String, Schema> propEntry : itemProps.entrySet()) {
                    String propName = propEntry.getKey();
                    Schema propSchema = propEntry.getValue();
                    OpenApiPropertyInfo propInfo = extractProperty(propName, propSchema,
                            requiredFields.contains(propName), allSchemas, unresolvedRefs);
                    properties.add(propInfo);
                }
            }
        }

        builder.properties(properties);
        builder.propertyCount(properties.size());
        builder.extractedDataElementCount(properties.size());
        builder.warnings(warnings);

        return builder.build();
    }

    private List<OpenApiPropertyInfo> extractComposedSchema(ComposedSchema composedSchema, String schemaName,
                                                            Map<String, Schema> allSchemas, List<String> unresolvedRefs,
                                                            List<String> requiredFields) {
        List<OpenApiPropertyInfo> properties = new ArrayList<>();

        if (composedSchema.getAllOf() != null) {
            for (Schema part : composedSchema.getAllOf()) {
                if (part.get$ref() != null) {
                    Schema resolved = resolveRef(part.get$ref(), allSchemas);
                    Map<String, Schema> resolvedProps = getPropertiesMap(resolved);
                    if (resolved != null && resolvedProps != null) {
                        for (Map.Entry<String, Schema> entry : resolvedProps.entrySet()) {
                            String propName = entry.getKey();
                            Schema propSchema = entry.getValue();
                            properties.add(extractProperty(propName, propSchema,
                                    requiredFields.contains(propName), allSchemas, unresolvedRefs));
                        }
                    }
                } else {
                    Map<String, Schema> partProps = getPropertiesMap(part);
                    if (partProps != null) {
                        for (Map.Entry<String, Schema> entry : partProps.entrySet()) {
                            String propName = entry.getKey();
                            Schema propSchema = entry.getValue();
                            properties.add(extractProperty(propName, propSchema,
                                    requiredFields.contains(propName), allSchemas, unresolvedRefs));
                        }
                    }
                }
            }
        }

        if (composedSchema.getOneOf() != null) {
            log.debug("Schema '{}' uses oneOf; creating property with composite references", schemaName);
            OpenApiPropertyInfo compositeProp = OpenApiPropertyInfo.builder()
                    .name(schemaName + "_oneOf")
                    .description("One of multiple types: " + composedSchema.getOneOf().stream()
                            .map(s -> s.get$ref() != null ? s.get$ref() : s.getType())
                            .toList())
                    .openApiType("object")
                    .compositeType("oneOf")
                    .compositeReferences(composedSchema.getOneOf().stream()
                            .map(s -> s.get$ref() != null ? s.get$ref() : s.getType())
                            .toList())
                    .build();
            compositeProp.getWarnings().add("oneOf composite type requires manual review for Value Domain mapping");
            properties.add(compositeProp);
        }

        if (composedSchema.getAnyOf() != null) {
            log.debug("Schema '{}' uses anyOf; creating property with composite references", schemaName);
            OpenApiPropertyInfo compositeProp = OpenApiPropertyInfo.builder()
                    .name(schemaName + "_anyOf")
                    .description("Any of multiple types: " + composedSchema.getAnyOf().stream()
                            .map(s -> s.get$ref() != null ? s.get$ref() : s.getType())
                            .toList())
                    .openApiType("object")
                    .compositeType("anyOf")
                    .compositeReferences(composedSchema.getAnyOf().stream()
                            .map(s -> s.get$ref() != null ? s.get$ref() : s.getType())
                            .toList())
                    .build();
            compositeProp.getWarnings().add("anyOf composite type requires manual review for Value Domain mapping");
            properties.add(compositeProp);
        }

        return properties;
    }

    private OpenApiPropertyInfo extractProperty(String propName, Schema propSchema, boolean required,
                                                Map<String, Schema> allSchemas, List<String> unresolvedRefs) {
        OpenApiPropertyInfo.OpenApiPropertyInfoBuilder builder = OpenApiPropertyInfo.builder();
        builder.name(propName);
        builder.required(required);

        if (propSchema == null) {
            builder.openApiType("unknown");
            builder.suggestedDataType("StringType");
            builder.warnings(List.of("Property schema is null; defaulted to StringType"));
            return builder.build();
        }

        builder.description(propSchema.getDescription());
        builder.example(propSchema.getExample() != null ? propSchema.getExample().toString() : null);
        builder.defaultValue(propSchema.getDefault() != null ? propSchema.getDefault().toString() : null);

        // Handle $ref
        if (propSchema.get$ref() != null) {
            String ref = propSchema.get$ref();
            builder.reference(ref);
            builder.openApiType("object");
            builder.suggestedDataType("Object");
            builder.nestedObject(true);

            Schema resolved = resolveRef(ref, allSchemas);
            if (resolved == null) {
                unresolvedRefs.add(ref);
                builder.warnings(List.of("Unresolved reference: " + ref));
            } else {
                builder.description(resolved.getDescription());
            }
            return builder.build();
        }

        // Handle arrays
        if (propSchema instanceof ArraySchema arraySchema) {
            builder.array(true);
            builder.openApiType("array");

            Schema itemsSchema = arraySchema.getItems();
            if (itemsSchema != null) {
                if (itemsSchema.get$ref() != null) {
                    builder.itemType(itemsSchema.get$ref());
                    builder.reference(itemsSchema.get$ref());
                    builder.suggestedDataType("Array");
                    builder.nestedObject(true);
                } else {
                    String itemType = itemsSchema.getType();
                    builder.itemType(itemType);
                    builder.suggestedDataType(mapToDataType(itemType, itemsSchema.getFormat()));
                }
            } else {
                builder.itemType("unknown");
                builder.suggestedDataType("Array");
            }

            builder.warnings(List.of("Array type mapped; item type should be reviewed"));
            return builder.build();
        }

        // Handle composed schemas at property level
        if (propSchema instanceof ComposedSchema composedProp) {
            builder.openApiType("object");
            builder.suggestedDataType("Object");
            builder.nestedObject(true);

            List<String> compositeRefs = new ArrayList<>();
            if (composedProp.getAllOf() != null) {
                builder.compositeType("allOf");
                for (Schema part : composedProp.getAllOf()) {
                    if (part.get$ref() != null) {
                        compositeRefs.add(part.get$ref());
                    }
                }
            }
            if (composedProp.getOneOf() != null) {
                builder.compositeType("oneOf");
                for (Schema part : composedProp.getOneOf()) {
                    if (part.get$ref() != null) {
                        compositeRefs.add(part.get$ref());
                    }
                }
            }
            builder.compositeReferences(compositeRefs);
            builder.warnings(List.of("Composed schema at property level requires manual review"));
            return builder.build();
        }

        // Regular scalar property
        String type = propSchema.getType();
        String format = propSchema.getFormat();
        builder.openApiType(type);
        builder.openApiFormat(format);
        builder.suggestedDataType(mapToDataType(type, format));

        // Constraints
        builder.minLength(propSchema.getMinLength());
        builder.maxLength(propSchema.getMaxLength());
        builder.minimum(propSchema.getMinimum());
        builder.maximum(propSchema.getMaximum());
        builder.pattern(propSchema.getPattern());

        // Enum values → PermissibleValue mapping
        if (propSchema.getEnum() != null && !propSchema.getEnum().isEmpty()) {
            List<String> enumValues = propSchema.getEnum().stream()
                    .map(Object::toString)
                    .toList();
            builder.enumValues(enumValues);
            builder.mappedPermissibleValues(enumValues);
        }

        // Nested inline object
        if ("object".equals(type) && propSchema.getProperties() != null && !propSchema.getProperties().isEmpty()) {
            builder.nestedObject(true);
            builder.warnings(List.of("Inline nested object; consider extracting to separate schema"));
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Schema> getPropertiesMap(Schema schema) {
        if (schema == null || schema.getProperties() == null) {
            return null;
        }
        return (Map<String, Schema>) (Map<?, ?>) schema.getProperties();
    }

    private Schema resolveRef(String ref, Map<String, Schema> allSchemas) {
        // ref format: #/components/schemas/SchemaName
        String schemaName = extractSchemaNameFromRef(ref);
        if (schemaName != null) {
            return allSchemas.get(schemaName);
        }
        return null;
    }

    private String extractSchemaNameFromRef(String ref) {
        if (ref == null) {
            return null;
        }
        // Handle #/components/schemas/Name or #/definitions/Name
        if (ref.contains("/")) {
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
        return ref;
    }

    private String toObjectClassName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return "UnknownObject";
        }
        // Convert to PascalCase if snake_case or camelCase
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;
        for (char c : schemaName.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                nextUpper = true;
            } else if (nextUpper) {
                result.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String mapToDataType(String openApiType, String format) {
        if (format != null && !format.isEmpty()) {
            String mapped = FORMAT_TO_DATA_TYPE_MAP.get(format.toLowerCase());
            if (mapped != null) {
                return mapped;
            }
        }
        if (openApiType != null) {
            String mapped = OPENAPI_TO_DATA_TYPE_MAP.get(openApiType.toLowerCase());
            if (mapped != null) {
                return mapped;
            }
        }
        return "StringType";
    }
}
