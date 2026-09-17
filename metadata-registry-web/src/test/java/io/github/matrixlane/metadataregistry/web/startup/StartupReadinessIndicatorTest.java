package io.github.matrixlane.metadataregistry.web.startup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StartupReadinessIndicatorTest {

    @Test
    void initialStateShouldBeNotReady() {
        StartupReadinessIndicator indicator = new StartupReadinessIndicator();
        assertThat(indicator.isReady()).isFalse();
    }

    @Test
    void applicationReadyEventShouldSetReady() {
        StartupReadinessIndicator indicator = new StartupReadinessIndicator();
        indicator.onApplicationReady();
        assertThat(indicator.isReady()).isTrue();
    }

    @Test
    void readyStateShouldNotRegress() {
        StartupReadinessIndicator indicator = new StartupReadinessIndicator();
        indicator.onApplicationReady();
        indicator.onApplicationReady();
        assertThat(indicator.isReady()).isTrue();
    }
}
