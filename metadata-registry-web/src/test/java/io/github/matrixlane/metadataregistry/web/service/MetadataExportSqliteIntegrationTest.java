package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MetadataExportService} 真实 SQLite 集成测试。
 *
 * <p>回归锚点：2026-11-20 事故——导出经 JPA 加载 JOINED 继承根 Item，Hibernate 拼出
 * 106 表 JOIN，超 SQLite 64 表上限，MCP export-data-dictionary 与 REST 元数据导出
 * 全部 500。修复后导出走原生 SQL 标量投影，本测试在真实内存 SQLite 上验证：
 *
 * <ul>
 *   <li>itemType 由 EXISTS 子查询正确推导（ValueDomain vs 非匹配类型）；</li>
 *   <li>名称按 zh-CN 语言优先取 designation，标识符/命名空间标签正确回填；</li>
 *   <li>注册状态过滤、未知类型空集、CSV BOM 与行结构。</li>
 * </ul>
 *
 * <p>种子数据直接经 JdbcTemplate 写入（被测对象本身就是原生 SQL，等价于最小真实库）。
 * 应用连接池统一 autoCommit=false，测试类经 {@code @Transactional} 聚合种子与导出调用，
 * 由 Spring 事务提交/回滚，避免裸 JdbcTemplate 写库被池回收时回滚。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:./target/export-it-test.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "app.seed.demo-data.enabled=false",
        "mdr.mcp.enabled=false"
})
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class MetadataExportSqliteIntegrationTest {

    @Autowired
    private MetadataExportService exportService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseEnvironment env;

    private UUID langZh;
    private UUID namespaceId;
    private UUID itemA;
    private UUID itemB;
    private UUID administeredA;

    @BeforeEach
    void seed() {
        langZh = UUID.randomUUID();
        namespaceId = UUID.randomUUID();
        itemA = UUID.randomUUID();
        itemB = UUID.randomUUID();
        UUID stateA = UUID.randomUUID();
        UUID stateB = UUID.randomUUID();
        administeredA = UUID.randomUUID();
        UUID administeredB = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID srId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO language_identification(id, language_identifier) VALUES (?, ?)",
                env.toJdbcUuid(langZh), "zh-CN");
        jdbcTemplate.update("INSERT INTO item(id) VALUES (?)", env.toJdbcUuid(namespaceId));
        jdbcTemplate.update("INSERT INTO namespace(id) VALUES (?)", env.toJdbcUuid(namespaceId));
        jdbcTemplate.update("INSERT INTO designation(id, value, item_id, language_id) VALUES (?, ?, ?, ?)",
                env.toJdbcUuid(UUID.randomUUID()), "导出集成测试命名空间",
                env.toJdbcUuid(namespaceId), env.toJdbcUuid(langZh));

        // 条目 A：ValueDomain（实体层 dataType 必填），RECORDED，带中文名称/定义/标识符
        UUID dataTypeId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO item(id) VALUES (?)", env.toJdbcUuid(dataTypeId));
        jdbcTemplate.update("INSERT INTO data_type(id) VALUES (?)", env.toJdbcUuid(dataTypeId));
        jdbcTemplate.update("INSERT INTO item(id) VALUES (?)", env.toJdbcUuid(itemA));
        jdbcTemplate.update("INSERT INTO value_domain(id, data_type_id) VALUES (?, ?)",
                env.toJdbcUuid(itemA), env.toJdbcUuid(dataTypeId));
        jdbcTemplate.update("INSERT INTO registered_item(id, item_id) VALUES (?, ?)",
                env.toJdbcUuid(administeredA), env.toJdbcUuid(itemA));
        jdbcTemplate.update("INSERT INTO registration_state(id, registration_status, effective_date, administrative_status) VALUES (?, ?, ?, ?)",
                env.toJdbcUuid(stateA), "RECORDED", Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)), "initial");
        jdbcTemplate.update("INSERT INTO administered_item(id, creation_date_time, registration_state_id, origin) VALUES (?, ?, ?, ?)",
                env.toJdbcUuid(administeredA), Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)),
                env.toJdbcUuid(stateA), "集成测试种子");
        jdbcTemplate.update("INSERT INTO designation(id, value, item_id, language_id) VALUES (?, ?, ?, ?)",
                env.toJdbcUuid(UUID.randomUUID()), "集成测试值域甲",
                env.toJdbcUuid(itemA), env.toJdbcUuid(langZh));
        jdbcTemplate.update("INSERT INTO definition(id, value, item_id, language_id) VALUES (?, ?, ?, ?)",
                env.toJdbcUuid(UUID.randomUUID()), "集成测试值域甲的定义",
                env.toJdbcUuid(itemA), env.toJdbcUuid(langZh));
        jdbcTemplate.update("INSERT INTO scoped_identifier(id, identifier, version, identified_item_id, namespace_id) VALUES (?, ?, ?, ?, ?)",
                env.toJdbcUuid(UUID.randomUUID()), "EXP-IT-001", "1.0",
                env.toJdbcUuid(administeredA), env.toJdbcUuid(namespaceId));

        // 条目 B：ValueDomain，INCOMPLETE，无名称（标签应为 null）
        jdbcTemplate.update("INSERT INTO item(id) VALUES (?)", env.toJdbcUuid(itemB));
        jdbcTemplate.update("INSERT INTO value_domain(id, data_type_id) VALUES (?, ?)",
                env.toJdbcUuid(itemB), env.toJdbcUuid(dataTypeId));
        jdbcTemplate.update("INSERT INTO registered_item(id, item_id) VALUES (?, ?)",
                env.toJdbcUuid(administeredB), env.toJdbcUuid(itemB));
        jdbcTemplate.update("INSERT INTO registration_state(id, registration_status, effective_date) VALUES (?, ?, ?)",
                env.toJdbcUuid(stateB), "INCOMPLETE", Timestamp.valueOf(LocalDateTime.of(2026, 1, 2, 0, 0)));
        jdbcTemplate.update("INSERT INTO administered_item(id, creation_date_time, registration_state_id, origin) VALUES (?, ?, ?, ?)",
                env.toJdbcUuid(administeredB), Timestamp.valueOf(LocalDateTime.of(2026, 1, 2, 0, 0)),
                env.toJdbcUuid(stateB), "集成测试种子");

        // 监管链：组织 + 名称 + 联系人（列级 NOT NULL）+ stewardship_record 挂在条目 A 上
        jdbcTemplate.update("INSERT INTO organization(id) VALUES (?)", env.toJdbcUuid(orgId));
        jdbcTemplate.update("INSERT INTO organization_name(organization_id, value) VALUES (?, ?)",
                env.toJdbcUuid(orgId), "集成测试监管机构");
        jdbcTemplate.update("INSERT INTO contact(id) VALUES (?)", env.toJdbcUuid(contactId));
        jdbcTemplate.update("INSERT INTO stewardship_record(id, organization_id, contact_id) VALUES (?, ?, ?)",
                env.toJdbcUuid(srId), env.toJdbcUuid(orgId), env.toJdbcUuid(contactId));
        jdbcTemplate.update("UPDATE administered_item SET stewardship_record_id = ? WHERE id = ?",
                env.toJdbcUuid(srId), env.toJdbcUuid(administeredA));
    }

    @Test
    @DisplayName("按 ValueDomain 类型导出：2 条，类型推导正确，名称/定义/标识符/监管链回填")
    void export_byValueDomain_returnsSeededItems() {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("ValueDomain"));

        MetadataExportResult result = exportService.export(request);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allMatch(e -> "ValueDomain".equals(e.itemType()));

        AdministeredItemExport item = result.items().stream()
                .filter(e -> administeredA.equals(e.id()))
                .findFirst().orElseThrow();
        assertThat(item.itemLabel()).isEqualTo("集成测试值域甲");
        assertThat(item.itemDefinition()).isEqualTo("集成测试值域甲的定义");
        assertThat(item.registrationState().status()).isEqualTo(RegistrationStatus.RECORDED);
        assertThat(item.registrationState().administrativeStatus()).isEqualTo("initial");
        assertThat(item.identifiers()).hasSize(1);
        assertThat(item.identifiers().get(0).identifier()).isEqualTo("EXP-IT-001");
        assertThat(item.identifiers().get(0).namespaceLabel()).isEqualTo("导出集成测试命名空间");
        assertThat(item.stewardship().organizationName()).isEqualTo("集成测试监管机构");

        AdministeredItemExport unnamed = result.items().stream()
                .filter(e -> !administeredA.equals(e.id()))
                .findFirst().orElseThrow();
        assertThat(unnamed.itemLabel()).isNull();
        assertThat(unnamed.identifiers()).isEmpty();
    }

    @Test
    @DisplayName("按不存在于库中的类型导出：空集")
    void export_byDataElementType_returnsEmpty() {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("DataElement"));

        MetadataExportResult result = exportService.export(request);

        assertThat(result.items()).isEmpty();
        assertThat(result.exportMetadata().totalCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("按注册状态过滤：仅 RECORDED 条目")
    void export_byStatus_returnsOnlyMatching() {
        MetadataExportRequest request = new MetadataExportRequest(
                RegistrationStatus.RECORDED, null, null, null, null, null, null, null, null);

        MetadataExportResult result = exportService.export(request);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).registrationState().status())
                .isEqualTo(RegistrationStatus.RECORDED);
        assertThat(result.items().get(0).itemLabel()).isEqualTo("集成测试值域甲");
    }

    @Test
    @DisplayName("CSV 导出：BOM + 表头 + 数据行含标识符")
    void exportCsv_containsSeededIdentifier() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportCsvToStream(new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("ValueDomain")), baos);

        String csv = baos.toString(StandardCharsets.UTF_8);
        assertThat(csv.charAt(0)).isEqualTo('\ufeff');
        String[] lines = csv.substring(1).split("\n");
        assertThat(lines).hasSize(3); // header + 2 rows
        assertThat(lines[1]).contains("EXP-IT-001");
        assertThat(lines[1]).contains("集成测试值域甲");
    }

    @Test
    @DisplayName("分页导出：首页 size=1 只含 1 条，totalCount 为全量命中数 2")
    void export_paged_firstPage_returnsSliceWithFullTotalCount() throws Exception {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("ValueDomain"), 0, 1);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportJsonToStream(request, baos);

        JsonNode root = new ObjectMapper().readTree(baos.toString(StandardCharsets.UTF_8));
        assertThat(root.get("exportMetadata").get("totalCount").asInt()).isEqualTo(2);
        assertThat(root.get("items")).hasSize(1);
        // 稳定序按 creation_date_time：首页为较早的条目 A
        assertThat(root.get("items").get(0).get("itemLabel").asText()).isEqualTo("集成测试值域甲");
    }

    @Test
    @DisplayName("分页导出：越界页 items 为空且 totalCount 不变")
    void export_paged_outOfRangePage_returnsEmptyItems() throws Exception {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("ValueDomain"), 5, 10);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportJsonToStream(request, baos);

        JsonNode root = new ObjectMapper().readTree(baos.toString(StandardCharsets.UTF_8));
        assertThat(root.get("items")).isEmpty();
        assertThat(root.get("exportMetadata").get("totalCount").asInt()).isEqualTo(2);
    }
}
