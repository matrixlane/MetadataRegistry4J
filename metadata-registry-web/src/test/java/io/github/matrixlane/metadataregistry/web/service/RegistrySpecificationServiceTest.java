package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrySpecificationServiceTest {

    @Mock
    private RegistrySpecificationRepository repository;

    @Mock
    private AdministeredItemRepository administeredItemRepository;

    @InjectMocks
    private RegistrySpecificationService service;

    private RegistrySpecification spec;
    private UUID specId;

    @BeforeEach
    void setUp() {
        specId = UUID.randomUUID();
        spec = new RegistrySpecification();
        spec.setId(specId);
        spec.setRegistryName("测试注册规范");
        spec.setIdentifierPrefix("TEST");
        spec.setAutoGenerateIdentifier(true);
        spec.setIdentifierPattern("^[A-Z]+$");
        spec.setVersioningPolicy("SEMVER");
        spec.setMandatoryClassification(true);
        spec.setDefaultLanguage("zh-CN");
        spec.setDescription("描述");
    }

    @Test
    @DisplayName("创建规范 - 成功")
    void createSpecification_success() {
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(RegistrySpecification.class))).thenReturn(spec);

        RegistrySpecification result = service.create(spec);

        assertThat(result).isNotNull();
        assertThat(result.getRegistryName()).isEqualTo("测试注册规范");
        verify(repository).save(spec);
    }

    @Test
    @DisplayName("创建规范 - 失败：规范已存在")
    void createSpecification_fail_alreadyExists() {
        when(repository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.create(spec))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("注册规范已存在");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("更新规范 - 没有元数据时允许修改关键字段")
    void updateSpecification_noMetadata_allowsCriticalFields() {
        when(repository.findById(specId)).thenReturn(Optional.of(spec));
        when(administeredItemRepository.count()).thenReturn(0L);
        when(repository.save(any(RegistrySpecification.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrySpecification updatedSpec = new RegistrySpecification();
        updatedSpec.setRegistryName("修改后的规范");
        updatedSpec.setIdentifierPrefix("NEW");
        updatedSpec.setAutoGenerateIdentifier(false);
        updatedSpec.setIdentifierPattern("^[a-z]+$");
        updatedSpec.setVersioningPolicy("INTEGRAL");
        updatedSpec.setMandatoryClassification(false);
        updatedSpec.setDefaultLanguage("en-US");
        updatedSpec.setDescription("新描述");

        RegistrySpecification result = service.update(specId, updatedSpec);

        assertThat(result.getRegistryName()).isEqualTo("修改后的规范");
        assertThat(result.getIdentifierPrefix()).isEqualTo("NEW");
        assertThat(result.getAutoGenerateIdentifier()).isFalse();
        assertThat(result.getIdentifierPattern()).isEqualTo("^[a-z]+$");
        assertThat(result.getVersioningPolicy()).isEqualTo("INTEGRAL");
        assertThat(result.getMandatoryClassification()).isFalse();
        assertThat(result.getDefaultLanguage()).isEqualTo("en-US");
        assertThat(result.getDescription()).isEqualTo("新描述");
    }

    @Test
    @DisplayName("更新规范 - 存在元数据时，允许修改非关键字段")
    void updateSpecification_hasMetadata_allowsNonCriticalFields() {
        when(repository.findById(specId)).thenReturn(Optional.of(spec));
        when(administeredItemRepository.count()).thenReturn(5L);
        when(repository.save(any(RegistrySpecification.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrySpecification updatedSpec = new RegistrySpecification();
        updatedSpec.setRegistryName("修改后的规范");
        // 保持关键属性一致
        updatedSpec.setIdentifierPrefix("TEST");
        updatedSpec.setAutoGenerateIdentifier(true);
        updatedSpec.setIdentifierPattern("^[A-Z]+$");
        updatedSpec.setVersioningPolicy("SEMVER");
        updatedSpec.setMandatoryClassification(true);
        updatedSpec.setDefaultLanguage("zh-CN");
        updatedSpec.setDescription("新描述");

        RegistrySpecification result = service.update(specId, updatedSpec);

        assertThat(result.getRegistryName()).isEqualTo("修改后的规范");
        assertThat(result.getDescription()).isEqualTo("新描述");
    }

    @Test
    @DisplayName("更新规范 - 存在元数据时，禁止修改关键字段：前缀")
    void updateSpecification_hasMetadata_blocksCriticalField_prefix() {
        when(repository.findById(specId)).thenReturn(Optional.of(spec));
        when(administeredItemRepository.count()).thenReturn(5L);

        RegistrySpecification updatedSpec = new RegistrySpecification();
        updatedSpec.setRegistryName("修改后的规范");
        updatedSpec.setIdentifierPrefix("NEW"); // 修改了关键字段
        updatedSpec.setAutoGenerateIdentifier(true);
        updatedSpec.setIdentifierPattern("^[A-Z]+$");
        updatedSpec.setVersioningPolicy("SEMVER");
        updatedSpec.setMandatoryClassification(true);
        updatedSpec.setDefaultLanguage("zh-CN");
        updatedSpec.setDescription("描述");

        assertThatThrownBy(() -> service.update(specId, updatedSpec))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("禁止修改注册规范关键配置字段");

        verify(repository, never()).save(any());
    }
}
