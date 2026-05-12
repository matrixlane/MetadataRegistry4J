package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageIdentificationTest {

    @Test
    void shouldSetComponents() {
        LanguageIdentification li = new LanguageIdentification();
        li.setLanguageIdentifier("zh");
        li.setScriptIdentifier("Hans");
        li.setGeopoliticalTerritoryIdentifier("CN");
        li.getVariantIdentifier().add("variant1");
        li.getExtensionIdentifier().add("u-ca-chinese");
        li.setPrivateUseQualifier("custom");

        assertThat(li.getLanguageIdentifier()).isEqualTo("zh");
        assertThat(li.getScriptIdentifier()).isEqualTo("Hans");
        assertThat(li.getGeopoliticalTerritoryIdentifier()).isEqualTo("CN");
        assertThat(li.getVariantIdentifier()).containsExactly("variant1");
        assertThat(li.getExtensionIdentifier()).containsExactly("u-ca-chinese");
        assertThat(li.getPrivateUseQualifier()).isEqualTo("custom");
    }

    @Test
    void toBcp47TagWithAllComponents() {
        LanguageIdentification li = new LanguageIdentification();
        li.setLanguageIdentifier("zh");
        li.setScriptIdentifier("Hans");
        li.setGeopoliticalTerritoryIdentifier("CN");
        li.getVariantIdentifier().add("variant1");
        li.getExtensionIdentifier().add("u-ca-chinese");
        li.setPrivateUseQualifier("custom");

        assertThat(li.toBcp47Tag()).isEqualTo("zh-Hans-CN-variant1-u-ca-chinese-x-custom");
    }

    @Test
    void toBcp47TagWithLanguageOnly() {
        LanguageIdentification li = new LanguageIdentification();
        li.setLanguageIdentifier("en");
        assertThat(li.toBcp47Tag()).isEqualTo("en");
    }

    @Test
    void toBcp47TagWithEmptyComponents() {
        LanguageIdentification li = new LanguageIdentification();
        assertThat(li.toBcp47Tag()).isEmpty();
    }

    @Test
    void toStringShouldReturnLanguageIdentifier() {
        LanguageIdentification li = new LanguageIdentification();
        li.setLanguageIdentifier("fr");
        assertThat(li.toString()).isEqualTo("fr");
    }
}
