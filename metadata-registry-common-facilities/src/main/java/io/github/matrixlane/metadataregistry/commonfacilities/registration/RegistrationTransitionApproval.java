package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 注册流程审批记录（registration-process-designer 设计文档 §4.2）。
 *
 * <p>对需审核的迁移，合格 Registrar 累积批准：同一 Registrar 对同一
 * (item, from, to) 只计一次（唯一约束兜底）；去重后批准人数达到规则的
 * 通过人数时，在同一事务内完成状态迁移并清除该项全部审批记录。
 * 审核只有"批准"一个操作，无驳回、无发起动作（决策 3）。</p>
 */
@jakarta.persistence.Entity
@Table(name = "registration_transition_approval",
        uniqueConstraints = @UniqueConstraint(name = "uk_rta_item_from_to_registrar",
                columnNames = {"administered_item_id", "from_status", "to_status", "registrar_id"}))
@Getter
@Setter
@NoArgsConstructor
public class RegistrationTransitionApproval extends Entity {

    /** 被审批的注册项。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "administered_item_id", nullable = false)
    private AdministeredItem administeredItem;

    /** 针对的迁移：源状态 / 目标状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private RegistrationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private RegistrationStatus toStatus;

    /** 批准人（Registrar 身份）。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "registrar_id", nullable = false)
    private Registrar registrar;

    /** 操作账号（批准动作由哪个账号发起）。 */
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;
}
