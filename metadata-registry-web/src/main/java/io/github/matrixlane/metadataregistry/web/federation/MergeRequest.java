package io.github.matrixlane.metadataregistry.web.federation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 合并请求（Git Pull Request 的等价物）。
 */
@Entity
@Table(name = "merge_request")
@Getter
@Setter
@NoArgsConstructor
public class MergeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_id", nullable = false)
    private RegistryNode sourceNode;

    /**
     * 目标命名空间（评审范围的边界）。
     */
    @Column(name = "target_namespace_id", nullable = false)
    private UUID targetNamespaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MergeRequestStatus status = MergeRequestStatus.DRAFT;

    /**
     * 合并包引用（文件路径或 "inline"）。
     */
    @Column(name = "bundle_ref", length = 512)
    private String bundleRef;

    @Column(name = "submitted_by", length = 128)
    private String submittedBy;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
