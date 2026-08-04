package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;

import java.util.List;
import java.util.UUID;

/**
 * 合并包中的条目快照：全局逻辑身份 + 完整导出内容。
 *
 * @param remoteItemKey  全局逻辑身份（来源 RAI + 来源标识符）
 * @param remoteItemUuid 来源实例内 UUID（仅供回执对照）
 * @param rai            来源注册机构标识
 * @param identifier     来源标识符
 * @param version        来源版本
 * @param versionNumber  源端版本号（增量与合并基推导用）
 * @param export         条目完整导出快照
 * @param designations   全部命名（语言标识 + 文本）
 * @param definitions    全部定义（语言标识 + 文本）
 * @param sourceNamespaceGlobalId            来源命名空间的全局标识（全局命名空间下
 *                                           共享 UUID 的 Scoped_Identifier.identifier，
 *                                           跨实例命名空间映射键）
 * @param sourceNamespaceName                来源命名空间名称（目的方建副本用）
 * @param sourceNamespaceNamingAuthorityName 来源命名空间的 naming_authority 名称
 *                                           （目的方副本记录真实来源机构用）
 */
public record BundleItemSnapshot(
        String remoteItemKey,
        UUID remoteItemUuid,
        String rai,
        String identifier,
        String version,
        Integer versionNumber,
        AdministeredItemExport export,
        List<LangValue> designations,
        List<LangValue> definitions,
        String sourceNamespaceGlobalId,
        String sourceNamespaceName,
        String sourceNamespaceNamingAuthorityName
) {}
