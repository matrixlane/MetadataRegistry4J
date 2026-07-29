package io.github.matrixlane.metadataregistry.web.service;

import java.util.Locale;

/**
 * Resolves localized display names for language identifiers and geopolitical
 * territory identifiers using the JDK {@link Locale} API. Falls back to the
 * raw code when no display name can be resolved.
 */
public final class LanguageDisplayNames {

    private LanguageDisplayNames() {
    }

    public static String languageName(String languageIdentifier, Locale displayLocale) {
        if (languageIdentifier == null || languageIdentifier.isBlank()) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(languageIdentifier);
        String name = locale.getDisplayLanguage(displayLocale);
        if (name == null || name.isBlank() || name.equalsIgnoreCase(languageIdentifier)) {
            return languageIdentifier;
        }
        return name;
    }

    public static String territoryName(String territoryIdentifier, Locale displayLocale) {
        if (territoryIdentifier == null || territoryIdentifier.isBlank()) {
            return null;
        }
        Locale locale = new Locale.Builder().setRegion(territoryIdentifier).build();
        String name = locale.getDisplayCountry(displayLocale);
        if (name == null || name.isBlank() || name.equalsIgnoreCase(territoryIdentifier)) {
            return territoryIdentifier;
        }
        return name;
    }
}
