package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.seed.StaticDataPresetService;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * setup 创建顺序的纯 Mockito 单测：注册规范必须先于注册机构创建
 * （注册规范是注册治理的先决条件，注册机构创建时即置 Primary）。
 */
@ExtendWith(MockitoExtension.class)
class InitWizardServiceOrderTest {

    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private RegistrarService registrarService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private IdentifierStrategyConfigRepository identifierStrategyConfigRepository;
    @Mock
    private IdentifierStrategyLoader identifierStrategyLoader;
    @Mock
    private RegistrySpecificationService registrySpecificationService;
    @Mock
    private LanguageIdentificationRepository languageRepository;
    @Mock
    private LanguageIdentifierResolver languageIdentifierResolver;
    @Mock
    private StaticDataPresetService staticDataPresetService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DatabaseCleaner databaseCleaner;
    @Mock
    private NamespaceIdentifierSettingsService namespaceIdentifierSettingsService;
    @Mock
    private RegistryContextBootstrapService registryContextBootstrapService;
    @Mock
    private SystemDefaultNamespaceInitializer systemDefaultNamespaceInitializer;
    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private InitWizardService initWizardService;

    @Test
    void setup_createsRegistrySpecificationBeforeRegistrationAuthority() {
        UUID appUserId = UUID.randomUUID();
        Account account = new Account();
        account.setId(appUserId);
        account.setUsername("admin");
        when(accountRepository.findById(appUserId)).thenReturn(Optional.of(account));
        when(registrationAuthorityRepository.save(any(RegistrationAuthority.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(namespaceRepository.save(any(Namespace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(languageIdentifierResolver.resolve(anyString()))
                .thenAnswer(invocation -> Optional.of(new LanguageIdentification()));

        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "TEAM",                 "Order Test RA",
                null, null,
                "0000", "RA", List.of("zh-CN"),
                false, // selfAsRegistrar：关闭，本用例只锁定规范/机构创建顺序
                true,  // createNamespace
                null, null, null, null, null, null,
                true,  // createRegistrySpecification
                null, null, null, null,
                null, null);

        initWizardService.setup(request, appUserId);

        InOrder inOrder = inOrder(registrySpecificationService, registrationAuthorityRepository);
        inOrder.verify(registrySpecificationService).createDefault(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        // setup 对同一 RA 共 save 三次（创建、文档语言、命名空间挂接），全部发生在
        // createDefault 之后；此处锁定的是顺序与首次 save 的 Primary 标志
        ArgumentCaptor<RegistrationAuthority> raCaptor = ArgumentCaptor.forClass(RegistrationAuthority.class);
        inOrder.verify(registrationAuthorityRepository, times(3)).save(raCaptor.capture());

        // 初始化创建的注册机构即 Primary 注册机构
        assertThat(raCaptor.getAllValues().get(0).isPrimary()).isTrue();
    }
}
