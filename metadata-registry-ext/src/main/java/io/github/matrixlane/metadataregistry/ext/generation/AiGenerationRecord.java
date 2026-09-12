package io.github.matrixlane.metadataregistry.ext.generation;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 生成来源记录：一次 AI 生成内容的溯源信息（任务、Agent、生成时间、操作者、
 * 结果种类、输出原文、动作类型），关联到所属受管条目。
 *
 * <p>建表由 Hibernate 依据实体元数据管理（含下方索引注解，不写迁移脚本）；
 * schema 归属遵循 MDR/EXT 拆分约定——不硬编码 schema，team profile 经
 * {@code orm-team.xml} 登记到 ext schema。
 */
@Entity
@Table(name = "ai_generation_record",
        indexes = @Index(name = "idx_ai_generation_record_item", columnList = "administered_item_id"))
@Getter
@Setter
@NoArgsConstructor
public class AiGenerationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** 所属受管条目（生成内容所落的注册项目）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administered_item_id", nullable = false)
    private AdministeredItem administeredItem;

    /** 协作任务 id（如 explain-selection）。 */
    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    /** 提供方（Agent）id（如 claude / codex / kimi / direct）。 */
    @Column(name = "provider_id", length = 64)
    private String providerId;

    /** Agent 显示名。 */
    @Column(name = "agent_display_name", length = 128)
    private String agentDisplayName;

    /** 生成时间（服务端落库时刻）。 */
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    /** 操作者（当前登录用户名）。 */
    @Column(name = "operator", nullable = false, length = 128)
    private String operator;

    /** 结果种类：CANDIDATES / TEXT / FINDINGS（与协作任务输出种类一致）。 */
    @Column(name = "output_kind", nullable = false, length = 16)
    private String outputKind;

    /** AI 输出原文。 */
    @Lob
    @Column(name = "raw_output", nullable = false)
    private String rawOutput;

    /** 动作类型（如 SAVED_TO_EXPLANATORY_COMMENT）。 */
    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    public AiGenerationRecord(AdministeredItem administeredItem, String taskId, String providerId,
                              String agentDisplayName, String operator, String outputKind,
                              String rawOutput, String actionType) {
        this.administeredItem = administeredItem;
        this.taskId = taskId;
        this.providerId = providerId;
        this.agentDisplayName = agentDisplayName;
        this.operator = operator;
        this.outputKind = outputKind;
        this.rawOutput = rawOutput;
        this.actionType = actionType;
        this.generatedAt = LocalDateTime.now();
    }
}
