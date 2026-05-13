package io.github.matrixlane.metadataregistry.schemaextraction.javasource.mapping;

import io.github.matrixlane.metadataregistry.schemaextraction.javasource.model.*;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.MappedDataElement;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.MappedDataElementConcept;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.MappedValueDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps extracted Java source comment information to ISO/IEC 11179 Administered Items.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>Java class comment → DataElementConcept (ObjectClass = class name, Property = "attributes")</li>
 *   <li>Java field comment → DataElement (name = field name, description = field comment)</li>
 *   <li>Java enum type → ValueDomain (EnumeratedValueDomain, name = enum class name)</li>
 *   <li>Java enum constant → PermissibleValue (name = constant name, description = constant comment)</li>
 * </ul>
 */
public class JavaSourceMappingService {

    private static final Logger logger = LoggerFactory.getLogger(JavaSourceMappingService.class);

    /**
     * Map a single extracted Java class to Administered Items.
     */
    public JavaSourceMappingResult mapClass(JavaClassInfo classInfo) {
        List<MappedDataElementConcept> decs = new ArrayList<>();
        List<MappedDataElement> des = new ArrayList<>();
        List<MappedValueDomain> vds = new ArrayList<>();
        List<MappedPermissibleValue> pvs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Map class to DataElementConcept
        MappedDataElementConcept dec = mapToDataElementConcept(classInfo);
        if (dec != null) {
            decs.add(dec);
        }

        // Map fields to DataElements
        for (JavaFieldInfo field : classInfo.getFields()) {
            MappedDataElement de = mapToDataElement(classInfo, field);
            if (de != null) {
                des.add(de);
            }
        }

        // Map enum to ValueDomain and PermissibleValues
        if (classInfo.isEnum()) {
            MappedValueDomain vd = mapToValueDomain(classInfo);
            if (vd != null) {
                vds.add(vd);
            }
            for (JavaEnumValueInfo ev : classInfo.getEnumValues()) {
                MappedPermissibleValue pv = mapToPermissibleValue(classInfo, ev);
                if (pv != null) {
                    pvs.add(pv);
                }
            }
        }

        return JavaSourceMappingResult.builder()
                .dataElementConcepts(decs)
                .dataElements(des)
                .valueDomains(vds)
                .permissibleValues(pvs)
                .warnings(warnings)
                .build();
    }

    /**
     * Map all classes from an extraction result.
     */
    public JavaSourceMappingResult mapExtractionResult(JavaSourceExtractionResult result) {
        List<MappedDataElementConcept> allDecs = new ArrayList<>();
        List<MappedDataElement> allDes = new ArrayList<>();
        List<MappedValueDomain> allVds = new ArrayList<>();
        List<MappedPermissibleValue> allPvs = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();

        for (JavaClassInfo classInfo : result.getClasses()) {
            JavaSourceMappingResult classResult = mapClass(classInfo);
            allDecs.addAll(classResult.getDataElementConcepts());
            allDes.addAll(classResult.getDataElements());
            allVds.addAll(classResult.getValueDomains());
            allPvs.addAll(classResult.getPermissibleValues());
            allWarnings.addAll(classResult.getWarnings());
        }

        allWarnings.addAll(result.getErrors());

        return JavaSourceMappingResult.builder()
                .dataElementConcepts(allDecs)
                .dataElements(allDes)
                .valueDomains(allVds)
                .permissibleValues(allPvs)
                .warnings(allWarnings)
                .build();
    }

    private MappedDataElementConcept mapToDataElementConcept(JavaClassInfo classInfo) {
        String className = classInfo.getClassName();
        String comment = classInfo.getComment();

        if (className == null || className.isBlank()) {
            return null;
        }

        // ObjectClass = class name (represents the entity)
        // Property = "attributes" or inferred from class context
        String description = comment != null && !comment.isBlank()
                ? comment
                : "Data element concept derived from Java class: " + className;

        return MappedDataElementConcept.builder()
                .name(className)
                .description(description)
                .objectClassName(className)
                .propertyName("attributes")
                .tableName(classInfo.getQualifiedName())
                .build();
    }

    private MappedDataElement mapToDataElement(JavaClassInfo classInfo, JavaFieldInfo field) {
        String fieldName = field.getFieldName();
        String comment = field.getComment();

        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }

        String description = comment != null && !comment.isBlank()
                ? comment
                : "Data element derived from Java field: " + fieldName;

        return MappedDataElement.builder()
                .name(fieldName)
                .description(description)
                .columnName(fieldName)
                .tableName(classInfo.getQualifiedName())
                .nullable(!field.getModifiers().contains("final"))
                .primaryKey(false)
                .autoIncrement(false)
                .build();
    }

    private MappedValueDomain mapToValueDomain(JavaClassInfo classInfo) {
        String className = classInfo.getClassName();
        String comment = classInfo.getComment();

        if (className == null || className.isBlank()) {
            return null;
        }

        String description = comment != null && !comment.isBlank()
                ? comment
                : "Enumerated value domain derived from Java enum: " + className;

        return MappedValueDomain.builder()
                .name(className)
                .description(description)
                .dataTypeName("STRING")
                .build();
    }

    private MappedPermissibleValue mapToPermissibleValue(JavaClassInfo classInfo, JavaEnumValueInfo enumValue) {
        String name = enumValue.getName();
        String comment = enumValue.getComment();

        if (name == null || name.isBlank()) {
            return null;
        }

        String description = comment != null && !comment.isBlank()
                ? comment
                : "Permissible value derived from enum constant: " + name;

        return MappedPermissibleValue.builder()
                .name(name)
                .description(description)
                .valueDomainName(classInfo.getClassName())
                .enumClassName(classInfo.getQualifiedName())
                .valueMeaning(name)
                .build();
    }
}
