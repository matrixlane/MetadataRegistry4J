package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionResponse;
import io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefinitionServiceTest {

    @Mock private DefinitionRepository definitionRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private LanguageIdentificationRepository languageRepository;
    @Mock private ContextRepository contextRepository;
    @Mock private ReferenceDocumentRepository referenceDocumentRepository;
    @Mock private DefinitionAuditLogService definitionAuditLogService;

    @InjectMocks
    private DefinitionService definitionService;

    private UUID itemId;
    private UUID languageId;
    private UUID definitionId;
    private Item item;
    private LanguageIdentification lang;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        languageId = UUID.randomUUID();
        definitionId = UUID.randomUUID();

        item = new TestItem();
        item.setId(itemId);

        lang = new LanguageIdentification();
        lang.setId(languageId);
        lang.setLanguageIdentifier("zh-CN");
    }

    @Test
    void listByItemId_shouldReturnResults() {
        Definition d = createDefinition("患者的唯一标识");
        when(definitionRepository.findByItem_Id(itemId)).thenReturn(List.of(d));

        List<DefinitionResponse> result = definitionService.listByItemId(itemId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualTo("患者的唯一标识");
    }

    @Test
    void get_shouldReturnDefinition() {
        Definition d = createDefinition("患者的唯一标识");
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(d));

        DefinitionResponse result = definitionService.get(definitionId);
        assertThat(result.value()).isEqualTo("患者的唯一标识");
    }

    @Test
    void create_shouldSucceedAndLogAudit() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        when(definitionRepository.save(any(Definition.class))).thenAnswer(i -> {
            Definition d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DefinitionRequest request = new DefinitionRequest(
                itemId, languageId, "患者的唯一标识", null, null, Acceptability.PREFERRED);

        DefinitionResponse result = definitionService.create(request);
        assertThat(result.value()).isEqualTo("患者的唯一标识");

        ArgumentCaptor<DefinitionAuditLog> logCaptor = ArgumentCaptor.forClass(DefinitionAuditLog.class);
        verify(definitionAuditLogService).save(logCaptor.capture());
        DefinitionAuditLog log = logCaptor.getValue();
        assertThat(log.getAction()).isEqualTo("CREATE");
        assertThat(log.getItemId()).isEqualTo(itemId);
    }

    @Test
    void update_shouldSucceedAndLogAudit() {
        Definition d = createDefinition("旧定义");
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(d));
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        when(definitionRepository.save(any(Definition.class))).thenReturn(d);

        DefinitionRequest request = new DefinitionRequest(
                itemId, languageId, "新定义", null, null, Acceptability.PREFERRED);

        DefinitionResponse result = definitionService.update(definitionId, request);
        assertThat(result.value()).isEqualTo("新定义");

        ArgumentCaptor<DefinitionAuditLog> logCaptor = ArgumentCaptor.forClass(DefinitionAuditLog.class);
        verify(definitionAuditLogService).save(logCaptor.capture());
        DefinitionAuditLog log = logCaptor.getValue();
        assertThat(log.getAction()).isEqualTo("UPDATE");
        assertThat(log.getOldValueSummary()).isEqualTo("旧定义");
        assertThat(log.getNewValueSummary()).isEqualTo("新定义");
    }

    @Test
    void delete_shouldRemoveAndLogAudit() {
        Definition d = createDefinition("将被删除的定义");
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.of(d));
        doNothing().when(definitionRepository).deleteById(definitionId);

        definitionService.delete(definitionId);

        verify(definitionRepository).deleteById(definitionId);
        ArgumentCaptor<DefinitionAuditLog> logCaptor = ArgumentCaptor.forClass(DefinitionAuditLog.class);
        verify(definitionAuditLogService).save(logCaptor.capture());
        DefinitionAuditLog log = logCaptor.getValue();
        assertThat(log.getAction()).isEqualTo("DELETE");
        assertThat(log.getOldValueSummary()).isEqualTo("将被删除的定义");
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(definitionRepository.findById(definitionId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> definitionService.delete(definitionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Definition createDefinition(String value) {
        Definition d = new Definition();
        d.setId(definitionId);
        d.setText(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text.of(value));
        d.setLanguage(lang);
        d.setItem(item);
        return d;
    }

    @jakarta.persistence.Entity
    static class TestItem extends Item {}
}
