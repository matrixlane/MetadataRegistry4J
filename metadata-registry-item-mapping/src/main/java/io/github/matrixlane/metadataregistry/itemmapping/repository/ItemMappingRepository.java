package io.github.matrixlane.metadataregistry.itemmapping.repository;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ItemMapping}（条目映射）。
 *
 * <p>注意：Item 是 JOINED 继承根（30+ 子类表），任何直接加载 Item 实体
 * 的操作都会触发多态整图 join（超出 SQLite 单条 SQL 的 FROM 项上限）。
 * 因此批量/遍历场景一律使用下方返回标量行（mappingId / itemId）的
 * 投影查询，不初始化 Item 代理。
 */
@Repository
public interface ItemMappingRepository extends JpaRepository<ItemMapping, UUID> {

    // ---- 分页过滤（过滤条件走外键列，不产生 Item join） ----

    Page<ItemMapping> findByDegree(MappingDegree degree, Pageable pageable);

    Page<ItemMapping> findBySubjects_Item_Id(UUID subjectItemId, Pageable pageable);

    Page<ItemMapping> findByObjects_Item_Id(UUID objectItemId, Pageable pageable);

    Page<ItemMapping> findBySubjects_Item_IdAndDegree(UUID subjectItemId, MappingDegree degree,
                                                     Pageable pageable);

    Page<ItemMapping> findByObjects_Item_IdAndDegree(UUID objectItemId, MappingDegree degree,
                                                    Pageable pageable);

    Page<ItemMapping> findBySubjects_Item_IdAndObjects_Item_Id(UUID subjectItemId, UUID objectItemId,
                                                             Pageable pageable);

    Page<ItemMapping> findBySubjects_Item_IdAndObjects_Item_IdAndDegree(UUID subjectItemId,
                                                                      UUID objectItemId,
                                                                      MappingDegree degree,
                                                                      Pageable pageable);

    // ---- 标量投影查询（BFS 遍历 / 批量组装用，不加载 Item） ----

    /**
     * 主体为给定条目的全部映射 ID。
     */
    @Query("SELECT DISTINCT s.itemMapping.id FROM ItemMappingSubject s WHERE s.item.id = :itemId")
    List<UUID> findMappingIdsBySubjectItemId(@Param("itemId") UUID itemId);

    /**
     * 客体为给定条目的全部映射 ID。
     */
    @Query("SELECT DISTINCT o.itemMapping.id FROM ItemMappingObject o WHERE o.item.id = :itemId")
    List<UUID> findMappingIdsByObjectItemId(@Param("itemId") UUID itemId);

    /**
     * 涉及给定条目（主体或客体）的全部映射 ID。
     */
    default List<UUID> findMappingIdsByItemId(UUID itemId) {
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>(findMappingIdsBySubjectItemId(itemId));
        ids.addAll(findMappingIdsByObjectItemId(itemId));
        return List.copyOf(ids);
    }

    /**
     * 批量取映射度：[mappingId, degree]。
     */
    @Query("SELECT im.id, im.degree FROM ItemMapping im WHERE im.id IN :ids")
    List<Object[]> findDegreesByMappingIds(@Param("ids") List<UUID> ids);

    /**
     * 批量取主体行：[mappingId, subjectItemId, mappingVariableName]。
     */
    @Query("SELECT s.itemMapping.id, s.item.id, s.mappingVariableName FROM ItemMappingSubject s " +
           "WHERE s.itemMapping.id IN :ids")
    List<Object[]> findSubjectRowsByMappingIds(@Param("ids") List<UUID> ids);

    /**
     * 批量取客体行：[mappingId, objectItemId, mappingVariableName]。
     */
    @Query("SELECT o.itemMapping.id, o.item.id, o.mappingVariableName FROM ItemMappingObject o " +
           "WHERE o.itemMapping.id IN :ids")
    List<Object[]> findObjectRowsByMappingIds(@Param("ids") List<UUID> ids);
}
