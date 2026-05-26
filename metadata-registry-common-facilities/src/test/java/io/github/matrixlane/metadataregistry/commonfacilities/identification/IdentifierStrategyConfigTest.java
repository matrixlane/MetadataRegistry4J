package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierStrategyConfigTest {

    @Test
    void shouldCreateWithDefaults() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);

        assertThat(config.getStrategyType()).isEqualTo("UUID");
        assertThat(config.getIdentifierType()).isEqualTo(IdentifierType.INTERNAL);
        assertThat(config.getVersion()).isEqualTo(1);
        assertThat(config.getStatus()).isEqualTo(IdentifierStrategyConfig.StrategyStatus.DRAFT);
        assertThat(config.getIsGlobalDefault()).isFalse();
        assertThat(config.getSequenceScope()).isEqualTo("PER_NAMESPACE_TYPE");
        assertThat(config.getEffectiveFrom()).isNotNull();
        assertThat(config.getEffectiveUntil()).isNull();
    }

    @Test
    void shouldPublish() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);

        assertThat(config.getStatus()).isEqualTo(IdentifierStrategyConfig.StrategyStatus.DRAFT);
        assertThat(config.isEffective()).isFalse();

        config.publish();

        assertThat(config.getStatus()).isEqualTo(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        assertThat(config.isEffective()).isTrue();
    }

    @Test
    void shouldDisable() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);
        config.publish();

        assertThat(config.isEffective()).isTrue();

        config.disable();

        assertThat(config.getStatus()).isEqualTo(IdentifierStrategyConfig.StrategyStatus.DISABLED);
        assertThat(config.isEffective()).isFalse();
        assertThat(config.getEffectiveUntil()).isNotNull();
    }

    @Test
    void shouldNotBeEffectiveWhenNotPublished() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);
        assertThat(config.isEffective()).isFalse();
    }

    @Test
    void shouldSetNamespace() {
        Namespace ns = new Namespace();
        ns.setShorthandPrefix("TEST");

        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);
        config.setNamespace(ns);

        assertThat(config.getNamespace()).isEqualTo(ns);
    }

    @Test
    void shouldSetConfigJson() {
        String json = "{\"prefix\":\"DE\",\"start\":1,\"pad\":6}";
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("SEQUENCE", IdentifierType.INTERNAL);
        config.setConfigJson(json);

        assertThat(config.getConfigJson()).isEqualTo(json);
    }

    @Test
    void shouldTrackVersion() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);
        assertThat(config.getVersion()).isEqualTo(1);

        config.setVersion(2);
        assertThat(config.getVersion()).isEqualTo(2);
    }

    @Test
    void shouldSetAuditFields() {
        LocalDateTime now = LocalDateTime.now();
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);
        config.setCreatedBy("admin");
        config.setCreatedAt(now);
        config.setModifiedBy("admin2");
        config.setModifiedAt(now.plusHours(1));

        assertThat(config.getCreatedBy()).isEqualTo("admin");
        assertThat(config.getCreatedAt()).isEqualTo(now);
        assertThat(config.getModifiedBy()).isEqualTo("admin2");
        assertThat(config.getModifiedAt()).isEqualTo(now.plusHours(1));
    }

    @Test
    void shouldSetGlobalDefaultFlag() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL);
        assertThat(config.getIsGlobalDefault()).isFalse();

        config.setIsGlobalDefault(true);
        assertThat(config.getIsGlobalDefault()).isTrue();
    }

    @Test
    void shouldSetSequenceScope() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("SEQUENCE", IdentifierType.INTERNAL);
        assertThat(config.getSequenceScope()).isEqualTo("PER_NAMESPACE_TYPE");

        config.setSequenceScope("PER_NAMESPACE");
        assertThat(config.getSequenceScope()).isEqualTo("PER_NAMESPACE");
    }
}
