package io.github.matrixlane.metadataregistry.web.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accessDenied_withDetailMessage_passesMessageThrough() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleAccessDenied(
                new AccessDeniedException("仅条目所属注册机构的活动 Registrar 或已登记联系人可修改该条目"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo(ApiCodes.FORBIDDEN);
        assertThat(res.getBody().getMessage()).contains("活动 Registrar");
    }

    @Test
    void accessDenied_withBlankMessage_fallsBackToGenericText() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleAccessDenied(
                new AccessDeniedException(""));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).isEqualTo("无权访问此资源");
    }
}
