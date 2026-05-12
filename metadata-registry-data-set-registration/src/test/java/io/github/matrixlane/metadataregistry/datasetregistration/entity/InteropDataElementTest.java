package io.github.matrixlane.metadataregistry.datasetregistration.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InteropDataElementTest {

    @Test
    void shouldCreateWithDefaultValues() {
        InteropDataElement de = new InteropDataElement();
        assertThat(de.getId()).isNull();
        assertThat(de.getMappingStatus()).isEqualTo(InteropDataElement.MappingStatus.UNMAPPED);
        assertThat(de.getRequired()).isNull();
        assertThat(de.getCreatedAt()).isNull();
    }

    @Test
    void shouldSetAndGetProperties() {
        InteropDataSet ds = new InteropDataSet();
        ds.setId(java.util.UUID.randomUUID());

        InteropDataElement de = new InteropDataElement();
        de.setDataSet(ds);
        de.setDeIdentifier("DE01.01.001.00");
        de.setDeName("医疗机构组织机构代码");
        de.setDeDefinition("医疗机构组织机构代码是指各级各类医疗机构……");
        de.setDataType("字符型");
        de.setRepresentationFormat("AN..20");
        de.setPermissibleValues("[\"01\", \"02\", \"03\"]");
        de.setRequired(true);
        de.setMappingStatus(InteropDataElement.MappingStatus.UNMAPPED);

        assertThat(de.getDataSet()).isEqualTo(ds);
        assertThat(de.getDeIdentifier()).isEqualTo("DE01.01.001.00");
        assertThat(de.getDeName()).isEqualTo("医疗机构组织机构代码");
        assertThat(de.getDeDefinition()).isEqualTo("医疗机构组织机构代码是指各级各类医疗机构……");
        assertThat(de.getDataType()).isEqualTo("字符型");
        assertThat(de.getRepresentationFormat()).isEqualTo("AN..20");
        assertThat(de.getPermissibleValues()).isEqualTo("[\"01\", \"02\", \"03\"]");
        assertThat(de.getRequired()).isTrue();
        assertThat(de.getMappingStatus()).isEqualTo(InteropDataElement.MappingStatus.UNMAPPED);
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        InteropDataElement de = new InteropDataElement();
        de.onCreate();

        assertThat(de.getCreatedAt()).isNotNull();
        assertThat(de.getUpdatedAt()).isNotNull();
        assertThat(de.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void shouldUpdateTimestampOnPreUpdate() {
        InteropDataElement de = new InteropDataElement();
        de.onCreate();
        LocalDateTime firstCreated = de.getCreatedAt();
        LocalDateTime firstUpdated = de.getUpdatedAt();

        de.onUpdate();
        assertThat(de.getUpdatedAt()).isAfterOrEqualTo(firstUpdated);
        assertThat(de.getCreatedAt()).isEqualTo(firstCreated); // created_at unchanged
    }

    @Test
    void shouldAcceptAllMappingStatuses() {
        InteropDataElement de = new InteropDataElement();
        assertThat(de.getMappingStatus()).isEqualTo(InteropDataElement.MappingStatus.UNMAPPED);

        de.setMappingStatus(InteropDataElement.MappingStatus.MAPPED);
        assertThat(de.getMappingStatus()).isEqualTo(InteropDataElement.MappingStatus.MAPPED);

        de.setMappingStatus(InteropDataElement.MappingStatus.CONFLICT);
        assertThat(de.getMappingStatus()).isEqualTo(InteropDataElement.MappingStatus.CONFLICT);
    }

    @Test
    void shouldAcceptMappedItemId() {
        java.util.UUID itemId = java.util.UUID.randomUUID();
        InteropDataElement de = new InteropDataElement();
        de.setMappedItemId(itemId);
        assertThat(de.getMappedItemId()).isEqualTo(itemId);
    }
}
