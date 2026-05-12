package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical base class providing JPA identity for all registry entities.
 *
 * <p>DEVIATION: In ISO/IEC 11179-3:2023, 6.4.5, {@code Entity} is a domain
 * concept that extends {@link Item}. This class is a {@code MappedSuperclass}
 * used as a technical JPA foundation and should not be confused with the
 * standard's {@code Entity}. The standard's {@code Entity} concept is
 * represented by {@link Item} and its subclasses in this implementation.
 */
@StandardReference(value = "ISO/IEC 11179-3:2023, 6.4.5",
        deviation = "Used as JPA MappedSuperclass instead of a domain subclass of Item. See class javadoc.")
@MappedSuperclass
@Getter
@Setter
public abstract class Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity other)) return false;
        if (id == null || other.id == null) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
