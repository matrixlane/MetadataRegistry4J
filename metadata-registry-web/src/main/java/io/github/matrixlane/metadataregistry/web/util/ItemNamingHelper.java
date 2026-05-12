package io.github.matrixlane.metadataregistry.web.util;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ItemNamingHelper {

    public static final String DEFAULT_LANGUAGE = "zh-CN";
    public static final String FALLBACK_LANGUAGE = "en-US";

    private ItemNamingHelper() {
    }

    public static String preferredDesignation(Item item) {
        return preferredDesignation(item, DEFAULT_LANGUAGE);
    }

    public static String preferredDesignation(Item item, String preferredLang) {
        if (item == null || item.getDesignations() == null || item.getDesignations().isEmpty()) {
            return null;
        }
        return pickByLanguage(item.getDesignations(), Designation::getLanguage, preferredLang)
                .map(d -> d.getSign() != null ? d.getSign().getValue() : null)
                .orElse(null);
    }

    public static String preferredDefinition(Item item) {
        return preferredDefinition(item, DEFAULT_LANGUAGE);
    }

    public static String preferredDefinition(Item item, String preferredLang) {
        if (item == null || item.getDefinitions() == null || item.getDefinitions().isEmpty()) {
            return null;
        }
        return pickByLanguage(item.getDefinitions(), Definition::getLanguage, preferredLang)
                .map(d -> d.getText() != null ? d.getText().getValue() : null)
                .orElse(null);
    }

    public static String firstScopedIdentifier(Item item) {
        IdentifiedItem ii = item == null ? null : item.getIdentifiedItem();
        if (ii == null || ii.getScopedIdentifiers() == null || ii.getScopedIdentifiers().isEmpty()) {
            return null;
        }
        ScopedIdentifier si = ii.getScopedIdentifiers().get(0);
        StringBuilder sb = new StringBuilder();
        if (si.getNamespace() != null) {
            String ns = preferredDesignation(si.getNamespace());
            if (ns != null) sb.append(ns).append(':');
        }
        sb.append(si.getIdentifier() == null ? "" : si.getIdentifier());
        if (si.getVersion() != null && !si.getVersion().isBlank()) {
            sb.append('@').append(si.getVersion());
        }
        return sb.toString();
    }

    public static RegistrationStatus currentRegistrationStatus(Item item) {
        IdentifiedItem ii = item == null ? null : item.getIdentifiedItem();
        if (ii instanceof AdministeredItem ai && ai.getRegistrationState() != null) {
            return ai.getRegistrationState().getRegistrationStatus();
        }
        return null;
    }

    private static <T> Optional<T> pickByLanguage(List<T> items,
                                                  java.util.function.Function<T, LanguageIdentification> langFn,
                                                  String preferredLang) {
        return items.stream()
                .min(Comparator.comparingInt(d -> {
                    LanguageIdentification lang = langFn.apply(d);
                    String code = lang == null ? null : lang.getLanguageIdentifier();
                    if (code == null) return 9;
                    if (code.equalsIgnoreCase(preferredLang)) return 0;
                    if (code.equalsIgnoreCase(FALLBACK_LANGUAGE)) return 1;
                    return 5;
                }));
    }
}
