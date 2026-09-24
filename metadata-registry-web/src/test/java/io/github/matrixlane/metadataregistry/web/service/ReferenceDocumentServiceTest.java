package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance;
import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentResponse;
import io.github.matrixlane.metadataregistry.web.repository.DocumentTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDocumentServiceTest {

    @Mock
    private ReferenceDocumentRepository repository;

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @Mock
    private LanguageIdentificationRepository languageRepository;

    @Mock
    private LanguageGovernanceService governanceService;

    @Mock
    private LanguageExtensionService extensionService;

    @InjectMocks
    private ReferenceDocumentService service;

    @BeforeEach
    void stubGovernance() {
        lenient().when(governanceService.getOrDefault(any()))
                .thenAnswer(inv -> new LanguageGovernance());
    }

    private LanguageIdentification language(String identifier, String territory) {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setId(UUID.randomUUID());
        lang.setLanguageIdentifier(identifier);
        lang.setScriptIdentifier("Hans");
        lang.setGeopoliticalTerritoryIdentifier(territory);
        return lang;
    }

    private ReferenceDocument documentWith(LanguageIdentification... languages) {
        ReferenceDocument doc = new ReferenceDocument();
        doc.setId(UUID.randomUUID());
        doc.setIdentifier("doc-1");
        for (LanguageIdentification lang : languages) {
            doc.getLanguage().add(lang);
        }
        return doc;
    }

    @Test
    @DisplayName("详情:语言名称/地区名称经 LanguageExtensionService 取持久化值")
    void resolvesNamesFromExtensionService() {
        LanguageIdentification lang = language("zh-CN", "CN");
        ReferenceDocument doc = documentWith(lang);
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));
        LanguageIdentificationExtension extension = new LanguageIdentificationExtension(lang.getId());
        extension.setLanguageName("中文");
        extension.setTerritoryName("中国");
        when(extensionService.getOrDefault(lang.getId())).thenReturn(extension);

        ReferenceDocumentResponse response = service.get(doc.getId());

        LanguageResponse langResponse = response.languages().get(0);
        assertThat(langResponse.languageName()).isEqualTo("中文");
        assertThat(langResponse.geopoliticalTerritoryName()).isEqualTo("中国");
        verify(extensionService).getOrDefault(lang.getId());
    }

    @Test
    @DisplayName("详情:扩展行缺失时名称为 null(展示为空,不做运行时解析)")
    void returnsNullNamesWhenExtensionMissing() {
        LanguageIdentification lang = language("zh-CN", "CN");
        ReferenceDocument doc = documentWith(lang);
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(extensionService.getOrDefault(lang.getId())).thenReturn(null);

        ReferenceDocumentResponse response = service.get(doc.getId());

        LanguageResponse langResponse = response.languages().get(0);
        assertThat(langResponse.languageName()).isNull();
        assertThat(langResponse.geopoliticalTerritoryName()).isNull();
    }
}
