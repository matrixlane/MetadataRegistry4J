package io.github.matrixlane.metadataregistry.ext.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * 默认注册人员标志卫星实体（应用扩展，ISO/IEC 11179-3 无此概念），
 * ext schema 存储（团队库 {@code ext.registrar}；SQLite 物理名
 * {@code ext_registrar}，由 orm/orm-sqlite.xml 覆盖）。
 *
 * <p>personal-no-login-registrar-default：原 {@code account.active_registrar_id}
 * （激活缓存）与 {@code contact_account_binding.is_default}（默认联系人）两个概念
 * 收敛为唯一的"默认注册人员"标志——每个账号的一系列 Registrar 身份中的默认者，
 * 是浏览器端激活状态的回退目标，也是提交信息默认预填的来源。
 *
 * <p>标准实体 {@code Registrar} 只持有 ISO/IEC 11179-3 标准属性；本卫星与
 * {@code mdr.registrar} 按 id 一一对应，有意不建 JPA 关联，读写经值键。
 * 缺失行语义：无卫星行 = 非默认（新建 Registrar 默认无标志）；
 * 应用层保证同一账号绑定的 Registrar 中至多一个 {@code is_default=true}。
 */
@jakarta.persistence.Entity
@Table(name = "registrar")
@Getter
@Setter
@NoArgsConstructor
public class RegistrarExtension {

    /** 与 mdr.registrar.id 同值（值键，不生成）。 */
    @Id
    private UUID id;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    public RegistrarExtension(UUID id, boolean isDefault) {
        this.id = id;
        this.isDefault = isDefault;
    }
}
