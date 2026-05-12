package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAuthorityTest {

    @Test
    void shouldInitializeDocumentationLanguageIdentifiers() {
        RegistrationAuthority authority = new RegistrationAuthority();
        assertThat(authority.getDocumentationLanguageIdentifier()).isEmpty();
    }

    @Test
    void shouldInitializeRegistrars() {
        RegistrationAuthority authority = new RegistrationAuthority();
        assertThat(authority.getRegistrars()).isEmpty();
    }

    @Test
    void shouldSetRegistrationAuthorityIdentifier() {
        RegistrationAuthority authority = new RegistrationAuthority();
        RegistrationAuthorityIdentifier id = new RegistrationAuthorityIdentifier();
        authority.setRegistrationAuthorityIdentifier(id);
        assertThat(authority.getRegistrationAuthorityIdentifier()).isEqualTo(id);
    }

    @Test
    void shouldAddDocumentationLanguage() {
        RegistrationAuthority authority = new RegistrationAuthority();
        LanguageIdentification lang = new LanguageIdentification();
        authority.getDocumentationLanguageIdentifier().add(lang);
        assertThat(authority.getDocumentationLanguageIdentifier()).hasSize(1);
    }

    @Test
    void shouldAddRegistrar() {
        RegistrationAuthority authority = new RegistrationAuthority();
        Registrar registrar = new Registrar();
        authority.getRegistrars().add(registrar);
        assertThat(authority.getRegistrars()).hasSize(1);
    }

    @Test
    void shouldSetNamespace() {
        RegistrationAuthority authority = new RegistrationAuthority();
        Namespace ns = new Namespace();
        authority.setNamespace(ns);
        assertThat(authority.getNamespace()).isEqualTo(ns);
    }
}
