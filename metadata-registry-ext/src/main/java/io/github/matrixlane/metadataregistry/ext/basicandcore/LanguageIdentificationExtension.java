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
 * LanguageIdentification 语言名称/地区名称的卫星实体(扩展，非标准模型)。
 *
 * <p>标准实体 {@code LanguageIdentification}只持有
 * 语言标识组成属性;语言名称(language_name)、地区名称(territory_name)为注册表
 * 软件自身的展示扩展,剥离至本卫星实体,按 language_id 一一对应。
 *
 * <p>有意不建立到 LanguageIdentification 的 JPA 关联:避免卫星加载反向触发
 * 标准实体对象图,读写一律经值键。缺失行语义:名称为纯展示字段,响应组装时
 * 记录缺失或名称为空返回 null(前端展示为空),应用代码不做名称运行时解析;
 * 预置名称数据仅存在于 SQL 迁移脚本。
 *
 * <p>团队库(SQL Server/达梦)表与标准表同名不同 schema(ext.language_identification);
 * SQLite 无 schema 概念,由 orm/orm-sqlite.xml 覆盖为物理名 ext_language_identification。
 * 单语设计(默认中文):每语言一条记录;未来如需按界面语言多语言显示,可加
 * display_locale 列升级为 1:N。
 */
@jakarta.persistence.Entity
@Table(name = "language_identification")
@Getter
@Setter
@NoArgsConstructor
public class LanguageIdentificationExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "language_id", nullable = false, unique = true)
    private UUID languageId;

    @Column(name = "language_name")
    private String languageName;

    @Column(name = "territory_name")
    private String territoryName;

    public LanguageIdentificationExtension(UUID languageId) {
        this.languageId = languageId;
    }
}
