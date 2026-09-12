package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端验证：{@code META-INF/services} 注册的大小写不敏感枚举描述符
 * 在真实 SessionFactory 引导后生效。库中按 ISO/IEC 11179-6:2023 存储的
 * 首字母大写值 {@code Standard} 必须能读回 {@link RegistrationStatus#STANDARD}，
 * 不再抛 {@code No enum constant}（数据元列表 500 的根因）。
 */
@SpringBootTest(properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite，
        // 使本测试与宿主机数据源配置无关（导入的配置优先级高于非 profile 默认值）。
        "spring.config.import=",
        // 置空 mapping-resources：用户主目录 application-local.yml 可能注入 orm-team.xml（SQL Server 专用），内存 SQLite 测试无需 EXT schema 覆盖。
        "spring.jpa.mapping-resources=",
        "spring.datasource.url=jdbc:sqlite:file:ci-enum-persist-test?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@Transactional
@ActiveProfiles("test")
class CaseInsensitiveEnumPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void readsCapitalizedStandardValueFromDatabase() {
        // 绕过 JPA 直接写入标准格式（首字母大写）的历史导入数据
        jdbc.update("insert into registration_state (id, registration_status, effective_date) values (?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), "Standard");

        entityManager.clear();
        RegistrationState state = soleState();

        assertThat(state.getRegistrationStatus()).isEqualTo(RegistrationStatus.STANDARD);
    }

    @Test
    void readsUppercaseJavaEnumNameFromDatabase() {
        // 应用自身写入的大写枚举名也必须继续可读
        jdbc.update("insert into registration_state (id, registration_status, effective_date) values (?, ?, CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), "RECORDED");

        entityManager.clear();
        RegistrationState state = soleState();

        assertThat(state.getRegistrationStatus()).isEqualTo(RegistrationStatus.RECORDED);
    }

    private RegistrationState soleState() {
        // 测试库每次新建且仅插入一行，直接全量读取（id 的存储格式因方言而异，不做按 id 查找）
        return entityManager.createQuery("from RegistrationState", RegistrationState.class)
                .getSingleResult();
    }
}
