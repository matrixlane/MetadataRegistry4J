package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LanguageIdentificationRepository#findAllByLanguageIdentifier(String)} 集成测试。
 *
 * <p>该派生方法是 {@code LanguageIdentifierResolver} 多行防御的数据来源：它必须按
 * {@code language_identifier} 返回<b>全部</b>命中行，而不是像 {@code findByLanguageIdentifier} 那样
 * 在多行时抛 {@code NonUniqueResultException}。因此本测试的核心是"同一 {@code language_identifier}
 * 存在 2 行时返回 2 行"。
 *
 * <p><b>为何能造出重复行</b>：test profile 关闭 Flyway（{@code application-test.yml} 的
 * {@code spring.flyway.enabled=false}）且 {@code ddl-auto=update}，表结构由 Hibernate 依据实体生成；
 * {@link LanguageIdentification#getLanguageIdentifier()} 未声明 {@code unique=true}，故
 * {@code uq_language_identification_identifier} 唯一索引在测试库里<b>不存在</b>——它只由生产迁移
 * {@code V20260926001__Enforce_language_identifier_unique.sql} 建立。多行防御的价值正在于此：
 * 库级约束之前的存量库、绕过约束的写入都可能产生重复行，应用层不得因此崩掉。测试以
 * {@link #uniqueIndexAbsentInTestSchema(String)} 显式断言该前提，schema 若变更会给出可读失败信息。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:language-identification-repo-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml"
})
@ActiveProfiles("test")
class LanguageIdentificationRepositoryTest {

    @Autowired
    private LanguageIdentificationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LanguageIdentification save(String identifier) {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setLanguageIdentifier(identifier);
        return repository.saveAndFlush(lang);
    }

    private void uniqueIndexAbsentInTestSchema(String context) {
        Integer uniqueIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pragma_index_list('language_identification')"
                        + " WHERE origin = 'u' AND name = 'uq_language_identification_identifier'",
                Integer.class);
        assertThat(uniqueIndexes)
                .as("test profile 关闭 Flyway，uq_language_identification_identifier 唯一索引不应存在；"
                        + "若已存在则无法为 %s 造重复行，本测试的多行场景失去意义", context)
                .isZero();
    }

    @Test
    @DisplayName("同一 language_identifier 存在 2 行时返回全部 2 行，不抛 NonUniqueResultException")
    void findAllByLanguageIdentifier_returnsEveryDuplicateRow() {
        uniqueIndexAbsentInTestSchema("多行场景");
        String identifier = "repo-it-dup-" + java.util.UUID.randomUUID();
        save(identifier);
        save(identifier);

        List<LanguageIdentification> matches = repository.findAllByLanguageIdentifier(identifier);

        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(LanguageIdentification::getLanguageIdentifier)
                .containsOnly(identifier);
        assertThat(matches).extracting(LanguageIdentification::getId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("单行命中返回 1 行；无命中返回空列表")
    void findAllByLanguageIdentifier_singleAndNoMatch() {
        String single = "repo-it-single-" + java.util.UUID.randomUUID();
        save(single);

        assertThat(repository.findAllByLanguageIdentifier(single)).hasSize(1);
        assertThat(repository.findAllByLanguageIdentifier("repo-it-absent-" + java.util.UUID.randomUUID()))
                .isEmpty();
    }
}
