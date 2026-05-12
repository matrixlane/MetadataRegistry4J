package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * 导出任务 Repository — 暂存桩，后续 WI 补充完整。
 */
public interface ExportTaskRepository extends JpaRepository<ExportTask, UUID> {
}
