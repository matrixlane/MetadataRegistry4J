package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance;
import io.github.matrixlane.metadataregistry.web.repository.LanguageGovernanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LanguageGovernance 卫星实体的读写服务。缺失行语义等同默认值(enabled=true)。
 */
@Service
@RequiredArgsConstructor
public class LanguageGovernanceService {

    private final LanguageGovernanceRepository governanceRepository;

    @Transactional(readOnly = true)
    public LanguageGovernance getOrDefault(UUID languageId) {
        if (languageId == null) {
            return new LanguageGovernance();
        }
        return governanceRepository.findByLanguageId(languageId)
                .orElseGet(LanguageGovernance::new);
    }

    @Transactional(readOnly = true)
    public Map<UUID, LanguageGovernance> mapByLanguageIds(Collection<UUID> languageIds) {
        return governanceRepository.findByLanguageIdIn(languageIds).stream()
                .collect(Collectors.toMap(LanguageGovernance::getLanguageId, g -> g));
    }

    /** 显式禁用的语言 id 集合(缺失行等同启用,不在此列)。 */
    @Transactional(readOnly = true)
    public Set<UUID> disabledLanguageIds() {
        return governanceRepository.findByEnabledFalse().stream()
                .map(LanguageGovernance::getLanguageId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public LanguageGovernance save(LanguageGovernance governance) {
        return governanceRepository.save(governance);
    }

    @Transactional
    public LanguageGovernance mutate(UUID languageId, Consumer<LanguageGovernance> mutator) {
        LanguageGovernance governance = governanceRepository.findByLanguageId(languageId)
                .orElseGet(LanguageGovernance::new);
        mutator.accept(governance);
        governance.setLanguageId(languageId);
        return governanceRepository.save(governance);
    }
}
