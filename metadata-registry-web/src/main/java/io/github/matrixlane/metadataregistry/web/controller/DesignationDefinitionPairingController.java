package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationDefinitionPairingRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationDefinitionPairingResponse;
import io.github.matrixlane.metadataregistry.web.service.DesignationDefinitionPairingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/items/{itemId}/designation-definition-pairings")
@RequiredArgsConstructor
@Tag(name = "指称-定义配对", description = "DesignationDefinitionPairing 管理")
public class DesignationDefinitionPairingController {

    private final DesignationDefinitionPairingService pairingService;

    @GetMapping
    @Operation(summary = "列出某注册项的所有配对")
    public ApiResponse<List<DesignationDefinitionPairingResponse>> list(@PathVariable UUID itemId) {
        return ApiResponse.ok(pairingService.listByItemId(itemId));
    }

    @PostMapping
    @Operation(summary = "新增配对")
    public ApiResponse<DesignationDefinitionPairingResponse> create(@PathVariable UUID itemId,
                                                                    @Valid @RequestBody DesignationDefinitionPairingRequest request) {
        return ApiResponse.ok(pairingService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新配对")
    public ApiResponse<DesignationDefinitionPairingResponse> update(@PathVariable UUID itemId,
                                                                    @PathVariable UUID id,
                                                                    @Valid @RequestBody DesignationDefinitionPairingRequest request) {
        return ApiResponse.ok(pairingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除配对")
    public ApiResponse<Void> delete(@PathVariable UUID itemId, @PathVariable UUID id) {
        pairingService.delete(id);
        return ApiResponse.ok();
    }
}
