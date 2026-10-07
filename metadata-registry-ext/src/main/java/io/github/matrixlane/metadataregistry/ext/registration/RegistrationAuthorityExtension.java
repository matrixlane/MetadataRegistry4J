package io.github.matrixlane.metadataregistry.ext.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Primary 隐式标志卫星实体（应用扩展，ISO/IEC 11179-3:2023 表 24 无此属性），
 * ext schema 存储（团队库 {@code ext.registration_authority}；SQLite 物理名
 * {@code ext_registration_authority}，由 orm/orm-sqlite.xml 覆盖）。
 *
 * <p>标准实体 {@code RegistrationAuthority} 只持有 ISO/IEC 11179-3 标准属性；
 * Primary 标志（初始化向导创建的机构在后台被标为 Primary，个人版据此实施删除
 * 保护）为注册表软件自身扩展，按数据模型分层规则剥离至本卫星实体，
 * 与 {@code mdr.registration_authority} 按 id 一一对应。
 *
 * <p>有意不建立到 RegistrationAuthority 的 JPA 关联：避免卫星加载反向触发
 * 标准实体对象图，读写一律经值键（同 {@code ContactExtension} 范式）。
 * 缺失行语义：无卫星行按 {@code isPrimary=false} 处理（新建机构默认无标志）。
 */
@jakarta.persistence.Entity
@Table(name = "registration_authority")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationAuthorityExtension {

    /** 与 mdr.registration_authority.id 同值（值键，不生成）。 */
    @Id
    private UUID id;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    public RegistrationAuthorityExtension(UUID id, boolean isPrimary) {
        this.id = id;
        this.isPrimary = isPrimary;
    }
}
