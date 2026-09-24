package io.github.matrixlane.metadataregistry.itemmapping.repository;

import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingHierarchy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link MappingHierarchy}（映射层级）。
 */
@Repository
public interface MappingHierarchyRepository extends JpaRepository<MappingHierarchy, UUID> {

    /**
     * 查询某映射的全部下层（subset）映射。
     */
    List<MappingHierarchy> findBySupersetMappingId(UUID supersetMappingId);

    /**
     * 查询某映射的上层（superset）关联（至多一条）。
     */
    Optional<MappingHierarchy> findBySubsetMappingId(UUID subsetMappingId);

    /**
     * 删除某映射的全部下层关联（删除映射前清理）。
     */
    void deleteBySupersetMappingId(UUID supersetMappingId);

    /**
     * 删除某映射的上层关联。
     */
    void deleteBySubsetMappingId(UUID subsetMappingId);
}
