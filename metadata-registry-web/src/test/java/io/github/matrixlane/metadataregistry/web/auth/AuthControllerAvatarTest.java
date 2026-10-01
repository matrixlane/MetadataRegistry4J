package io.github.matrixlane.metadataregistry.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.dto.AvatarRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AvatarResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerAvatarTest {

    private static final String AVATAR_URL = "/api/v1/auth/me/avatar";
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private SystemSettingService systemSettingService;

    @MockBean
    private AppProperties appProperties;

    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        when(systemSettingService.isInitialized()).thenReturn(true);
    }

    private UsernamePasswordAuthenticationToken createAuthToken(UUID id) {
        AuthenticatedUser principal = new AuthenticatedUser(id, "admin", SystemRole.SUPER_ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }

    private String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("获取头像：已设置头像时返回相同 dataUrl")
    void getAvatar_withAvatar_shouldReturnDataUrl() throws Exception {
        String dataUrl = dataUrl("image/png", new byte[]{1, 2, 3});
        when(authService.getMyAvatar(currentUserId)).thenReturn(dataUrl);
        var auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(get(AVATAR_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dataUrl").value(dataUrl));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("获取头像：未设置头像时返回 dataUrl=null（HTTP 200）")
    void getAvatar_withoutAvatar_shouldReturnNull() throws Exception {
        when(authService.getMyAvatar(currentUserId)).thenReturn(null);
        var auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(get(AVATAR_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dataUrl").doesNotExist());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("上传头像：合法 png dataUrl 返回 HTTP 200")
    void putAvatar_validPng_shouldSucceed() throws Exception {
        String dataUrl = dataUrl("image/png", new byte[]{1, 2, 3});
        var auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(put(AVATAR_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AvatarRequest(dataUrl))))
                    .andExpect(status().isOk());
            verify(authService).updateMyAvatar(currentUserId, dataUrl);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("上传头像：非白名单类型（gif）返回 400")
    void putAvatar_gif_shouldReturnBadRequest() throws Exception {
        String dataUrl = dataUrl("image/gif", new byte[]{1, 2, 3});
        doThrow(new BusinessException("头像仅支持 PNG、JPEG 或 WebP 格式")).when(authService)
                .updateMyAvatar(eq(currentUserId), anyString());
        var auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(put(AVATAR_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AvatarRequest(dataUrl))))
                    .andExpect(status().isBadRequest());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("上传头像：解码后超过 2 MiB 返回 400，2 MiB 边界通过")
    void putAvatar_sizeBoundary_shouldValidate() throws Exception {
        byte[] exactlyMax = new byte[MAX_BYTES];
        String okUrl = dataUrl("image/png", exactlyMax);
        var auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(put(AVATAR_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AvatarRequest(okUrl))))
                    .andExpect(status().isOk());
            verify(authService).updateMyAvatar(currentUserId, okUrl);

            byte[] overMax = new byte[MAX_BYTES + 1];
            String overUrl = dataUrl("image/png", overMax);
            doThrow(new BusinessException("头像大小不能超过 2MB")).when(authService)
                    .updateMyAvatar(eq(currentUserId), eq(overUrl));
            mockMvc.perform(put(AVATAR_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AvatarRequest(overUrl))))
                    .andExpect(status().isBadRequest());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("删除头像：已设置时删除成功")
    void deleteAvatar_shouldClear() throws Exception {
        var auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(delete(AVATAR_URL))
                    .andExpect(status().isOk());
            verify(authService).deleteMyAvatar(currentUserId);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("响应不泄露头像：AvatarResponse 仅含 dataUrl 字段")
    void avatarResponse_shouldOnlyContainDataUrl() throws Exception {
        AvatarResponse response = new AvatarResponse("data:image/png;base64,AAA");
        String json = objectMapper.writeValueAsString(response);
        org.assertj.core.api.Assertions.assertThat(json).contains("dataUrl").doesNotContain("avatar");
    }
}
