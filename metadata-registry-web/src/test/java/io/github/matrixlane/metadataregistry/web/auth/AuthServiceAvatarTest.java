package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.service.ContactAccountBindingService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceAvatarTest {

    @Mock
    private AccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private ContactAccountBindingService contactAccountBindingService;

    @Mock
    private SystemSettingService systemSettingService;

    @InjectMocks
    private AuthService authService;

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private UUID userId;
    private Account account;

    @BeforeEach
    void setUp() {
        new File("emergency-code.txt").delete();
        userId = UUID.randomUUID();
        account = new Account();
        account.setId(userId);
        account.setUsername("admin");
        account.setSysRole(SystemRole.SUPER_ADMIN);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(account));
    }

    @AfterEach
    void tearDown() {
        new File("emergency-code.txt").delete();
    }

    private String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("上传头像：用户无 Individual 记录时先创建再写入 avatar")
    void updateAvatar_firstTime_shouldCreateIndividual() {
        String dataUrl = dataUrl("image/png", new byte[]{1, 2, 3});
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateMyAvatar(userId, dataUrl);

        verify(individualRepository, org.mockito.Mockito.times(2)).save(any(Individual.class));
        assertThat(account.getIndividual()).isNotNull();
        assertThat(account.getIndividual().getAvatar()).isEqualTo(dataUrl);
    }

    @Test
    @DisplayName("上传头像：已有 Individual 时直接更新 avatar")
    void updateAvatar_existing_shouldUpdate() {
        Individual individual = new Individual();
        account.setIndividual(individual);
        String dataUrl = dataUrl("image/webp", new byte[]{9, 9});
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.updateMyAvatar(userId, dataUrl);

        assertThat(individual.getAvatar()).isEqualTo(dataUrl);
    }

    @Test
    @DisplayName("上传头像：非白名单前缀（gif）抛 BusinessException")
    void updateAvatar_gif_shouldThrow() {
        assertThatThrownBy(() -> authService.updateMyAvatar(userId, dataUrl("image/gif", new byte[]{1})))
                .isInstanceOf(BusinessException.class);
        verify(individualRepository, never()).save(any(Individual.class));
    }

    @Test
    @DisplayName("上传头像：非 base64 data URL 抛 BusinessException")
    void updateAvatar_notBase64_shouldThrow() {
        assertThatThrownBy(() -> authService.updateMyAvatar(userId, "data:image/png;base64,!!!not-base64!!!"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("上传头像：解码后 2MiB+1 抛异常，恰好 2MiB 通过")
    void updateAvatar_sizeBoundary_shouldValidate() {
        assertThatThrownBy(() -> authService.updateMyAvatar(userId, dataUrl("image/png", new byte[MAX_BYTES + 1])))
                .isInstanceOf(BusinessException.class);

        Individual individual = new Individual();
        account.setIndividual(individual);
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> inv.getArgument(0));
        String okUrl = dataUrl("image/jpeg", new byte[MAX_BYTES]);
        authService.updateMyAvatar(userId, okUrl);
        assertThat(individual.getAvatar()).isEqualTo(okUrl);
    }

    @Test
    @DisplayName("获取头像：未设置返回 null，已设置返回 dataUrl")
    void getAvatar_shouldReturnValue() {
        assertThat(authService.getMyAvatar(userId)).isNull();

        Individual individual = new Individual();
        individual.setAvatar("data:image/png;base64,AAA");
        account.setIndividual(individual);
        assertThat(authService.getMyAvatar(userId)).isEqualTo("data:image/png;base64,AAA");
    }

    @Test
    @DisplayName("保存基本资料：不得覆盖已存头像，且响应不携带 avatar 字段")
    void updateMyIndividual_shouldNotTouchAvatar() throws Exception {
        Individual individual = new Individual();
        individual.setAvatar("data:image/png;base64,KEEP");
        account.setIndividual(individual);
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = authService.updateMyIndividual(userId,
                new io.github.matrixlane.metadataregistry.web.dto.individual.IndividualRequest(
                        "张三", null, "架构师", null, null, null));

        assertThat(individual.getAvatar()).isEqualTo("data:image/png;base64,KEEP");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
        assertThat(json).doesNotContain("avatar").doesNotContain("dataUrl");
    }

    @Test
    @DisplayName("删除头像：清除 avatar；未设置时幂等成功")
    void deleteAvatar_shouldBeIdempotent() {
        Individual individual = new Individual();
        individual.setAvatar("data:image/png;base64,AAA");
        account.setIndividual(individual);
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.deleteMyAvatar(userId);
        assertThat(individual.getAvatar()).isNull();

        authService.deleteMyAvatar(userId);
        assertThat(individual.getAvatar()).isNull();
    }
}
