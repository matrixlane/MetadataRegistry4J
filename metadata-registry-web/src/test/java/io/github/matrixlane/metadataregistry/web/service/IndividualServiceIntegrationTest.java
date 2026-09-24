package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.AuthService;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualRequest;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualResponse;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite,
        // 使本测试与宿主机数据源配置无关(导入的配置优先级高于非 profile 默认值)。
        "spring.config.import=",
        // 覆盖 mapping-resources：用户主目录 application-local.yml 可能注入 orm-team.xml（SQL Server 专用）；
        // 内存 SQLite 测试须改用 orm-sqlite.xml（EXT 物理名 ext_ 前缀），否则与标准表同名冲突。
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:individual-service-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@Transactional
@ActiveProfiles("test")
class IndividualServiceIntegrationTest {

    @Autowired
    private IndividualService individualService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IndividualRepository individualRepository;

    @Test
    void list_shouldExcludeDisabledUserIndividuals() {
        // 1. Create an independent individual
        Individual ind1 = new Individual();
        ind1.getName().setValue("Independent Individual");
        ind1 = individualRepository.save(ind1);

        // 2. Create an individual linked to an enabled user
        Individual ind2 = new Individual();
        ind2.getName().setValue("Enabled User Individual");
        ind2 = individualRepository.save(ind2);

        Account user1 = new Account();
        user1.setUsername("user1_" + UUID.randomUUID().toString().substring(0, 8));
        user1.setPasswordHash("pass");
        user1.setEnabled(true);
        user1.setIndividual(ind2);
        accountRepository.save(user1);
 
        // 3. Create an individual linked to a disabled user
        Individual ind3 = new Individual();
        ind3.getName().setValue("Disabled User Individual");
        ind3 = individualRepository.save(ind3);
 
        Account user2 = new Account();
        user2.setUsername("user2_" + UUID.randomUUID().toString().substring(0, 8));
        user2.setPasswordHash("pass");
        user2.setEnabled(false);
        user2.setIndividual(ind3);
        accountRepository.save(user2);

        // 4. Query the list of individuals
        Page<IndividualResponse> result = individualService.list(null, PageRequest.of(0, 100));

        // 5. Verify results: ind1 and ind2 must be present, ind3 must be excluded!
        List<UUID> ids = result.getContent().stream().map(IndividualResponse::id).toList();
        assertThat(ids).contains(ind1.getId(), ind2.getId());
        assertThat(ids).doesNotContain(ind3.getId());
    }

    @Test
    void myIndividual_shouldCreateAndLinkCorrect() {
        // Create user
        Account user = new Account();
        user.setUsername("user3_" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("pass");
        user.setEnabled(true);
        user = accountRepository.save(user);

        // Verify my individual is initially null
        IndividualResponse res = authService.getMyIndividual(user.getId());
        assertThat(res).isNull();

        // Create individual setting request
        IndividualRequest request = new IndividualRequest(
                "My Name", null, "My Title", "My Address",
                List.of("test@test.com"), List.of("1234567")
        );

        // Update profile
        IndividualResponse updatedRes = authService.updateMyIndividual(user.getId(), request);
        assertThat(updatedRes).isNotNull();
        assertThat(updatedRes.identifier()).isEqualTo(user.getUsername() + "-individual");
        assertThat(updatedRes.name()).isEqualTo("My Name");
        assertThat(updatedRes.title()).isEqualTo("My Title");
        assertThat(updatedRes.mailAddress()).isEqualTo("My Address");
        assertThat(updatedRes.emailAddress()).containsExactly("test@test.com");
        assertThat(updatedRes.phoneNumber()).containsExactly("1234567");

        // Verify it is updated in DB
        Account userAfter = accountRepository.findById(user.getId()).orElseThrow();
        assertThat(userAfter.getIndividual()).isNotNull();
        assertThat(userAfter.getIndividual().getId()).isEqualTo(updatedRes.id());
        assertThat(userAfter.getIndividual().getName().getValue()).isEqualTo("My Name");
    }
}
