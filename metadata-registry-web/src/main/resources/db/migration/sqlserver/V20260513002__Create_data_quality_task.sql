-- ============================================================================
-- 数据质量评估任务表
-- ============================================================================
-- 用于跟踪异步数据质量评估任务的进度和结果。
-- 评估结果以 JSON 形式存储在 result_json 中，
-- 不持久化到 MDR 核心领域模型（遵循"轻量级初评"约束）。
-- ============================================================================

CREATE TABLE data_quality_task (
    id              UNIQUEIDENTIFIER    NOT NULL PRIMARY KEY,
    status          NVARCHAR(20)        NOT NULL DEFAULT 'PENDING',
    progress_pct    INT                 NOT NULL DEFAULT 0,
    step_name       NVARCHAR(100),
    table_name      NVARCHAR(255)       NOT NULL,
    schema_name     NVARCHAR(50),
    data_source_key NVARCHAR(100)       NOT NULL,
    request_params  NVARCHAR(MAX),
    result_json     NVARCHAR(MAX),
    error_message   NVARCHAR(MAX),
    created_at      DATETIME2           NOT NULL DEFAULT GETUTCDATE(),
    completed_at    DATETIME2,
    created_by      UNIQUEIDENTIFIER    NOT NULL
);

CREATE INDEX idx_dq_task_created_by ON data_quality_task (created_by, created_at DESC);
CREATE INDEX idx_dq_task_status ON data_quality_task (status, created_at ASC);
