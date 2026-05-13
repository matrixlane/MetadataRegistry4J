package io.github.matrixlane.metadataregistry.itemmapping.repository;

import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link MappingScheme} entities.
 *
 * <p>Provides data access operations for mapping scheme CRUD and
 * version lifecycle management.
 */
@Repository
public interface MappingSchemeRepository
        extends JpaRepository<MappingScheme, UUID>, JpaSpecificationExecutor<MappingScheme> {

    /**
     * Finds all scheme versions with the given parent ID.
     *
     * @param parentId the parent scheme UUID
     * @return list of child schemes (newer versions)
     */
    List<MappingScheme> findByParentId(UUID parentId);

    /**
     * Finds the latest version of a scheme lineage by parent ID,
     * ordered by creation date descending.
     *
     * @param parentId the parent scheme UUID
     * @return the latest child scheme, if any
     */
    Optional<MappingScheme> findTopByParentIdOrderByCreatedAtDesc(UUID parentId);

    /**
     * Finds all schemes with the given status.
     *
     * @param status the mapping scheme status
     * @return list of schemes in that status
     */
    List<MappingScheme> findByStatus(MappingSchemeStatus status);

    /**
     * Checks whether a scheme with the given parent and version already exists.
     *
     * @param parentId the parent scheme UUID (may be null)
     * @param version  the version string
     * @return true if a scheme with that parent+version exists
     */
    boolean existsByParentIdAndVersion(UUID parentId, String version);
}
