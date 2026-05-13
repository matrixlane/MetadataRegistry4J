package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.dataspecification.*;

import java.util.*;

/**
 * Maps extracted schema candidates to actual ISO/IEC 11179 administered item JPA entities.
 *
 * <p>This service creates proper entity instances with designations and definitions,
 * ready for persistence through the appropriate repository layer.
 */
public class AdministeredItemMappingService {

    /**
     * Converts a {@link SchemaMappingResult} into actual JPA entity instances.
     *
     * @param mappingResult the schema mapping result containing mapped candidates
     * @return administered item mapping result with JPA entity instances
     */
    public AdministeredItemMappingResult mapToEntities(SchemaMappingResult mappingResult) {
        List<ObjectClass> objectClasses = new ArrayList<>();
        List<Property> properties = new ArrayList<>();
        List<DataElementConcept> decs = new ArrayList<>();
        List<DataType> dataTypes = new ArrayList<>();
        List<DescribedValueDomain> valueDomains = new ArrayList<>();
        List<DataElement> dataElements = new ArrayList<>();

        Map<String, DataType> dataTypeCache = new LinkedHashMap<>();

        for (MappedDataElementConcept mappedDec : mappingResult.getDataElementConcepts()) {
            // Create ObjectClass from table name
            ObjectClass objectClass = createObjectClass(mappedDec.getObjectClassName(), mappedDec.getDescription());
            objectClasses.add(objectClass);

            // Create Property
            Property property = createProperty(mappedDec.getPropertyName(), "Record property for " + mappedDec.getTableName());
            properties.add(property);

            // Create DataElementConcept
            DataElementConcept dec = createDataElementConcept(mappedDec, objectClass, property);
            decs.add(dec);

            // Find columns belonging to this table
            List<MappedDataElement> tableColumns = mappingResult.getDataElements().stream()
                    .filter(de -> de.getTableName().equals(mappedDec.getTableName()))
                    .toList();

            for (MappedDataElement mappedDe : tableColumns) {
                // Create or reuse DataType
                DataType dataType = dataTypeCache.computeIfAbsent(
                        mappedDe.getValueDomain().getDataTypeName(),
                        dtName -> {
                            DataType dt = createDataType(dtName);
                            dataTypes.add(dt);
                            return dt;
                        }
                );

                // Create ValueDomain
                DescribedValueDomain vd = createValueDomain(mappedDe.getValueDomain(), dataType);
                valueDomains.add(vd);

                // Create DataElement
                DataElement de = createDataElement(mappedDe, dec, vd);
                dataElements.add(de);
            }
        }

        return AdministeredItemMappingResult.builder()
                .databaseProductName(mappingResult.getDatabaseProductName())
                .catalog(mappingResult.getCatalog())
                .schema(mappingResult.getSchema())
                .objectClasses(objectClasses)
                .properties(properties)
                .dataElementConcepts(decs)
                .dataTypes(dataTypes)
                .valueDomains(valueDomains)
                .dataElements(dataElements)
                .build();
    }

    private ObjectClass createObjectClass(String name, String description) {
        ObjectClass oc = new ObjectClass();
        oc.setDesignations(List.of(createDesignation(name)));
        oc.setDefinitions(List.of(createDefinition(description != null ? description : "Object class for " + name)));
        return oc;
    }

    private Property createProperty(String name, String description) {
        Property property = new Property();
        property.setDesignations(List.of(createDesignation(name)));
        property.setDefinitions(List.of(createDefinition(description != null ? description : "Property " + name)));
        return property;
    }

    private DataElementConcept createDataElementConcept(MappedDataElementConcept mapped, ObjectClass oc, Property property) {
        DataElementConcept dec = new DataElementConcept();
        dec.setDesignations(List.of(createDesignation(mapped.getName())));
        dec.setDefinitions(List.of(createDefinition(mapped.getDescription() != null ? mapped.getDescription() : "Data element concept for " + mapped.getTableName())));
        dec.setObjectClass(oc);
        dec.setProperty(property);
        return dec;
    }

    private DataType createDataType(String typeName) {
        DataType dt = new DataType();
        dt.setDesignations(List.of(createDesignation(typeName)));
        dt.setDefinitions(List.of(createDefinition("Data type " + typeName)));
        return dt;
    }

    private DescribedValueDomain createValueDomain(MappedValueDomain mapped, DataType dataType) {
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setDesignations(List.of(createDesignation(mapped.getName())));
        vd.setDefinitions(List.of(createDefinition(mapped.getDescription() != null ? mapped.getDescription() : "Value domain for " + mapped.getDataTypeName())));
        vd.setDataType(dataType);
        if (mapped.getMaximumCharacterQuantity() != null) {
            vd.setMaximumCharacterQuantity(mapped.getMaximumCharacterQuantity());
        }
        if (mapped.getDecimalScale() != null) {
            vd.setFormat(mapped.getDecimalScale().toString());
        }
        return vd;
    }

    private DataElement createDataElement(MappedDataElement mapped, DataElementConcept dec, DescribedValueDomain vd) {
        DataElement de = new DataElement();
        de.setDesignations(List.of(createDesignation(mapped.getName())));
        de.setDefinitions(List.of(createDefinition(mapped.getDescription() != null ? mapped.getDescription() : "Data element for column " + mapped.getTableName() + "." + mapped.getColumnName())));
        de.setDataElementConcept(dec);
        de.setValueDomain(vd);
        return de;
    }

    private Designation createDesignation(String name) {
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        return designation;
    }

    private Definition createDefinition(String text) {
        Definition definition = new Definition();
        definition.setText(Text.of(text));
        return definition;
    }
}
