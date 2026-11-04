package io.github.matrixlane.metadataregistry.web.dto.datasource;

import java.util.UUID;

/**
 * "保持当前数据源并继续"的确认响应。
 *
 * @param restartRequired true 表示向导相：已创建重启事务，由自动重启完成数据库
 *                        初始化（{@code restartId} 用于进度查询）；false 表示
 *                        完整相且已确认，无需重启，向导直接继续
 * @param restartId 重启事务标识（{@code restartRequired=false} 时为 null）
 */
public record DatasourceConfirmResponse(boolean restartRequired, UUID restartId) {
}
