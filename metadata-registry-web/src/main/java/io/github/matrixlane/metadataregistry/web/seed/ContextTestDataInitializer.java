package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextRequest;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.ContextService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 启动时初始化上下文（Context）测试数据。仅当 Context 表为空时执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ContextTestDataInitializer implements CommandLineRunner {

    private final ContextRepository contextRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final NamespaceRepository namespaceRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ContextService contextService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;

    @Override
    @Transactional
    public void run(String... args) {
        long oldCount = contextRepository.count();
        if (oldCount > 0) {
            // 幂等：已有上下文数据时跳过，避免每次启动删除并重建用户/合并来的数据
            log.info("已有 {} 条 Context 数据，跳过测试数据初始化", oldCount);
            return;
        }

        LanguageIdentification zh = findOrCreateLanguage("zh-CN", "Hans", "CN");
        LanguageIdentification en = findOrCreateLanguage("en-US", "Latn", "US");

        Namespace ns = namespaceRepository.findAll().stream().findFirst().orElse(null);
        RegistrationAuthority ra = registrationAuthorityRepository.findAll().stream().findFirst().orElse(null);

        createContext(zh, en, ns, ra, "业务上下文", "Business_Context",
                "用于描述业务场景中的语义环境。", "The semantic environment used to describe business scenarios.");
        createContext(zh, en, ns, ra, "技术上下文", "Technical_Context",
                "用于描述技术实现层面的语义环境。", "The semantic environment used to describe technical implementation aspects.");
        createContext(zh, en, ns, ra, "临床上下文", "Clinical_Context",
                "用于医疗健康领域的语义环境。", "The semantic environment used in healthcare and clinical domains.");
        createContext(zh, en, ns, ra, "地理上下文", "Geographic_Context",
                "用于描述地理位置相关的语义环境。", "The semantic environment related to geographic locations.");
        createContext(zh, en, ns, ra, "法律上下文", "Legal_Context",
                "用于描述法律法规相关的语义环境。", "The semantic environment related to laws and regulations.");
        createContext(zh, en, ns, ra, "教育上下文", "Educational_Context",
                "用于描述教育教学相关的语义环境。", "The semantic environment related to education and teaching.");
        createContext(zh, en, ns, ra, "金融上下文", "Financial_Context",
                "用于描述金融投资相关的语义环境。", "The semantic environment related to finance and investment.");
        createContext(zh, en, ns, ra, "制造上下文", "Manufacturing_Context",
                "用于描述生产制造相关的语义环境。", "The semantic environment related to manufacturing and production.");

        log.info("上下文测试数据创建完成，当前共 {} 条", contextRepository.count());
    }

    /**
     * 通过 JDBC 按外键依赖顺序级联删除所有 Context 数据。
     * identified_item.id 与 item.id 是分离的，需先查后删。
     */
    private void deleteAllContextsViaJdbc() {
        List<UUID> itemIds = queryUuids("SELECT id FROM " + t("context"));
        if (itemIds.isEmpty()) return;

        String itemIn = inClause(itemIds.size());
        List<UUID> identifiedItemIds = queryUuids(
                "SELECT id FROM " + t("identified_item") + " WHERE item_id IN (" + itemIn + ")", uuidParams(itemIds));
        String identifiedIn = identifiedItemIds.isEmpty() ? null : inClause(identifiedItemIds.size());

        // 子表 -> 父表，遵循 FK 约束顺序
        deleteWhereIn(t("submission_record_registered_item"), "registered_item_id", identifiedItemIds, identifiedIn);
        deleteWhereIn(t("designation_context_relation"), "context_id", itemIds, itemIn);
        deleteWhereIn(t("definition_context_relation"), "context_id", itemIds, itemIn);
        deleteWhereIn(t("designation"), "item_id", itemIds, itemIn);
        deleteWhereIn(t("definition"), "item_id", itemIds, itemIn);
        deleteWhereIn(t("scoped_identifier"), "identified_item_id", identifiedItemIds, identifiedIn);
        deleteWhereIn(t("attached_item"), "administered_item_id", identifiedItemIds, identifiedIn);

        List<UUID> stateIds = identifiedIn == null ? List.of()
                : queryUuids(
                        "SELECT registration_state_id FROM " + t("administered_item") + " WHERE id IN (" + identifiedIn + ")",
                        uuidParams(identifiedItemIds));
        String stateIn = stateIds.isEmpty() ? null : inClause(stateIds.size());

        deleteWhereIn(t("administered_item"), "id", identifiedItemIds, identifiedIn);
        deleteWhereIn(t("registered_item"), "id", identifiedItemIds, identifiedIn);
        deleteWhereIn(t("registration_state"), "id", stateIds, stateIn);
        deleteWhereIn(t("identified_item"), "id", identifiedItemIds, identifiedIn);
        deleteWhereIn(t("context"), "id", itemIds, itemIn);
        deleteWhereIn(t("item"), "id", itemIds, itemIn);
    }

    /**
     * 表名按当前方言/schema 限定（SQL Server: "mdr"."x"；SQLite: "x"）。
     */
    private String t(String table) {
        return databaseEnvironment.qualify(table);
    }

    /**
     * 原生 JDBC 查询 UUID 列：兼容 SQL Server 的 UNIQUEIDENTIFIER 与 SQLite 的 BLOB 存储。
     */
    private List<UUID> queryUuids(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> DatabaseEnvironment.toUuid(rs.getObject(1)), args);
    }

    private void deleteWhereIn(String table, String column, List<UUID> ids, String inClause) {
        if (inClause == null || ids.isEmpty()) return;
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (" + inClause + ")", uuidParams(ids));
    }

    /**
     * UUID 参数按当前方言转换为可绑定形式（DM/SQLite 用 byte[16]，SQL Server 用字符串）。
     */
    private Object[] uuidParams(List<UUID> ids) {
        return ids.stream().map(databaseEnvironment::toJdbcUuid).toArray();
    }

    private String inClause(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private LanguageIdentification findOrCreateLanguage(String identifier, String script, String territory) {
        return languageRepository.findByLanguageIdentifier(identifier).orElseGet(() -> {
            LanguageIdentification lang = new LanguageIdentification();
            lang.setLanguageIdentifier(identifier);
            lang.setScriptIdentifier(script);
            lang.setGeopoliticalTerritoryIdentifier(territory);
            return languageRepository.save(lang);
        });
    }

    private void createContext(LanguageIdentification zh, LanguageIdentification en,
                               Namespace ns, RegistrationAuthority ra,
                               String zhLabel, String enLabel, String zhDef, String enDef) {
        List<TextLine> designations = List.of(
                new TextLine(zh.getId(), zhLabel, null, null),
                new TextLine(en.getId(), enLabel, null, null)
        );
        List<TextLine> definitions = List.of(
                new TextLine(zh.getId(), zhDef, null, null),
                new TextLine(en.getId(), enDef, null, null)
        );

        ItemRegistrationRequest registration = null;
        if (ns != null && ra != null) {
            String identifier = enLabel.toUpperCase().replace(" ", "-");
            registration = new ItemRegistrationRequest(
                    ns.getId(), identifier, "1.0", ra.getId(),
                    designations, definitions);
        }

        ContextRequest request = new ContextRequest(designations, definitions, registration);
        contextService.create(request);
    }
}
