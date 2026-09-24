package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationExtensionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageExtensionServiceTest {

    @Mock
    private LanguageIdentificationExtensionRepository extensionRepository;

    @InjectMocks
    private LanguageExtensionService service;

    @Test
    @DisplayName("getOrDefault:扩展行存在时返回该行")
    void getOrDefaultReturnsExistingRow() {
        UUID languageId = UUID.randomUUID();
        LanguageIdentificationExtension extension = new LanguageIdentificationExtension(languageId);
        extension.setLanguageName("中文");
        when(extensionRepository.findByLanguageId(languageId)).thenReturn(Optional.of(extension));

        assertThat(service.getOrDefault(languageId)).isSameAs(extension);
    }

    @Test
    @DisplayName("getOrDefault:无扩展行时返回 null(名称展示为空)")
    void getOrDefaultReturnsNullWhenMissing() {
        UUID languageId = UUID.randomUUID();
        when(extensionRepository.findByLanguageId(languageId)).thenReturn(Optional.empty());

        assertThat(service.getOrDefault(languageId)).isNull();
    }

    @Test
    @DisplayName("getOrDefault:languageId 为 null 时不触仓储,直接返回 null")
    void getOrDefaultSkipsRepositoryForNullId() {
        assertThat(service.getOrDefault(null)).isNull();

        verify(extensionRepository, never()).findByLanguageId(any());
    }

    @Test
    @DisplayName("mapByLanguageIds:批量返回 language_id 到扩展行的映射,空入参返回空 Map")
    void mapByLanguageIdsReturnsBatchMap() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        LanguageIdentificationExtension ext1 = new LanguageIdentificationExtension(id1);
        LanguageIdentificationExtension ext2 = new LanguageIdentificationExtension(id2);
        when(extensionRepository.findByLanguageIdIn(List.of(id1, id2))).thenReturn(List.of(ext1, ext2));

        Map<UUID, LanguageIdentificationExtension> map = service.mapByLanguageIds(List.of(id1, id2));

        assertThat(map).containsOnlyKeys(id1, id2);
        assertThat(service.mapByLanguageIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("upsert:无扩展行时新建并写入名称")
    void upsertCreatesRowWhenMissing() {
        UUID languageId = UUID.randomUUID();
        when(extensionRepository.findByLanguageId(languageId)).thenReturn(Optional.empty());
        when(extensionRepository.save(any(LanguageIdentificationExtension.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LanguageIdentificationExtension saved = service.upsert(languageId, "中文", "中国");

        assertThat(saved.getLanguageId()).isEqualTo(languageId);
        assertThat(saved.getLanguageName()).isEqualTo("中文");
        assertThat(saved.getTerritoryName()).isEqualTo("中国");
        verify(extensionRepository).save(saved);
    }

    @Test
    @DisplayName("upsert:已有扩展行时覆盖名称,不新建行")
    void upsertUpdatesExistingRow() {
        UUID languageId = UUID.randomUUID();
        LanguageIdentificationExtension existing = new LanguageIdentificationExtension(languageId);
        existing.setLanguageName("旧名称");
        existing.setTerritoryName("旧地区");
        when(extensionRepository.findByLanguageId(languageId)).thenReturn(Optional.of(existing));
        when(extensionRepository.save(any(LanguageIdentificationExtension.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LanguageIdentificationExtension saved = service.upsert(languageId, "英语", "美国");

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getLanguageName()).isEqualTo("英语");
        assertThat(saved.getTerritoryName()).isEqualTo("美国");
        verify(extensionRepository).save(existing);
    }
}
