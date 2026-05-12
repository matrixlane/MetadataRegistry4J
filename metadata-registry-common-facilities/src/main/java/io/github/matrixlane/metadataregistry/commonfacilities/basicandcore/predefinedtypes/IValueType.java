package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;

/**
 * Common interface for predefined data types in ISO/IEC 11179-3:2023, Clause 6.2.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2")
public interface IValueType<T> {
    T getValue();
    void setValue(T value);
}
