package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig.StrategyStatus;
import io.github.matrixlane.metadataregistry.ext.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement.MappingStatus;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import io.github.matrixlane.metadataregistry.web.entity.NamespacePermissionType;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import io.github.matrixlane.metadataregistry.web.federation.MergeItemChangeType;
import io.github.matrixlane.metadataregistry.web.federation.MergeItemResolution;
import io.github.matrixlane.metadataregistry.web.federation.MergeRequestStatus;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.spi.MetadataBuilderInitializer;

import java.util.List;

/**
 * 为所有 {@code @Enumerated(EnumType.STRING)} 字段注册大小写不敏感的枚举读取描述符。
 *
 * <p>ISO/IEC 11179 各部分对枚举取值的大小写约定不一致：11179-3:2023 表 20（8.7.1.2）
 * 的可接受性为小写，11179-6:2023 的注册状态为首字母大写（Incomplete/…/Standard/…）。
 * 按标准格式导入的历史数据与 Java 枚举常量（大写）不匹配时，Hibernate 默认解析
 * 会抛出 {@code No enum constant}（如数据元列表的 RegistrationStatus.Standard）。
 * 逐一按枚举定制存储映射太脆，这里统一注册 {@link CaseInsensitiveEnumJavaType}：
 * 读取兼容任意大小写，写入仍落库 Java 枚举常量名，不改变既有存储格式。
 *
 * <p>通过 {@code META-INF/services/org.hibernate.boot.spi.MetadataBuilderInitializer}
 * 服务文件由 Hibernate 自动加载（Hibernate 6 的 MetadataBuilderContributor 不再有
 * 配置项入口，初始化器是标准注册途径）。
 *
 * <p>注意：Acceptability 已由 {@code AcceptabilityConverter} 接管（落库标准小写），
 * 不在此列表中。
 */
public class CaseInsensitiveEnumConfig implements MetadataBuilderInitializer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaseInsensitiveEnumConfig.class);

    /** 所有以 @Enumerated(EnumType.STRING) 持久化的枚举类型。 */
    private static final List<Class<? extends Enum<?>>> ENUM_TYPES = List.of(
            SystemRole.class,
            IdentifierCreationMode.class,
            StrategyStatus.class,
            VersionStatus.class,
            DegreeOfConformance.class,
            RegistrationStatus.class,
            MappingStatus.class,
            FhirMappingEquivalence.class,
            FhirMappingType.class,
            InteropMappingStatus.class,
            NamespacePermissionType.class,
            RejectStrategy.class,
            MergeItemChangeType.class,
            MergeItemResolution.class,
            MergeRequestStatus.class);

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void contribute(MetadataBuilder metadataBuilder, StandardServiceRegistry serviceRegistry) {
        metadataBuilder.applyTypes((typeContributions, sr) -> {
            for (Class<? extends Enum<?>> enumType : ENUM_TYPES) {
                typeContributions.contributeJavaType(new CaseInsensitiveEnumJavaType<>((Class) enumType));
            }
        });
        log.info("已为 {} 个 STRING 枚举注册大小写不敏感读取描述符", ENUM_TYPES.size());
    }
}
