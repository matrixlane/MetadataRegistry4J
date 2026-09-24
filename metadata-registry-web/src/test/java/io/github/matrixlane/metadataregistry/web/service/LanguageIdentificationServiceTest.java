package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageIdentificationServiceTest {

    @Mock
    private LanguageIdentificationRepository repository;

    @Mock
    private LanguageGovernanceService governanceService;

    @Mock
    private LanguageExtensionService extensionService;

    @InjectMocks
    private LanguageIdentificationService service;

    @org.junit.jupiter.api.BeforeEach
    void stubGovernance() {
        org.mockito.Mockito.lenient().when(governanceService.getOrDefault(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance());
    }

    private LanguageIdentification entity(String languageIdentifier, String territoryIdentifier) {
        LanguageIdentification entity = new LanguageIdentification();
        entity.setLanguageIdentifier(languageIdentifier);
        entity.setScriptIdentifier("Hans");
        entity.setGeopoliticalTerritoryIdentifier(territoryIdentifier);
        return entity;
    }

    @Test
    @DisplayName("扩展行缺失时语言名称与地区名称返回 null(展示为空,不做运行时解析)")
    void returnsNullNamesWhenExtensionMissing() {
        UUID id = UUID.randomUUID();
        LanguageIdentification entity = entity("zh-CN", "CN");
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(extensionService.getOrDefault(id)).thenReturn(null);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isNull();
        assertThat(response.geopoliticalTerritoryName()).isNull();
    }

    @Test
    @DisplayName("扩展行名称为空白时返回 null(展示为空)")
    void returnsNullNamesWhenExtensionNamesBlank() {
        UUID id = UUID.randomUUID();
        LanguageIdentification entity = entity("zh-CN", "CN");
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension extension =
                new io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension(id);
        extension.setLanguageName(" ");
        extension.setTerritoryName(null);
        when(extensionService.getOrDefault(id)).thenReturn(extension);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isNull();
        assertThat(response.geopoliticalTerritoryName()).isNull();
    }

    @Test
    @DisplayName("扩展行持久化名称非空时原样返回")
    void returnsPersistedExtensionNames() {
        UUID id = UUID.randomUUID();
        LanguageIdentification entity = entity("en-US", "US");
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension extension =
                new io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension(id);
        extension.setLanguageName("英语(持久化)");
        extension.setTerritoryName("美国(持久化)");
        when(extensionService.getOrDefault(id)).thenReturn(extension);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isEqualTo("英语(持久化)");
        assertThat(response.geopoliticalTerritoryName()).isEqualTo("美国(持久化)");
    }

    @Test
    @DisplayName("地区标识为空且扩展行缺失时地区名称为 null")
    void returnsNullTerritoryNameWhenTerritoryMissing() {
        UUID id = UUID.randomUUID();
        LanguageIdentification entity = entity("zh-CN", null);
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(extensionService.getOrDefault(id)).thenReturn(null);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isNull();
        assertThat(response.geopoliticalTerritoryName()).isNull();
    }

    @Test
    @DisplayName("create:语言名称/地区名称留空时存 null 并 upsert 扩展行(不按 Locale 回填)")
    void createStoresNullNamesWhenBlank() {
        LanguageIdentification entity = entity("zh-CN", "CN");
        entity.setId(UUID.randomUUID());
        when(repository.existsByLanguageIdentifier("zh-CN")).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any(LanguageIdentification.class))).thenReturn(entity);

        service.create(new io.github.matrixlane.metadataregistry.web.dto.language.LanguageRequest(
                "zh-CN", "Hans", "CN", null, null, null, null, " ", null));

        verify(extensionService).upsert(entity.getId(), null, null);
    }

    @Test
    @DisplayName("create:显式传入语言名称/地区名称时按传入值 upsert 扩展行")
    void createUpsertsProvidedNames() {
        LanguageIdentification entity = entity("fr-FR", "FR");
        entity.setId(UUID.randomUUID());
        when(repository.existsByLanguageIdentifier("fr-FR")).thenReturn(false);
        when(repository.save(org.mockito.ArgumentMatchers.any(LanguageIdentification.class))).thenReturn(entity);

        service.create(new io.github.matrixlane.metadataregistry.web.dto.language.LanguageRequest(
                "fr-FR", "Latn", "FR", null, null, null, null, "法语(自定义)", "法国(自定义)"));

        verify(extensionService).upsert(entity.getId(), "法语(自定义)", "法国(自定义)");
    }

    @Test
    @DisplayName("update:按请求值 upsert 扩展行")
    void updateUpsertsExtensionNames() {
        UUID id = UUID.randomUUID();
        LanguageIdentification entity = entity("ja-JP", "JP");
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(org.mockito.ArgumentMatchers.any(LanguageIdentification.class))).thenReturn(entity);

        service.update(id, new io.github.matrixlane.metadataregistry.web.dto.language.LanguageRequest(
                "ja-JP", "Jpan", "JP", null, null, null, null, "日语", "日本"));

        verify(extensionService).upsert(id, "日语", "日本");
    }

    @Test
    @DisplayName("update:名称为空时存 null 并 upsert 扩展行")
    void updateStoresNullNamesWhenBlank() {
        UUID id = UUID.randomUUID();
        LanguageIdentification entity = entity("ja-JP", "JP");
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(org.mockito.ArgumentMatchers.any(LanguageIdentification.class))).thenReturn(entity);

        service.update(id, new io.github.matrixlane.metadataregistry.web.dto.language.LanguageRequest(
                "ja-JP", "Jpan", "JP", null, null, null, null, null, " "));

        verify(extensionService).upsert(id, null, null);
    }
}
