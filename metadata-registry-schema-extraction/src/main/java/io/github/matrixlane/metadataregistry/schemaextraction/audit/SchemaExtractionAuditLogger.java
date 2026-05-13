package io.github.matrixlane.metadataregistry.schemaextraction.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Audit logger for schema extraction operations.
 * Supports both SLF4J logging and custom event handlers.
 */
public class SchemaExtractionAuditLogger {

    private final List<Consumer<SchemaExtractionAuditEvent>> handlers = new ArrayList<>();

    public void addHandler(Consumer<SchemaExtractionAuditEvent> handler) {
        handlers.add(handler);
    }

    public void log(SchemaExtractionAuditEvent event) {
        for (Consumer<SchemaExtractionAuditEvent> handler : handlers) {
            handler.accept(event);
        }
    }
}
