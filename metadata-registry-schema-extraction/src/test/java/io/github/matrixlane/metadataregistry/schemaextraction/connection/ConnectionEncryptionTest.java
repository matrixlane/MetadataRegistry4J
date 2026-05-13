package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class ConnectionEncryptionTest {

    private static final String MASTER_KEY = "test-master-key-for-encryption-32bytes!";

    @Test
    void shouldEncryptAndDecrypt() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        String plaintext = "my-secret-password-123";

        String encrypted = encryption.encrypt(plaintext);
        String decrypted = encryption.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        String plaintext = "same-text";

        String encrypted1 = encryption.encrypt(plaintext);
        String encrypted2 = encryption.encrypt(plaintext);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryption.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThat(encryption.decrypt(encrypted2)).isEqualTo(plaintext);
    }

    @Test
    void shouldHandleNullEncryption() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        assertThat(encryption.encrypt(null)).isNull();
    }

    @Test
    void shouldHandleNullDecryption() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        assertThat(encryption.decrypt(null)).isNull();
    }

    @Test
    void shouldRejectBlankMasterKey() {
        assertThatThrownBy(() -> new ConnectionEncryption(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Master key must not be blank");

        assertThatThrownBy(() -> new ConnectionEncryption(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Master key must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "a-very-long-password-with-special-chars-!@#$%^"})
    void shouldEncryptVariousLengths(String plaintext) {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);

        String encrypted = encryption.encrypt(plaintext);
        String decrypted = encryption.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldFailToDecryptInvalidCiphertext() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        assertThatThrownBy(() -> encryption.decrypt("not-valid-base64!!!"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }
}
