package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityDeletionPreview;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrationAuthorityDeletionService;
import io.github.matrixlane.metadataregistry.web.service.RegistrationAuthorityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/registration-authorities")
@RequiredArgsConstructor
@Tag(name = "3. 注册管理 - 注册机构", description = "RegistrationAuthority 实体的 CRUD")
public class RegistrationAuthorityController {

    private final RegistrationAuthorityService service;
    private final RegistrationAuthorityDeletionService deletionService;

    @GetMapping
    @Operation(summary = "分页列出注册机构")
    public ApiResponse<PageResponse<RegistrationAuthorityResponse>> list(@RequestParam(required = false) String q,
                                                                         Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询注册机构详情")
    public ApiResponse<RegistrationAuthorityResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/deletion-preview")
    @Operation(summary = "删除预览：统计级联删除将波及的数据量")
    public ApiResponse<RegistrationAuthorityDeletionPreview> deletionPreview(@PathVariable UUID id) {
        return ApiResponse.ok(deletionService.preview(id));
    }

    @PostMapping
    @Operation(summary = "新增注册机构")
    public ApiResponse<RegistrationAuthorityResponse> create(@Valid @RequestBody RegistrationAuthorityRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.create(request, user.id(), user.role()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新注册机构")
    public ApiResponse<RegistrationAuthorityResponse> update(@PathVariable UUID id,
                                                             @Valid @RequestBody RegistrationAuthorityRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除注册机构")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
