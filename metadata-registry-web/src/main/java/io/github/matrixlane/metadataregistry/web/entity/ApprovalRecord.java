package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审批记录实体。
 *
 * <p>记录每次元数据项的审批操作，支持完整的1级审批工作流：</p>
 * <ul>
 *   <li>提交人提交 → PENDING</li>
 *   <li>审批人批准 → APPROVED（触发ISO状态迁移）</li>
 *   <li>审批人退回 → REJECTED（保留审批意见）</li>
 *   <li>提交人修改后重新提交 → 创建新的 PENDING 记录</li>
 * </ul>
 *
 * <p>使用乐观锁（{@code @Version}）防止并发审批竞态。\u003c/p>
 */
@Entity
@Table(name = "approval_record")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "administered_item_id", nullable = false)
    private UUID administeredItemId;

    @Column(name = "submitter", nullable = false, length = 100)
    private String submitter;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "approver", length = 100)
    private String approver;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "opinion", length = 2000)
    private String opinion;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private ApprovalResult result;

    @Version
    @Column(name = "version")
    private Long version;

    public static ApprovalRecord ofPending(UUID administeredItemId, String submitter) {
        ApprovalRecord record = new ApprovalRecord();
        record.setAdministeredItemId(administeredItemId);
        record.setSubmitter(submitter);
        record.setSubmittedAt(LocalDateTime.now());
        record.setResult(ApprovalResult.PENDING);
        return record;
    }

    public void approve(String approver, String opinion) {
        this.approver = approver;
        this.opinion = opinion;
        this.approvedAt = LocalDateTime.now();
        this.result = ApprovalResult.APPROVED;
    }

    public void reject(String approver, String opinion) {
        this.approver = approver;
        this.opinion = opinion;
        this.approvedAt = LocalDateTime.now();
        this.result = ApprovalResult.REJECTED;
    }
}
