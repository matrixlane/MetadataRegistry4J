package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.DataQualityTask;
import io.github.matrixlane.metadataregistry.web.entity.DataQualityTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 数据质量评估任务 Repository.
 */
public interface DataQualityTaskRepository extends JpaRepository<DataQualityTask, UUID> {

    List<DataQualityTask> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    List<DataQualityTask> findByCreatedByAndStatusOrderByCreatedAtDesc(UUID createdBy, DataQualityTaskStatus status);

    List<DataQualityTask> findByStatusOrderByCreatedAtAsc(DataQualityTaskStatus status);
}
