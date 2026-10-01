package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrarTest {

    @Test
    void shouldConstructWithRegistrationAuthority() {
        RegistrationAuthority authority = new RegistrationAuthority();
        Registrar registrar = new Registrar(authority);
        assertThat(registrar.getRegistrationAuthority()).isEqualTo(authority);
    }

    /**
     * ISO/IEC 11179-3:2023 §6.3.5 Contact 的标准属性仅组织 / 个人 / 角色三要素，
     * Registrar 子类只额外挂 registration_authority 关联。
     */
    @Test
    void shouldWireContactThreeElements() {
        RegistrationAuthority authority = new RegistrationAuthority();
        Individual individual = new Individual();
        Role role = new Role();
        Registrar registrar = new Registrar(authority);
        registrar.setIndividual(individual);
        registrar.setRole(role);
        assertThat(registrar.getOrganization()).isEqualTo(authority);
        assertThat(registrar.getIndividual()).isEqualTo(individual);
        assertThat(registrar.getRole()).isEqualTo(role);
    }
}
