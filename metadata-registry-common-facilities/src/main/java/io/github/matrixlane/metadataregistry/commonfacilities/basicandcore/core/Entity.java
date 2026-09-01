package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

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
 * <p>DEVIATION: 本类是纯技术基类({@code MappedSuperclass}),在 ISO/IEC
 * 11179-3:2023 中无对应条款(2023 版无 "Entity" 领域概念,此前引用的 6.4.5
 * 系沿用旧版编号,已删除)。标准中的实体语义由 {@link Item}(6.4.2.1)及其
 * 子类承载。
 */
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
