package io.github.matrixlane.metadataregistry.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批超时定时任务调度器。
 *
 * <p>每 5 分钟扫描一次超时步骤记录，标记为 TIMEOUT 状态。
 * 基础实现使用 Spring @Scheduled，V1 阶段评估迁移至 Quartz 集群模式。</p>
 *
 * <p><b>超时阈值：</b></p>
 * <ul>
 *   <li>24h — 快速审批（如数据项更新）</li>
 *   <li>72h — 常规审批（默认）</li>
 *   <li>7d — 复杂审批（如标准变更）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutScheduler {

    private final MultiLevelApprovalService multiLevelApprovalService;

    /**
     * 每 5 分钟检查一次超时步骤。
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void checkTimeout() {
        log.debug("开始扫描超时审批步骤...");
        try {
            int count = multiLevelApprovalService.handleTimeoutSteps();
            if (count > 0) {
                log.info("标记 {} 条审批步骤记录为超时状态", count);
            }
        } catch (Exception e) {
            log.error("审批超时处理失败", e);
        }
    }
}
