package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactResponse;
import io.github.matrixlane.metadataregistry.web.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * "我的"联系人身份（add-submitter-contact-identity）：任何登录用户可管理自己的
 * 多个联系人身份（Registrar 身份只读可见，变更走 Registrar 管理流程）。
 */
@RestController
@RequestMapping("/api/v1/my/contacts")
@RequiredArgsConstructor
@Tag(name = "我的联系人身份", description = "当前账号的联系人身份管理（多身份、默认身份）")
public class MyContactsController {

    private final ContactService contactService;

    @GetMapping
    @Operation(summary = "列出当前账号的全部联系人身份（含 Registrar 身份）")
    public ApiResponse<List<MyContactResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(contactService.listMyContacts(user.id()));
    }

    @PostMapping
    @Operation(summary = "新建联系人身份并绑定当前账号（首个身份自动设为默认）")
    public ApiResponse<MyContactResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody MyContactRequest request) {
        return ApiResponse.ok(contactService.createMyContact(user.id(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新自建联系人身份（Registrar 身份不可修改）")
    public ApiResponse<MyContactResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody MyContactRequest request) {
        return ApiResponse.ok(contactService.updateMyContact(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除自建联系人身份（Registrar 身份拒绝；删默认自动递补）")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable UUID id) {
        contactService.deleteMyContact(user.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/set-default")
    @Operation(summary = "将指定联系人身份设为默认")
    public ApiResponse<Void> setDefault(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID id) {
        contactService.setDefaultMyContact(user.id(), id);
        return ApiResponse.ok();
    }
}
