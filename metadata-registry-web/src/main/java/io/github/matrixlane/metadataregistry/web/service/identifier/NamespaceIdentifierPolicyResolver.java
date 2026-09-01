package io.github.matrixlane.metadataregistry.web.service.identifier;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierInputMode;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.VersioningPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads a Namespace's identifier governance policy from its persisted columns
 * ({@code identifier_input_mode}, {@code version_input_mode},
 * {@code versioning_policy}, {@code manual_identifier_pattern}).
 *
 * <p>Unparseable values fall back to defaults with a warning — the columns are
 * weakly-typed strings and must tolerate dirty data.
 */
@Slf4j
@Component
public class NamespaceIdentifierPolicyResolver {

    /**
     * Resolves the effective policy of the given namespace. A namespace
     * without explicit policy settings yields {@link NamespaceIdentifierPolicy#DEFAULT}.
     */
    public NamespaceIdentifierPolicy resolve(Namespace namespace) {
        if (namespace == null) {
            return NamespaceIdentifierPolicy.DEFAULT;
        }
        IdentifierInputMode mode = parseEnum(
                IdentifierInputMode.class, namespace.getIdentifierInputMode(),
                IdentifierInputMode.AUTO_WITH_OVERRIDE, namespace);
        IdentifierInputMode versionMode = parseEnum(
                IdentifierInputMode.class, namespace.getVersionInputMode(),
                IdentifierInputMode.AUTO_WITH_OVERRIDE, namespace);
        VersioningPolicy versioning = parseEnum(
                VersioningPolicy.class, namespace.getVersioningPolicy(),
                VersioningPolicy.SEMVER, namespace);
        String pattern = namespace.getManualIdentifierPattern();
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
