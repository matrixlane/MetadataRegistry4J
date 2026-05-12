package io.github.matrixlane.metadataregistry.web.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalTimeoutScheduler} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalTimeoutSchedulerTest {

    @Mock
    private MultiLevelApprovalService multiLevelApprovalService;

    @InjectMocks
    private ApprovalTimeoutScheduler approvalTimeoutScheduler;

    @Test
    @DisplayName("定时任务：应调用超时处理服务")
    void scheduledTimeoutCheck_shouldInvokeService() {
        when(multiLevelApprovalService.handleTimeoutSteps()).thenReturn(3);

        approvalTimeoutScheduler.checkTimeout();

        verify(multiLevelApprovalService).handleTimeoutSteps();
    }
}
