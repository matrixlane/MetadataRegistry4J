package io.github.matrixlane.metadataregistry.web.notification.event;

import io.github.matrixlane.metadataregistry.web.notification.service.EmailNotificationService;
import io.github.matrixlane.metadataregistry.web.notification.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 通知事件监听器。
 * 使用 @Async 异步投递通知，TransactionPhase.AFTER_COMMIT 确保事务安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final InAppNotificationService inAppService;
    private final EmailNotificationService emailService;

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        log.debug("收到通知事件: recipient={}, type={}", event.getRecipientId(), event.getNotificationType());

        try {
            if (inAppService.isEnabled(event.getRecipientId(), event.getNotificationType())) {
                inAppService.sendWithTemplate(
                        event.getRecipientId(), event.getNotificationType(),
                        event.getVariables(), event.getRelatedBusinessId()
                );
            } else {
                log.debug("用户 {} 已关闭站内信通知", event.getRecipientId());
            }
        } catch (Exception e) {
            log.error("站内信通知发送异常: recipient={}, type={}", event.getRecipientId(), event.getNotificationType(), e);
        }

        try {
            if (emailService.isEnabled(event.getRecipientId(), event.getNotificationType())) {
                emailService.sendWithTemplate(
                        event.getRecipientId(), event.getNotificationType(),
                        event.getVariables(), event.getRelatedBusinessId()
                );
            } else {
                log.debug("用户 {} 未开启邮件通知或无邮箱配置", event.getRecipientId());
            }
        } catch (Exception e) {
            log.error("邮件通知发送异常: recipient={}, type={}", event.getRecipientId(), event.getNotificationType(), e);
        }
    }
}
