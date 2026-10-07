package io.github.matrixlane.metadataregistry.ext.basicandcore;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Contact 与登录账号（Account）绑定的卫星实体（应用扩展，非 ISO/IEC 11179 标准模型）。
 *
 * <p>标准实体 {@code Contact}（ISO/IEC 11179-3:2023, 6.3.5）不持有任何登录账号语义；
 * Contact↔Account 绑定为注册表软件自身扩展，剥离至本卫星实体（表 contact，
 * 与 mdr.contact 按 contact_id 一一对应）。一个 Contact 至多绑定一个账号
 * （contact_id 即主键），一个账号可绑定多个 Contact（account_id 不唯一）。
 *
 * <p>本表由 personal-no-login-registrar-default 变更自 contact_account_binding
 * 整表迁移而来（contact_id, account_id 两列直搬，原 is_default 标志随
 * "默认身份"概念收窄迁至 registrar 卫星表）。
 *
 * <p>有意不建立到 Contact/Account 的 JPA 关联：避免卫星加载反向触发
 * 标准实体对象图，读写一律经值键（同 {@code RegistrationAuthorityExtension} 范式）。
 * 缺失行语义：无卫星行 = 未绑定任何账号。
 */
@jakarta.persistence.Entity
@Table(name = "contact")
@Getter
@Setter
@NoArgsConstructor
public class ContactExtension {

    /** 与 mdr.contact.id 同值（值键，不生成）。Registrar 行的 id 同为 mdr.registrar.id。 */
    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    public ContactExtension(UUID id, UUID accountId) {
        this.id = id;
        this.accountId = accountId;
    }
}
