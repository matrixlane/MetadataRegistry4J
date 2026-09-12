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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 生成来源记录服务：追溯各 Agent 产出（任务、Agent、时间、操作者、动作类型）。
 *
 * <p>创建时校验关联项目必须是受管条目（AdministeredItem）；生成时间取服务端当前
 * 时刻，操作者取当前登录用户。记录创建失败不回滚已完成的注释保存（调用侧顺序：
 * 先落注释、再建记录，允许单独重试记录写入）。
 */
@Service
@RequiredArgsConstructor
public class AiGenerationRecordService {

    private final AiGenerationRecordRepository repository;
    private final ItemRepository itemRepository;

    @Transactional
    public GenerationRecordResponse create(GenerationRecordRequest request, String operator) {
        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.itemId()));
        if (!(item.getIdentifiedItem() instanceof AdministeredItem administeredItem)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "项目尚未注册为受管条目，无法写入生成来源记录：" + request.itemId());
        }
        AiGenerationRecord record = new AiGenerationRecord(administeredItem, request.taskId(),
                request.providerId(), request.agentDisplayName(), operator,
                request.outputKind(), request.rawOutput(), request.actionType());
        return GenerationRecordResponse.of(repository.save(record));
    }

    @Transactional(readOnly = true)
    public List<GenerationRecordResponse> listByItem(UUID itemId) {
        return repository.findByAdministeredItem_Item_IdOrderByGeneratedAtDesc(itemId).stream()
                .map(GenerationRecordResponse::of)
                .toList();
    }
}
