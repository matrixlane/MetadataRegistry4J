package io.github.matrixlane.metadataregistry.schemaextraction.javasource.mapping;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a mapped Permissible Value derived from an enum constant.
 */
@Data
@Builder
public class MappedPermissibleValue {

    private String name;
    private String description;
    private String valueDomainName;
    private String enumClassName;
    private String valueMeaning;
}
