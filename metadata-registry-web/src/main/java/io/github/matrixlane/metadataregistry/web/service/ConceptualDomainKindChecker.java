package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;

import java.util.UUID;

/**
 * 概念域类型校验工具：在引用枚举值域指向概念域时校验目标实例是 ReferenceEnumeratedConceptualDomain。
 */
final class ConceptualDomainKindChecker {

    private ConceptualDomainKindChecker() {
    }

    /**
     * 校验 conceptualDomainRepository 中指定 ID 的概念域是 ReferenceEnumeratedConceptualDomain 实例。
     * 找不到 / 类型不符均抛 BusinessException。
     */
    static void requireReferenceEnumeratedConceptualDomain(ConceptualDomainRepository repository, UUID conceptualDomainId) {
        ConceptualDomain cd = repository.findById(conceptualDomainId)
                .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", conceptualDomainId));
        if (!(cd instanceof ReferenceEnumeratedConceptualDomain)) {
            throw new BusinessException(
                    "referencedConceptualDomainId must point to ReferenceEnumeratedConceptualDomain, but was "
                            + cd.getClass().getSimpleName());
        }
    }
}