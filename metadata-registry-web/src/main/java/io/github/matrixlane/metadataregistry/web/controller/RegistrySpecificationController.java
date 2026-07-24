package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.registryspec.RegistrySpecificationRequest;
import io.github.matrixlane.metadataregistry.web.dto.registryspec.RegistrySpecificationResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrySpecificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/registry-specifications")
@RequiredArgsConstructor
@Tag(name = "0. 系统管理 - 注册规范", description = "RegistrySpecification 实体的 CRUD")
public class RegistrySpecificationController {

    private final RegistrySpecificationService service;

    @GetMapping
    @Operation(summary = "查询当前的注册规范")
    public ApiResponse<RegistrySpecificationResponse> get() {
        RegistrySpecification spec = service.getSpecification();
        return ApiResponse.ok(RegistrySpecificationResponse.from(spec));
    }

    @GetMapping("/has-metadata")
    @Operation(summary = "查询系统中是否已登记元数据")
    public ApiResponse<Boolean> hasMetadata() {
        return ApiResponse.ok(service.hasMetadata());
    }

    @PostMapping
    @Operation(summary = "创建注册规范")
    public ApiResponse<RegistrySpecificationResponse> create(@Valid @RequestBody RegistrySpecificationRequest request) {
        RegistrySpecification entity = new RegistrySpecification();
        applyRequest(entity, request);
        RegistrySpecification saved = service.create(entity);
        return ApiResponse.ok(RegistrySpecificationResponse.from(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新注册规范")
    public ApiResponse<RegistrySpecificationResponse> update(@PathVariable UUID id,
                                                             @Valid @RequestBody RegistrySpecificationRequest request) {
        RegistrySpecification entity = new RegistrySpecification();
        applyRequest(entity, request);
        RegistrySpecification saved = service.update(id, entity);
        return ApiResponse.ok(RegistrySpecificationResponse.from(saved));
    }

    private void applyRequest(RegistrySpecification entity, RegistrySpecificationRequest request) {
        entity.setRegistryName(request.registryName());
        entity.setIdentifierPrefix(request.identifierPrefix());
        entity.setAutoGenerateIdentifier(request.autoGenerateIdentifier());
        entity.setIdentifierPattern(request.identifierPattern());
        entity.setVersioningPolicy(request.versioningPolicy());
        entity.setMandatoryClassification(request.mandatoryClassification());
        entity.setDefaultLanguage(request.defaultLanguage());
        entity.setDescription(request.description());
    }
}
