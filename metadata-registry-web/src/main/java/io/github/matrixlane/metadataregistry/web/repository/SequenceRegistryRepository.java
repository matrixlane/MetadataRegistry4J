package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.identification.SequenceRegistry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface SequenceRegistryRepository extends JpaRepository<SequenceRegistry, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SequenceRegistry r WHERE r.namespace.id = :namespaceId")
    Optional<SequenceRegistry> findByNamespaceIdWithLock(@Param("namespaceId") UUID namespaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SequenceRegistry r WHERE r.namespace.id = :namespaceId AND r.scopeKey = :scopeKey")
    Optional<SequenceRegistry> findByNamespaceIdAndScopeKeyWithLock(@Param("namespaceId") UUID namespaceId,
                                                                    @Param("scopeKey") String scopeKey);

    Optional<SequenceRegistry> findByNamespaceIdAndScopeKey(UUID namespaceId, String scopeKey);

    // 派生删除查询不走 SimpleJpaRepository 的事务模板，需要显式开启事务
    @Transactional
    void deleteByNamespaceId(UUID namespaceId);
}
