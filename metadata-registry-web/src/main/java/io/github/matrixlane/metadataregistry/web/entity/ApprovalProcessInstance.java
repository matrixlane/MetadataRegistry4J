package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 审批流程实例实体。
 *
 * <p>记录某元数据项基于某个审批模板启动的实际审批流程，跟踪当前步骤和整体状态。
 * 流程状态与底层 {@link ApprovalRecord} 兼容：最终审批通过后触发 ISO 状态迁移。</p>
 */
@Entity
@Table(name = "approval_process_instance")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalProcessInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ApprovalTemplate template;

    @Column(name = "administered_item_id", nullable = false)
    private UUID administeredItemId;

    @Column(name = "submitter", nullable = false, length = 100)
    private String submitter;

    @Column(name = "current_step", nullable = false)
    private Integer currentStep = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalProcessStatus status = ApprovalProcessStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "processInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<ApprovalStepRecord> stepRecords = new ArrayList<>();

    public static ApprovalProcessInstance of(ApprovalTemplate template, UUID administeredItemId, String submitter) {
        ApprovalProcessInstance instance = new ApprovalProcessInstance();
        instance.setTemplate(template);
        instance.setAdministeredItemId(administeredItemId);
        instance.setSubmitter(submitter);
        instance.setCurrentStep(1);
        instance.setStatus(ApprovalProcessStatus.PENDING);
        instance.setCreatedAt(LocalDateTime.now());
        return instance;
    }

    /**
     * 推进到下一步。
     *
     * @param totalSteps 总步骤数
     * @return true 如果流程已完成
     */
    public boolean advance(int totalSteps) {
        if (currentStep >= totalSteps) {
            this.status = ApprovalProcessStatus.APPROVED;
            this.completedAt = LocalDateTime.now();
            return true;
        }
        this.currentStep++;
        return false;
    }

    /**
     * 整体退回。
     */
    public void rejectAll() {
        this.status = ApprovalProcessStatus.REJECTED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 标记超时。
     */
    public void markTimeout() {
        this.status = ApprovalProcessStatus.TIMEOUT;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 检查是否处于进行中状态。
     */
    public boolean isPending() {
        return this.status == ApprovalProcessStatus.PENDING;
    }
}
