package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierMetadataTest {

    @Test
    void shouldExposeExpectedIdentifierInputModes() {
        assertThat(IdentifierInputMode.values())
                .containsExactly(
                        IdentifierInputMode.AUTO_ONLY,
                        IdentifierInputMode.AUTO_WITH_OVERRIDE,
                        IdentifierInputMode.MANUAL_ONLY);
    }

    @Test
    void shouldExposeExpectedVersioningPolicies() {
        assertThat(VersioningPolicy.values())
                .containsExactly(
                        VersioningPolicy.SEMVER,
                        VersioningPolicy.SEQUENTIAL,
                        VersioningPolicy.DATE,
                        VersioningPolicy.FREE_TEXT);
    }

    @Test
    void shouldExposeExpectedCreationModes() {
        assertThat(IdentifierCreationMode.values())
                .containsExactly(
                        IdentifierCreationMode.AUTO,
                        IdentifierCreationMode.MANUAL);
    }

    @Test
    void shouldExposeExpectedVersionStatuses() {
        assertThat(VersionStatus.values())
                .containsExactly(
                        VersionStatus.DRAFT,
                        VersionStatus.PUBLISHED,
                        VersionStatus.DEPRECATED,
                        VersionStatus.OBSOLETE);
    }

    @Test
    void shouldRecordVersionHistoryMetadata() {
        ScopedIdentifier scopedIdentifier = new ScopedIdentifier("SI-001", null, null);

        IdentifierVersionHistory history = new IdentifierVersionHistory(
                scopedIdentifier,
                "2.0.0",
                VersionStatus.PUBLISHED,
                "qa-bot",
                "released for validation");

        assertThat(history.getScopedIdentifier()).isSameAs(scopedIdentifier);
        assertThat(history.getVersion()).isEqualTo("2.0.0");
        assertThat(history.getVersionStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(history.getOperator()).isEqualTo("qa-bot");
        assertThat(history.getChangeSummary()).isEqualTo("released for validation");
        assertThat(history.getTimestamp()).isNotNull();
        assertThat(history.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }
}
