package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Namespace 标识符治理设置的卫星实体(扩展,非 ISO/IEC 11179 标准模型)。
 *
 * <p>承载命名空间级标识符/版本治理:版本生成策略、标识符输入模式、
 * 版本输入模式、手动标识校验正则。标准实体 {@code Namespace} 只保留标准
 * 属性;本卫星实体与其按 {@code namespace_id} 一一对应,物理上独立成表,
 * 阶段 3 起归入 EXT schema。
 *
 * <p>有意不建立到 Namespace 的 JPA 关联:避免卫星加载反向触发标准实体
 * 对象图,读写一律经 {@code namespaceId} 值键。缺失卫星行语义等同字段
 * 时代的列默认值(SEMVER / AUTO_WITH_OVERRIDE / AUTO_WITH_OVERRIDE / null)。
 */
@jakarta.persistence.Entity
@Table(name = "namespace_identifier_settings")
@Getter
@Setter
@NoArgsConstructor
public class NamespaceIdentifierSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "namespace_id", nullable = false, unique = true)
    private UUID namespaceId;

    /**
     * 版本生成策略(如 SEMVER / SEQUENTIAL / DATE)。
     * 命名空间级设置,供该命名空间下条目版本自动递增生效。
     */
    @Column(name = "versioning_policy", length = 32)
    private String versioningPolicy = "SEMVER";

    /**
     * 标识符输入模式(AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY)。
     * 控制该命名空间下标识符允许自动生成、手动覆盖还是纯手动。
     */
    @Column(name = "identifier_input_mode", length = 32)
    private String identifierInputMode = "AUTO_WITH_OVERRIDE";

    /**
     * 版本号输入模式(AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY),独立于标识符。
     * AUTO 模式下版本号留空时按 versioningPolicy 生成初始版本。
     */
    @Column(name = "version_input_mode", length = 32)
    private String versionInputMode = "AUTO_WITH_OVERRIDE";

    /**
     * 手动标识校验正则(可空)。配置后,手动填写的标识符必须匹配该正则。
     */
    @Column(name = "manual_identifier_pattern", length = 255)
    private String manualIdentifierPattern;
}
