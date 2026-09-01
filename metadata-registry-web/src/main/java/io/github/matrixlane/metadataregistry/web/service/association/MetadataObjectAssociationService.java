package io.github.matrixlane.metadataregistry.web.service.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;

import java.util.ArrayList;
import java.util.List;

/**
 * 元数据对象关联操作服务。
 * <p>
 * 提供建立、解除、替换和校验元数据对象之间关联的统一入口，封装 ISO/IEC 11179 核心元模型中
 * {@link DataElement}、{@link DataElementConcept}、{@link ValueDomain}、{@link ConceptualDomain}
 * 等对象的关系维护逻辑。
 */
public class MetadataObjectAssociationService {

    private final List<MetadataAssociationValidator> validators = new ArrayList<>();

    /**
     * 注册关联校验器。
     */
    public void registerValidator(MetadataAssociationValidator validator) {
        this.validators.add(validator);
    }

    /**
     * 创建带有默认校验器集合的服务实例。
     */
    public static MetadataObjectAssociationService withDefaults() {
        MetadataObjectAssociationService service = new MetadataObjectAssociationService();
        service.registerValidator(new DataElementAssociationValidator());
        service.registerValidator(new DataElementConceptAssociationValidator());
        service.registerValidator(new ValueDomainAssociationValidator());
        return service;
    }

    /**
     * 建立 source 与 target 之间的关联。
     */
    public void associate(AssociationType type, Item source, Item target) {
        validate(type, source, target);
        applyAssociation(type, source, target);
    }

    /**
     * 解除 source 与 target 之间的关联。
     * <p>
     * 要求 source 当前确实与 target 存在对应关联；否则抛出 {@link MetadataAssociationValidationException}。
     */
    public void dissociate(AssociationType type, Item source, Item target) {
        ensureCurrentAssociation(type, source, target);
        applyDissociation(type, source, target);
    }

    /**
     * 将 source 与 oldTarget 的关联替换为 source 与 newTarget 的关联。
     */
    public void replace(AssociationType type, Item source, Item oldTarget, Item newTarget) {
        dissociate(type, source, oldTarget);
        associate(type, source, newTarget);
    }

    /**
     * 对单次关联操作进行校验，不修改对象状态。
     */
    public void validate(AssociationType type, Item source, Item target) {
        ensureSupported(type);
        for (MetadataAssociationValidator validator : validators) {
            if (validator.supports(type)) {
                validator.validate(type, source, target);
            }
        }
    }

    /**
     * 校验对象在持久化前的完整关联状态。
     */
    public void validateCompleteness(Item source) {
        for (MetadataAssociationValidator validator : validators) {
            validator.validateCompleteness(source);
        }
    }

    private void ensureSupported(AssociationType type) {
        boolean supported = validators.stream().anyMatch(v -> v.supports(type));
        if (!supported) {
            throw new UnsupportedMetadataAssociationException("未找到支持关联类型 %s 的校验器".formatted(type));
        }
    }

