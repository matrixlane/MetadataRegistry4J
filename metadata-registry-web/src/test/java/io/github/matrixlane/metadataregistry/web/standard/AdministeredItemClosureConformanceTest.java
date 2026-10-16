package io.github.matrixlane.metadataregistry.web.standard;

import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闭环不变式守护：ISO/IEC 11179-3:2023 §9.4.1.2
 *
 * <pre>
 * "As an abstract superclass, Registered_Item shall be instantiated as either an
 *  Administered_Item (9.4.2) or an Attached_Item (9.4.3) but not both."
 * </pre>
 *
 * <p>受管链与内容链在标准中是<b>正交</b>的两条链：
 * {@code Entity → AdministeredItem → RegisteredItem → AdministeredItem/AttachedItem}
 * 与 {@code Entity → Item → DataElement/ValueDomain/...}，二者靠 §7.4.1
 * {@code item_identification} 关联连接而非继承。因此判定闭环要看
 * {@code identified_item} 是否能沿继承链到达受管实例，不能看内容类表的行数。
 *
 * <p>§7.4.1 NOTE 允许条目不带标识符入库（{@code item_identification} 为零或一），
 * 故本不变式<b>只约束已标识条目</b>：无 {@code identified_item} 的 item 不计入违反集。
 *
 * <p>2026-10-09 事故：WS 363/364 基准还原脚本为裸 SQL，未自建受管链，导致 11,177 个
 * 已标识条目既非 Administered_Item 亦非 Attached_Item，注册状态恒为空、操作落入死角，
 * 且因验收只比对行数与字节而长期未被发现。本测试即为该类缺陷的构建期防线。
 *
 * <p>与 {@link StandardInheritanceConformanceTest} 分开：后者守"extends 边须有标准
 * Direct superclass 依据"（继承边），本项守跨表正交链的数据不变式（实例完整性），
 * 语义不同，混在一起会让两类失败的归因困难。
 */
