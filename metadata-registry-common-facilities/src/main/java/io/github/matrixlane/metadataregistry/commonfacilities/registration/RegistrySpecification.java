package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Embedded;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ISO/IEC 11179-3:2023, 9.4.10 — Registry_Specification
 *
 * <p>Describes the environment in which a registry operates.</p>
 *
 * <p>直接超类为 {@link Entity}（技术性 {@code @MappedSuperclass}，只提供 UUID 主键）：
 * 9.4.10 无 "Direct superclass" 小节，标准未声明本类为 Item 子类，故不得实现为
 * Item 继承（否则凭空获得指称/定义/注册等非标准能力，并产生指向 {@code mdr.item}
 * 的外键）。表 28 全部为类自有属性。</p>
 */
@StandardReference("ISO/IEC 11179-3:2023, 9.4.10")
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class RegistrySpecification extends Entity {

    /**
     * ISO/IEC 11179-3:2023 表 28(Registry_Specification):name 1 Sign (6.2.10),必备。
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 255))
    })
    private Sign name;

    @Column(name = "comment", length = 4000)
    private String comment;

    @Column(name = "web_address", length = 512)
    private String webAddress;

    @ManyToOne
    @JoinColumn(name = "default_language_id")
    private LanguageIdentification defaultLanguage;

    @ManyToMany
    @JoinTable(
            name = "registry_specification_alternative_language",
            joinColumns = @JoinColumn(name = "registry_specification_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id")
    )
    private List<LanguageIdentification> alternativeLanguages = new ArrayList<>();

    /** 表 28:character_repertoire 1,必备。 */
    @Column(name = "character_repertoire", nullable = false, length = 255)
    private String characterRepertoire;

    @ElementCollection
    @JoinTable(name = "registry_specification_reference_doc_id_kind",
            joinColumns = @JoinColumn(name = "registry_specification_id"))
    @Column(name = "identifier_kind", length = 255)
    private List<String> referenceDocumentIdentifierKinds = new ArrayList<>();

    @Column(name = "representation_class_scheme_id", length = 255)
    private String representationClassSchemeId;

    @ManyToOne
    @JoinColumn(name = "context_id")
    private Context context;

    @Column(name = "standard", length = 255)
    private String standard;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree_of_conformance", length = 32)
    private DegreeOfConformance degreeOfConformance;

    @Column(name = "conformance_level", length = 255)
    private String conformanceLevel;

    @Column(name = "conformance_label", length = 255)
    private String conformanceLabel;

    @Column(name = "included_features", length = 4000)
    private String includedFeatures;

    @Column(name = "excluded_features", length = 4000)
    private String excludedFeatures;

    /**
     * ISO/IEC 11179-3:2023 表 28:default_time_zone 0..1 Datetime(仅时、分,
     * 格式 "-0230":前导符号、时分无分隔、小时前导零)。
     * <b>妥协声明</b>:实现保持 String 存储,不加时区偏移语义,仅校验 [-+]HHMM 格式。
     */
    @Column(name = "default_time_zone", length = 8)
    private String defaultTimeZone;

    /**
     * 设置默认时区,校验 [-+]HHMM 格式(如 "+0800"、"-0230");null 放行(0..1)。
     */
    public void setDefaultTimeZone(String defaultTimeZone) {
        if (defaultTimeZone != null && !defaultTimeZone.matches("[+-][0-9]{2}[0-9]{2}")) {
            throw new IllegalArgumentException(
                    "default_time_zone 必须为 [-+]HHMM 格式(如 \"-0230\"),实际: " + defaultTimeZone);
        }
        this.defaultTimeZone = defaultTimeZone;
    }
}
