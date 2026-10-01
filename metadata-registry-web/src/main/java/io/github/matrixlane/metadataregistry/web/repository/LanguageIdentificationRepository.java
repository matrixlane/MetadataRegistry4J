package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageIdentificationRepository
        extends JpaRepository<LanguageIdentification, UUID>,
                JpaSpecificationExecutor<LanguageIdentification> {

    Optional<LanguageIdentification> findByLanguageIdentifier(String languageIdentifier);

    /**
     * 按 BCP 47 标识符取全部命中行，不预设唯一。
     *
     * <p>{@code language_identifier} 是 ISO/IEC 11179-3:2023 6.3.7 语言标识自然键，库级唯一性
     * 由 {@code uq_language_identification_identifier} 唯一索引保证（迁移 V20260926001）。派生方法
     * {@code findByLanguageIdentifier} 返回 {@code Optional} 隐含"至多一行"，一旦历史脏数据或迁移
     * 前遗留重复行存在就会抛 {@code NonUniqueResultException}，故业务读取一律走
     * {@code LanguageIdentifierResolver.resolve}。
     */
    List<LanguageIdentification> findAllByLanguageIdentifier(String languageIdentifier);

    boolean existsByLanguageIdentifier(String languageIdentifier);
}
