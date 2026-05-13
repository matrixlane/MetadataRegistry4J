package io.github.matrixlane.metadataregistry.itemmapping.repository;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ItemMapping} entities.
 *
 * <p>Provides data access operations for item mappings including querying
 * by subject/object item IDs and mapping types.
 */
@Repository
public interface ItemMappingRepository extends JpaRepository<ItemMapping, UUID> {

    /**
     * Finds all mappings where the subject references the given item ID.
     *
     * @param itemId the AdministeredItem UUID
     * @return list of mappings with the given item as subject
     */
    @Query("SELECT im FROM ItemMapping im JOIN FETCH im.subject s JOIN FETCH im.object o " +
           "WHERE s.itemId = :itemId")
    List<ItemMapping> findBySubjectItemId(@Param("itemId") UUID itemId);

    /**
     * Finds all mappings where the object references the given item ID.
     *
     * @param itemId the AdministeredItem UUID
     * @return list of mappings with the given item as object
     */
    @Query("SELECT im FROM ItemMapping im JOIN FETCH im.subject s JOIN FETCH im.object o " +
           "WHERE o.itemId = :itemId")
    List<ItemMapping> findByObjectItemId(@Param("itemId") UUID itemId);

    /**
     * Finds all mappings involving the given item ID (as either subject or object).
     *
     * @param itemId the AdministeredItem UUID
     * @return list of all mappings that reference the given item
     */
    @Query("SELECT im FROM ItemMapping im JOIN FETCH im.subject s JOIN FETCH im.object o " +
           "WHERE s.itemId = :itemId OR o.itemId = :itemId")
    List<ItemMapping> findByItemId(@Param("itemId") UUID itemId);

    /**
     * Finds mappings by type.
     *
     * @param mappingType the mapping type to filter by
     * @return list of mappings of the given type
     */
    List<ItemMapping> findByMappingType(MappingType mappingType);

    /**
     * Checks whether a mapping already exists between the given subject and object.
     *
     * @param subjectId the subject ID
     * @param objectId  the object ID
     * @return true if a mapping exists between subject and object
     */
    @Query("SELECT COUNT(im) > 0 FROM ItemMapping im " +
           "WHERE im.subject.id = :subjectId AND im.object.id = :objectId")
    boolean existsBySubjectAndObject(@Param("subjectId") UUID subjectId,
                                     @Param("objectId") UUID objectId);

    /**
     * Finds all mappings belonging to a specific mapping scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return list of mappings in the given scheme
     */
    @Query("SELECT im FROM ItemMapping im JOIN FETCH im.subject s JOIN FETCH im.object o " +
           "WHERE im.mappingScheme.id = :schemeId")
    List<ItemMapping> findByMappingSchemeId(@Param("schemeId") UUID schemeId);

    /**
     * Counts mappings belonging to a specific mapping scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return count of mappings in the given scheme
     */
    long countByMappingSchemeId(@Param("schemeId") UUID schemeId);

    /**
     * Counts mappings with a non-null description in the given scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return count of mappings with description
     */
    @Query("SELECT COUNT(im) FROM ItemMapping im " +
           "WHERE im.mappingScheme.id = :schemeId AND im.description IS NOT NULL " +
           "AND im.description <> ''")
    long countByMappingSchemeIdAndDescriptionNotEmpty(@Param("schemeId") UUID schemeId);

    /**
     * Counts mappings with a non-null transformation rule in the given scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return count of mappings with transformation rule
     */
    @Query("SELECT COUNT(im) FROM ItemMapping im " +
           "WHERE im.mappingScheme.id = :schemeId AND im.transformationRule IS NOT NULL " +
           "AND im.transformationRule <> ''")
    long countByMappingSchemeIdAndTransformationRuleNotEmpty(@Param("schemeId") UUID schemeId);

    /**
     * Finds all distinct item IDs that appear as either subject or object in mappings
     * within a given mapping scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return list of distinct item IDs
     */
    @Query("SELECT DISTINCT s.itemId FROM ItemMapping im " +
           "JOIN im.subject s WHERE im.mappingScheme.id = :schemeId AND s.itemId IS NOT NULL")
    List<UUID> findDistinctSubjectItemIdsBySchemeId(@Param("schemeId") UUID schemeId);

    /**
     * Finds all distinct object item IDs for mappings in a given scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return list of distinct object item IDs
     */
    @Query("SELECT DISTINCT o.itemId FROM ItemMapping im " +
           "JOIN im.object o WHERE im.mappingScheme.id = :schemeId AND o.itemId IS NOT NULL")
    List<UUID> findDistinctObjectItemIdsBySchemeId(@Param("schemeId") UUID schemeId);

    /**
     * Finds all distinct mapping types used in mappings for a given scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return list of distinct mapping types
     */
    @Query("SELECT DISTINCT im.mappingType FROM ItemMapping im " +
           "WHERE im.mappingScheme.id = :schemeId")
    List<MappingType> findDistinctMappingTypesBySchemeId(@Param("schemeId") UUID schemeId);

    /**
     * Recursively finds all item IDs reachable from the given item ID via
     * internal-to-internal mappings, up to 3 levels deep.
     *
     * <p>Uses a recursive CTE compatible with PostgreSQL and SQL Server.
     * This is intended as a database-level optimization for large mapping networks.
     *
     * @param itemId the starting item UUID
     * @return list of reachable item IDs (excluding the starting item)
     */
    @Query(value = """
        WITH RECURSIVE mapping_path(start_item, neighbor_item, depth) AS (
            SELECT s.item_id, o.item_id, 1
            FROM item_mapping im
            JOIN item_mapping_subject s ON im.subject_id = s.id
            JOIN item_mapping_object o ON im.object_id = o.id
            WHERE s.item_id = :itemId AND o.item_id IS NOT NULL

            UNION ALL

            SELECT s.item_id, o.item_id, mp.depth + 1
            FROM mapping_path mp
            JOIN item_mapping im ON (im.subject_id IN (
                SELECT id FROM item_mapping_subject WHERE item_id = mp.neighbor_item
            ) OR im.object_id IN (
                SELECT id FROM item_mapping_object WHERE item_id = mp.neighbor_item
            ))
            JOIN item_mapping_subject s ON im.subject_id = s.id
            JOIN item_mapping_object o ON im.object_id = o.id
            WHERE mp.depth < 3
              AND s.item_id IS NOT NULL
              AND o.item_id IS NOT NULL
              AND s.item_id <> mp.start_item
        )
        SELECT DISTINCT neighbor_item FROM mapping_path
        """, nativeQuery = true)
    List<UUID> findReachableItemIdsByRecursiveCte(@Param("itemId") UUID itemId);
}
