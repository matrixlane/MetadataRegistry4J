package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the corresponding clause in ISO/IEC 11179-3:2023 for a class or interface.
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StandardReference {
    /**
     * The standard clause reference, e.g. "ISO/IEC 11179-3:2023, 6.2.1"
     */
    String value();

    /**
     * Optional description of any deviation from the standard.
     */
    String deviation() default "";
}
