package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AdministrativeStatusServiceTest {

    @InjectMocks
    private AdministrativeStatusService administrativeStatusService;

    @Test
    void landingOf_incomplete_returnsInitial() {
        assertThat(administrativeStatusService.landingOf(RegistrationStatus.INCOMPLETE))
                .isEqualTo(AdministrativeStatus.INITIAL);
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = {"INCOMPLETE"}, mode = EnumSource.Mode.EXCLUDE)
    void landingOf_nonIncomplete_returnsNull(RegistrationStatus status) {
        assertThat(administrativeStatusService.landingOf(status)).isNull();
    }

    @Test
    void markDraft_setsAdministrativeStatusToDraft() {
        AdministeredItem item = itemWithState(RegistrationStatus.RECORDED);

        administrativeStatusService.markDraft(item);

        assertThat(item.getRegistrationState().getAdministrativeStatus())
                .isEqualTo(AdministrativeStatus.DRAFT);
    }

    @Test
    void markDraft_missingState_doesNothing() {
        AdministeredItem item = new AdministeredItem();
        item.setCreationDateTime(LocalDateTime.now());

        administrativeStatusService.markDraft(item);

        assertThat(item.getRegistrationState()).isNull();
    }

    @Test
    void clear_setsAdministrativeStatusToNull() {
        AdministeredItem item = itemWithState(RegistrationStatus.QUALIFIED);
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.DRAFT);

        administrativeStatusService.clear(item);

        assertThat(item.getRegistrationState().getAdministrativeStatus()).isNull();
    }

    @Test
    void clear_missingState_doesNothing() {
        AdministeredItem item = new AdministeredItem();
        item.setCreationDateTime(LocalDateTime.now());

        administrativeStatusService.clear(item);

        assertThat(item.getRegistrationState()).isNull();
    }

    @Test
    void applyLanding_incomplete_setsInitial() {
        AdministeredItem item = itemWithState(RegistrationStatus.INCOMPLETE);
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.DRAFT);

        administrativeStatusService.applyLanding(item);

        assertThat(item.getRegistrationState().getAdministrativeStatus())
                .isEqualTo(AdministrativeStatus.INITIAL);
    }

    @Test
    void applyLanding_recorded_clearsAdministrativeStatus() {
        AdministeredItem item = itemWithState(RegistrationStatus.RECORDED);
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.DRAFT);

        administrativeStatusService.applyLanding(item);

        assertThat(item.getRegistrationState().getAdministrativeStatus()).isNull();
    }

    private AdministeredItem itemWithState(RegistrationStatus status) {
        AdministeredItem item = new AdministeredItem();
        item.setCreationDateTime(LocalDateTime.now());
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(status);
        state.setEffectiveDate(LocalDateTime.now());
        item.setRegistrationState(state);
        return item;
    }
}
