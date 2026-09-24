package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 注册流程迁移规则（registration-process-designer 设计文档 §4.1）。
 *
 * <p>迁移白名单、审核点与通过规则按注册机构（Registration_Authority）作用域
 * 持久化；标准默认流程由应用层
 * {@code RegistrationProcessService.ensureSeed} 幂等播种。本期无编辑入口，
 * 结构为每注册机构定制预留。</p>
 *
 * <p>唯一约束 {@code (registration_authority_id, from_status, to_status)}。</p>
 */
@jakarta.persistence.Entity
@Table(name = "registration_transition_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_rtr_authority_from_to",
                columnNames = {"registration_authority_id", "from_status", "to_status"}))
@Getter
@Setter
@NoArgsConstructor
public class RegistrationTransitionRule extends Entity {

    /** 规则所属注册机构（决策 4：每 RA 一套流程）。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_authority_id", nullable = false)
    private RegistrationAuthority registrationAuthority;

    /** 源注册状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private RegistrationStatus fromStatus;

    /** 目标注册状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private RegistrationStatus toStatus;

    /** 操作命名（如"提议"）；空 = 使用标准迁移名。本期种子默认空。 */
    @Column(name = "display_name")
    private String displayName;

    /** 是否需注册机构批准后方可生效（默认审核点）。 */
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    /** 审核人模式：全部 Registrar（默认）/ 指定集合。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "approver_mode", nullable = false, length = 32)
    private ApproverMode approverMode = ApproverMode.ALL_REGISTRARS;

    /** 通过规则：任一人（默认）/ 至少 N 人。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "quorum_type", nullable = false, length = 16)
    private QuorumType quorumType = QuorumType.ANY_ONE;

    /** AT_LEAST 时的通过人数；ANY_ONE 时为空。 */
    @Column(name = "quorum_count")
    private Integer quorumCount;

    /** 禁用后该迁移不可执行（未来定制入口；本期种子恒 true）。 */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** SELECTED_REGISTRARS 时的指定审核人集合（子表 registration_transition_rule_approver）。 */
    @ElementCollection
    @CollectionTable(name = "registration_transition_rule_approver",
            joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "registrar_id")
    private Set<UUID> approverRegistrarIds = new HashSet<>();
}
