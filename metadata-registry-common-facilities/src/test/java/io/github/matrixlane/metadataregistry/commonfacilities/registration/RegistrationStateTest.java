package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationStateTest {

    @Test
    void shouldSetRegistrationStatus() {
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.RECORDED);
        assertThat(state.getRegistrationStatus()).isEqualTo(RegistrationStatus.RECORDED);
    }

    @Test
    void shouldSetEffectiveDate() {
        RegistrationState state = new RegistrationState();
        LocalDateTime now = LocalDateTime.now();
        state.setEffectiveDate(now);
        assertThat(state.getEffectiveDate()).isEqualTo(now);
    }

    @Test
    void shouldSetUntilDate() {
        RegistrationState state = new RegistrationState();
        LocalDateTime now = LocalDateTime.now();
        state.setUntilDate(now);
        assertThat(state.getUntilDate()).isEqualTo(now);
    }

    @Test
    void shouldSetAdministrativeNote() {
        RegistrationState state = new RegistrationState();
        state.setAdministrativeNote("Approved");
        assertThat(state.getAdministrativeNote()).isEqualTo("Approved");
    }

    @Test
    void shouldSetUnresolvedIssue() {
        RegistrationState state = new RegistrationState();
        state.setUnresolvedIssue("Pending review");
        assertThat(state.getUnresolvedIssue()).isEqualTo("Pending review");
    }

    @Test
    void shouldSetAdministrativeStatus() {
        RegistrationState state = new RegistrationState();
        state.setAdministrativeStatus(AdministrativeStatus.DRAFT);
        assertThat(state.getAdministrativeStatus()).isEqualTo(AdministrativeStatus.DRAFT);
    }

    @Test
    void shouldSetPreviousState() {
        RegistrationState state = new RegistrationState();
        RegistrationState previous = new RegistrationState();
        state.setPreviousState(previous);
        assertThat(state.getPreviousState()).isEqualTo(previous);
    }
}
