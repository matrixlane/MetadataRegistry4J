package io.github.matrixlane.metadataregistry.commonfacilities.identification;

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
            ns.setSchemeReference("urn:example:hl7");
            ns.setMandatoryNamingConventionIndicator(true);
            NamingConvention nc = new NamingConvention();
            nc.setId(ncId);
            ns.getNamingConventions().add(nc);
            ns.setOneNamePerItemIndicator(true);
            ns.setOneItemPerNameIndicator(true);
            
            assertThat(ns.getShorthandPrefix()).isEqualTo("hl7:");
            assertThat(ns.getSchemeReference()).isEqualTo("urn:example:hl7");
            assertThat(ns.getMandatoryNamingConventionIndicator()).isTrue();
            assertThat(ns.getNamingConventions()).hasSize(1);
            assertThat(ns.getNamingConventions().get(0).getId()).isEqualTo(ncId);
            assertThat(ns.getOneNamePerItemIndicator()).isTrue();
            assertThat(ns.getOneItemPerNameIndicator()).isTrue();
        }
    }

    @Nested
    @DisplayName("Hierarchical Namespace Support")
    class HierarchyTests {

        @Test
        @DisplayName("Parent-child relationship can be established")
        void parentChildRelationship_canBeEstablished() {
            Namespace parent = new Namespace();
            UUID parentId = UUID.randomUUID();
            parent.setId(parentId);
            
            Namespace child = new Namespace();
            UUID childId = UUID.randomUUID();
            child.setId(childId);
            child.setParent(parent);
            
            assertThat(child.getParent()).isEqualTo(parent);
            assertThat(child.getParent().getId()).isEqualTo(parentId);
        }

        @Test
        @DisplayName("Root namespace has no parent")
        void rootNamespace_hasNoParent() {
            Namespace root = new Namespace();
            root.setId(UUID.randomUUID());
            
            assertThat(root.getParent()).isNull();
        }

        @Test
        @DisplayName("Children collection is initialized")
        void childrenCollection_isInitialized() {
            Namespace parent = new Namespace();
            parent.setId(UUID.randomUUID());
            
            assertThat(parent.getChildren()).isNotNull();
            assertThat(parent.getChildren()).isEmpty();
        }

        @Test
        @DisplayName("Multi-level hierarchy can be represented")
        void multiLevelHierarchy_canBeRepresented() {
            Namespace root = new Namespace();
            root.setId(UUID.randomUUID());
            
            Namespace level1 = new Namespace();
            level1.setId(UUID.randomUUID());
            level1.setParent(root);
            
            Namespace level2 = new Namespace();
            level2.setId(UUID.randomUUID());
            level2.setParent(level1);
            
            assertThat(level2.getParent()).isEqualTo(level1);
            assertThat(level2.getParent().getParent()).isEqualTo(root);
            assertThat(level2.getParent().getParent().getParent()).isNull();
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
            ns.setSchemeReference("urn:iso:std:iso:11179");
            
            assertThat(ns.getNamingAuthority().getName().get(0).getValue()).isEqualTo("ISO");
            assertThat(ns.getSchemeReference()).isEqualTo("urn:iso:std:iso:11179");
        }
    }
}
