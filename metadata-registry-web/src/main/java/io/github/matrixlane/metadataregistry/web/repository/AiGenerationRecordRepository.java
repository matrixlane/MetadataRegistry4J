package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.generation.AiGenerationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * AI 生成来源记录存储。
 */
public interface AiGenerationRecordRepository extends JpaRepository<AiGenerationRecord, UUID> {

    /** 按条目（Item）查询生成来源记录，新的在前。 */
    List<AiGenerationRecord> findByAdministeredItem_Item_IdOrderByGeneratedAtDesc(UUID itemId);
}
