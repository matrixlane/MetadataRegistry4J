package io.github.matrixlane.metadataregistry.draftgeneration.service;

import io.github.matrixlane.metadataregistry.draftgeneration.generator.MetadataDraftGenerator;
import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftGenerationResult;
import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftStatus;
import io.github.matrixlane.metadataregistry.draftgeneration.persistence.MetadataDraftConverter;
import io.github.matrixlane.metadataregistry.draftgeneration.persistence.MetadataDraftEntity;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 元数据草稿服务。
 *
 * <p>提供草稿生成、状态管理和持久化的 orchestration 服务。
 */
@Slf4j
@RequiredArgsConstructor
public class MetadataDraftService {

    private final MetadataDraftGenerator draftGenerator;

    /**
     * 从数据库 Schema 生成并返回草稿结果（不持久化）。
     *
     * @param databaseSchema 数据库 Schema
     * @return 草稿生成结果
     */
    public DraftGenerationResult generateDrafts(DatabaseSchema databaseSchema) {
        log.info("Generating metadata drafts for schema: {}",
                databaseSchema != null ? databaseSchema.getSchema() : "null");
        return draftGenerator.generate(databaseSchema);
    }

    /**
     * 从数据库 Schema 生成草稿并转换为持久化实体（不实际保存到数据库）。
     *
     * @param databaseSchema 数据库 Schema
     * @return 草稿实体列表
     */
    public List<MetadataDraftEntity> generateDraftEntities(DatabaseSchema databaseSchema) {
        DraftGenerationResult result = generateDrafts(databaseSchema);
        List<MetadataDraftEntity> entities = MetadataDraftConverter.toEntities(result);
        log.info("Generated {} draft entities from {} tables",
                entities.size(), result.getTotalTables());
        return entities;
    }

    /**
     * 批准草稿。
     *
     * @param draft 草稿实体
     */
    public void approve(MetadataDraftEntity draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Draft cannot be null");
        }
        draft.transitionTo(DraftStatus.APPROVED);
        log.info("Draft approved: {} ({})", draft.getSourceTableName(), draft.getId());
    }

    /**
     * 拒绝草稿。
     *
     * @param draft 草稿实体
     */
    public void reject(MetadataDraftEntity draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Draft cannot be null");
        }
        draft.transitionTo(DraftStatus.REJECTED);
        log.info("Draft rejected: {} ({})", draft.getSourceTableName(), draft.getId());
    }

    /**
     * 获取低置信度草稿列表（需要人工确认）。
     *
     * @param drafts 草稿实体列表
     * @return 包含低置信度列映射的草稿列表
     */
    public List<MetadataDraftEntity> findLowConfidenceDrafts(List<MetadataDraftEntity> drafts) {
        if (drafts == null) {
            return List.of();
        }
        return drafts.stream()
                .filter(d -> d.getColumns().stream()
                        .anyMatch(c -> c.getMappingConfidence()
                                == io.github.matrixlane.metadataregistry.draftgeneration.model.MappingConfidence.LOW))
                .toList();
    }
}
