package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Namespace Entity Tests - ISO 11179-3 Attributes")
class NamespaceTest {

    @Nested
    @DisplayName("ISO 11179-3 Attribute Initialization")
    class IsoAttributeTests {

        @Test
        @DisplayName("Default values should match ISO 11179-3 specification")
        void defaultValues_shouldMatchSpec() {
            Namespace ns = new Namespace();
            
            assertThat(ns.getMandatoryNamingConventionIndicator()).isFalse();
            assertThat(ns.getOneNamePerItemIndicator()).isFalse();
            assertThat(ns.getOneItemPerNameIndicator()).isTrue();
        }

        @Test
        @DisplayName("All ISO attributes can be set and retrieved")
        void isoAttributes_canBeSetAndRetrieved() {
            Namespace ns = new Namespace();
            UUID ncId = UUID.randomUUID();
            
            ns.setShorthandPrefix("hl7:");
            ns.setSchemeReference(Sign.of("urn:example:hl7"));
            ns.setMandatoryNamingConventionIndicator(true);
            NamingConvention nc = new NamingConvention();
            nc.setId(ncId);
            ns.getNamingConventions().add(nc);
            ns.setOneNamePerItemIndicator(true);
            ns.setOneItemPerNameIndicator(true);
            
            assertThat(ns.getShorthandPrefix()).isEqualTo("hl7:");
            assertThat(ns.getSchemeReference().getValue()).isEqualTo("urn:example:hl7");
            assertThat(ns.getMandatoryNamingConventionIndicator()).isTrue();
            assertThat(ns.getNamingConventions()).hasSize(1);
            assertThat(ns.getNamingConventions().get(0).getId()).isEqualTo(ncId);
            assertThat(ns.getOneNamePerItemIndicator()).isTrue();
            assertThat(ns.getOneItemPerNameIndicator()).isTrue();
        }
    }

    @Nested
    @DisplayName("Naming Authority")
    class NamingAuthorityTests {

        @Test
        @DisplayName("Naming authority can be set as string")
        void namingAuthority_canBeSetAsString() {
            Namespace ns = new Namespace();
            Organization org = new Organization();
            org.getName().add(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of("HL7 International"));
            ns.setNamingAuthority(org);
            
            assertThat(ns.getNamingAuthority().getName().get(0).getValue()).isEqualTo("HL7 International");
        }

        @Test
        @DisplayName("Naming authority supports ISO scheme reference")
        void namingAuthority_supportsIsoSchemeReference() {
            Namespace ns = new Namespace();
            Organization org = new Organization();
            org.getName().add(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of("ISO"));
            ns.setNamingAuthority(org);
            ns.setSchemeReference(Sign.of("urn:iso:std:iso:11179"));
            
            assertThat(ns.getNamingAuthority().getName().get(0).getValue()).isEqualTo("ISO");
            assertThat(ns.getSchemeReference().getValue()).isEqualTo("urn:iso:std:iso:11179");
        }
    }
}
