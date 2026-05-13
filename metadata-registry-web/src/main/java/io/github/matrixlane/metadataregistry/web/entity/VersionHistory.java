package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "version_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "version_number"})
)
@Getter
@Setter
@NoArgsConstructor
public class VersionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false, length = 36)
    private UUID itemId;

    @Column(name = "item_type", nullable = false, length = 64)
    private String itemType;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "change_type", nullable = false, length = 16)
    private String changeType;

    @Column(name = "changed_by", nullable = false, length = 128)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String snapshotJson;

    @Column(name = "change_summary", length = 512)
    private String changeSummary;

    public static VersionHistory of(
            UUID itemId,
            String itemType,
            Integer versionNumber,
            String changeType,
            String changedBy,
            LocalDateTime changedAt,
            String snapshotJson,
            String changeSummary) {
        VersionHistory vh = new VersionHistory();
        vh.itemId = itemId;
        vh.itemType = itemType;
        vh.versionNumber = versionNumber;
        vh.changeType = changeType;
        vh.changedBy = changedBy;
        vh.changedAt = changedAt;
        vh.snapshotJson = snapshotJson;
        vh.changeSummary = changeSummary;
        return vh;
    }
}
