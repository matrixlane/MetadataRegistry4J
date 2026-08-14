package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指称在顶层请求体中已做非空校验（见 DataElementRequest 等），
 * 注册子对象的 designations 仅为兼容字段，不得再强制非空，
 * 否则前端（不在 registration 内携带 designations）的创建请求会被 422 拒绝。
 */
class ItemRegistrationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void nullDesignations_passesValidation() {
        var req = new ItemRegistrationRequest(UUID.randomUUID(), null, null, null, null, null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void emptyDesignations_passesValidation() {
        var req = new ItemRegistrationRequest(UUID.randomUUID(), "id-1", "1.0.0", List.of(), List.of());
        assertThat(validator.validate(req)).isEmpty();
    }
}
