package io.github.matrixlane.metadataregistry.web.dto.dec;

import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainType;

import java.util.UUID;

/**
 * 数据元概念响应中的"概念域标签"：含 id、名称与 kind，用于前端 picker 按 kind 渲染。
 */
public record ConceptualDomainKindLabel(
        UUID id,
        String label,
        ConceptualDomainType type) {
}