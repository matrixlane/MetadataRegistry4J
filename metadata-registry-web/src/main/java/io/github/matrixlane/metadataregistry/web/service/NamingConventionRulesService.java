package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.designationanddefinition.NamingConventionRules;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRulesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * NamingConventionRules 卫星实体的读写服务。缺失行语义等同 rulesJson=null。
 */
@Service
@RequiredArgsConstructor
public class NamingConventionRulesService {

    private final NamingConventionRulesRepository rulesRepository;

    @Transactional(readOnly = true)
    public NamingConventionRules getOrDefault(UUID namingConventionId) {
        if (namingConventionId == null) {
            return new NamingConventionRules();
        }
        return rulesRepository.findByNamingConventionId(namingConventionId)
                .orElseGet(NamingConventionRules::new);
    }

    @Transactional
    public NamingConventionRules mutate(UUID namingConventionId, Consumer<NamingConventionRules> mutator) {
        NamingConventionRules rules = rulesRepository.findByNamingConventionId(namingConventionId)
                .orElseGet(NamingConventionRules::new);
        mutator.accept(rules);
        rules.setNamingConventionId(namingConventionId);
        return rulesRepository.save(rules);
    }

    /** 有文本则 upsert;空值则清除既有行内容(不新建空行)。 */
    @Transactional
    public void applyRules(UUID namingConventionId, String rulesJson) {
        if (StringUtils.hasText(rulesJson)) {
            mutate(namingConventionId, r -> r.setRulesJson(rulesJson));
        } else {
            rulesRepository.findByNamingConventionId(namingConventionId).ifPresent(r -> {
                r.setRulesJson(null);
                rulesRepository.save(r);
            });
        }
    }
}
