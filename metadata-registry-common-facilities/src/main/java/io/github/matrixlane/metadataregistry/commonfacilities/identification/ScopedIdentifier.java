package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ISO/IEC 11179-3:2023, Clause 7 — Scoped Identifier
 *
 * <p>Associates a business identifier with a namespace and an identified item.
 * The {@code id} field is the technical primary key; {@code identifier} is the
 * business identifier whose value is immutable once assigned.
 *
 * <p>An Item may have multiple ScopedIdentifiers across different Namespaces
 * (e.g., one in the local hospital namespace, another mirroring the LOINC
 * external standard). The {@code identifier} value is unique within a single
 * Namespace.
 */
@StandardReference("ISO/IEC 11179-3:2023, Clause 7")
@Entity
@Table(name = "scoped_identifier",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scoped_identifier_ns_identifier",
                columnNames = {"namespace_id", "identifier"}))
@Getter
@Setter
@NoArgsConstructor
public class ScopedIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Business identifier — immutable once assigned.
     */
    @Column(name = "identifier", nullable = false, length = 255)
    private String identifier;

    /**
     * 创建时用户选择的标识符生成方式。
     * AUTO = 创建时按命名空间策略自动生成（编辑态 MUST 锁定）；
     * MANUAL = 创建时由用户手动录入（编辑态 MUST 可编辑）；
     * NULL = 迁移前既存记录或外部命名空间下的 SI（按 MANUAL 等价处理）。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_creation_mode", length = 16)
    private IdentifierCreationMode identifierCreationMode;

    /**
     * 创建时用户选择的版本生成方式。语义与 {@link #identifierCreationMode} 对称。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "version_creation_mode", length = 16)
    private IdentifierCreationMode versionCreationMode;

    /** ISO/IEC 11179-3:2023 表 12(Scoped_Identifier):version 1,必备。 */
    @Column(name = "version", nullable = false, length = 32)
    private String version;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "namespace_id")
    private Namespace namespace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identified_item_id")
    private IdentifiedItem identifiedItem;

    /**
     * Convenience constructor for programmatic creation.
     */
    public ScopedIdentifier(String identifier,
                            Namespace namespace, IdentifiedItem identifiedItem) {
        this.identifier = identifier;
        this.namespace = namespace;
        this.identifiedItem = identifiedItem;
    }

    /**
     * ISO/IEC 11179-3:2023 表 12:full_expansion 0..1,Conditional,derived。
     * 仅当所属 Namespace 自身恰好有一个标识符时可派生:将命名空间的唯一标识符
     * 与本标识符组合以完整限定作用域。组合方式因命名空间类型而异(标准原文
     * "The precise manner of derivation can vary"),本实现采用
     * "{命名空间标识符}:{标识符}"。不落库,纯派生计算。
     */
    public String getFullExpansion() {
        Namespace ns = this.namespace;
        if (ns == null || ns.getIdentifiedItem() == null) {
            return null;
        }
        java.util.List<ScopedIdentifier> nsIds = ns.getIdentifiedItem().getScopedIdentifiers();
        if (nsIds == null || nsIds.size() != 1) {
            return null;
        }
        String nsIdentifier = nsIds.get(0).getIdentifier();
        if (nsIdentifier == null || nsIdentifier.isBlank() || this.identifier == null) {
            return null;
        }
        return nsIdentifier + ":" + this.identifier;
    }

    /**
     * ISO/IEC 11179-3:2023 表 12:shorthand_expansion 0..1,Conditional,derived。
     * 将所属 Namespace.shorthand_prefix 前置到本标识符;命名空间未配置前缀时
     * 为 null。不落库,纯派生计算。
     */
    public String getShorthandExpansion() {
        Namespace ns = this.namespace;
        if (ns == null || ns.getShorthandPrefix() == null || ns.getShorthandPrefix().isBlank()
                || this.identifier == null) {
            return null;
        }
        return ns.getShorthandPrefix() + this.identifier;
    }
}