package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTest {

    static class TestEntity extends Entity {
    }

    @Test
    void idShouldBeNullInitially() {
        Entity e = new TestEntity();
        assertThat(e.getId()).isNull();
    }

    @Test
    void shouldSetId() {
        Entity e = new TestEntity();
        UUID id = UUID.randomUUID();
        e.setId(id);
        assertThat(e.getId()).isEqualTo(id);
    }

    @Test
    void equalsShouldBeTrueForSameInstance() {
        Entity e = new TestEntity();
        assertThat(e.equals(e)).isTrue();
    }

    @Test
    void equalsShouldBeFalseForNullId() {
        Entity a = new TestEntity();
        Entity b = new TestEntity();
        assertThat(a.equals(b)).isFalse();
    }

    @Test
    void equalsShouldBeTrueForSameId() {
        UUID id = UUID.randomUUID();
        Entity a = new TestEntity();
        Entity b = new TestEntity();
        a.setId(id);
        b.setId(id);
        assertThat(a.equals(b)).isTrue();
    }

    @Test
    void equalsShouldBeFalseForDifferentId() {
        Entity a = new TestEntity();
        Entity b = new TestEntity();
        a.setId(UUID.randomUUID());
        b.setId(UUID.randomUUID());
        assertThat(a.equals(b)).isFalse();
    }

    @Test
    void equalsShouldBeFalseForNonEntity() {
        Entity a = new TestEntity();
        a.setId(UUID.randomUUID());
        assertThat(a.equals("not an entity")).isFalse();
    }

    @Test
    void hashCodeShouldHandleNullId() {
        Entity e = new TestEntity();
        assertThat(e.hashCode()).isZero();
    }

    @Test
    void hashCodeShouldBeBasedOnId() {
        UUID id = UUID.randomUUID();
        Entity e = new TestEntity();
        e.setId(id);
        assertThat(e.hashCode()).isEqualTo(id.hashCode());
    }
}
