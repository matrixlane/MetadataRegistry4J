package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 审批流程模板实体。
 *
 * <p>定义多级审批的流程结构，包括步骤数、每级分配规则、超时设置等。
 * 模板启用后锁定（{@code locked = true}），避免运行中实例受配置变更影响。</p>
 */
@Entity
@Table(name = "approval_template")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "step_count", nullable = false)
    private Integer stepCount;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "locked", nullable = false)
    private Boolean locked = false;

    @Column(name = "default_timeout_hours")
    private Integer defaultTimeoutHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_reject_strategy", length = 20)
    private RejectStrategy defaultRejectStrategy = RejectStrategy.REJECT_ALL;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    public static ApprovalTemplate of(String name, String description, Integer stepCount) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setStepCount(stepCount);
        template.setEnabled(false);
        template.setLocked(false);
        template.setCreatedAt(LocalDateTime.now());
        return template;
    }

    /**
     * 启用模板并锁定（启用后只读）。
     */
    public void enable() {
        this.enabled = true;
        this.locked = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 禁用模板（不解锁）。
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 验证模板步骤完整性。
     *
     * @throws IllegalStateException 步骤数不匹配或步骤序号不连续
     */
    public void validate() {
        if (steps == null || steps.size() != stepCount) {
            throw new IllegalStateException("模板步骤数不匹配: 期望 " + stepCount + ", 实际 " +
                    (steps == null ? 0 : steps.size()));
        }
        for (int i = 0; i < steps.size(); i++) {
            ApprovalStep step = steps.get(i);
            if (step.getStepNumber() == null || step.getStepNumber() != i + 1) {
                throw new IllegalStateException("步骤序号不连续: 期望 " + (i + 1) +
                        ", 实际 " + step.getStepNumber());
            }
        }
    }
}
