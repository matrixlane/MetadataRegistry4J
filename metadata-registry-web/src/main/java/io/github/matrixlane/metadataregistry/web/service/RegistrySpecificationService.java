package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrySpecificationService {

    private final RegistrySpecificationRepository repository;
    private final AdministeredItemRepository administeredItemRepository;

    @Transactional(readOnly = true)
    public RegistrySpecification getSpecification() {
        return repository.findAll().stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean exists() {
        return repository.count() > 0;
    }

    @Transactional(readOnly = true)
    public boolean hasMetadata() {
        return administeredItemRepository.count() > 0;
    }

    public RegistrySpecification create(RegistrySpecification spec) {
        if (exists()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册规范已存在，不能重复创建");
        }
        return repository.save(spec);
    }

    public RegistrySpecification update(UUID id, RegistrySpecification newSpec) {
        RegistrySpecification existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegistrySpecification", id));

        if (hasMetadata()) {
            // Check critical fields
            if (!Objects.equals(existing.getIdentifierPrefix(), newSpec.getIdentifierPrefix())
                    || !Objects.equals(existing.getAutoGenerateIdentifier(), newSpec.getAutoGenerateIdentifier())
                    || !Objects.equals(existing.getIdentifierPattern(), newSpec.getIdentifierPattern())
                    || !Objects.equals(existing.getVersioningPolicy(), newSpec.getVersioningPolicy())
                    || !Objects.equals(existing.getMandatoryClassification(), newSpec.getMandatoryClassification())
                    || !Objects.equals(existing.getDefaultLanguage(), newSpec.getDefaultLanguage())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "已有元数据，禁止修改注册规范关键配置字段");
            }
        }

        existing.setRegistryName(newSpec.getRegistryName());
        existing.setDescription(newSpec.getDescription());
        
        // If no metadata, update critical fields as well
        if (!hasMetadata()) {
            existing.setIdentifierPrefix(newSpec.getIdentifierPrefix());
            existing.setAutoGenerateIdentifier(newSpec.getAutoGenerateIdentifier());
            existing.setIdentifierPattern(newSpec.getIdentifierPattern());
            existing.setVersioningPolicy(newSpec.getVersioningPolicy());
            existing.setMandatoryClassification(newSpec.getMandatoryClassification());
            existing.setDefaultLanguage(newSpec.getDefaultLanguage());
        }

        return repository.save(existing);
    }
}
