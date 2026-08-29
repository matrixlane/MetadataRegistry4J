package io.github.matrixlane.metadataregistry.dataspecification.association;

/**
 * 元数据对象关联操作的基础运行时异常。
 */
public class MetadataObjectAssociationException extends RuntimeException {

    public MetadataObjectAssociationException(String message) {
        super(message);
    }

    public MetadataObjectAssociationException(String message, Throwable cause) {
        super(message, cause);
    }
}
