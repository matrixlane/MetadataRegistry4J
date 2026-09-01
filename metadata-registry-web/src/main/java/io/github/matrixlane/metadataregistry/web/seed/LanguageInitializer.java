package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LanguageInitializer implements ApplicationRunner {

    private final LanguageIdentificationRepository languageRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initializeLanguages();
    }

    @Transactional
    public void initializeLanguages() {
        log.info("开始初始化内置语言标识数据（G20 国家）");

        upsertLanguage("zh-CN", "Hans", "CN");
        upsertLanguage("en-US", "Latn", "US");
        upsertLanguage("ja-JP", "Jpan", "JP");
        upsertLanguage("en-GB", "Latn", "GB");
        upsertLanguage("en-CA", "Latn", "CA");
        upsertLanguage("fr-CA", "Latn", "CA");
        upsertLanguage("fr-FR", "Latn", "FR");
        upsertLanguage("de-DE", "Latn", "DE");
        upsertLanguage("ru-RU", "Cyrl", "RU");
        upsertLanguage("ko-KR", "Kore", "KR");
        upsertLanguage("it-IT", "Latn", "IT");
        upsertLanguage("es-ES", "Latn", "ES");
        upsertLanguage("es-AR", "Latn", "AR");
        upsertLanguage("es-MX", "Latn", "MX");
        upsertLanguage("pt-BR", "Latn", "BR");
        upsertLanguage("hi-IN", "Deva", "IN");
        upsertLanguage("en-IN", "Latn", "IN");
        upsertLanguage("en-AU", "Latn", "AU");
        upsertLanguage("en-ZA", "Latn", "ZA");
        upsertLanguage("ar-SA", "Arab", "SA");
        upsertLanguage("tr-TR", "Latn", "TR");
        upsertLanguage("id-ID", "Latn", "ID");

        log.info("内置语言标识数据初始化完成");
    }

    private void upsertLanguage(String identifier, String script, String territory) {
        languageRepository.findByLanguageIdentifier(identifier).ifPresentOrElse(
            existing -> {
                // Already exists, do nothing
            },
            () -> {
                LanguageIdentification lang = new LanguageIdentification();
                lang.setLanguageIdentifier(identifier);
                lang.setScriptIdentifier(script);
                lang.setGeopoliticalTerritoryIdentifier(territory);
                languageRepository.save(lang);
                log.info("已新增内置语言标识: {}", identifier);
            }
        );
    }
}
