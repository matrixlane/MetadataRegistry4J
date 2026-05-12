package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefinitionTest {

    @Test
    void shouldInitializeCollections() {
        Definition definition = new Definition();
        assertThat(definition.getNamespaces()).isEmpty();
        assertThat(definition.getContexts()).isEmpty();
        assertThat(definition.getDefinitionContexts()).isEmpty();
        assertThat(definition.getDesignationDefinitionPairings()).isEmpty();
    }

    @Test
    void shouldSetText() {
        Definition definition = new Definition();
        Text text = new Text();
        definition.setText(text);
        assertThat(definition.getText()).isEqualTo(text);
    }

    @Test
    void shouldSetItem() {
        Definition definition = new Definition();
        Context item = new Context();
        definition.setItem(item);
        assertThat(definition.getItem()).isEqualTo(item);
    }

    @Test
    void shouldAddNamespace() {
        Definition definition = new Definition();
        definition.getNamespaces().add(new Namespace());
        assertThat(definition.getNamespaces()).hasSize(1);
    }

    @Test
    void shouldAddContext() {
        Definition definition = new Definition();
        definition.getContexts().add(new Context());
        assertThat(definition.getContexts()).hasSize(1);
    }

    @Test
    void toStringShouldReturnTextValue() {
        Definition definition = new Definition();
        Text text = new Text();
        text.setValue("Test definition");
        definition.setText(text);
        assertThat(definition.toString()).isEqualTo("Test definition");
    }

    @Test
    void toStringShouldReturnNullWhenTextIsNull() {
        Definition definition = new Definition();
        assertThat(definition.toString()).isNull();
    }
}
