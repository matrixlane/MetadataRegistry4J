package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdministeredItemTest {

    @Test
    void shouldConstructWithItem() {
        Context item = new Context();
        AdministeredItem ai = new AdministeredItem(item);
        assertThat(ai).isNotNull();
    }

    @Test
    void shouldInitializeCollections() {
        AdministeredItem ai = new AdministeredItem();
        assertThat(ai.getAttachedItems()).isEmpty();
    }

    @Test
    void shouldSetRegistrationState() {
        AdministeredItem ai = new AdministeredItem();
        RegistrationState state = new RegistrationState();
        ai.setRegistrationState(state);
        assertThat(ai.getRegistrationState()).isEqualTo(state);
    }

    @Test
    void shouldSetStewardshipRecord() {
        AdministeredItem ai = new AdministeredItem();
        StewardshipRecord record = new StewardshipRecord();
        ai.setStewardshipRecord(record);
        assertThat(ai.getStewardshipRecord()).isEqualTo(record);
    }

    @Test
    void shouldSetRegistrationAuthority() {
        AdministeredItem ai = new AdministeredItem();
        RegistrationAuthority authority = new RegistrationAuthority();
        ai.setRegistrationAuthority(authority);
        assertThat(ai.getRegistrationAuthority()).isEqualTo(authority);
    }

    @Test
    void shouldSetCreationDateTime() {
        AdministeredItem ai = new AdministeredItem();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ai.setCreationDateTime(now);
        assertThat(ai.getCreationDateTime()).isEqualTo(now);
    }

    @Test
    void shouldSetLastChangeDateTime() {
        AdministeredItem ai = new AdministeredItem();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ai.setLastChangeDateTime(now);
        assertThat(ai.getLastChangeDateTime()).isEqualTo(now);
    }

    @Test
    void shouldSetChangeDescription() {
        AdministeredItem ai = new AdministeredItem();
        ai.setChangeDescription("Updated");
        assertThat(ai.getChangeDescription()).isEqualTo("Updated");
    }

    @Test
    void shouldSetExplanatoryComment() {
        AdministeredItem ai = new AdministeredItem();
        ai.setExplanatoryComment("Test comment");
        assertThat(ai.getExplanatoryComment()).isEqualTo("Test comment");
    }

    @Test
    void shouldSetOrigin() {
        AdministeredItem ai = new AdministeredItem();
        ai.setOrigin("External");
        assertThat(ai.getOrigin()).isEqualTo("External");
    }

    @Test
    void shouldAddAttachedItem() {
        AdministeredItem ai = new AdministeredItem();
        AttachedItem attached = new AttachedItem(new Context());
        ai.getAttachedItems().add(attached);
        assertThat(ai.getAttachedItems()).hasSize(1);
    }
}