    private void ensureCurrentAssociation(AssociationType type, Item source, Item target) {
        switch (type) {
            case DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT -> {
                DataElement de = (DataElement) source;
                if (de.getDataElementConcept() != target) {
                    throw new MetadataAssociationValidationException(
                            "DataElement 当前未关联到指定的 DataElementConcept");
                }
            }
            case DATA_ELEMENT_TO_VALUE_DOMAIN -> {
                DataElement de = (DataElement) source;
                if (de.getValueDomain() != target) {
                    throw new MetadataAssociationValidationException(
                            "DataElement 当前未关联到指定的 ValueDomain");
                }
            }
            case DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS -> {
                DataElementConcept dec = (DataElementConcept) source;
                if (dec.getObjectClass() != target) {
                    throw new MetadataAssociationValidationException(
                            "DataElementConcept 当前未关联到指定的 ObjectClass");
                }
            }
            case DATA_ELEMENT_CONCEPT_TO_PROPERTY -> {
                DataElementConcept dec = (DataElementConcept) source;
                if (dec.getProperty() != target) {
                    throw new MetadataAssociationValidationException(
                            "DataElementConcept 当前未关联到指定的 Property");
                }
            }
            case DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN -> {
                DataElementConcept dec = (DataElementConcept) source;
                if (!dec.getConceptualDomains().contains(target)) {
                    throw new MetadataAssociationValidationException(
                            "DataElementConcept 当前未关联到指定的 ConceptualDomain");
                }
            }
            case VALUE_DOMAIN_TO_DATA_TYPE -> {
                ValueDomain vd = (ValueDomain) source;
                if (vd.getDataType() != target) {
                    throw new MetadataAssociationValidationException(
                            "ValueDomain 当前未关联到指定的 DataType");
                }
            }
            case VALUE_DOMAIN_TO_UNIT_OF_MEASURE -> {
                ValueDomain vd = (ValueDomain) source;
                if (vd.getUnitOfMeasure() != target) {
                    throw new MetadataAssociationValidationException(
                            "ValueDomain 当前未关联到指定的 UnitOfMeasure");
                }
            }
            case VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN -> {
                ValueDomain vd = (ValueDomain) source;
                if (ValueDomainConceptualDomains.get(vd) != target) {
                    throw new MetadataAssociationValidationException(
                            "ValueDomain 当前未关联到指定的 ConceptualDomain");
                }
            }
        }
    }

    private void applyAssociation(AssociationType type, Item source, Item target) {
        switch (type) {
            case DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT ->
                    ((DataElement) source).setDataElementConcept((DataElementConcept) target);
            case DATA_ELEMENT_TO_VALUE_DOMAIN ->
                    ((DataElement) source).setValueDomain((ValueDomain) target);
            case DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS ->
                    ((DataElementConcept) source).setObjectClass((ObjectClass) target);
            case DATA_ELEMENT_CONCEPT_TO_PROPERTY ->
                    ((DataElementConcept) source).setProperty((Property) target);
            case DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN -> {
                DataElementConcept dec = (DataElementConcept) source;
                ConceptualDomain cd = (ConceptualDomain) target;
                if (!cd.getDataElementConcepts().contains(dec)) {
                    cd.getDataElementConcepts().add(dec);
                }
                if (!dec.getConceptualDomains().contains(cd)) {
                    dec.getConceptualDomains().add(cd);
                }
            }
            case VALUE_DOMAIN_TO_DATA_TYPE ->
                    ((ValueDomain) source).setDataType((DataType) target);
            case VALUE_DOMAIN_TO_UNIT_OF_MEASURE ->
                    ((ValueDomain) source).setUnitOfMeasure((UnitOfMeasure) target);
            case VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN ->
                    ValueDomainConceptualDomains.set((ValueDomain) source, (ConceptualDomain) target);
        }
    }

    private void applyDissociation(AssociationType type, Item source, Item target) {
        switch (type) {
            case DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT ->
                    ((DataElement) source).setDataElementConcept(null);
            case DATA_ELEMENT_TO_VALUE_DOMAIN ->
                    ((DataElement) source).setValueDomain(null);
            case DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS ->
                    ((DataElementConcept) source).setObjectClass(null);
            case DATA_ELEMENT_CONCEPT_TO_PROPERTY ->
                    ((DataElementConcept) source).setProperty(null);
            case DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN -> {
                DataElementConcept dec = (DataElementConcept) source;
                ConceptualDomain cd = (ConceptualDomain) target;
                cd.getDataElementConcepts().remove(dec);
                dec.getConceptualDomains().remove(cd);
            }
            case VALUE_DOMAIN_TO_DATA_TYPE ->
                    ((ValueDomain) source).setDataType(null);
            case VALUE_DOMAIN_TO_UNIT_OF_MEASURE ->
                    ((ValueDomain) source).setUnitOfMeasure(null);
            case VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN ->
                    ValueDomainConceptualDomains.clear((ValueDomain) source);
        }
    }
}