@SpringBootTest(properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite，
        // 使本测试与宿主机数据源配置无关（导入的配置优先级高于非 profile 默认值）。
        "spring.config.import=",
        // 内存 SQLite 测试须改用 orm-sqlite.xml（EXT 物理名 ext_ 前缀），否则与标准表同名冲突。
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:administered-item-closure-test?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@Transactional
@ActiveProfiles("test")
class AdministeredItemClosureConformanceTest {

    /**
     * 违反集查询：已标识（有 scoped_identifier）但未落到 Administered_Item 或
     * Attached_Item 的条目。逐条返回标识符，而非聚合计数——聚合会让失败无法定位。
     */
    private static final String UNCLOSED_ITEMS_SQL = """
            SELECT si.identifier
              FROM registered_item ri
              JOIN scoped_identifier si ON si.identified_item_id = ri.id
              LEFT JOIN administered_item ai ON ai.id = ri.id
              LEFT JOIN attached_item at ON at.id = ri.id
             WHERE ai.id IS NULL AND at.id IS NULL
             ORDER BY si.identifier
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdministeredItemRepository administeredItemRepository;

    @Test
    @DisplayName("已标识条目缺受管实例时被逐条报出")
    void reportsAdministeredItemWithoutAdministeredChain() {
        insertAdministeredItem("DE99.99.999.01");

        assertThat(unclosedIdentifiers()).containsExactly("DE99.99.999.01");
    }

    @Test
    @DisplayName("已标识条目具备 Administered_Item 闭环后不再违反")
    void acceptsAdministeredItemWithAdministeredChain() {
        UUID identifiedItemId = insertAdministeredItem("DE99.99.998.01");
        insertRegisteredItem(identifiedItemId);
        insertAdministeredItem(identifiedItemId, insertRegistrationState());

        assertThat(unclosedIdentifiers()).isEmpty();
    }

    @Test
    @DisplayName("已标识条目具备 Attached_Item 闭环同样合规（§9.4.1.2 二者其一）")
    void acceptsAdministeredItemWithAttachedChain() {
        UUID identifiedItemId = insertAdministeredItem("DE99.99.997.01");
        UUID registeredItemId = insertRegisteredItem(identifiedItemId);
        insertAdministeredItem(registeredItemId, insertRegistrationState());
        jdbc.update("insert into attached_item (id, administered_item_id) values (?, ?)",
                UUID.randomUUID().toString(), registeredItemId.toString());

        assertThat(unclosedIdentifiers()).isEmpty();
    }

    @Test
    @DisplayName("无标识符的 item 不计入违反集（§7.4.1 允许不带标识符入库）")
    void ignoresItemsWithoutIdentification() {
        jdbc.update("insert into item (id) values (?)", UUID.randomUUID().toString());

        assertThat(unclosedIdentifiers()).isEmpty();
    }

    @Test
    @DisplayName("多条违反逐条报出，不折叠为计数")
    void reportsEveryViolatingIdentifier() {
        insertAdministeredItem("DE99.99.995.02");
        insertAdministeredItem("DE99.99.995.01");

        assertThat(unclosedIdentifiers())
                .containsExactly("DE99.99.995.01", "DE99.99.995.02");
    }

    /**
     * 权限校验所用的投影查询在 SQLite 上可对「已补壳」的条目执行。
     *
     * <p>{@code assertCanModifyItem} 过去用 {@code findByItem_Id} 取整个
     * {@code AdministeredItem} 实体，Hibernate 会把 Item 全部子类表与受管项关联对象图
     * 拼进同一条 SQL 的 join，SQLite 侧必超 64 表上限。改为只取归属注册机构标识的
     * 投影后不再展开该对象图（见
     * {@code AdministeredItemRepository#findRegistrationAuthorityIdByItemId}）。
     * 本用例锁定投影在有完整受管链的条目上仍返回正确归属。
     */
    @Test
    @DisplayName("权限校验投影查询对已补壳条目返回归属注册机构（SQLite 可执行）")
    void resolvesRegistrationAuthorityViaProjection() {
        UUID authorityId = insertRegistrationAuthority();
        UUID identifiedItemId = insertAdministeredItem("DE99.99.996.01");
        insertRegisteredItem(identifiedItemId);
        insertAdministeredItem(identifiedItemId, insertRegistrationState(), authorityId);

        Optional<UUID> resolved = administeredItemRepository.findRegistrationAuthorityIdByItemId(itemIdOf(identifiedItemId));

        assertThat(resolved).contains(authorityId);
    }

    @Test
    @DisplayName("无受管实例时投影查询返回空（权限校验据此放行）")
    void projectionReturnsEmptyForUnadministeredItem() {
        UUID identifiedItemId = insertAdministeredItem("DE99.99.994.01");

        assertThat(administeredItemRepository.findRegistrationAuthorityIdByItemId(itemIdOf(identifiedItemId)))
                .isEmpty();
    }

    private List<String> unclosedIdentifiers() {
        return jdbc.queryForList(UNCLOSED_ITEMS_SQL, String.class);
    }

    /**
     * UUID 的存储形态：SQLite 侧 schema 列为 {@code blob}，Hibernate 按 16 字节二进制
     * 写入。故本类的 JdbcTemplate 插入一律用 {@link #uuid(UUID)} 转成同样的二进制形态，
     * 否则 JPA 查询以 blob 绑定参数时会与文本形态的行匹配不上（表现为"行存在但查不到"）。
     */
    private static byte[] uuid(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private UUID insertAdministeredItem(String identifier) {
        UUID itemId = UUID.randomUUID();
        UUID identifiedItemId = UUID.randomUUID();
        jdbc.update("insert into item (id) values (?)", uuid(itemId));
        // 2026-10-09 表合并：identified_item 已并入 registered_item（item_id 列）
        jdbc.update("insert into registered_item (id, item_id) values (?, ?)",
                uuid(identifiedItemId), uuid(itemId));
        jdbc.update("insert into scoped_identifier (id, identified_item_id, identifier, version) "
                        + "values (?, ?, ?, ?)",
                uuid(UUID.randomUUID()), uuid(identifiedItemId), identifier, "1.0");
        return identifiedItemId;
    }

    /**
     * 继承链每层 PK/FK 同名 {@code id}，故返回的 registered_item id 即上层 id。
     */
    private UUID insertRegisteredItem(UUID identifiedItemId) {
        // 表合并后 registered_item 行已由 insertAdministeredItem 建立
        return identifiedItemId;
    }

    private UUID insertAdministeredItem(UUID registeredItemId, UUID registrationStateId) {
        return insertAdministeredItem(registeredItemId, registrationStateId, null);
    }

    private UUID insertAdministeredItem(UUID registeredItemId, UUID registrationStateId, UUID authorityId) {
        // creation_date_time 在 ISO/IEC 11179-3:2023 表 21 中重数为 1（强制），故必须提供
        jdbc.update("insert into administered_item (id, registration_state_id, registration_authority_id, creation_date_time) "
                        + "values (?, ?, ?, ?)",
                uuid(registeredItemId), uuid(registrationStateId),
                authorityId == null ? null : uuid(authorityId), "2024-04-01 00:00:00");
        return registeredItemId;
    }

    private UUID insertRegistrationAuthority() {
        UUID authorityId = UUID.randomUUID();
        // RegistrationAuthority extends Organization（独立根）；SQLite 侧由 Hibernate
        // 建表，organization_identifier 与 international_code_designator 为 NOT NULL
        // （SQL Server 侧二者可空）。
        jdbc.update("insert into registration_authority "
                        + "(id, organization_identifier, international_code_designator) values (?, ?, ?)",
                uuid(authorityId), "ORG-TEST", "ICD-TEST");
        return authorityId;
    }

    private UUID insertRegistrationState() {
        UUID stateId = UUID.randomUUID();
        jdbc.update("insert into registration_state (id, registration_status, effective_date) "
                + "values (?, ?, ?)", uuid(stateId), "PREFERRED_STANDARD", "2024-04-01 00:00:00");
        return stateId;
    }

    private UUID itemIdOf(UUID identifiedItemId) {
        return jdbc.queryForObject("select item_id from registered_item where id = ?",
                (rs, n) -> readUuid(rs.getBytes(1)), uuid(identifiedItemId));
    }

    private static UUID readUuid(byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw);
        return new UUID(buf.getLong(), buf.getLong());
    }
}
