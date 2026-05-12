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
}
