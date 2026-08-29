package io.github.matrixlane.metadataregistry.dataspecification.association;

/**
 * 尝试建立未被允许的元数据对象关联组合时抛出。
 */
public class UnsupportedMetadataAssociationException extends MetadataObjectAssociationException {

    public UnsupportedMetadataAssociationException(String message) {
        super(message);
    }
}
