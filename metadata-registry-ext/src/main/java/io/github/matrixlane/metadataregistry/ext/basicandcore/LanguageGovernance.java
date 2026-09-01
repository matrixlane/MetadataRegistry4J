package io.github.matrixlane.metadataregistry.ext.basicandcore;

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
 * LanguageIdentification 语言治理属性的卫星实体(扩展,非 ISO/IEC 11179 标准模型)。
 *
 * <p>标准实体 {@code LanguageIdentification}(ISO/IEC 11179-3:2023, 5.3.2)只持有
 * 语言标识组成属性;enabled(语言在注册表中是否启用)为注册表软件自身的治理扩展,
 * 剥离至本卫星实体(表 language_governance,按 language_id 一一对应)。
 *
 * <p>有意不建立到 LanguageIdentification 的 JPA 关联:避免卫星加载反向触发
 * 标准实体对象图,读写一律经值键。缺失行语义等同 enabled=true(与原字段默认值一致)。
 * 阶段 3 起归入 EXT schema。
 */
@jakarta.persistence.Entity
@Table(name = "language_governance")
@Getter
@Setter
@NoArgsConstructor
public class LanguageGovernance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "language_id", nullable = false, unique = true)
    private UUID languageId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    public LanguageGovernance(UUID languageId) {
        this.languageId = languageId;
    }
}
