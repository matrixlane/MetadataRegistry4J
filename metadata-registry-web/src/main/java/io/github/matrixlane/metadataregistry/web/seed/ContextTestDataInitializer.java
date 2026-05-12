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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
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
public class ContextTestDataInitializer implements CommandLineRunner {

    private final ContextRepository contextRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final NamespaceRepository namespaceRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ContextService contextService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        long oldCount = contextRepository.count();
        if (oldCount > 0) {
            deleteAllContextsViaJdbc();
            log.info("已清空 {} 条旧 Context 数据", oldCount);
        }

        LanguageIdentification zh = findOrCreateLanguage("zh-CN", "Hans", "CN");
        LanguageIdentification en = findOrCreateLanguage("en-US", "Latn", "US");

        Namespace ns = namespaceRepository.findAll().stream().findFirst().orElse(null);
        RegistrationAuthority ra = registrationAuthorityRepository.findAll().stream().findFirst().orElse(null);

        createContext(zh, en, ns, ra, "业务上下文", "Business Context",
                "用于描述业务场景中的语义环境。", "The semantic environment used to describe business scenarios.");
        createContext(zh, en, ns, ra, "技术上下文", "Technical Context",
                "用于描述技术实现层面的语义环境。", "The semantic environment used to describe technical implementation aspects.");
        createContext(zh, en, ns, ra, "临床上下文", "Clinical Context",
                "用于医疗健康领域的语义环境。", "The semantic environment used in healthcare and clinical domains.");
        createContext(zh, en, ns, ra, "地理上下文", "Geographic Context",
                "用于描述地理位置相关的语义环境。", "The semantic environment related to geographic locations.");
        createContext(zh, en, ns, ra, "法律上下文", "Legal Context",
                "用于描述法律法规相关的语义环境。", "The semantic environment related to laws and regulations.");
        createContext(zh, en, ns, ra, "教育上下文", "Educational Context",
                "用于描述教育教学相关的语义环境。", "The semantic environment related to education and teaching.");
        createContext(zh, en, ns, ra, "金融上下文", "Financial Context",
                "用于描述金融投资相关的语义环境。", "The semantic environment related to finance and investment.");
        createContext(zh, en, ns, ra, "制造上下文", "Manufacturing Context",
                "用于描述生产制造相关的语义环境。", "The semantic environment related to manufacturing and production.");

        log.info("上下文测试数据创建完成，当前共 {} 条", contextRepository.count());
    }

    /**
     * 通过 JDBC 按外键依赖顺序级联删除所有 Context 数据。
     * identified_item.id 与 item.id 是分离的，需先查后删。
     */
    private void deleteAllContextsViaJdbc() {
        List<UUID> itemIds = jdbcTemplate.queryForList("SELECT id FROM context", UUID.class);
        if (itemIds.isEmpty()) return;

        String itemIn = inClause(itemIds.size());
        List<UUID> identifiedItemIds = jdbcTemplate.queryForList(
                "SELECT id FROM identified_item WHERE item_id IN (" + itemIn + ")", UUID.class, itemIds.toArray());
        String identifiedIn = identifiedItemIds.isEmpty() ? null : inClause(identifiedItemIds.size());

        // 子表 -> 父表，遵循 FK 约束顺序
        deleteWhereIn("submission_record_registered_item", "registered_item_id", identifiedItemIds, identifiedIn);
        deleteWhereIn("designation_context", "context_id", itemIds, itemIn);
        deleteWhereIn("definition_context", "context_id", itemIds, itemIn);
        deleteWhereIn("designation", "item_id", itemIds, itemIn);
        deleteWhereIn("definition", "item_id", itemIds, itemIn);
        deleteWhereIn("scoped_identifier", "identified_item_id", identifiedItemIds, identifiedIn);
        deleteWhereIn("attached_item", "administered_item_id", identifiedItemIds, identifiedIn);

        List<UUID> stateIds = identifiedIn == null ? List.of()
                : jdbcTemplate.queryForList(
                        "SELECT registration_state_id FROM administered_item WHERE id IN (" + identifiedIn + ")",
                        UUID.class, identifiedItemIds.toArray());
        String stateIn = stateIds.isEmpty() ? null : inClause(stateIds.size());

        deleteWhereIn("administered_item", "id", identifiedItemIds, identifiedIn);
        deleteWhereIn("registered_item", "id", identifiedItemIds, identifiedIn);
        deleteWhereIn("registration_state", "id", stateIds, stateIn);
        deleteWhereIn("identified_item", "id", identifiedItemIds, identifiedIn);
        deleteWhereIn("context", "id", itemIds, itemIn);
        deleteWhereIn("item", "id", itemIds, itemIn);
    }

    private void deleteWhereIn(String table, String column, List<UUID> ids, String inClause) {
        if (inClause == null || ids.isEmpty()) return;
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN (" + inClause + ")", ids.toArray());
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
