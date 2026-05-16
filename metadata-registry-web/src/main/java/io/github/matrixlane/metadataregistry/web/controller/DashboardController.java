package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "概览", description = "Dashboard 统计信息")
public class DashboardController {

    private final DataElementRepository dataElementRepository;
    private final ObjectClassRepository objectClassRepository;
    private final PropertyRepository propertyRepository;
    private final ValueDomainRepository valueDomainRepository;
    private final ConceptualDomainRepository conceptualDomainRepository;
    private final DataTypeRepository dataTypeRepository;
    private final OrganizationRepository organizationRepository;
    private final ContextRepository contextRepository;

    @GetMapping
    @Operation(summary = "获取 Dashboard 统计数据")
    public ApiResponse<Map<String, Long>> getStats() {
        return ApiResponse.ok(Map.of(
                "dataElements", dataElementRepository.count(),
                "objectClasses", objectClassRepository.count(),
                "properties", propertyRepository.count(),
                "valueDomains", valueDomainRepository.count(),
                "conceptualDomains", conceptualDomainRepository.count(),
                "dataTypes", dataTypeRepository.count(),
                "organizations", organizationRepository.count(),
                "contexts", contextRepository.count()
        ));
    }
}
