package io.github.matrixlane.metadataregistry.web.dto.initwizard;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;

import java.util.List;

/**
 * 初始化向导注册规范步骤携带的属性（ISO/IEC 11179-3:2023 表 28 标量属性，
 * 不含上下文与指称/定义——二者初始化后于设置页维护）。
 * 各字段均可为空，由 RegistrySpecificationService.createDefault 按系统默认值兜底。
 */
public record InitWizardSpecAttributes(
        String webAddress,
        String characterRepertoire,
        String standard,
        DegreeOfConformance degreeOfConformance,
        String conformanceLevel,
        String conformanceLabel,
        String includedFeatures,
        String excludedFeatures,
        String defaultTimeZone,
        List<String> alternativeLanguages,
        List<String> referenceDocumentIdentifierKinds,
        String representationClassSchemeId
) {}
