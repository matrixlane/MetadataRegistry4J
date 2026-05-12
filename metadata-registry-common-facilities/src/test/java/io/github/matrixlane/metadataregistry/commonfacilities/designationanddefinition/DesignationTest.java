package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesignationTest {

    @Test
    void shouldInitializeCollections() {
        Designation designation = new Designation();
        assertThat(designation.getNamespaces()).isEmpty();
        assertThat(designation.getContexts()).isEmpty();
        assertThat(designation.getDesignationContexts()).isEmpty();
        assertThat(designation.getDesignationDefinitionPairings()).isEmpty();
    }

    @Test
    void shouldSetSign() {
        Designation designation = new Designation();
        Sign sign = new Sign();
        designation.setSign(sign);
        assertThat(designation.getSign()).isEqualTo(sign);
    }

    @Test
    void shouldSetItem() {
        Designation designation = new Designation();
        Context item = new Context();
        designation.setItem(item);
        assertThat(designation.getItem()).isEqualTo(item);
    }

    @Test
    void shouldAddNamespace() {
        Designation designation = new Designation();
        designation.getNamespaces().add(new Namespace());
        assertThat(designation.getNamespaces()).hasSize(1);
    }

    @Test
    void shouldAddContext() {
        Designation designation = new Designation();
        designation.getContexts().add(new Context());
        assertThat(designation.getContexts()).hasSize(1);
    }

    @Test
    void toStringShouldReturnSignValue() {
        Designation designation = new Designation();
        Sign sign = new Sign();
        sign.setValue("Test designation");
        designation.setSign(sign);
        assertThat(designation.toString()).isEqualTo("Test designation");
    }

    @Test
    void toStringShouldReturnNullWhenSignIsNull() {
        Designation designation = new Designation();
        assertThat(designation.toString()).isNull();
    }
}
