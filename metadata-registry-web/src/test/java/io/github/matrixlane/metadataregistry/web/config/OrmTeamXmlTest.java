package io.github.matrixlane.metadataregistry.web.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * orm-team.xml 漂移守卫:26 个 EXT 实体全限定名可达、表名与实体解析一致、
 * 全部落在 ext schema。清单依据 design.md 第 3 节(含 V20260901015 补迁的 interop 两表)。
 */
class OrmTeamXmlTest {

    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("io.github.matrixlane.metadataregistry.ext.classification.ClassificationScheme", "classification_scheme"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.classification.ClassificationNode", "classification_node"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig", "identifier_strategy_config"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.identification.SequenceRegistry", "identifier_sequence_registry"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory", "identifier_version_history"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle", "scoped_identifier_lifecycle"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings", "namespace_identifier_settings"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding", "contact_account_binding"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance", "language_governance"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.designationanddefinition.NamingConventionRules", "naming_convention_rules"),
            Map.entry("io.github.matrixlane.metadataregistry.ext.generation.AiGenerationRecord", "ai_generation_record"),
            Map.entry("io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account", "account"),
            Map.entry("io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme", "mapping_scheme"),
            Map.entry("io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscription", "mapping_subscription"),
            Map.entry("io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping", "fhir_element_mapping"),
            Map.entry("io.github.matrixlane.metadataregistry.itemmapping.entity.InteropMapping", "interop_mapping"),
            Map.entry("io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataSet", "interop_data_set"),
            Map.entry("io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement", "interop_data_element"),
            Map.entry("io.github.matrixlane.metadataregistry.web.federation.ItemLink", "item_link"),
            Map.entry("io.github.matrixlane.metadataregistry.web.federation.MergeRequest", "merge_request"),
            Map.entry("io.github.matrixlane.metadataregistry.web.federation.MergeRequestItem", "merge_request_item"),
            Map.entry("io.github.matrixlane.metadataregistry.web.federation.RegistryNode", "registry_node"),
            Map.entry("io.github.matrixlane.metadataregistry.web.federation.SyncWatermark", "sync_watermark"),
            Map.entry("io.github.matrixlane.metadataregistry.web.entity.NamespacePermission", "namespace_permission"),
            Map.entry("io.github.matrixlane.metadataregistry.web.entity.SystemSetting", "system_setting"),
            Map.entry("io.github.matrixlane.metadataregistry.web.entity.VersionHistory", "version_history"));

    @Test
    void ormTeamXmlCoversExactlyTheExtEntities() throws Exception {
        List<String> classes = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/orm/orm-team.xml")) {
            assertThat(in).as("orm-team.xml 必须存在于类路径").isNotNull();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList entities = doc.getDocumentElement().getElementsByTagName("entity");
            for (int i = 0; i < entities.getLength(); i++) {
                Element entity = (Element) entities.item(i);
                Element table = (Element) entity.getElementsByTagName("table").item(0);
                assertThat(table.getAttribute("schema")).isEqualTo("ext");
                classes.add(entity.getAttribute("class"));
            }
        }

        assertThat(classes).containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());

        for (String className : classes) {
            Class<?> type = Class.forName(className);
            assertThat(resolveTableName(type)).isEqualTo(EXPECTED.get(className));
        }
    }

    private static String resolveTableName(Class<?> type) {
        jakarta.persistence.Table table = type.getAnnotation(jakarta.persistence.Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name();
        }
        // Spring 默认隐式命名:CamelCase → snake_case
        return type.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
