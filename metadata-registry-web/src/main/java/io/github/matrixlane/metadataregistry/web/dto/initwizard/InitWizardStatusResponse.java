package io.github.matrixlane.metadataregistry.web.dto.initwizard;

public record InitWizardStatusResponse(
    boolean initialized,
    String runningMode,
    String edition
) {}
