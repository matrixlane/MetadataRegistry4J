package io.github.matrixlane.metadataregistry.web.notification.template;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;

import java.util.Map;

/**
 * 通知模板接口。MVP 阶段集中式管理，V1 可扩展为动态模板引擎。
 */
public interface NotificationTemplate {

    NotificationType getNotificationType();

    String renderTitle(Map<String, Object> variables);

    String renderContent(Map<String, Object> variables);

    boolean isHtml();
}
