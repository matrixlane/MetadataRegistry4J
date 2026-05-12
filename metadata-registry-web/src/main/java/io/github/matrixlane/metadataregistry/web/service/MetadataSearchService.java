package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service providing unified search across all metadata entity types.
 * <p>
 * Searches across Designation (name), ScopedIdentifier (business identifier),
 * and Definition (definition text) with optional filters for entity type
 * and registration status.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetadataSearchService {

    private final AdministeredItemRepository repository;

    /**
     * Unified search across all metadata entities.
     *
     * @param keyword  search keyword (matched against name, identifier, definition text)
     * @param type     optional entity type to filter (e.g., "DataElement", "ObjectClass")
     * @param status   optional registration status to filter
     * @param pageable pagination parameters
     * @return paginated search results as AdministeredItemResponse
     */
    public Page<AdministeredItemResponse> search(String keyword, String type,
                                                  RegistrationStatus status, Pageable pageable) {
        Specification<AdministeredItem> spec = buildSpecification(keyword, type, status);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    private Specification<AdministeredItem> buildSpecification(String keyword, String type,
                                                               RegistrationStatus status) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            // AdministeredItem IS-A IdentifiedItem which has an 'item' property (Item)
            Join<AdministeredItem, Item> itemJoin = (Join) root.join("item");

            // --- Keyword search ---
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";

                // Join to Designation for name search (sign.value)
                Join<Object, Object> desJoin = (Join) itemJoin.join("designations", JoinType.LEFT);

                // Join to Definition for definition text search (text.value)
                Join<Object, Object> defJoin = (Join) itemJoin.join("definitions", JoinType.LEFT);

                // Join to ScopedIdentifier for identifier search
                Join<Object, Object> siJoin = (Join) root.join("scopedIdentifiers", JoinType.LEFT);

                Predicate keywordPredicate = cb.or(
                        cb.like(cb.lower(desJoin.get("sign").get("value")), pattern),
                        cb.like(cb.lower(defJoin.get("text").get("value")), pattern),
                        cb.like(cb.lower(siJoin.get("identifier")), pattern)
                );
                predicates.add(keywordPredicate);
            }

            // --- Entity type filter ---
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(itemJoin.type(), type));
            }

            // --- Registration status filter ---
            if (status != null) {
                predicates.add(
                        cb.equal(root.get("registrationState").get("registrationStatus"), status)
                );
            }

            // Default order: creationDateTime descending
            query.orderBy(cb.desc(root.get("creationDateTime")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AdministeredItemResponse toResponse(AdministeredItem ai) {
        Item item = ai.getItem();
        Item realItem = item == null ? null : (Item) Hibernate.unproxy(item);
        UUID itemId = realItem == null ? null : realItem.getId();
        String itemType = realItem == null ? null : realItem.getClass().getSimpleName();
        String itemLabel = realItem == null ? null : ItemNamingHelper.preferredDesignation(realItem);

        String identifier = null;
        String version = null;
        UUID nsId = null;
        String nsLabel = null;
        if (ai.getScopedIdentifiers() != null && !ai.getScopedIdentifiers().isEmpty()) {
            ScopedIdentifier si = ai.getScopedIdentifiers().get(0);
            identifier = si.getIdentifier();
            version = si.getVersion();
            if (si.getNamespace() != null) {
                nsId = si.getNamespace().getId();
                nsLabel = ItemNamingHelper.preferredDesignation(si.getNamespace());
            }
        }

        UUID raId = null;
        String raLabel = null;
        RegistrationAuthority ra = ai.getRegistrationAuthority();
        if (ra != null) {
            raId = ra.getId();
            if (ra.getName() != null && !ra.getName().isEmpty()) {
                Sign s = ra.getName().get(0);
                if (s != null) raLabel = s.getValue();
            }
        }

        RegistrationState state = ai.getRegistrationState();
        return new AdministeredItemResponse(
                ai.getId(),
                itemId,
                itemType,
                itemLabel,
                identifier,
                version,
                nsId,
                nsLabel,
                raId,
                raLabel,
                state == null ? null : state.getRegistrationStatus(),
                state == null ? null : state.getEffectiveDate(),
                state == null ? null : state.getAdministrativeNote(),
                ai.getCreationDateTime(),
                ai.getLastChangeDateTime(),
                ai.getChangeDescription());
    }
}
