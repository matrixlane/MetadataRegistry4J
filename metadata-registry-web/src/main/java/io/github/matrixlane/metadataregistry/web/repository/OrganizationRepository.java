package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository
        extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {

    /**
     * 按名称（Sign.value）查找组织，用于迁移命名空间副本的
     * naming_authority 解析。
     */
    @Query("SELECT o FROM Organization o JOIN o.name n WHERE n.value = :name")
    List<Organization> findByNameValue(@Param("name") String name);
}
