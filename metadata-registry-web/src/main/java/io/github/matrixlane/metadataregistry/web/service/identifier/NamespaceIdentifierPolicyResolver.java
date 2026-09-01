package io.github.matrixlane.metadataregistry.web.service.identifier;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierInputMode;
import io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.VersioningPolicy;
import io.github.matrixlane.metadataregistry.web.service.NamespaceIdentifierSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads a Namespace's identifier governance policy from its satellite entity
 * {@link NamespaceIdentifierSettings} (table {@code namespace_identifier_settings};
 * formerly columns on {@code namespace}).
 *
 * <p>Unparseable values fall back to defaults with a warning — the columns are
 * weakly-typed strings and must tolerate dirty data.
 */
@Slf4j
@Component
public class NamespaceIdentifierPolicyResolver {

    private final NamespaceIdentifierSettingsService settingsService;

    public NamespaceIdentifierPolicyResolver(NamespaceIdentifierSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Resolves the effective policy of the given namespace. A namespace
     * without explicit policy settings yields {@link NamespaceIdentifierPolicy#DEFAULT}.
     */
    public NamespaceIdentifierPolicy resolve(Namespace namespace) {
        if (namespace == null) {
            return NamespaceIdentifierPolicy.DEFAULT;
        }
        NamespaceIdentifierSettings settings = settingsService.getOrDefault(namespace.getId());
        IdentifierInputMode mode = parseEnum(
                IdentifierInputMode.class, settings.getIdentifierInputMode(),
                IdentifierInputMode.AUTO_WITH_OVERRIDE, namespace);
        IdentifierInputMode versionMode = parseEnum(
                IdentifierInputMode.class, settings.getVersionInputMode(),
                IdentifierInputMode.AUTO_WITH_OVERRIDE, namespace);
        VersioningPolicy versioning = parseEnum(
                VersioningPolicy.class, settings.getVersioningPolicy(),
                VersioningPolicy.SEMVER, namespace);
        String pattern = settings.getManualIdentifierPattern();
        if (pattern != null && pattern.isBlank()) {
            pattern = null;
        }
        return new NamespaceIdentifierPolicy(mode, versionMode, versioning, pattern);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback, Namespace ns) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[NamespaceIdentifierPolicyResolver] Unparseable value '{}' for namespace {}, falling back to {}",
                    raw, ns.getId(), fallback);
            return fallback;
        }
    }
}
