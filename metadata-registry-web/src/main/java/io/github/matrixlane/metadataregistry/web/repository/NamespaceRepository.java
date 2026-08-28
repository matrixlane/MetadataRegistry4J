package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NamespaceRepository
        extends JpaRepository<Namespace, UUID>, JpaSpecificationExecutor<Namespace> {

    boolean existsByShorthandPrefix(String shorthandPrefix);

    Optional<Namespace> findByShorthandPrefix(String shorthandPrefix);

    boolean existsByRegistrationAuthorityIsNotNull();

    @Query("SELECT ns FROM Namespace ns WHERE ns.schemeReference = :schemeReference")
    Optional<Namespace> findBySchemeReference(@Param("schemeReference") String schemeReference);

    /**
     * 取与给定 Registration_Authority 直接关联、且 schemeReference 非 system-default
     * 的自有命名空间。1:1 不变量由 active-registrar-ownership 能力保障。
     * 用于 ItemAdministrationService 默认命名空间推导。
     */
    Optional<Namespace> findFirstByRegistrationAuthority_IdAndSchemeReferenceNot(
            UUID registrationAuthorityId, String schemeReference);
}
