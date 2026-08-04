package io.github.matrixlane.metadataregistry.web.service.identifier;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierInputMode;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersioningPolicy;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierInputPolicyServiceTest {

    @Mock
    private NamespaceIdentifierPolicyResolver policyResolver;

    @Mock
    private IdentifierStrategyConfigService identifierStrategyConfigService;

    @InjectMocks
    private IdentifierInputPolicyService service;

    private Namespace namespace;

    @BeforeEach
    void setUp() {
        namespace = new Namespace();
    }

    private void stubPolicy(IdentifierInputMode mode, IdentifierInputMode versionMode,
                            VersioningPolicy versioning, String pattern) {
        when(policyResolver.resolve(any())).thenReturn(
                new NamespaceIdentifierPolicy(mode, versionMode, versioning, pattern));
    }

    // ── 标识符输入模式裁决 ─────────────────────────────────────────────

    @Test
    void autoOnly_rejectsManualIdentifier() {
        stubPolicy(IdentifierInputMode.AUTO_ONLY, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);

        assertThatThrownBy(() -> service.resolveIdentifier(namespace, "MANUAL-1", "DataElement"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AUTO_ONLY");
    }

    @Test
    void autoOnly_generatesWhenBlank() {
        stubPolicy(IdentifierInputMode.AUTO_ONLY, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);
        when(identifierStrategyConfigService.generateIdentifier(eq(namespace), any()))
                .thenReturn("GEN-1");

        assertThat(service.resolveIdentifier(namespace, null, "DataElement")).isEqualTo("GEN-1");
    }

    @Test
    void manualOnly_rejectsBlankIdentifier() {
        stubPolicy(IdentifierInputMode.MANUAL_ONLY, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);

        assertThatThrownBy(() -> service.resolveIdentifier(namespace, "  ", "DataElement"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MANUAL_ONLY");
        verify(identifierStrategyConfigService, never()).generateIdentifier(any(), any());
    }

    @Test
    void manualOnly_acceptsManualIdentifier() {
        stubPolicy(IdentifierInputMode.MANUAL_ONLY, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);

        assertThat(service.resolveIdentifier(namespace, "EXT-001", "DataElement")).isEqualTo("EXT-001");
    }

    @Test
    void autoWithOverride_generatesWhenBlank() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);
        when(identifierStrategyConfigService.generateIdentifier(eq(namespace), any()))
                .thenReturn("GEN-2");

        assertThat(service.resolveIdentifier(namespace, "", "DataElement")).isEqualTo("GEN-2");
    }

    @Test
    void autoWithOverride_acceptsManualIdentifier() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);

        assertThat(service.resolveIdentifier(namespace, "MANUAL-9", "DataElement")).isEqualTo("MANUAL-9");
        verify(identifierStrategyConfigService, never()).generateIdentifier(any(), any());
    }

    // ── 手动标识正则校验 ───────────────────────────────────────────────

    @Test
    void manualIdentifier_failingPattern_rejected() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, "^[A-Z0-9_-]+$");

        assertThatThrownBy(() -> service.resolveIdentifier(namespace, "de 001", "DataElement"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("校验规则");
    }

    @Test
    void manualIdentifier_passingPattern_accepted() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, "^[A-Z0-9_-]+$");

        assertThat(service.resolveIdentifier(namespace, "DE-001", "DataElement")).isEqualTo("DE-001");
    }

    @Test
    void manualIdentifier_invalidPattern_skipsValidation() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, "([unclosed");

        assertThat(service.resolveIdentifier(namespace, "anything goes", "DataElement"))
                .isEqualTo("anything goes");
    }

    // ── 版本输入模式裁决 ───────────────────────────────────────────────

    @Test
    void version_blank_generatesSemverInitial() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);

        assertThat(service.resolveVersion(namespace, null)).isEqualTo("1.0.0");
    }

    @Test
    void version_blank_generatesSequentialInitial() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_ONLY,
                VersioningPolicy.SEQUENTIAL, null);

        assertThat(service.resolveVersion(namespace, "")).isEqualTo("1");
    }

    @Test
    void version_blank_generatesDateInitial() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.DATE, null);

        assertThat(service.resolveVersion(namespace, null)).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void version_manualOnly_rejectsBlank() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.MANUAL_ONLY,
                VersioningPolicy.SEMVER, null);

        assertThatThrownBy(() -> service.resolveVersion(namespace, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必填");
    }

    @Test
    void version_autoOnly_rejectsManual() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_ONLY,
                VersioningPolicy.SEMVER, null);

        assertThatThrownBy(() -> service.resolveVersion(namespace, "2.0.0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AUTO_ONLY");
        assertThatThrownBy(() -> service.validateManualVersion(namespace, "2.0.0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AUTO_ONLY");
    }

    @Test
    void version_semver_rejectsNonSemver() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEMVER, null);

        assertThatThrownBy(() -> service.resolveVersion(namespace, "v2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("语义化版本");
    }

    @Test
    void version_sequential_rejectsNonInteger() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.SEQUENTIAL, null);

        assertThat(service.resolveVersion(namespace, "42")).isEqualTo("42");
        assertThatThrownBy(() -> service.resolveVersion(namespace, "1.0.0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("顺序整数");
    }

    @Test
    void version_date_rejectsInvalidDate() {
        stubPolicy(IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
                VersioningPolicy.DATE, null);

        assertThat(service.resolveVersion(namespace, "2026-01-31")).isEqualTo("2026-01-31");
        assertThatThrownBy(() -> service.resolveVersion(namespace, "2026-13-45"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("日期版本");
    }
}
