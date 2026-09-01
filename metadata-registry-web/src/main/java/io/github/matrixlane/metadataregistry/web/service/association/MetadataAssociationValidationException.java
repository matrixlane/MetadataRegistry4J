package io.github.matrixlane.metadataregistry.web.service.association;

/**
 * 元数据对象关联关系未通过业务规则校验时抛出。
 */
public class MetadataAssociationValidationException extends MetadataObjectAssociationException {

    public MetadataAssociationValidationException(String message) {
        super(message);
    }
}
