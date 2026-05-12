package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ClassificationScheme;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClassificationSchemeRepository extends JpaRepository<ClassificationScheme, UUID> {

    Page<ClassificationScheme> findBySchemeNameContainingIgnoreCase(String q, Pageable pageable);
}
