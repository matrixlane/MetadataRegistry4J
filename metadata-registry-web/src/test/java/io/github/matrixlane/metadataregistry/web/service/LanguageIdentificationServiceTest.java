package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageIdentificationServiceTest {

    @Mock
    private LanguageIdentificationRepository repository;

    @InjectMocks
    private LanguageIdentificationService service;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private LanguageIdentification entity(String languageIdentifier, String territoryIdentifier) {
        LanguageIdentification entity = new LanguageIdentification();
        entity.setLanguageIdentifier(languageIdentifier);
        entity.setScriptIdentifier("Hans");
        entity.setGeopoliticalTerritoryIdentifier(territoryIdentifier);
        entity.setEnabled(true);
        return entity;
    }

    @Test
    @DisplayName("中文 Locale 下返回本地化的语言名称与地区名称")
    void resolvesLocalizedNamesForChineseLocale() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entity("zh-CN", "CN")));
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isEqualTo("中文");
        assertThat(response.geopoliticalTerritoryName()).isEqualTo("中国");
    }

    @Test
    @DisplayName("英文 Locale 下返回英文的语言名称与地区名称")
    void resolvesLocalizedNamesForEnglishLocale() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entity("zh-CN", "CN")));
        LocaleContextHolder.setLocale(Locale.US);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isEqualTo("Chinese");
        assertThat(response.geopoliticalTerritoryName()).isEqualTo("China");
    }

    @Test
    @DisplayName("地区标识为空时地区名称为 null")
    void returnsNullTerritoryNameWhenTerritoryMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entity("zh-CN", null)));
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isEqualTo("中文");
        assertThat(response.geopoliticalTerritoryName()).isNull();
    }

    @Test
    @DisplayName("无法解析的标识降级显示原代码")
    void fallsBackToRawCodeWhenUnresolvable() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entity("x-invalid-code", null)));
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        LanguageResponse response = service.get(id);

        assertThat(response.languageName()).isNotBlank();
    }
}
