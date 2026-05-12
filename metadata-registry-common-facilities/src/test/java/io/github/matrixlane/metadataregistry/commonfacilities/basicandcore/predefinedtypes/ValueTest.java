package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueTest {

    @Test
    void shouldCreateWithValueOnly() {
        Value v = Value.of("42");
        assertThat(v.getValue()).isEqualTo("42");
        assertThat(v.getDatatypeUri()).isNull();
    }

    @Test
    void shouldCreateWithDatatypeUri() {
        Value v = Value.of("42", "http://www.w3.org/2001/XMLSchema#integer");
        assertThat(v.getValue()).isEqualTo("42");
        assertThat(v.getDatatypeUri()).isEqualTo("http://www.w3.org/2001/XMLSchema#integer");
    }

    @Test
    void shouldSetFields() {
        Value v = new Value();
        v.setValue("hello");
        v.setDatatypeUri("xsd:string");
        assertThat(v.getValue()).isEqualTo("hello");
        assertThat(v.getDatatypeUri()).isEqualTo("xsd:string");
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(Value.of("hello").toString()).isEqualTo("hello");
        assertThat(new Value().toString()).isNull();
    }

    @Test
    void equalsShouldConsiderValueAndDatatype() {
        Value a = Value.of("42", "xsd:int");
        Value b = Value.of("42", "xsd:int");
        Value c = Value.of("42", "xsd:long");
        Value d = Value.of("99", "xsd:int");
        Value nil = new Value();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(d);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void hashCodeShouldBeConsistent() {
        Value a = Value.of("42", "xsd:int");
        Value b = Value.of("42", "xsd:int");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
