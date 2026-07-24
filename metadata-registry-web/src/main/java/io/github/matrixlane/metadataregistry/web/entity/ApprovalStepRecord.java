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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审批步骤实例记录实体。
 *
 * <p>记录审批流程实例中某个步骤的实际执行情况，包括分配给谁、审批结果、意见、超时等。</p>
 */
@Entity
@Table(name = "approval_step_record")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalStepRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_instance_id", nullable = false)
    private ApprovalProcessInstance processInstance;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_rule", nullable = false, length = 20)
    private ApprovalStepAssigneeRule assigneeRule;

    @Column(name = "assignee_value", nullable = false, length = 200)
    private String assigneeValue;

    @Column(name = "approver", length = 100)
    private String approver;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "opinion", length = 2000)
    private String opinion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStepStatus status = ApprovalStepStatus.PENDING;

    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;

    @Version
    @Column(name = "version")
    private Long version;

    public static ApprovalStepRecord of(ApprovalProcessInstance processInstance, Integer stepNumber,
                                         String stepName, ApprovalStepAssigneeRule assigneeRule,
                                         String assigneeValue, Integer timeoutHours) {
        ApprovalStepRecord record = new ApprovalStepRecord();
        record.setProcessInstance(processInstance);
        record.setStepNumber(stepNumber);
        record.setStepName(stepName);
        record.setAssigneeRule(assigneeRule);
        record.setAssigneeValue(assigneeValue);
        record.setAssignedAt(LocalDateTime.now());
        record.setStatus(ApprovalStepStatus.PENDING);
        if (timeoutHours != null && timeoutHours > 0) {
            record.setTimeoutAt(LocalDateTime.now().plusHours(timeoutHours));
        }
        return record;
    }

    public void approve(String approver, String opinion) {
        this.approver = approver;
        this.opinion = opinion;
        this.approvedAt = LocalDateTime.now();
        this.status = ApprovalStepStatus.APPROVED;
    }

    public void reject(String approver, String opinion) {
        this.approver = approver;
        this.opinion = opinion;
        this.approvedAt = LocalDateTime.now();
        this.status = ApprovalStepStatus.REJECTED;
    }

    public void transfer(String newAssigneeValue) {
        this.assigneeValue = newAssigneeValue;
        this.status = ApprovalStepStatus.TRANSFERRED;
    }

    public void markTimeout() {
        this.status = ApprovalStepStatus.TIMEOUT;
    }

    /**
     * 检查是否已超时。
     */
    public boolean isTimedOut() {
        if (this.timeoutAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(this.timeoutAt);
    }
}

