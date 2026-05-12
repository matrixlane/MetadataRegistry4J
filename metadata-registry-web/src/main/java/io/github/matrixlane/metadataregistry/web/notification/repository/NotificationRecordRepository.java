package io.github.matrixlane.metadataregistry.web.notification.repository;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, UUID> {

    Page<NotificationRecord> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    @Query("SELECT n FROM NotificationRecord n WHERE n.recipientId = :recipientId "
            + "AND n.channel = :channel AND n.status IN (:statuses) "
            + "ORDER BY n.createdAt DESC")
    List<NotificationRecord> findUnreadByRecipient(@Param("recipientId") String recipientId,
                                                    @Param("channel") NotificationChannel channel,
                                                    @Param("statuses") List<NotificationStatus> statuses);

    @Query("SELECT n FROM NotificationRecord n WHERE n.status = 'PENDING' "
            + "AND n.retryCount > 0 AND n.nextRetryAt <= :now")
    List<NotificationRecord> findPendingRetries(@Param("now") LocalDateTime now);

    List<NotificationRecord> findByRelatedBusinessId(String relatedBusinessId);

    long countByRecipientIdAndChannelAndStatusIn(String recipientId, NotificationChannel channel,
                                                   List<NotificationStatus> statuses);

    NotificationRecord findFirstByRecipientIdAndChannelAndRelatedBusinessIdOrderByCreatedAtDesc(
            String recipientId, NotificationChannel channel, String relatedBusinessId);
}
