package io.github.matrixlane.metadataregistry.ext.identification.segment;

import io.github.matrixlane.metadataregistry.ext.identification.segment.types.ChecksumSegmentHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.types.LiteralSegmentHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.types.ObjectAbbreviationSegmentHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.types.SeparatorSegmentHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.types.SequenceSegmentHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.types.TimestampSegmentHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.types.UuidSegmentHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.types.VariableSegmentHandler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extensible registry of segment type handlers. Built-in handlers cover the
 * spec's segment catalog; additional types are plugged in via
 * {@link #register(SegmentTypeHandler)} without touching the engine core.
 */
public class SegmentTypeRegistry {

    private static volatile SegmentTypeRegistry INSTANCE;

    private final Map<SegmentType, SegmentTypeHandler> handlers = new ConcurrentHashMap<>();

    private SegmentTypeRegistry() {
        register(new LiteralSegmentHandler());
        register(new ObjectAbbreviationSegmentHandler());
        register(new SequenceSegmentHandler());
        register(new UuidSegmentHandler());
        register(new SeparatorSegmentHandler());
        register(new TimestampSegmentHandler());
        register(new ChecksumSegmentHandler());
        register(new VariableSegmentHandler());
    }

    public static SegmentTypeRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (SegmentTypeRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SegmentTypeRegistry();
                }
            }
        }
        return INSTANCE;
    }

    /** Registers (or replaces) the handler for a segment type. */
    public void register(SegmentTypeHandler handler) {
        if (handler == null || handler.type() == null) {
            throw new IllegalArgumentException("Handler and its type must not be null");
        }
        handlers.put(handler.type(), handler);
    }

    /** Returns the handler for a type, never null. */
    public SegmentTypeHandler handlerOf(SegmentType type) {
        SegmentTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new SegmentTemplateException("No handler registered for segment type: " + type);
        }
        return handler;
    }

    /** All registered handlers, in catalog order. */
    public Collection<SegmentTypeHandler> handlers() {
        Map<SegmentType, SegmentTypeHandler> ordered = new LinkedHashMap<>();
        for (SegmentType type : SegmentType.values()) {
            SegmentTypeHandler h = handlers.get(type);
            if (h != null) {
                ordered.put(type, h);
            }
        }
        return ordered.values();
    }
}
