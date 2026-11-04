package io.github.matrixlane.metadataregistry.web.startup;

import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapFailure;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapProgress;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapStage;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StartupReadinessIndicatorTest {

    private StartupReadinessIndicator indicator(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        if (properties != null) {
            environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        }
        return new StartupReadinessIndicator(environment, new BootstrapProgress());
    }

    @Test
    void initialStateShouldBeNotReady() {
        StartupReadinessIndicator indicator = indicator(null);
        assertThat(indicator.isReady()).isFalse();
    }

    @Test
    void applicationReadyEventShouldSetReady() {
        StartupReadinessIndicator indicator = indicator(null);
        indicator.onApplicationReady();
        assertThat(indicator.isReady()).isTrue();
        assertThat(indicator.failure()).isNull();
    }

    @Test
    void readyStateShouldNotRegress() {
        StartupReadinessIndicator indicator = indicator(null);
        indicator.onApplicationReady();
        indicator.onApplicationReady();
        assertThat(indicator.isReady()).isTrue();
    }

    @Test
    void datasourceNotReadyIsReadyOnceContextIsUp() {
        StartupReadinessIndicator indicator = indicator(Map.of(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, false));
        indicator.onApplicationReady();
        assertThat(indicator.isReady()).isTrue();
        assertThat(indicator.failure()).isNull();
    }

    @Test
    void datasourceReadyWithoutBootstrapIsNotReadyAndExposesFailure() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, true)));
        BootstrapProgress progress = new BootstrapProgress();
        StartupReadinessIndicator indicator = new StartupReadinessIndicator(environment, progress);
        indicator.onApplicationReady();

        assertThat(indicator.isReady()).isFalse();
        assertThat(indicator.failure()).isNotNull();
        assertThat(indicator.failure().code()).isEqualTo("NOT_STARTED");

        progress.record(io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapOutcome.failed(
                new BootstrapFailure(BootstrapStage.PRESET, "PRESET_FAILED", "静态数据预置：no such table")));
        assertThat(indicator.isReady()).isFalse();
        assertThat(indicator.failure().stage()).isEqualTo(BootstrapStage.PRESET);

        progress.record(io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapOutcome.success());
        assertThat(indicator.isReady()).isTrue();
        assertThat(indicator.failure()).isNull();
    }
}
