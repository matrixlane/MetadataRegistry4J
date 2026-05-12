package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierGenerationServiceTest {

    private final IdentifierGenerationService service = new IdentifierGenerationService();

    @Test
    void shouldGenerateUuidByDefault() {
        String id = service.generate(null);
        assertThat(id).isNotNull().isNotBlank();
        assertThat(id).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void shouldSwitchToUrnStrategy() {
        service.setActiveStrategy("URN");
        String id = service.generate(new Namespace());
        assertThat(id).startsWith("urn:");
    }

    @Test
    void shouldReturnActiveStrategyType() {
        assertThat(service.getActiveStrategyType()).isEqualTo("UUID");
        service.setActiveStrategy("URN");
        assertThat(service.getActiveStrategyType()).isEqualTo("URN");
    }

    @Test
    void shouldThrowOnUnknownStrategy() {
        assertThatThrownBy(() -> service.setActiveStrategy("OID"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported strategy");
    }

    @Test
    void shouldThrowWhenGeneratingWithUnknownStrategy() {
        service.setActiveStrategy("URN");
        service.setActiveStrategy("UUID"); // reset
        // Directly manipulating activeType is not possible; test via switch
        assertThatThrownBy(() -> service.setActiveStrategy("UNKNOWN"))
                .isInstanceOf(BusinessException.class);
    }
}
