package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * 审批步骤定义实体。
 *
 * <p>定义审批流程模板中的单个步骤，包括步骤序号、名称、审批人分配规则、超时设置、退回策略等。</p>
 */
@Entity
@Table(name = "approval_step")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ApprovalTemplate template;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_rule", nullable = false, length = 20)
    private ApprovalStepAssigneeRule assigneeRule;

    @Column(name = "assignee_value", nullable = false, length = 200)
    private String assigneeValue;

    @Column(name = "timeout_hours")
    private Integer timeoutHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_strategy", length = 20)
    private RejectStrategy rejectStrategy;

    @Column(name = "description", length = 1000)
    private String description;

    @Version
    @Column(name = "version")
    private Long version;

    public static ApprovalStep of(ApprovalTemplate template, Integer stepNumber, String stepName,
                                   ApprovalStepAssigneeRule assigneeRule, String assigneeValue) {
        ApprovalStep step = new ApprovalStep();
        step.setTemplate(template);
        step.setStepNumber(stepNumber);
        step.setStepName(stepName);
        step.setAssigneeRule(assigneeRule);
        step.setAssigneeValue(assigneeValue);
        return step;
    }
}

