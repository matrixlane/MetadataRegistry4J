package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageInitializerTest {

    @Mock
    private LanguageIdentificationRepository languageRepository;

    @InjectMocks
    private LanguageInitializer initializer;

    private LanguageIdentification existingLanguage(String identifier) {
        LanguageIdentification language = new LanguageIdentification();
        language.setId(UUID.randomUUID());
        language.setLanguageIdentifier(identifier);
        return language;
    }

    @Test
    @DisplayName("内置语言已存在时:不重复创建,且不触碰名称扩展(名称预置仅存在于 SQL 迁移)")
    void keepsExistingLanguagesWithoutTouchingExtension() {
        when(languageRepository.findByLanguageIdentifier(anyString()))
                .thenAnswer(inv -> Optional.of(existingLanguage(inv.getArgument(0))));

        initializer.initializeLanguages();

        verify(languageRepository, never()).save(any(LanguageIdentification.class));
    }

    @Test
    @DisplayName("内置语言缺失时:按标识/文字/地区新建语言(不补种名称)")
    void createsMissingLanguage() {
        when(languageRepository.findByLanguageIdentifier(anyString()))
                .thenAnswer(inv -> inv.getArgument(0).equals("zh-CN")
                        ? Optional.<LanguageIdentification>empty()
                        : Optional.of(existingLanguage(inv.getArgument(0))));
        when(languageRepository.save(any(LanguageIdentification.class)))
                .thenAnswer(inv -> {
                    LanguageIdentification lang = inv.getArgument(0);
                    lang.setId(UUID.randomUUID());
                    return lang;
                });

        initializer.initializeLanguages();

        verify(languageRepository).save(any(LanguageIdentification.class));
    }
}
