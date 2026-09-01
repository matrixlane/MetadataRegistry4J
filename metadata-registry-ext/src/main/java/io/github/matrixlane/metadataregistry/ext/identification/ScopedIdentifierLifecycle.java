package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Scoped_Identifier 扩展生命周期属性的卫星实体(扩展,非 ISO/IEC 11179 标准模型)。
 *
 * <p>承载创建方式(AUTO/MANUAL)、版本状态与生命周期时间戳。标准实体
 * {@code ScopedIdentifier} 只保留标准属性;本卫星实体与其按
 * {@code scoped_identifier_id} 一一对应,物理上独立成表,阶段 3 起归入
 * EXT schema。
 *
 * <p>有意不建立到 ScopedIdentifier 的 JPA 关联:避免卫星加载反向触发
 * 标准实体对象图,读写一律经 {@code scopedIdentifierId} 值键。
 */
@jakarta.persistence.Entity
@Table(name = "scoped_identifier_lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ScopedIdentifierLifecycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scoped_identifier_id", nullable = false, unique = true)
    private UUID scopedIdentifierId;

    /**
     * 创建时用户选择的标识符生成方式。
     * AUTO = 创建时按命名空间策略自动生成(编辑态 MUST 锁定);
     * MANUAL = 创建时由用户手动录入(编辑态 MUST 可编辑);
     * NULL = 迁移前既存记录或外部命名空间下的 SI(按 MANUAL 等价处理)。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_creation_mode", length = 16)
    private IdentifierCreationMode identifierCreationMode;

    /** 创建时用户选择的版本生成方式。语义与 {@link #identifierCreationMode} 对称。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "version_creation_mode", length = 16)
    private IdentifierCreationMode versionCreationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", length = 16)
    private VersionStatus versionStatus = VersionStatus.DRAFT;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deprecated_at")
    private LocalDateTime deprecatedAt;

    @Column(name = "obsolete_at")
    private LocalDateTime obsoleteAt;
}
