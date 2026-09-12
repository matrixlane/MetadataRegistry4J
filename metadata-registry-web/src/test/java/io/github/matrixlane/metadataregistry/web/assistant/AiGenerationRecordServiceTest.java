package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.ext.generation.AiGenerationRecord;
import io.github.matrixlane.metadataregistry.web.assistant.dto.GenerationRecordRequest;
import io.github.matrixlane.metadataregistry.web.assistant.dto.GenerationRecordResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.AiGenerationRecordRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGenerationRecordServiceTest {

    @Mock
    private AiGenerationRecordRepository repository;
    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private AiGenerationRecordService service;

    private Item administeredItemBacked() {
        Item item = new TestItem();
        item.setId(UUID.randomUUID());
        item.setIdentifiedItem(new AdministeredItem(item));
        return item;
    }

    private GenerationRecordRequest request(UUID itemId) {
        return new GenerationRecordRequest(itemId, "explain-selection", "claude", "Claude Code",
                "TEXT", "解释原文", "SAVED_TO_EXPLANATORY_COMMENT");
    }

    @Test
    void create_savesRecordWithOperatorAndGeneratedAt() {
        Item item = administeredItemBacked();
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(repository.save(any(AiGenerationRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GenerationRecordResponse response = service.create(request(item.getId()), "alice");

        ArgumentCaptor<AiGenerationRecord> captor = ArgumentCaptor.forClass(AiGenerationRecord.class);
        verify(repository).save(captor.capture());
        AiGenerationRecord saved = captor.getValue();
        assertThat(saved.getTaskId()).isEqualTo("explain-selection");
        assertThat(saved.getProviderId()).isEqualTo("claude");
        assertThat(saved.getAgentDisplayName()).isEqualTo("Claude Code");
        assertThat(saved.getOutputKind()).isEqualTo("TEXT");
        assertThat(saved.getRawOutput()).isEqualTo("解释原文");
        assertThat(saved.getActionType()).isEqualTo("SAVED_TO_EXPLANATORY_COMMENT");
        assertThat(saved.getOperator()).isEqualTo("alice");
        assertThat(saved.getGeneratedAt()).isNotNull();
        assertThat(saved.getAdministeredItem()).isSameAs(item.getIdentifiedItem());
        assertThat(response.itemId()).isEqualTo(item.getId());
    }

    @Test
    void create_itemNotFound_throwsNotFound() {
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(itemId), "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_itemWithoutAdministeredItem_throwsValidationFailed() {
        Item item = new TestItem();
        item.setId(UUID.randomUUID());
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.create(request(item.getId()), "alice"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
    }

    @Test
    void listByItem_returnsRecordsNewestFirst() {
        UUID itemId = UUID.randomUUID();
        AiGenerationRecord older = new AiGenerationRecord();
        older.setId(UUID.randomUUID());
        AiGenerationRecord newer = new AiGenerationRecord();
        newer.setId(UUID.randomUUID());
        when(repository.findByAdministeredItem_Item_IdOrderByGeneratedAtDesc(itemId))
                .thenReturn(List.of(newer, older));

        List<GenerationRecordResponse> records = service.listByItem(itemId);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).id()).isEqualTo(newer.getId());
    }

    /** 测试用具体 Item 子类。 */
    static class TestItem extends Item {
    }
}
