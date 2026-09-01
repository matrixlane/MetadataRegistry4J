package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SequenceOverflowException;
import io.github.matrixlane.metadataregistry.ext.identification.SequenceProvider;
import io.github.matrixlane.metadataregistry.ext.identification.SequenceRegistry;
import io.github.matrixlane.metadataregistry.web.repository.SequenceRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class SequenceRegistryService implements SequenceProvider {

    private final SequenceRegistryRepository repository;

    @Override
    public long getNextValue(Namespace namespace,
                             long start, int increment, int pad, String prefix) {
        return allocateScoped(namespace, SequenceRegistry.GLOBAL_SCOPE_KEY, start, increment, pad, prefix);
    }

    @Override
    public long getNextValue(Namespace namespace, String scopeKey,
                             long start, int increment, int pad) {
        return allocateScoped(namespace, scopeKey, start, increment, pad, null);
    }

    private long allocateScoped(Namespace namespace, String scopeKey,
                                long start, int increment, int pad, String prefix) {
        if (namespace == null || namespace.getId() == null) {
            throw new IllegalArgumentException("Namespace must not be null and must have an ID for sequence generation.");
        }
        String effectiveScope = normalizeScope(scopeKey);

        SequenceRegistry registry = repository.findByNamespaceIdAndScopeKeyWithLock(namespace.getId(), effectiveScope)
                .orElseGet(() -> {
                    SequenceRegistry r = new SequenceRegistry(namespace, effectiveScope);
                    r.setStartValue(start);
                    r.setCurrentValue(start - increment);
                    r.setIncrementBy(increment);
                    r.setPadLength(pad);
                    if (prefix != null) {
                        r.setPrefix(prefix);
                    }
                    r.setCreatedAt(LocalDateTime.now());
                    r.setLastAllocatedAt(LocalDateTime.now());
                    return repository.save(r);
                });

        long nextVal = registry.peekNext();
        if (registry.wouldOverflow(registry.getMaxValue())) {
            throw new SequenceOverflowException(
                    "Sequence overflow in namespace " + namespace.getId() + " scope '" + effectiveScope
                            + "': next value " + nextVal + " exceeds pad ceiling " + registry.getMaxValue()
                            + "; adjust the overflow configuration (larger pad or new scope)");
        }
        registry.setCurrentValue(nextVal);
        registry.setLastAllocatedAt(LocalDateTime.now());
        repository.save(registry);
        return nextVal;
    }

    /**
     * Aligns a scope's counter to at least the given value (sequence
     * backfill): after importing existing identifiers, subsequent generation
     * must continue above the highest imported number. Never moves a counter
     * backwards.
     */
    public void alignCounter(Namespace namespace, String scopeKey, long usedValue,
                             long start, int increment, int pad) {
        if (namespace == null || namespace.getId() == null) {
            throw new IllegalArgumentException("Namespace must not be null and must have an ID for backfill.");
        }
        String effectiveScope = normalizeScope(scopeKey);
        SequenceRegistry registry = repository.findByNamespaceIdAndScopeKeyWithLock(namespace.getId(), effectiveScope)
                .orElseGet(() -> {
                    SequenceRegistry r = new SequenceRegistry(namespace, effectiveScope);
                    r.setStartValue(start);
                    r.setCurrentValue(start - increment);
                    r.setIncrementBy(increment);
                    r.setPadLength(pad);
                    r.setCreatedAt(LocalDateTime.now());
                    r.setLastAllocatedAt(LocalDateTime.now());
                    return repository.save(r);
                });
        if (usedValue > registry.getCurrentValue()) {
            registry.setCurrentValue(usedValue);
            registry.setLastAllocatedAt(LocalDateTime.now());
            repository.save(registry);
        }
    }

    private static String normalizeScope(String scopeKey) {
        return scopeKey != null && !scopeKey.isBlank() ? scopeKey : SequenceRegistry.GLOBAL_SCOPE_KEY;
    }
}
