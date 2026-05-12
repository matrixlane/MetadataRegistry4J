package io.github.matrixlane.metadataregistry.web.notification.template;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTemplateTypeTest {

    @Test
    void shouldRenderApprovalSubmittedTemplate() {
        Map<String, Object> vars = Map.of(
                "itemName", "患者性别代码",
                "approverName", "admin",
                "submitterName", "user1",
                "opinion", "请尽快审批"
        );

        NotificationTemplateType template = NotificationTemplateType.fromNotificationType(
                NotificationType.APPROVAL_SUBMITTED);

        String title = template.renderTitle(vars);
        String content = template.renderContent(vars);

        assertThat(title).contains("患者性别代码");
        assertThat(content).contains("admin");
        assertThat(content).contains("user1");
        assertThat(content).contains("请尽快审批");
        assertThat(template.isHtml()).isFalse();
        assertThat(template.getNotificationType()).isEqualTo(NotificationType.APPROVAL_SUBMITTED);
    }

    @Test
    void shouldRenderApprovalApprovedTemplate() {
        Map<String, Object> vars = Map.of(
                "itemName", "患者性别代码",
                "submitterName", "user1",
                "approverName", "admin",
                "opinion", "符合标准"
        );

        NotificationTemplateType template = NotificationTemplateType.fromNotificationType(
                NotificationType.APPROVAL_APPROVED);

        assertThat(template.renderTitle(vars)).contains("患者性别代码");
        assertThat(template.renderContent(vars)).contains("符合标准");
    }

    @Test
    void shouldRenderApprovalRejectedTemplate() {
        Map<String, Object> vars = Map.of(
                "itemName", "患者性别代码",
                "submitterName", "user1",
                "approverName", "admin",
                "opinion", "命名不规范"
        );

        NotificationTemplateType template = NotificationTemplateType.fromNotificationType(
                NotificationType.APPROVAL_REJECTED);

        assertThat(template.renderTitle(vars)).contains("患者性别代码");
        assertThat(template.renderContent(vars)).contains("命名不规范");
    }

    @Test
    void shouldRenderApprovalTimeoutTemplate() {
        Map<String, Object> vars = Map.of(
                "itemName", "患者性别代码",
                "approverName", "admin",
                "submitterName", "user1"
        );

        NotificationTemplateType template = NotificationTemplateType.fromNotificationType(
                NotificationType.APPROVAL_TIMEOUT);

        assertThat(template.renderTitle(vars)).contains("患者性别代码");
        assertThat(template.renderContent(vars)).contains("已超时");
    }

    @Test
    void shouldHandleNullVariables() {
        NotificationTemplateType template = NotificationTemplateType.fromNotificationType(
                NotificationType.APPROVAL_SUBMITTED);

        String content = template.renderContent(null);
        assertThat(content).contains("{itemName}");
    }

    @Test
    void shouldHandleMissingVariables() {
        NotificationTemplateType template = NotificationTemplateType.fromNotificationType(
                NotificationType.APPROVAL_SUBMITTED);

        String content = template.renderContent(Map.of("itemName", "测试项"));
        assertThat(content).contains("测试项");
        assertThat(content).contains("{approverName}");
    }

    @Test
    void shouldThrowForUnknownNotificationType() {
        assertThatThrownBy(() -> NotificationTemplateType.fromNotificationType(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到通知类型对应的模板");
    }
}
