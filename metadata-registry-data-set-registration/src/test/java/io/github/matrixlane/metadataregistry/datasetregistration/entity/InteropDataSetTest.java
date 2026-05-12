package io.github.matrixlane.metadataregistry.datasetregistration.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InteropDataSetTest {

    @Test
    void shouldCreateWithDefaultValues() {
        InteropDataSet ds = new InteropDataSet();
        assertThat(ds.getId()).isNull();
        assertThat(ds.getCode()).isNull();
        assertThat(ds.getImportedAt()).isNull();
        assertThat(ds.getUpdatedAt()).isNull();
        assertThat(ds.getElementCount()).isNull();
    }

    @Test
    void shouldSetAndGetProperties() {
        InteropDataSet ds = new InteropDataSet();
        ds.setCode("HDSB01.01");
        ds.setName("卫生服务数据集");
        ds.setVersion("1.0");
        ds.setSourceFileName("HDSB01.01.xlsx");
        ds.setElementCount(42);

        assertThat(ds.getCode()).isEqualTo("HDSB01.01");
        assertThat(ds.getName()).isEqualTo("卫生服务数据集");
        assertThat(ds.getVersion()).isEqualTo("1.0");
        assertThat(ds.getSourceFileName()).isEqualTo("HDSB01.01.xlsx");
        assertThat(ds.getElementCount()).isEqualTo(42);
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        InteropDataSet ds = new InteropDataSet();
        ds.onCreate();

        assertThat(ds.getImportedAt()).isNotNull();
        assertThat(ds.getUpdatedAt()).isNotNull();
        assertThat(ds.getImportedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void shouldUpdateTimestampOnPreUpdate() {
        InteropDataSet ds = new InteropDataSet();
        ds.onCreate();
        LocalDateTime firstUpdate = ds.getUpdatedAt();

        // Simulate a short delay and update
        ds.onUpdate();
        assertThat(ds.getUpdatedAt()).isAfterOrEqualTo(firstUpdate);
        assertThat(ds.getImportedAt()).isEqualTo(firstUpdate); // imported_at unchanged
    }
}
