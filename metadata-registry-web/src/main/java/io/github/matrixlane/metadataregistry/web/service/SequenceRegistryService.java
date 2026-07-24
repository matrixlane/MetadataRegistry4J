package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.SequenceProvider;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.SequenceRegistry;
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
    public long getNextValue(Namespace namespace, IdentifierType identifierType, 
                             long start, int increment, int pad, String prefix) {
        if (namespace == null || namespace.getId() == null) {
            throw new IllegalArgumentException("Namespace must not be null and must have an ID for sequence generation.");
        }

        SequenceRegistry registry = repository.findByNamespaceIdAndIdentifierTypeWithLock(
                namespace.getId(), identifierType)
                .orElseGet(() -> {
                    SequenceRegistry r = new SequenceRegistry(namespace, identifierType);
                    r.setStartValue(start);
                    r.setCurrentValue(start - increment);
                    r.setIncrementBy(increment);
                    r.setPadLength(pad);
                    r.setPrefix(prefix);
                    r.setCreatedAt(LocalDateTime.now());
                    r.setLastAllocatedAt(LocalDateTime.now());
                    return repository.save(r);
                });

        long nextVal = registry.peekNext();
        registry.setCurrentValue(nextVal);
        registry.setLastAllocatedAt(LocalDateTime.now());
        repository.save(registry);
        return nextVal;
    }
}
