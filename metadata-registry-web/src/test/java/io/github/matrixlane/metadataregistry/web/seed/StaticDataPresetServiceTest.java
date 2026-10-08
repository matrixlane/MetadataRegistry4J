package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StaticDataPresetService} 单元测试：步骤顺序、幂等语义、全局默认策略与占位管理员。
 */
@ExtendWith(MockitoExtension.class)
class StaticDataPresetServiceTest {

    @Mock
    private LanguagePresetSeeder languagePresetSeeder;
    @Mock
    private RegistrationProcessService registrationProcessService;
    @Mock
    private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseEnvironment databaseEnvironment;
    @Mock
    private IdentifierStrategyLoader identifierStrategyLoader;

    @InjectMocks
    private StaticDataPresetService presetService;

    @BeforeEach
    void setUp() {
        when(databaseEnvironment.qualifyAuto("identifier_strategy_config"))
                .thenReturn("ext.identifier_strategy_config");
    }

    @Test
    @DisplayName("presetAll:按固定顺序执行四个步骤并以 refreshRegistry 收尾")
    void presetAllExecutesStepsInOrder() {
        UUID raId = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(raId);
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of(ra));
        when(accountRepository.count()).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        presetService.presetAll();

        InOrder order = inOrder(languagePresetSeeder,
                jdbcTemplate, accountRepository, registrationProcessService, identifierStrategyLoader);
        order.verify(languagePresetSeeder).reapplyPreset();
        order.verify(jdbcTemplate).queryForObject(
                containsSql("SELECT COUNT(*) FROM ext.identifier_strategy_config"), eq(Integer.class));
        order.verify(accountRepository).count();
        order.verify(registrationProcessService).ensureSeed(raId);
        order.verify(identifierStrategyLoader).refreshRegistry();
        // 已存在全局默认行时不插入
        verify(jdbcTemplate, never()).update(containsSql("INSERT INTO"), any(Object[].class));
    }

    @Test
    @DisplayName("presetAll:全局默认策略缺失时幂等插入 UUID/INTERNAL 行")
    void insertsGlobalDefaultStrategyWhenMissing() {
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of());
        when(accountRepository.count()).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        presetService.presetAll();

        verify(jdbcTemplate).update(
                eq("INSERT INTO ext.identifier_strategy_config (strategy_type, namespace_id, config_json, version, status,"
                        + " is_global_default, created_by, created_at, effective_from) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                eq("UUID"), eq(null), eq("{}"), eq(1), eq("PUBLISHED"),
                eq(true), eq("system"), any(java.sql.Timestamp.class), any(java.sql.Timestamp.class));
    }

    @Test
    @DisplayName("presetAll:账户表为空时预置占位管理员(NO_PASSWORD 明文哨兵)")
    void seedsPlaceholderAdminWhenAccountTableEmpty() {
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of());
        when(accountRepository.count()).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        presetService.presetAll();

        org.mockito.ArgumentCaptor<Account> captor = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("NO_PASSWORD");
        assertThat(captor.getValue().getSysRole()).isEqualTo(SystemRole.SUPER_ADMIN);
    }

    @Test
    @DisplayName("presetAll:账户表非空时跳过占位管理员(出厂流程已重建带密码 admin)")
    void skipsPlaceholderAdminWhenAccountExists() {
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of());
        when(accountRepository.count()).thenReturn(1L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        presetService.presetAll();

        verify(accountRepository, never()).save(any());
    }

    private static String containsSql(String fragment) {
        return org.mockito.ArgumentMatchers.argThat(s -> s != null && s.toString().contains(fragment));
    }
}
