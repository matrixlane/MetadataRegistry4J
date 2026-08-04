package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RegistrationAuthorityRepository
        extends JpaRepository<RegistrationAuthority, UUID>,
                JpaSpecificationExecutor<RegistrationAuthority> {

    /**
     * 按 RAI（Registration_Authority_Identifier 的 organizationIdentifier）
     * 匹配注册机构。注册表合并以 RAI 一致为前置：来源节点的 RAI 必须能在
     * 本实例匹配到注册机构，视为同一注册机构在不同实例的表示。
     */
    @Query("SELECT ra FROM RegistrationAuthority ra"
            + " WHERE ra.registrationAuthorityIdentifier.organizationIdentifier = :rai")
    Optional<RegistrationAuthority> findByRai(@Param("rai") String rai);
}
