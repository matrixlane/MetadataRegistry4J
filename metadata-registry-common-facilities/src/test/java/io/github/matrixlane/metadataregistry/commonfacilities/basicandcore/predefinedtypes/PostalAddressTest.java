package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostalAddressTest {

    @Test
    void shouldCreateWithValue() {
        PostalAddress pa = PostalAddress.of("123 Main St");
        assertThat(pa.getValue()).isEqualTo("123 Main St");
    }

    @Test
    void equalsAndHashCodeShouldBeBasedOnValue() {
        PostalAddress a = PostalAddress.of("addr1");
        PostalAddress b = PostalAddress.of("addr1");
        PostalAddress c = PostalAddress.of("addr2");
        PostalAddress nil = new PostalAddress();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(PostalAddress.of("x").toString()).isEqualTo("x");
    }
}
