package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingSchemeRepository;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing mapping scheme CRUD and version lifecycle.
 *
 * <p>Handles:
 * <ul>
 *   <li>CRUD operations for mapping schemes</li>
 *   <li>Version lifecycle: Draft → Published → Archived</li>
 *   <li>Version generation (initial 0.1.0, auto-increment patch)</li>
 *   <li>New version creation from published schemes</li>
 * </ul>
 *
 * <h3>State Machine</h3>
 * <pre>
 * Draft ──[publish]──> Published ──[archive]──> Archived
 *   │
 *   └──[new-version]──> New Draft (patch incremented, parentId set)
 * </pre>
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>Only DRAFT schemes can be edited or deleted</li>
 *   <li>PUBLISHED schemes are locked (read-only)</li>
 *   <li>ARCHIVED schemes are read-only</li>
 *   <li>Creating a new version from PUBLISHED increments the patch number</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MappingSchemeService {

    private final MappingSchemeRepository repository;

    // ==================== CRUD Operations ====================

    /**
     * Lists all mapping schemes with optional keyword search.
     *
     * @param keyword  optional search keyword (matches name, sourceUri, targetUri)
     * @param pageable pagination info
     * @return paged list of mapping scheme responses
     */
    @Transactional(readOnly = true)
    public Page<MappingSchemeResponse> list(String keyword, Pageable pageable) {
        Specification<MappingScheme> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("name")), like),
                            cb.like(cb.lower(root.get("sourceUri")), like),
                            cb.like(cb.lower(root.get("targetUri")), like),
                            cb.like(cb.lower(root.get("version")), like)
                    ));
        }
        return repository.findAll(spec, pageable).map(MappingSchemeService::toResponse);
    }

    /**
     * Gets a single mapping scheme by ID.
     *
     * @param id the scheme UUID
     * @return the scheme response
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public MappingSchemeResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    /**
     * Creates a new mapping scheme with initial version {@code 0.1.0}
     * and status {@link MappingSchemeStatus#DRAFT}.
     *
     * @param request the creation request
     * @return the created scheme response
     */
    public MappingSchemeResponse create(MappingSchemeRequest request) {
        MappingScheme entity = new MappingScheme(
                request.name(),
                request.sourceUri(),
                request.targetUri(),
                request.description()
        );
        try {
            return toResponse(repository.save(entity));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    "创建映射方案失败，可能存在重复数据: " + e.getMessage());
        }
    }

    /**
     * Updates a DRAFT mapping scheme's basic information.
     *
     * @param id      the scheme UUID
     * @param request the update request
     * @return the updated scheme response
     * @throws BusinessException if the scheme is not in DRAFT status
     */
    public MappingSchemeResponse update(UUID id, MappingSchemeRequest request) {
        MappingScheme entity = loadOrThrow(id);

        if (entity.getStatus() != MappingSchemeStatus.DRAFT) {
            throw new BusinessException("只能编辑草稿状态的映射方案（当前状态: " + entity.getStatus() + "）");
        }

        entity.setName(request.name());
        entity.setSourceUri(request.sourceUri());
        entity.setTargetUri(request.targetUri());
        entity.setDescription(request.description());
        entity.setUpdatedAt(LocalDateTime.now());

        try {
            return toResponse(repository.save(entity));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    "更新映射方案失败，可能存在重复数据: " + e.getMessage());
        }
    }

    /**
     * Deletes a DRAFT mapping scheme.
     *
     * @param id the scheme UUID
     * @throws BusinessException if the scheme is not in DRAFT status
     */
    public void delete(UUID id) {
        MappingScheme entity = loadOrThrow(id);

        if (entity.getStatus() != MappingSchemeStatus.DRAFT) {
            throw new BusinessException("只能删除草稿状态的映射方案（当前状态: " + entity.getStatus() + "）");
        }

        repository.delete(entity);
        log.info("Deleted mapping scheme: {} ({})", entity.getName(), entity.getId());
    }

    // ==================== Lifecycle Operations ====================

    /**
     * Transitions the scheme to a new status.
     *
     * <p>Valid transitions:
     * <ul>
     *   <li>DRAFT → PUBLISHED (publish)</li>
     *   <li>PUBLISHED → ARCHIVED (archive)</li>
     * </ul>
     *
     * @param id   the scheme UUID
     * @param to   the target status
     * @return the updated scheme response
     * @throws BusinessException if the transition is invalid
     */
    public MappingSchemeResponse transitionStatus(UUID id, MappingSchemeStatus to) {
        MappingScheme entity = loadOrThrow(id);

        validateTransition(entity.getStatus(), to, entity.getName());

        entity.setStatus(to);
        entity.setUpdatedAt(LocalDateTime.now());

        MappingSchemeResponse response = toResponse(repository.save(entity));
        log.info("Mapping scheme '{}' ({}) transitioned: {} → {}",
                entity.getName(), entity.getId(), entity.getStatus(), to);
        return response;
    }

    /**
     * Creates a new DRAFT version derived from a PUBLISHED scheme.
     *
     * <p>The new version inherits the name, sourceUri, targetUri, and description
     * from the parent. The version number is auto-incremented (patch + 1).
     * The parent reference is set to the published version's ID.
     *
     * @param id the UUID of the PUBLISHED scheme to create a new version from
     * @return the new draft scheme response
     * @throws BusinessException if the scheme is not PUBLISHED
     */
    public MappingSchemeResponse createNewVersion(UUID id) {
        MappingScheme parent = loadOrThrow(id);

        if (parent.getStatus() != MappingSchemeStatus.PUBLISHED) {
            throw new BusinessException(
                    "只能基于已发布（PUBLISHED）的方案创建新版本（当前状态: " + parent.getStatus() + "）");
        }

        String newVersion = parent.nextPatchVersion();

        // Ensure unique constraint: (parent_id, version)
        if (repository.existsByParentIdAndVersion(parent.getId(), newVersion)) {
            throw new BusinessException(
                    "版本 " + newVersion + " 已存在，请重试");
        }

        MappingScheme newVersionEntity = new MappingScheme(
                parent.getName(),
                parent.getSourceUri(),
                parent.getTargetUri(),
                parent.getDescription()
        );
        newVersionEntity.setVersion(newVersion);
        newVersionEntity.setParentId(parent.getId());
        newVersionEntity.setStatus(MappingSchemeStatus.DRAFT);

        MappingSchemeResponse response = toResponse(repository.save(newVersionEntity));
        log.info("Created new version '{}' for mapping scheme '{}' (parent: {})",
                newVersion, parent.getName(), parent.getId());
        return response;
    }

    // ==================== Internal Helpers ====================

    /**
     * Validates a status transition against the allowed state machine.
     */
    private void validateTransition(MappingSchemeStatus from, MappingSchemeStatus to, String name) {
        if (from == to) {
            throw new BusinessException("映射方案 '" + name + "' 已经是 " + to + " 状态");
        }

        boolean valid = switch (from) {
            case DRAFT -> to == MappingSchemeStatus.PUBLISHED;
            case PUBLISHED -> to == MappingSchemeStatus.ARCHIVED;
            case ARCHIVED -> false; // ARCHIVED is terminal
        };

        if (!valid) {
            throw new BusinessException(
                    "无效的状态迁移: " + from + " → " + to +
                    "（允许的迁移: DRAFT→PUBLISHED, PUBLISHED→ARCHIVED）");
        }
    }

    private MappingScheme loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MappingScheme", id));
    }

    private static MappingSchemeResponse toResponse(MappingScheme entity) {
        return new MappingSchemeResponse(
                entity.getId(),
                entity.getName(),
                entity.getSourceUri(),
                entity.getTargetUri(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getParentId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Returns the service's repository for testing purposes.
     */
    MappingSchemeRepository getRepository() {
        return repository;
    }
}
