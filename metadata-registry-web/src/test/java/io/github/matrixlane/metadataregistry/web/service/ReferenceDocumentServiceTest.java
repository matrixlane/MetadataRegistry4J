package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance;
import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentResponse;
import io.github.matrixlane.metadataregistry.web.repository.DocumentTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("内容:本地文件存在时返回 200 与 inline 流")
    void contentReturnsFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("测试 文档.pdf");
        Files.write(file, new byte[]{1, 2, 3});
        ReferenceDocument doc = documentWith();
        doc.setUri(file.toString());
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));

        ResponseEntity<Resource> response = service.content(doc.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/pdf");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(3);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline; filename*=UTF-8''")
                .contains(UriUtils.encode("测试 文档.pdf", StandardCharsets.UTF_8));
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("内容:file:// URI 解析为本地路径")
    void contentResolvesFileUri(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("doc.txt");
        Files.write(file, new byte[]{1});
        String fileUri = "file://" + file.toAbsolutePath().toString().replace('\\', '/');
        ReferenceDocument doc = documentWith();
        doc.setUri(fileUri);
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));

        ResponseEntity<Resource> response = service.content(doc.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("内容:文件不存在时抛 ResourceNotFoundException(404)")
    void contentThrowsWhenFileMissing(@TempDir Path tempDir) {
        ReferenceDocument doc = documentWith();
        doc.setUri(tempDir.resolve("missing.pdf").toString());
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.content(doc.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("内容:http(s) 等非本地 URI 抛 BusinessException")
    void contentRejectsNonLocalUri() {
        ReferenceDocument doc = documentWith();
        doc.setUri("https://example.com/doc.pdf");
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.content(doc.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("内容:URI 为空时抛 BusinessException")
    void contentRejectsBlankUri() {
        ReferenceDocument doc = documentWith();
        doc.setUri("  ");
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.content(doc.getId()))
                .isInstanceOf(BusinessException.class);
    }
}
